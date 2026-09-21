package dev.vapee.core.quest;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.onlinereward.OnlineRewardProgress;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerListener;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reward.RewardModule;
import dev.vapee.core.reward.RewardResult;
import dev.vapee.core.reward.RewardStatus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class QuestLifecycleHarness {

    private static int checks;

    private QuestLifecycleHarness() {
    }

    public static void main(String[] args) throws Exception {
        testModuleOrderDependenciesAndBoundaries();
        testQuitLifecycle();
        testTaskAndShutdownStructure();
        System.out.println("QuestLifecycleHarness passed " + checks + " checks.");
    }

    private static void testModuleOrderDependenciesAndBoundaries() throws Exception {
        check(CoreModule.class.isAssignableFrom(QuestModule.class),
                "QuestModule is a regular CoreModule");
        check(!ReloadParticipant.class.isAssignableFrom(QuestModule.class),
                "QuestModule is not a sixth ReloadParticipant");
        check(QuestModule.QUEST_FLUSH_INTERVAL_TICKS == 100L,
                "QuestModule owns the central one-hundred-tick flush cadence");
        check(List.of(QuestModule.class.getConstructor(
                        JavaPlugin.class,
                        PlayerModule.class,
                        RewardModule.class
                ).getParameterTypes()).equals(List.of(
                        JavaPlugin.class,
                        PlayerModule.class,
                        RewardModule.class
                )),
                "QuestModule depends only on JavaPlugin, PlayerModule, and RewardModule");

        String coreSource = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        List<String> expectedOrder = List.of(
                "permissionModule", "rankModule", "playerModule", "socialModule", "economyModule",
                "rewardModule", "onlineRewardModule", "questModule", "lobbyModule", "chatModule",
                "privateMessageModule", "presentationModule", "settingsModule", "activityModule",
                "utilityModule", "seatModule", "worldDisplayModule", "blackjackModule",
                "warpModule", "lobbyExperienceModule"
        );
        int previousPosition = -1;
        for (String module : expectedOrder) {
            int position = coreSource.indexOf("moduleManager.register(" + module + ");");
            check(position > previousPosition, module + " has the required Phase-17A order position");
            previousPosition = position;
        }
        check(count(coreSource, "moduleManager.register(") == 20,
                "VapeeCore registers exactly twenty modules");
        check(coreSource.indexOf("onlineRewardModule = new OnlineRewardModule")
                        < coreSource.indexOf("questModule = new QuestModule")
                        && coreSource.indexOf("questModule = new QuestModule")
                        < coreSource.indexOf("lobbyModule = new LobbyModule"),
                "Quest is constructed directly after OnlineReward and before Lobby");
        check(coreSource.contains(
                        "List.of(configService, lobbyModule, chatModule, privateMessageModule, presentationModule)"
                ),
                "reload wiring remains exactly the existing five participants");

        String pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml")).toLowerCase();
        check(!pluginYaml.contains("quests:")
                        && !pluginYaml.contains("questadmin")
                        && !pluginYaml.contains("vapeecore.quest"),
                "Quest adds no command or permission to plugin.yml");
        try (Stream<Path> resources = Files.walk(Path.of("src/main/resources"))) {
            check(resources.noneMatch(path -> path.getFileName().toString().toLowerCase().contains("quest")),
                    "Quest adds no YAML or other configuration resource");
        }

        String questSource = questProductionSource();
        check(!questSource.contains("import dev.vapee.core.economy")
                        && !questSource.contains("import dev.vapee.core.onlinereward")
                        && !questSource.contains("import dev.vapee.core.activity")
                        && !questSource.contains("import dev.vapee.core.presentation")
                        && !questSource.contains("import dev.vapee.core.rank")
                        && !questSource.contains("import dev.vapee.core.permission"),
                "Quest production code has no Economy, OnlineReward, gameplay, presentation, rank, or permission dependency");
        check(!questSource.contains("org.bukkit.command")
                        && !questSource.contains("dev.vapee.core.message")
                        && !questSource.contains("BlockBreakEvent")
                        && !questSource.contains("PlayerMoveEvent"),
                "Quest owns no command, messaging, or gameplay producer hook");
        check(!questSource.contains("new QuestDefinition("),
                "production starts with no concrete quest definition");
    }

    private static void testQuitLifecycle() throws Exception {
        Method questQuit = QuestListener.class.getDeclaredMethod(
                "onPlayerQuit",
                org.bukkit.event.player.PlayerQuitEvent.class
        );
        EventHandler questHandler = questQuit.getAnnotation(EventHandler.class);
        check(questHandler != null && questHandler.priority() == EventPriority.LOWEST,
                "Quest quit flush runs at LOWEST before PlayerListener NORMAL unload");
        Method playerQuit = PlayerListener.class.getDeclaredMethod(
                "onPlayerQuit",
                org.bukkit.event.player.PlayerQuitEvent.class
        );
        check(playerQuit.getAnnotation(EventHandler.class).priority() == EventPriority.NORMAL,
                "existing PlayerListener unload remains at NORMAL priority");

        MemoryRepository repository = new MemoryRepository();
        PlayerService players = new PlayerService(repository, logger());
        QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
        registry.replaceAll(List.of(new QuestDefinition(
                "lifecycle",
                "Lifecycle",
                "Lifecycle test",
                QuestProgressKey.of("test:lifecycle"),
                10L,
                1L
        )));
        QuestService quests = new QuestService(
                registry,
                players,
                (playerId, coins, source, reason) -> new RewardResult(
                        RewardStatus.SUCCESS,
                        coins,
                        OptionalLong.of(coins)
                ),
                logger()
        );
        QuestListener listener = new QuestListener(quests);
        UUID playerId = UUID.randomUUID();
        repository.seed(player(playerId));
        players.loadPlayer(playerId, "Player");
        repository.saves = 0;
        quests.assignQuest(playerId, "lifecycle");
        quests.addProgress(playerId, QuestProgressKey.of("test:lifecycle"), 4L);

        listener.flushPlayer(playerId);
        check(repository.saves == 1
                        && repository.persistedProgress == 4L
                        && !quests.isDirty(playerId),
                "QuestListener delegates one controlled quit save before player unload");
    }

    private static void testTaskAndShutdownStructure() throws IOException {
        String moduleSource = Files.readString(
                Path.of("src/main/java/dev/vapee/core/quest/QuestModule.java")
        );
        check(count(moduleSource, "runTaskTimer(") == 1,
                "QuestModule owns exactly one shared scheduler task and no task per player");
        check(moduleSource.contains("this::flushSafely")
                        && moduleSource.contains("QUEST_FLUSH_INTERVAL_TICKS,\n                    QUEST_FLUSH_INTERVAL_TICKS"),
                "shared task uses the central one-hundred-tick delay and period");

        int cancel = moduleSource.indexOf("flushTask.cancel()");
        int flush = moduleSource.indexOf("flushSafely();", cancel);
        int unregister = moduleSource.indexOf("HandlerList.unregisterAll(questListener)", flush);
        int clear = moduleSource.indexOf("questService.clearDirtyTracking()", unregister);
        check(cancel >= 0 && cancel < flush && flush < unregister && unregister < clear,
                "Quest shutdown cancels task, flushes, unregisters, then clears runtime state");
        check(moduleSource.contains("public QuestService getQuestService()")
                        && moduleSource.contains("public QuestDefinitionRegistry getDefinitionRegistry()"),
                "QuestModule exposes its service and future definition registration boundary");
    }

    private static String questProductionSource() throws IOException {
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/dev/vapee/core/quest"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file));
            }
        }
        return source.toString();
    }

    private static int count(String value, String needle) {
        int occurrences = 0;
        for (int position = value.indexOf(needle); position >= 0;
             position = value.indexOf(needle, position + needle.length())) {
            occurrences++;
        }
        return occurrences;
    }

    private static CorePlayer player(UUID playerId) {
        Instant now = Instant.now();
        return new CorePlayer(
                playerId,
                "Player",
                now,
                now,
                PlayerSettings.defaults(),
                CoinWallet.empty(),
                PlayerSocial.empty(),
                OnlineRewardProgress.uninitialized(),
                PlayerQuestState.empty()
        );
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("QuestLifecycleHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MemoryRepository implements PlayerRepository {

        private final Map<UUID, CorePlayer> players = new HashMap<>();
        private int saves;
        private long persistedProgress;

        private void seed(CorePlayer player) {
            players.put(player.getUniqueId(), player);
        }

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(players.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            saves++;
            PlayerQuestProgress progress = player.getQuestState().snapshot().get("lifecycle");
            persistedProgress = progress == null ? 0L : progress.progress();
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return players.containsKey(uniqueId);
        }
    }
}

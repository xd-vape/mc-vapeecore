package dev.vapee.core.reward;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerListener;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import dev.vapee.core.reload.ReloadParticipant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class RewardLifecycleHarness {

    private static int checks;

    private RewardLifecycleHarness() {
    }

    public static void main(String[] args) throws Exception {
        testModuleRegistrationAndBoundaries();
        testQuitLifecycle();
        testShutdownStructure();
        System.out.println("RewardLifecycleHarness passed " + checks + " checks.");
    }

    private static void testModuleRegistrationAndBoundaries() throws IOException {
        check(CoreModule.class.isAssignableFrom(RewardModule.class),
                "RewardModule is a real CoreModule");
        check(!ReloadParticipant.class.isAssignableFrom(RewardModule.class),
                "RewardModule is not a ReloadParticipant");
        check(RewardModule.FLUSH_INTERVAL_TICKS == 20L,
                "RewardModule owns one-second flush cadence centrally");

        String coreSource = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        List<String> expectedOrder = List.of(
                "permissionModule", "rankModule", "playerModule", "socialModule", "economyModule",
                "rewardModule", "onlineRewardModule", "lobbyModule", "chatModule", "privateMessageModule",
                "presentationModule", "settingsModule", "activityModule", "utilityModule",
                "seatModule", "worldDisplayModule", "blackjackModule", "warpModule",
                "lobbyExperienceModule"
        );
        int previousPosition = -1;
        for (String module : expectedOrder) {
            int position = coreSource.indexOf("moduleManager.register(" + module + ");");
            check(position > previousPosition, module + " has the required module-order position");
            previousPosition = position;
        }
        check(count(coreSource, "moduleManager.register(") == 19,
                "VapeeCore registers exactly nineteen modules after Phase 16C");
        check(coreSource.indexOf("economyModule = new EconomyModule")
                        < coreSource.indexOf("rewardModule = new RewardModule")
                        && coreSource.indexOf("rewardModule = new RewardModule")
                        < coreSource.indexOf("onlineRewardModule = new OnlineRewardModule")
                        && coreSource.indexOf("onlineRewardModule = new OnlineRewardModule")
                        < coreSource.indexOf("lobbyModule = new LobbyModule"),
                "RewardModule remains after Economy and before its OnlineReward consumer");
        check(coreSource.contains(
                        "List.of(configService, lobbyModule, chatModule, privateMessageModule, presentationModule)"
                ),
                "reload remains limited to the existing five participants");

        String pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml")).toLowerCase();
        check(!pluginYaml.contains("vapeecore.reward") && !pluginYaml.contains("reward:"),
                "Reward adds neither command nor permission to plugin.yml");
        try (Stream<Path> resources = Files.walk(Path.of("src/main/resources"))) {
            check(resources.noneMatch(path -> path.getFileName().toString().toLowerCase().contains("reward")),
                    "Reward adds no configuration or persistence resource file");
        }

        String rewardImports = rewardProductionSource();
        check(!rewardImports.contains("import dev.vapee.core.activity")
                        && !rewardImports.contains("import dev.vapee.core.presentation")
                        && !rewardImports.contains("import dev.vapee.core.rank")
                        && !rewardImports.contains("import dev.vapee.core.chat"),
                "Reward production code has no Activity, Presentation, Rank, or Chat dependency");
        check(!rewardImports.contains("org.bukkit.command")
                        && !rewardImports.contains("dev.vapee.core.message"),
                "Reward owns no command or player-facing message service");
    }

    private static void testQuitLifecycle() throws Exception {
        Method rewardQuit = RewardListener.class.getDeclaredMethod(
                "onPlayerQuit",
                org.bukkit.event.player.PlayerQuitEvent.class
        );
        EventHandler rewardHandler = rewardQuit.getAnnotation(EventHandler.class);
        check(rewardHandler != null && rewardHandler.priority() == EventPriority.LOWEST,
                "Reward quit flush runs at LOWEST before PlayerListener NORMAL unload");
        Method playerQuit = PlayerListener.class.getDeclaredMethod(
                "onPlayerQuit",
                org.bukkit.event.player.PlayerQuitEvent.class
        );
        check(playerQuit.getAnnotation(EventHandler.class).priority() == EventPriority.NORMAL,
                "existing PlayerListener quit lifecycle remains at NORMAL");

        MemoryRepository repository = new MemoryRepository();
        PlayerService players = new PlayerService(repository, logger());
        EconomyService economy = new EconomyService(players);
        RewardService rewards = new RewardService(economy, players, logger());
        RewardListener listener = new RewardListener(rewards);
        UUID playerId = UUID.randomUUID();
        repository.seed(player(playerId));
        players.loadPlayer(playerId, "Player");
        repository.saves = 0;
        rewards.grantCoins(playerId, 5L, RewardSource.SYSTEM, "lifecycle:test");

        listener.flushPlayer(playerId);
        check(repository.saves == 1 && repository.persistedBalance == 5L && !rewards.isDirty(playerId),
                "RewardListener delegates quit persistence to flushPlayer before unload");
    }

    private static void testShutdownStructure() throws IOException {
        String moduleSource = Files.readString(
                Path.of("src/main/java/dev/vapee/core/reward/RewardModule.java")
        );
        int cancel = moduleSource.indexOf("flushTask.cancel()");
        int flush = moduleSource.indexOf("flushSafely();", cancel);
        int unregister = moduleSource.indexOf("HandlerList.unregisterAll(rewardListener)", flush);
        int clear = moduleSource.indexOf("rewardService.clearDirtyTracking()", unregister);
        check(cancel >= 0 && cancel < flush && flush < unregister && unregister < clear,
                "Reward shutdown cancels task, flushes, unregisters, then clears runtime state");
        check(moduleSource.contains("runTaskTimer(")
                        && count(moduleSource, "runTaskTimer(") == 1,
                "RewardModule owns exactly one shared synchronous timer");
        check(moduleSource.contains("this::flushSafely"),
                "periodic task contains a lifecycle-safe exception boundary");
        check(moduleSource.contains("public RewardService getRewardService()"),
                "RewardModule exposes its service through constructor-injection architecture");
    }

    private static String rewardProductionSource() throws IOException {
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/dev/vapee/core/reward"))) {
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
                PlayerSocial.empty()
        );
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("RewardLifecycleHarness-" + System.nanoTime());
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
        private long persistedBalance;

        private void seed(CorePlayer player) {
            players.put(player.getUniqueId(), player);
            persistedBalance = player.getWallet().getCoins();
        }

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(players.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            saves++;
            persistedBalance = player.getWallet().getCoins();
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return players.containsKey(uniqueId);
        }
    }
}

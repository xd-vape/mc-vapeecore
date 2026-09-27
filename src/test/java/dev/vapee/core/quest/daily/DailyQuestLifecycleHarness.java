package dev.vapee.core.quest.daily;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.quest.QuestDefinitionRegistry;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

public final class DailyQuestLifecycleHarness {

    private static int checks;

    private DailyQuestLifecycleHarness() {
    }

    public static void main(String[] args) throws Exception {
        check(CoreModule.class.isAssignableFrom(DailyQuestModule.class)
                        && ReloadParticipant.class.isAssignableFrom(DailyQuestModule.class),
                "DailyQuest owns one CoreModule and the sixth ReloadParticipant");
        check(List.of(DailyQuestModule.class.getConstructor(
                        JavaPlugin.class, PlayerModule.class, QuestModule.class
                ).getParameterTypes()).equals(List.of(JavaPlugin.class, PlayerModule.class, QuestModule.class)),
                "DailyQuest depends only on plugin, player, and quest modules");
        check(DailyQuestModule.SYNC_INTERVAL_TICKS == 1200L,
                "one shared sync task runs every 1200 ticks");
        EventHandler join = DailyQuestListener.class.getDeclaredMethod("onPlayerJoin", PlayerJoinEvent.class)
                .getAnnotation(EventHandler.class);
        check(join != null && join.priority() == EventPriority.HIGHEST,
                "daily join sync runs after PlayerListener NORMAL load");
        check(java.util.Arrays.stream(DailyQuestListener.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().toLowerCase().contains("quit")),
                "DailyQuest owns no extra quit-save listener");

        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(count(core, "moduleManager.register(") == 22
                        && core.indexOf("moduleManager.register(questModule)")
                        < core.indexOf("moduleManager.register(dailyQuestModule)")
                        && core.indexOf("moduleManager.register(dailyQuestModule)")
                        < core.indexOf("moduleManager.register(lobbyModule)"),
                "22 modules register DailyQuest directly after Quest");
        check(core.contains("presentationModule, dailyQuestModule)"),
                "DailyQuest is the sixth and last coordinated reload participant");
        String module = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/quest/daily/DailyQuestModule.java"));
        check(count(module, "runTaskTimer(") == 1
                        && module.contains("syncTask.cancel()")
                        && !module.contains("flushPlayer(")
                        && !module.contains("savePlayer("),
                "DailyQuest has one global sync task, cancels it, and has no persistence task");
        String dailySource = Files.readString(Path.of(
                "src/main/java/dev/vapee/core/quest/daily/DailyQuestService.java"));
        check(dailySource.contains("questService.replaceAssignments(")
                        && dailySource.contains("questService.retryPendingRewards(")
                        && !dailySource.contains("RewardService")
                        && !dailySource.contains("EconomyService"),
                "DailyQuest delegates assignment and pending retry without direct reward or economy ownership");
        String pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml")).toLowerCase();
        check(!pluginYaml.contains("dailyquests:") && !pluginYaml.contains("vapeecore.daily"),
                "DailyQuest adds no command or permission");

        Path directory = Files.createTempDirectory("vapeecore-daily-reload-");
        Path file = directory.resolve("daily-quests.yml");
        try {
            Files.writeString(file, yaml("first", 10));
            DailyQuestConfig config = new DailyQuestConfig(file, logger());
            config.initialize();
            QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
            registry.replaceAll(config.getState().definitions());
            DailyQuestConfig.State before = config.getState();
            var oldDefinitions = registry.snapshot();

            Files.writeString(file, yaml("second", 20));
            ReloadPlan plan = DailyQuestModule.prepareReloadPlan(config, registry);
            check(config.getState() == before && registry.snapshot().equals(oldDefinitions),
                    "reload prepare leaves both active config and registry untouched");
            plan.apply();
            check(config.getState() != before && registry.findById("second").isPresent()
                            && registry.findById("first").isEmpty(),
                    "reload apply atomically switches config and definition registry");
            plan.rollback();
            check(config.getState() == before && registry.snapshot().equals(oldDefinitions),
                    "reload rollback restores both previous snapshots");

            Files.writeString(file, yaml("invalid", 0));
            boolean failed = false;
            try {
                DailyQuestModule.prepareReloadPlan(config, registry);
            } catch (IllegalArgumentException expected) {
                failed = true;
            }
            check(failed && config.getState() == before && registry.snapshot().equals(oldDefinitions),
                    "invalid definition fails prepare without partial catalog activation");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        System.out.println("DailyQuestLifecycleHarness passed " + checks + " checks.");
    }

    private static String yaml(String id, int target) {
        return "enabled: true\nquests-per-day: 1\nquests:\n  " + id + ":\n"
                + "    name: Test\n    description: Test\n    progress-key: test:key\n"
                + "    target: " + target + "\n    reward-coins: 1\n";
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("DailyQuestLifecycleHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int position = text.indexOf(needle); position >= 0;
             position = text.indexOf(needle, position + needle.length())) count++;
        return count;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

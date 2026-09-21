package dev.vapee.core.onlinereward;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.reload.ReloadParticipant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class OnlineRewardLifecycleHarness {

    private static int checks;

    private OnlineRewardLifecycleHarness() {
    }

    public static void main(String[] args) throws IOException {
        testModuleBoundaryAndOrder();
        testTaskAndShutdownStructure();
        testFeatureBoundaries();
        System.out.println("OnlineRewardLifecycleHarness passed " + checks + " checks.");
    }

    private static void testModuleBoundaryAndOrder() throws IOException {
        check(CoreModule.class.isAssignableFrom(OnlineRewardModule.class),
                "OnlineRewardModule is a real CoreModule");
        check(!ReloadParticipant.class.isAssignableFrom(OnlineRewardModule.class),
                "OnlineRewardModule is not a sixth ReloadParticipant");
        check(OnlineRewardModule.PROCESS_INTERVAL_TICKS == 20L,
                "OnlineReward processing cadence is exactly twenty ticks");

        String coreSource = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        List<String> expectedOrder = List.of(
                "permissionModule", "rankModule", "playerModule", "socialModule", "economyModule",
                "rewardModule", "onlineRewardModule", "lobbyModule", "chatModule",
                "privateMessageModule", "presentationModule", "settingsModule", "activityModule",
                "utilityModule", "seatModule", "worldDisplayModule", "blackjackModule",
                "warpModule", "lobbyExperienceModule"
        );
        int previousPosition = -1;
        for (String module : expectedOrder) {
            int position = coreSource.indexOf("moduleManager.register(" + module + ");");
            check(position > previousPosition, module + " has the required Phase-16C order position");
            previousPosition = position;
        }
        check(count(coreSource, "moduleManager.register(") == 19,
                "VapeeCore registers exactly nineteen modules");
        check(coreSource.indexOf("rewardModule = new RewardModule")
                        < coreSource.indexOf("onlineRewardModule = new OnlineRewardModule")
                        && coreSource.indexOf("onlineRewardModule = new OnlineRewardModule")
                        < coreSource.indexOf("lobbyModule = new LobbyModule"),
                "OnlineReward is constructed directly after Reward and before Lobby");
        check(coreSource.contains(
                        "List.of(configService, lobbyModule, chatModule, privateMessageModule, presentationModule)"
                ),
                "reload wiring remains exactly the existing five participants");
    }

    private static void testTaskAndShutdownStructure() throws IOException {
        String moduleSource = Files.readString(
                Path.of("src/main/java/dev/vapee/core/onlinereward/OnlineRewardModule.java")
        );
        check(count(moduleSource, "runTaskTimer(") == 1,
                "OnlineRewardModule owns exactly one shared scheduler task");
        check(moduleSource.contains("this::processOnlinePlayers")
                        && moduleSource.contains("PROCESS_INTERVAL_TICKS,\n                    PROCESS_INTERVAL_TICKS"),
                "shared task uses the central twenty-tick delay and period");
        check(moduleSource.contains("getOnlinePlayers()"),
                "shared task iterates the server online-player collection");
        check(moduleSource.contains("getStatistic(Statistic.PLAY_ONE_MINUTE)"),
                "module adapter reads the canonical Minecraft playtime statistic");
        check(moduleSource.contains("catch (RuntimeException exception)")
                        && moduleSource.contains("continuing with other online players"),
                "unexpected per-player failures cannot cancel processing for other players");

        int cancel = moduleSource.indexOf("processingTask.cancel()");
        int clearTask = moduleSource.indexOf("processingTask = null", cancel);
        int clearRenderer = moduleSource.indexOf("messageRenderer = null", clearTask);
        int clearService = moduleSource.indexOf("onlineRewardService = null", clearRenderer);
        check(cancel >= 0 && cancel < clearTask && clearTask < clearRenderer && clearRenderer < clearService,
                "shutdown cancels processing before clearing runtime references");
        check(!moduleSource.contains("Listener") && !moduleSource.contains("savePlayer("),
                "OnlineReward owns neither a quit listener nor a duplicate persistence write");
    }

    private static void testFeatureBoundaries() throws IOException {
        String serviceSource = Files.readString(
                Path.of("src/main/java/dev/vapee/core/onlinereward/OnlineRewardService.java")
        );
        String packageSource;
        try (var files = Files.walk(Path.of("src/main/java/dev/vapee/core/onlinereward"))) {
            StringBuilder source = new StringBuilder();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file));
            }
            packageSource = source.toString();
        }

        check(!packageSource.contains("import dev.vapee.core.economy")
                        && !packageSource.contains("import dev.vapee.core.activity")
                        && !packageSource.contains("import dev.vapee.core.rank")
                        && !packageSource.contains("import dev.vapee.core.permission")
                        && !packageSource.contains("import dev.vapee.core.lobby")
                        && !packageSource.contains("import dev.vapee.core.activity.blackjack"),
                "OnlineReward has no Economy, Activity, Rank, LuckPerms, Lobby, or Blackjack dependency");
        check(serviceSource.contains("RewardSource.PLAYTIME")
                        && serviceSource.contains("online:playtime")
                        && count(serviceSource, "rewardGranter.grantCoins(") == 1,
                "service uses one aggregated PLAYTIME grant with the technical reason");
        check(!serviceSource.contains("savePlayer(") && !serviceSource.contains("OfflinePlayer"),
                "service performs no direct save or offline-player lookup");

        String pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml")).toLowerCase();
        check(!pluginYaml.contains("online-reward") && !pluginYaml.contains("playtime-reward"),
                "Phase 16C adds no command or permission entry");
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        check(config.contains("online-rewards:")
                        && config.contains("interval-minutes: 60")
                        && config.contains("coins: 250"),
                "default resource contains the documented online reward defaults");

        String presentation = Files.readString(
                Path.of("src/main/java/dev/vapee/core/presentation/PresentationRenderer.java")
        );
        check(presentation.contains("getStatistic(Statistic.PLAY_ONE_MINUTE)")
                        && presentation.contains("Placeholder.unparsed(\"playtime\"")
                        && !presentation.contains("next_reward")
                        && !presentation.contains("reward_progress"),
                "existing playtime presentation stays unchanged without reward-progress placeholders");
    }

    private static int count(String value, String needle) {
        int occurrences = 0;
        for (int position = value.indexOf(needle); position >= 0;
             position = value.indexOf(needle, position + needle.length())) {
            occurrences++;
        }
        return occurrences;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

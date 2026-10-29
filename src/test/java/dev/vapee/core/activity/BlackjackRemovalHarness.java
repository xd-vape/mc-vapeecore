package dev.vapee.core.activity;

import static dev.vapee.core.quest.QuestCompletionFixture.DEFAULT;
import static dev.vapee.core.quest.QuestCompletionFixture.proxy;

import dev.vapee.core.activity.location.ActivityArea;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.activity.location.ActivityVenue;
import dev.vapee.core.config.ConfigEvolution;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.i18n.Language;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.quest.QuestCompletionFixture;
import dev.vapee.core.quest.QuestDefinitionRegistry;
import dev.vapee.core.quest.QuestProgressKey;
import dev.vapee.core.quest.QuestService;
import dev.vapee.core.quest.daily.DailyQuestConfig;
import dev.vapee.core.quest.daily.DailyQuestCycleId;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.seat.SeatKey;
import dev.vapee.core.seat.SeatService;
import dev.vapee.core.seat.SeatType;
import dev.vapee.core.worlddisplay.WorldDisplayKey;
import dev.vapee.core.worlddisplay.WorldDisplayService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Artifact assertions and real shared domain behavior; full root lifecycle is also tested on Paper. */
public final class BlackjackRemovalHarness {
    private static int checks;
    private static final List<String> MODULES = List.of(
            "localizationModule", "permissionModule", "rankModule", "playerModule", "socialModule",
            "economyModule", "identityModule", "moderationModule", "friendModule", "presenceModule",
            "clanModule", "rewardModule", "onlineRewardModule", "questModule", "dailyQuestModule",
            "lobbyModule", "visibilityModule", "chatModule", "privateMessageModule", "presentationModule",
            "settingsModule", "activityModule", "utilityModule", "seatModule", "worldDisplayModule",
            "warpModule", "lobbyExperienceModule");

    public static void main(String[] args) throws Exception {
        compositionAndArtifact();
        activity();
        seatsAndDisplays();
        legacyData();
        System.out.println("BlackjackRemovalHarness passed " + checks + " checks.");
    }

    private static void compositionAndArtifact() throws Exception {
        // Independent exact order oracle; runtime acceptance checks the actual enabled module objects.
        String root = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        var matcher = Pattern.compile("moduleManager\\.register\\((\\w+)\\)").matcher(root);
        List<String> actual = new ArrayList<>();
        while (matcher.find()) actual.add(matcher.group(1));
        check(actual.equals(MODULES) && actual.size() == 27, "exact remaining production registration order");
        check(!root.contains("BlackjackModule") && !root.contains("blackjackModule"), "no root Blackjack wiring");
        check(root.contains("List.of(configService, localizationModule, lobbyModule, chatModule, privateMessageModule,\n"
                + "                        presentationModule, dailyQuestModule)"), "seven reload participants and order preserved");
        try (ZipFile jar = new ZipFile("target/vapeecore-1.0-SNAPSHOT.jar")) {
            check(jar.stream().noneMatch(entry -> entry.getName().toLowerCase(java.util.Locale.ROOT).contains("blackjack")),
                    "compiled Core JAR has no Blackjack classes, archive or resources");
            check(jar.getEntry("blackjack.yml") == null, "no bundled table default");
            var descriptor = new PluginDescriptionFile(jar.getInputStream(jar.getEntry("plugin.yml")));
            check(!descriptor.getCommands().containsKey("blackjack"), "no Blackjack command descriptor");
            check(descriptor.getPermissions().stream().noneMatch(p -> p.getName().equals("vapeecore.blackjack.admin")),
                    "no Blackjack permission descriptor");
            for (String path : List.of("activity/ActivityModule", "seat/SeatModule", "worlddisplay/WorldDisplayModule",
                    "quest/QuestProgressReporter", "friend/FriendModule", "clan/ClanModule", "presence/PresenceModule",
                    "player/PlayerModule", "reward/RewardModule", "quest/QuestModule", "quest/daily/DailyQuestModule")) {
                check(jar.getEntry("dev/vapee/core/" + path + ".class") != null, "shared production artifact retained: " + path);
            }
        }
        var catalogs = LocalizationService.bundled(Logger.getAnonymousLogger()).snapshot().bundled();
        for (Language language : Language.values()) {
            var catalog = catalogs.get(language);
            check(catalog.keySet().stream().noneMatch(key -> key.startsWith("blackjack.")
                    || key.equals("activity.blackjack") || key.equals("core.help.blackjack.help.description")),
                    "obsolete translations absent: " + language);
            check(catalog.containsKey("activity.context") && catalog.containsKey("warp.help.section.information")
                    && catalog.containsKey("warp.help.section.management"), "shared vocabulary preserved: " + language);
        }
    }

    private static void activity() {
        var f = new QuestCompletionFixture();
        var service = new ActivityService(f.players, f.logger, () -> true, ignored -> true);
        check(service.getActivityTypeCount() == 0 && service.getActivityType("blackjack").isEmpty(), "no built-in game type");
        var type = new ActivityType() {
            public String getKey() { return "practice"; }
            public int getMinParticipants() { return 1; }
            public int getMaxParticipants() { return 2; }
            public ActivitySession createSession(UUID id, ActivityVenue venue) {
                return new ActivitySession(id, getKey(), venue) { };
            }
        };
        check(service.registerActivityType(type) == ActivityResult.SUCCESS, "generic type registration");
        var venue = new ActivityVenue("sample", "practice", new ActivityArea("world", 0, 0, 0, 10, 10, 10),
                new ActivityPosition("world", 5, 5, 5, 0, 0));
        check(service.registerVenue(venue) == ActivityResult.SUCCESS, "generic venue registration");
        var session = service.createSession("practice", "sample").session().orElseThrow();
        UUID id = f.load();
        Player player = proxy(Player.class, (name, values) -> switch (name) {
            case "getUniqueId" -> id; case "isOnline" -> true; default -> DEFAULT;
        });
        check(service.joinSession(player, session.getSessionId()) == ActivityResult.SUCCESS, "generic participant joins");
        check(session.hasParticipant(id) && service.isParticipating(id), "shared participant authority");
        check(service.activateSession(session.getSessionId()) == ActivityResult.SUCCESS, "generic session activates");
        check(service.closeSession(session.getSessionId(), ActivityLeaveReason.SESSION_CLOSED) == ActivityResult.SUCCESS,
                "generic session closes");
        check(!service.isParticipating(id) && service.getMembershipCount() == 0, "participant cleanup");
        check(service.unregisterVenue("practice", "sample") == ActivityResult.SUCCESS, "venue removal");
        check(service.unregisterActivityType("practice") == ActivityResult.SUCCESS, "type removal");
        service.shutdown();
        check(service.getActivityTypeCount() == 0 && service.getSessionCount() == 0, "shared activity shutdown");
    }

    private static void seatsAndDisplays() throws Exception {
        UUID worldId = UUID.randomUUID();
        World world = proxy(World.class, (name, values) -> switch (name) {
            case "getUID" -> worldId; case "getName" -> "world"; default -> DEFAULT;
        });
        Location location = new Location(world, 2, 65, 2);
        Class<?> seatGateway = Class.forName(SeatService.class.getName() + "$SeatEntityGateway");
        List<UUID> removed = new ArrayList<>();
        Object seatBoundary = proxy(seatGateway, (name, values) -> {
            if (name.equals("spawnAndMount")) return UUID.randomUUID();
            if (name.equals("remove")) { removed.add((UUID) values[0]); return null; }
            return DEFAULT;
        });
        var constructor = SeatService.class.getDeclaredConstructor(BooleanSupplier.class, BooleanSupplier.class,
                Predicate.class, Consumer.class, seatGateway);
        constructor.setAccessible(true);
        var seats = constructor.newInstance((BooleanSupplier) () -> true, (BooleanSupplier) () -> true,
                (Predicate<UUID>) ignored -> true, (Consumer<Runnable>) Runnable::run, seatBoundary);
        UUID owner = UUID.randomUUID();
        SeatKey key = new SeatKey("practice", "one");
        Player player = proxy(Player.class, (name, values) -> name.equals("getUniqueId") ? owner : DEFAULT);
        check(seats.getAssignmentCount() == 0, "no Blackjack seat reservations");
        check(seats.reserve(key, SeatType.MANAGED, owner, location, null), "generic managed seat reserves");
        check(seats.mountReserved(player), "generic managed seat mounts");
        UUID entity = seats.getAssignment(key).orElseThrow().seatEntity().orElseThrow();
        check(seats.releaseOwner("practice") == 1 && removed.contains(entity), "generic seat release removes owned entity");
        check(seats.getAssignmentCount() == 0, "generic seat state empty after release");
        Class<?> displayGateway = Class.forName(WorldDisplayService.class.getName() + "$DisplayGateway");
        UUID displayId = UUID.randomUUID();
        Object displayBoundary = proxy(displayGateway, (name, values) -> switch (name) {
            case "createText" -> displayId;
            case "updateText" -> true;
            case "remove" -> { removed.add((UUID) values[0]); yield null; }
            default -> DEFAULT;
        });
        var displayConstructor = WorldDisplayService.class.getDeclaredConstructor(BooleanSupplier.class, displayGateway);
        displayConstructor.setAccessible(true);
        var displays = displayConstructor.newInstance((BooleanSupplier) () -> true, displayBoundary);
        check(displays.getDisplayCount() == 0, "no Blackjack display handles");
        WorldDisplayKey displayKey = new WorldDisplayKey("practice", "label");
        displays.createText(displayKey, location, Component.text("Practice"), null);
        check(displays.getHandle(displayKey).orElseThrow().entityId().equals(displayId), "shared display identity");
        check(displays.updateText(displayKey, Component.text("Updated")), "generic display update");
        check(displays.removeOwner("practice") == 1 && removed.contains(displayId), "generic display cleanup");
        seats.cleanup(); displays.cleanup();
        check(seats.getAssignmentCount() == 0 && displays.getDisplayCount() == 0, "shared resource cleanup idempotent");
    }

    private static void legacyData() throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-blackjack-removal-");
        var logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        try {
            Path table = directory.resolve("blackjack.yml");
            byte[] historicalTable = "# retained operator table\ntables:\n  old: {enabled: false}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(table, historicalTable);
            new ConfigEvolution(directory, logger, name -> BlackjackRemovalHarness.class.getResourceAsStream("/" + name)).evolveAll();
            check(Arrays.equals(historicalTable, Files.readAllBytes(table)), "startup evolution does not touch historic table data");
            Path config = directory.resolve("daily-quests.yml");
            Files.writeString(config, """
                    config-version: 1
                    enabled: false
                    quests:
                      old_win:
                        name: Legacy win
                        description: Historical game
                        progress-key: blackjack:win
                        target: 10
                        reward-coins: 5
                    """);
            var daily = new DailyQuestConfig(config, logger); daily.initialize();
            check(daily.getState().definitions().getFirst().progressKey().equals(QuestProgressKey.of("blackjack:win")),
                    "legacy progress key still parses under generic quest schema");
            var repository = new FilePlayerRepository(directory.resolve("players"), logger); repository.initialize();
            var players = new PlayerService(repository, logger);
            UUID id = UUID.randomUUID(); players.loadPlayer(id, "Legacy");
            var rewards = new RewardService(new EconomyService(players), players, logger);
            var registry = new QuestDefinitionRegistry(); registry.replaceAll(daily.getState().definitions());
            var quests = new QuestService(registry, players, rewards, logger);
            quests.assignQuest(id, "old_win");
            quests.addProgress(id, QuestProgressKey.of("blackjack:win"), 2);
            var cycle = new DailyQuestCycleId(LocalDate.of(2026, 10, 29));
            players.getPlayer(id).orElseThrow().getDailyQuestState().setCycleId(cycle);
            quests.flushPlayer(id);
            players.savePlayer(id);
            var cold = new FilePlayerRepository(directory.resolve("players"), logger).findByUniqueId(id).orElseThrow();
            check(cold.getQuestState().snapshot().get("old_win").progress() == 2, "historic assigned progress survives disk round trip");
            check(cold.getDailyQuestState().cycleId().orElseThrow().equals(cycle), "historic daily cycle retained");
            quests.addProgress(id, QuestProgressKey.of("practice:win"), 5);
            check(players.getPlayer(id).orElseThrow().getQuestState().snapshot().get("old_win").progress() == 2,
                    "unrelated producer cannot auto-convert or advance Blackjack quest");
            registry.replaceAll(List.of());
            players.savePlayer(id);
            var orphan = repository.findByUniqueId(id).orElseThrow();
            check(orphan.getQuestState().snapshot().equals(cold.getQuestState().snapshot()), "removed definition does not discard historical player progress");
            check(orphan.getWallet().getCoins() == 0, "no compensation or reward replay");
            check(Arrays.equals(historicalTable, Files.readAllBytes(table)), "legacy table file remains exact after all shared operations");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

package dev.vapee.core.player.repository;

import dev.vapee.core.persistence.PersistenceFaults;
import dev.vapee.core.persistence.SafeFileWriter;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsChangeListener;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.player.settings.VisibilityMode;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Real profile files and service commits; expected defaults/paths are independent literals. */
public final class SettingsPersistenceContractHarness {
    private static final String PRESENCE = "settings.friend-presence-notifications";
    private static final String BASE =
            "schema-version: 1\nname: SettingsProbe\nfirst-join: 1000\nlast-join: 2000\n";
    private static final UUID IGNORED = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID VISIBLE = UUID.fromString("00000000-0000-0000-0000-000000000222");
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-settings-contract-");
        try {
            if (args.length == 0) defaultsAndFallback(directory.resolve("defaults"));
            commitsAndFailures(directory.resolve("commits"));
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("SettingsPersistenceContractHarness passed " + checks + " checks.");
    }

    private static void defaultsAndFallback(Path directory) throws Exception {
        PlayerSettings defaults = PlayerSettings.defaults();
        check(defaults.isScoreboardEnabled(), "frozen scoreboard default true");
        check(defaults.isSoundsEnabled(), "frozen sounds default true");
        check(defaults.isPrivateMessagesEnabled(), "frozen private messages default true");
        check(defaults.isFriendRequestsEnabled(), "frozen friend requests default true");
        check(defaults.isFriendPresenceNotificationsEnabled(), "presence default enabled");
        check(
                defaults.getVisibility().getMode() == VisibilityMode.ALL,
                "frozen visibility mode ALL");
        check(
                !defaults.getVisibility().isShowFriends()
                        && !defaults.getVisibility().isShowStaff()
                        && !defaults.getVisibility().isShowAddedUsers()
                        && !defaults.getVisibility().isShowGameParticipants()
                        && defaults.getVisibility().getAddedPlayers().isEmpty(),
                "frozen filters false and selection empty");
        var logger = logger();
        var warnings = new ArrayList<LogRecord>();
        logger.addHandler(
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        warnings.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                });
        var repository = new FilePlayerRepository(directory, logger);
        repository.initialize();
        UUID owner = UUID.randomUUID();
        Path file = directory.resolve(owner + ".yml");
        for (String settings :
                List.of(
                        "",
                        "settings: {}\n",
                        "settings: broken\n",
                        "settings: {friend-presence-notifications: invalid}\n",
                        "settings: {friend-presence-notifications: 'true'}\n",
                        "settings: {friend-presence-notifications: 1}\n",
                        "settings: {friend-presence-notifications: null}\n")) {
            Files.writeString(file, BASE + settings);
            byte[] original = Files.readAllBytes(file);
            check(
                    repository
                            .findByUniqueId(owner)
                            .orElseThrow()
                            .getSettings()
                            .isFriendPresenceNotificationsEnabled(),
                    "missing/invalid presence inherits enabled default: " + settings.strip());
            check(
                    Arrays.equals(original, Files.readAllBytes(file)),
                    "fallback read never rewrites bytes");
        }
        check(
                warnings.stream().anyMatch(record -> record.getMessage().contains(PRESENCE)),
                "invalid Boolean produces path-specific warning");
        Files.writeString(file, BASE + "settings: {friend-presence-notifications: true}\n");
        check(
                repository
                        .findByUniqueId(owner)
                        .orElseThrow()
                        .getSettings()
                        .isFriendPresenceNotificationsEnabled(),
                "literal true deserializes at frozen presence path");
        Files.writeString(file, BASE + "settings: {friend-presence-notifications: false}\n");
        byte[] optedOut = Files.readAllBytes(file);
        check(!repository.findByUniqueId(owner).orElseThrow().getSettings()
                        .isFriendPresenceNotificationsEnabled(),
                "explicit historical false remains disabled regardless of its original intent");
        check(Arrays.equals(optedOut, Files.readAllBytes(file)),
                "reading explicit opt-out never rewrites a profile");
        Files.writeString(file, BASE);
        repository.save(repository.findByUniqueId(owner).orElseThrow());
        var yaml = yaml(file);
        Set<String> expected =
                Set.of(
                        "scoreboard",
                        "sounds",
                        "private-messages",
                        "friend-requests",
                        "friend-presence-notifications",
                        "lobby-players-visible",
                        "visibility.mode",
                        "visibility.show-friends",
                        "visibility.show-staff",
                        "visibility.show-added-users",
                        "visibility.show-game-participants",
                        "visibility.added-players");
        var section = yaml.getConfigurationSection("settings");
        Set<String> leaves =
                section.getKeys(true).stream()
                        .filter(key -> !section.isConfigurationSection(key))
                        .collect(java.util.stream.Collectors.toSet());
        check(
                leaves.equals(expected),
                "exact existing YAML preference paths, no synthetic property");
        check(yaml.getInt("schema-version") == 1, "player schema remains version one");
        var changed = yaml(file);
        changed.set(PRESENCE, false);
        expectFailure(
                () -> PlayerFileSchema.validateOwnedValues(changed, yaml),
                "schema validates actual canonical presence value");
    }

    private static void commitsAndFailures(Path directory) throws Exception {
        var access = new PersistenceFaults(PersistenceFaults.Fault.NONE);
        var repository = new FilePlayerRepository(directory, logger(), access);
        repository.initialize();
        UUID owner = UUID.randomUUID();
        Path file = directory.resolve(owner + ".yml");
        Files.writeString(
                file,
                BASE
                        + "settings:\n  scoreboard: false\n  sounds: false\n"
                        + "  private-messages: false\n  friend-requests: false\n  friend-presence-notifications: false\n"
                        + "  custom: {keep: value}\n  visibility: {mode: FILTERED, show-friends: true, added-players: ['"
                        + VISIBLE
                        + "']}\n"
                        + "economy: {coins: 1234}\nsocial: {ignored: ['"
                        + IGNORED
                        + "']}\n"
                        + "rewards: {online: {processed-playtime-ticks: 123}}\n"
                        + "quests: {daily: {cycle-id: '2026-10-21'}, active: {example: {progress: 7, status: ACTIVE}}}\n"
                        + "extension: {untouched: [one, two]}\n");
        var players = new PlayerService(repository, logger());
        var settings = new PlayerSettingsService(players);
        CorePlayer loaded = players.loadPlayer(owner, "SettingsProbe");
        int[] notifications = {0};
        PlayerSettingsChangeListener observer =
                id -> {
                    try {
                        check(id.equals(owner), "observer gets exact owner");
                        check(
                                yaml(file).getBoolean(PRESENCE),
                                "observer sees durable committed presence=true");
                        check(
                                access.events.contains("atomic"),
                                "observer runs after file replacement");
                        notifications[0]++;
                    } catch (java.io.IOException
                            | org.bukkit.configuration.InvalidConfigurationException exception) {
                        throw new AssertionError(exception);
                    }
                };
        settings.addChangeListener(observer);
        settings.addChangeListener(observer);
        check(
                settings.setFriendPresenceNotificationsEnabled(owner, true),
                "real presence mutation succeeds");
        check(
                notifications[0] == 1
                        && loaded.getSettings().isFriendPresenceNotificationsEnabled(),
                "one post-commit notification, no duplicate subscription");
        check(
                repository
                        .findByUniqueId(owner)
                        .orElseThrow()
                        .getSettings()
                        .isFriendPresenceNotificationsEnabled(),
                "independent repository reload retains true");
        byte[] committed = Files.readAllBytes(file);
        access.events.clear();
        check(
                settings.setFriendPresenceNotificationsEnabled(owner, true),
                "equivalent update retains true result");
        check(
                notifications[0] == 1
                        && access.events.isEmpty()
                        && Arrays.equals(committed, Files.readAllBytes(file)),
                "no-op emits no save or notification");
        UUID unloaded = UUID.randomUUID();
        check(
                !settings.setFriendPresenceNotificationsEnabled(unloaded, true)
                        && settings.getSettings(unloaded).isEmpty()
                        && !Files.exists(directory.resolve(unloaded + ".yml")),
                "unloaded mutation cannot create profile or preference");
        for (var fault :
                List.of(
                        PersistenceFaults.Fault.WRITE,
                        PersistenceFaults.Fault.CLOSE,
                        PersistenceFaults.Fault.INVALID_YAML,
                        PersistenceFaults.Fault.ATOMIC_IO,
                        PersistenceFaults.Fault.REPLACE)) {
            access.fault = fault;
            access.events.clear();
            expectFailure(
                    () -> settings.setFriendPresenceNotificationsEnabled(owner, false),
                    "save fault propagates " + fault);
            check(
                    loaded.getSettings().isFriendPresenceNotificationsEnabled(),
                    "real setter rolls back " + fault);
            check(
                    Arrays.equals(committed, Files.readAllBytes(file)) && notifications[0] == 1,
                    "failed save keeps exact durable bytes and sends no event " + fault);
            check(
                    access.candidate != null
                            && !Files.exists(access.candidate)
                            && !Files.exists(SafeFileWriter.recoveryFile(file)),
                    "fault artifacts cleaned/restored " + fault);
        }
        access.fault = PersistenceFaults.Fault.NONE;
        access.events.clear();
        access.onClosed =
                () -> {
                    try {
                        Files.writeString(
                                access.candidate,
                                Files.readString(access.candidate)
                                        .replace(
                                                "friend-presence-notifications: false",
                                                "friend-presence-notifications: true"));
                    } catch (java.io.IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                };
        expectFailure(
                () -> settings.setFriendPresenceNotificationsEnabled(owner, false),
                "valid YAML with wrong owned value refused");
        check(
                loaded.getSettings().isFriendPresenceNotificationsEnabled()
                        && Arrays.equals(committed, Files.readAllBytes(file))
                        && !access.events.contains("atomic"),
                "candidate validation precedes replacement and preserves runtime/disk");
        access.onClosed = () -> {};
        settings.removeChangeListener(observer);
        int[] independent = {0};
        PlayerSettingsChangeListener broken =
                id -> {
                    throw new IllegalStateException("injected observer failure");
                };
        settings.addChangeListener(broken);
        settings.addChangeListener(id -> independent[0]++);
        Logger observerLogger = Logger.getLogger(PlayerSettingsService.class.getName());
        boolean parentHandlers = observerLogger.getUseParentHandlers();
        observerLogger.setUseParentHandlers(false);
        try {
            check(
                    settings.setFriendPresenceNotificationsEnabled(owner, false),
                    "observer failure cannot invalidate committed setter result");
        } finally {
            observerLogger.setUseParentHandlers(parentHandlers);
        }
        check(
                independent[0] == 1
                        && !yaml(file).getBoolean(PRESENCE)
                        && !loaded.getSettings().isFriendPresenceNotificationsEnabled(),
                "independent observer receives retained durable commit");
        settings.removeChangeListener(broken);
        assertUnrelated(repository.findByUniqueId(owner).orElseThrow(), yaml(file));
        players.unloadPlayer(owner);
        byte[] offline = Files.readAllBytes(file);
        check(
                settings.areFriendRequestsEnabled(owner).isEmpty()
                        && settings.areKnownFriendRequestsEnabled(owner).orElseThrow().equals(false)
                        && !players.isLoaded(owner),
                "known-offline read does not become loaded preference authority");
        check(
                !settings.setFriendPresenceNotificationsEnabled(owner, true)
                        && Arrays.equals(offline, Files.readAllBytes(file)),
                "known-offline mutation still requires loaded profile");
        var reloaded = players.loadPlayer(owner, "SettingsProbe");
        check(
                !reloaded.getSettings().isFriendPresenceNotificationsEnabled(),
                "false survives unload/reload through PlayerService");
        assertUnrelated(reloaded, yaml(file));
    }

    private static void assertUnrelated(CorePlayer player, YamlConfiguration yaml) {
        check(
                !player.getSettings().isScoreboardEnabled()
                        && !player.getSettings().isSoundsEnabled()
                        && !player.getSettings().isPrivateMessagesEnabled()
                        && !player.getSettings().isFriendRequestsEnabled(),
                "other Boolean settings preserved");
        check(
                player.getSettings().getVisibility().getMode() == VisibilityMode.FILTERED
                        && player.getSettings().getVisibility().isShowFriends()
                        && player.getSettings()
                                .getVisibility()
                                .getAddedPlayers()
                                .equals(Set.of(VISIBLE)),
                "visibility mode, filter and explicit target preserved");
        check(
                player.getWallet().getCoins() == 1234 && player.getSocial().isIgnoring(IGNORED),
                "wallet and social state preserved");
        check(
                player.getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow() == 123
                        && player.getQuestState().snapshot().get("example").progress() == 7
                        && player.getDailyQuestState()
                                .cycleId()
                                .orElseThrow()
                                .toString()
                                .equals("2026-10-21"),
                "reward/quest state preserved");
        check(
                yaml.getString("settings.custom.keep").equals("value")
                        && yaml.getStringList("extension.untouched").equals(List.of("one", "two")),
                "unknown extension data preserved");
    }

    private static YamlConfiguration yaml(Path file)
            throws java.io.IOException, org.bukkit.configuration.InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(file));
        return yaml;
    }

    private static Logger logger() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void expectFailure(Runnable action, String reason) {
        try {
            action.run();
            throw new AssertionError(reason);
        } catch (IllegalStateException expected) {
            check(true, reason);
        }
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}

package dev.vapee.core.player.repository;

import dev.vapee.core.persistence.PersistenceFaults;
import dev.vapee.core.persistence.PersistenceFaults.Fault;
import dev.vapee.core.persistence.SafeFileWriter;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.player.settings.VisibilityMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/** Frozen YAML expectations plus real aggregate/service commits in disposable directories. */
public final class PlayerSettingsYamlCodecHarness {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LAST = UUID.fromString("abcdefab-0000-0000-0000-000000000002");
    private static final String BASE = "name: CodecProbe\nfirst-join: 1000\nlast-join: 2000\n";
    private static final String CURRENT = "schema-version: 1\n" + BASE;
    private static final String EXPECTED =
            """
            settings:
              scoreboard: false
              sounds: false
              private-messages: false
              friend-requests: false
              friend-presence-notifications: true
              lobby-players-visible: false
              visibility:
                mode: HIDDEN
                show-friends: true
                show-staff: false
                show-added-users: true
                show-game-participants: false
                added-players:
                - '00000000-0000-0000-0000-000000000001'
                - 'abcdefab-0000-0000-0000-000000000002'
            """;
    private static final List<String> BOOLEAN_PATHS =
            List.of(
                    "scoreboard",
                    "sounds",
                    "private-messages",
                    "friend-requests",
                    "friend-presence-notifications",
                    "lobby-players-visible",
                    "visibility.show-friends",
                    "visibility.show-staff",
                    "visibility.show-added-users",
                    "visibility.show-game-participants");
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-settings-codec-");
        try {
            String mode = args.length == 0 ? "all" : args[0];
            if (mode.equals("all") || mode.equals("defaults"))
                defaultsAndBooleans(root.resolve("defaults"));
            if (mode.equals("all") || mode.equals("write")) canonicalWrite();
            if (mode.equals("all") || mode.equals("legacy") || mode.equals("invalid-mode"))
                visibility(root.resolve("visibility"));
            if (mode.equals("all") || mode.equals("uuid")) uuidPolicy(root.resolve("uuid"));
            if (mode.equals("all") || mode.equals("aggregate"))
                aggregate(root.resolve("aggregate"));
            if (mode.equals("all") || mode.equals("safety"))
                candidateValidation(root.resolve("candidate"));
            if (mode.equals("all") || mode.equals("rollback")) faults(root.resolve("faults"));
            if (mode.equals("all") || mode.equals("migration"))
                migration(root.resolve("migration"));
            if (mode.equals("all") || mode.equals("future"))
                refusedSources(root.resolve("refused"));
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("PlayerSettingsYamlCodecHarness passed " + checks + " checks.");
    }

    private static void defaultsAndBooleans(Path directory) throws Exception {
        Capture capture = new Capture();
        for (String source :
                List.of("", "settings: {}\n", "settings: null\n", "settings: broken\n")) {
            PlayerSettings settings = read(directory, source, capture);
            assertDefaults(settings, "missing/malformed settings");
            check(
                    capture.messages.equals(
                            source.contains("broken")
                                    ? List.of(
                                            warning(
                                                    directory,
                                                    "settings",
                                                    "expected a YAML section"))
                                    : List.of()),
                    "settings section diagnostic exact");
        }
        for (String path : BOOLEAN_PATHS) {
            boolean expected =
                    List.of(
                                    "scoreboard",
                                    "sounds",
                                    "private-messages",
                                    "friend-requests",
                                    "friend-presence-notifications",
                                    "lobby-players-visible")
                            .contains(path);
            for (String raw :
                    List.of("true", "false", "null", "wrong", "'false'", "12", "[]", "{}")) {
                YamlConfiguration input = new YamlConfiguration();
                // Keep YAML's explicit null/type information; do not use getBoolean coercion.
                String text =
                        "settings:\n"
                                + (path.startsWith("visibility.")
                                        ? "  visibility:\n    "
                                                + path.substring("visibility.".length())
                                                + ": "
                                                + raw
                                        : "  " + path + ": " + raw)
                                + "\n";
                PlayerSettings value = read(directory, text, capture);
                PlayerSettingsYamlCodec.write(value, input);
                boolean valid = raw.equals("true") || raw.equals("false");
                check(
                        input.get("settings." + path)
                                .equals(valid ? Boolean.valueOf(raw) : expected),
                        "independent Boolean fallback " + path + "=" + raw);
                check(
                        capture.messages.equals(
                                valid || raw.equals("null")
                                        ? List.of()
                                        : List.of(
                                                warning(
                                                        directory,
                                                        "settings." + path,
                                                        "expected a boolean"))),
                        "Boolean warning frequency/context " + path + "=" + raw);
            }
        }
    }

    private static void assertDefaults(PlayerSettings value, String context) {
        check(value.isScoreboardEnabled(), context + ": scoreboard=true");
        check(value.isSoundsEnabled(), context + ": sounds=true");
        check(value.isPrivateMessagesEnabled(), context + ": private messages=true");
        check(value.isFriendRequestsEnabled(), context + ": friend requests=true");
        check(value.isFriendPresenceNotificationsEnabled(), context + ": presence=true");
        check(value.getVisibility().getMode() == VisibilityMode.ALL, context + ": mode=ALL");
        check(
                !value.getVisibility().isShowFriends()
                        && !value.getVisibility().isShowStaff()
                        && !value.getVisibility().isShowAddedUsers()
                        && !value.getVisibility().isShowGameParticipants()
                        && value.getVisibility().getAddedPlayers().isEmpty(),
                context + ": filters=false selection=empty");
    }

    private static void canonicalWrite() throws Exception {
        PlayerSettings settings = PlayerSettings.defaults();
        settings.setScoreboardEnabled(false);
        settings.setSoundsEnabled(false);
        settings.setPrivateMessagesEnabled(false);
        settings.setFriendRequestsEnabled(false);
        settings.setFriendPresenceNotificationsEnabled(true);
        settings.getVisibility().setMode(VisibilityMode.HIDDEN);
        settings.getVisibility().setShowFriends(true);
        settings.getVisibility().setShowAddedUsers(true);
        settings.getVisibility().addPlayer(OWNER, LAST);
        settings.getVisibility().addPlayer(OWNER, FIRST);
        YamlConfiguration actual = new YamlConfiguration();
        PlayerSettingsYamlCodec.write(settings, actual);
        check(
                leaves(actual).equals(leaves(yaml(EXPECTED))),
                "independent canonical Settings paths and values");
        check(
                actual.getStringList("settings.visibility.added-players")
                        .equals(List.of(FIRST.toString(), LAST.toString())),
                "canonical UUID output lexical order");
        String once = actual.saveToString();
        PlayerSettingsYamlCodec.write(settings, actual);
        check(actual.saveToString().equals(once), "repeated canonical serialization stable");
        PlayerSettingsYamlCodec.write(PlayerSettings.defaults(), actual);
        check(
                actual.isList("settings.visibility.added-players")
                        && actual.getList("settings.visibility.added-players").isEmpty(),
                "empty UUID list explicitly serialized");
        for (VisibilityMode mode : VisibilityMode.values()) {
            settings.getVisibility().setMode(mode);
            PlayerSettingsYamlCodec.write(settings, actual);
            check(
                    actual.get("settings.visibility.mode").equals(mode.name())
                            && actual.get("settings.lobby-players-visible")
                                    .equals(mode == VisibilityMode.ALL),
                    "explicit mode and legacy Boolean writer " + mode);
        }
    }

    private static void visibility(Path directory) throws Exception {
        Capture capture = new Capture();
        for (boolean legacy : List.of(true, false)) {
            String prefix = "settings:\n  sounds: false\n  lobby-players-visible: " + legacy + "\n";
            for (String visibility :
                    List.of(
                            "",
                            "  visibility: {}\n",
                            "  visibility: null\n",
                            "  visibility: broken\n")) {
                PlayerSettings value = read(directory, prefix + visibility, capture);
                check(
                        value.getVisibility().getMode()
                                == (legacy ? VisibilityMode.ALL : VisibilityMode.FILTERED),
                        "legacy missing mode preserves Boolean " + legacy);
                check(!value.isSoundsEnabled(), "invalid visibility preserves unrelated setting");
                check(
                        capture.messages.equals(
                                visibility.contains("broken")
                                        ? List.of(
                                                warning(
                                                        directory,
                                                        "settings.visibility",
                                                        "expected a YAML section"))
                                        : List.of()),
                        "visibility section diagnostics exact");
            }
            for (String mode :
                    List.of(
                            "ALL",
                            "FILTERED",
                            "HIDDEN",
                            "UNKNOWN",
                            "all",
                            "null",
                            "true",
                            "42",
                            "[ALL]",
                            "{}")) {
                boolean valid = List.of("ALL", "FILTERED", "HIDDEN").contains(mode);
                PlayerSettings value =
                        read(
                                directory,
                                prefix
                                        + "  visibility:\n    mode: "
                                        + mode
                                        + "\n    show-friends: true\n",
                                capture);
                check(
                        value.getVisibility().getMode()
                                == (valid ? VisibilityMode.valueOf(mode) : VisibilityMode.HIDDEN),
                        "invalid mode fails closed or explicit mode wins " + mode);
                check(
                        value.getVisibility().isShowFriends(),
                        "mode parsing preserves independent filters");
                check(
                        capture.messages.equals(
                                valid
                                        ? List.of()
                                        : List.of(
                                                warning(
                                                        directory,
                                                        "settings.visibility.mode",
                                                        "expected ALL, FILTERED or HIDDEN; using HIDDEN"))),
                        "mode diagnostic exact including explicit null " + mode);
            }
        }
    }

    private static void uuidPolicy(Path directory) throws Exception {
        Capture capture = new Capture();
        String text =
                "settings:\n  visibility:\n    added-players:\n"
                        + "    - '"
                        + LAST.toString().toUpperCase(java.util.Locale.ROOT)
                        + "'\n"
                        + "    - '"
                        + FIRST
                        + "'\n    - '"
                        + FIRST
                        + "'\n    - '"
                        + OWNER
                        + "'\n"
                        + "    - '1-1-1-1-1'\n    - invalid\n    - 12\n";
        PlayerSettings value = read(directory, text, capture);
        check(
                value.getVisibility().getAddedPlayers().equals(Set.of(FIRST, LAST)),
                "UUID policy rejects self/noncanonical/invalid and deduplicates");
        check(
                capture.messages.equals(
                        List.of(
                                warning(
                                        directory,
                                        "settings.visibility.added-players",
                                        "own UUID is not allowed"),
                                warning(
                                        directory,
                                        "settings.visibility.added-players",
                                        "non-canonical UUID entry"),
                                warning(
                                        directory,
                                        "settings.visibility.added-players",
                                        "invalid UUID entry"),
                                warning(
                                        directory,
                                        "settings.visibility.added-players",
                                        "expected a UUID string entry"))),
                "UUID diagnostics exact order frequency and owner/source");
        for (String raw : List.of("wrong", "{}", "12")) {
            value =
                    read(
                            directory,
                            "settings: {visibility: {added-players: " + raw + "}}\n",
                            capture);
            check(
                    value.getVisibility().getAddedPlayers().isEmpty(),
                    "invalid optional UUID list nonfatal");
            check(
                    capture.messages.equals(
                            List.of(
                                    warning(
                                            directory,
                                            "settings.visibility.added-players",
                                            "expected a list of UUID strings"))),
                    "invalid list diagnostic");
        }
    }

    private static void aggregate(Path directory) throws Exception {
        PersistenceFaults access = new PersistenceFaults(Fault.NONE);
        FilePlayerRepository repository = repository(directory, access);
        Path file = directory.resolve(OWNER + ".yml");
        String source =
                CURRENT
                        + EXPECTED.replace("sounds: false", "sounds: true")
                        + "economy: {coins: 456789, custom: {keep: coins}}\n"
                        + "social: {ignored: ['"
                        + LAST
                        + "', '"
                        + FIRST
                        + "'], custom: keep}\n"
                        + "rewards: {online: {processed-playtime-ticks: 4567, custom: keep}}\n"
                        + "quests:\n  daily: {cycle-id: '2026-10-25', custom: keep}\n  custom: keep\n  active:\n"
                        + "    z_active: {progress: 17, status: ACTIVE, closed-extra: remove}\n"
                        + "    m_pending: {progress: 88, status: REWARD_PENDING}\n"
                        + "    a_complete: {progress: 30, status: COMPLETED}\n"
                        + "future-root: {value: preserved, nested: [one, null, 42]}\n";
        // These are extension siblings, not new production preferences.
        source =
                source.replace(
                                "settings:\n",
                                "settings:\n  future-extension: {enabled: true, label: operator-value}\n")
                        .replace(
                                "  visibility:\n",
                                "  visibility:\n    future-extension: {keep: visibility}\n");
        Files.writeString(file, source);
        CorePlayer initial = repository.findByUniqueId(OWNER).orElseThrow();
        check(
                initial.getFirstJoin().toEpochMilli() == 1000
                        && initial.getLastJoin().toEpochMilli() == 2000
                        && initial.getName().equals("CodecProbe"),
                "identity read independently");
        check(
                Files.readString(file).equals(source) && access.events.isEmpty(),
                "aggregate read leaves exact source untouched");
        PlayerService players = new PlayerService(repository, quiet());
        PlayerSettingsService settings = new PlayerSettingsService(players);
        CorePlayer loaded = players.loadPlayer(OWNER, "CodecProbe");
        long lastJoin = loaded.getLastJoin().toEpochMilli();
        access.events.clear();
        int[] events = {0};
        settings.addChangeListener(
                id -> {
                    try {
                        check(
                                !yaml(Files.readString(file)).getBoolean("settings.sounds"),
                                "observer sees durable Settings commit");
                        check(access.events.contains("atomic"), "observer runs after replacement");
                        events[0]++;
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                });
        check(settings.setSoundsEnabled(OWNER, false), "owning Settings service mutation");
        check(
                events[0] == 1
                        && access.events.stream().filter("open"::equals).count() == 1
                        && access.events.stream().filter("atomic"::equals).count() == 1,
                "one atomic aggregate commit for Settings");
        YamlConfiguration written = yaml(Files.readString(file));
        Map<String, Object> expected = leaves(yaml(EXPECTED));
        for (var entry : expected.entrySet())
            check(
                    java.util.Objects.equals(written.get(entry.getKey()), entry.getValue()),
                    "frozen aggregate Settings value " + entry.getKey());
        assertProfile(repository.findByUniqueId(OWNER).orElseThrow(), lastJoin);
        check(
                written.getStringList("social.ignored")
                        .equals(List.of(FIRST.toString(), LAST.toString())),
                "social UUID order preserved");
        check(
                new ArrayList<>(written.getConfigurationSection("quests.active").getKeys(false))
                        .equals(List.of("a_complete", "m_pending", "z_active")),
                "quest ordering remains canonical");
        check(
                !written.contains("quests.active.z_active.closed-extra"),
                "closed assignment namespace still replaced");
        assertExtensions(written);
        loaded.getOnlineRewardProgress().setProcessedPlaytimeTicks(9876);
        players.savePlayer(OWNER);
        check(
                repository
                                .findByUniqueId(OWNER)
                                .orElseThrow()
                                .getOnlineRewardProgress()
                                .getProcessedPlaytimeTicks()
                                .orElseThrow()
                        == 9876,
                "different existing domain saved independently");
        assertExtensions(yaml(Files.readString(file)));
        try (var paths = Files.list(directory)) {
            check(
                    paths.map(path -> path.getFileName().toString())
                            .sorted()
                            .toList()
                            .equals(List.of(OWNER + ".yml")),
                    "only existing aggregate file; no new Settings storage");
        }
    }

    private static void assertProfile(CorePlayer player, long lastJoin) {
        check(
                player.getUniqueId().equals(OWNER)
                        && player.getName().equals("CodecProbe")
                        && player.getFirstJoin().toEpochMilli() == 1000
                        && player.getLastJoin().toEpochMilli() == lastJoin,
                "aggregate identity/timestamps retained");
        check(
                player.getWallet().getCoins() == 456789
                        && player.getSocial().getIgnoredPlayers().equals(Set.of(FIRST, LAST)),
                "aggregate economy/social retained");
        check(
                player.getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow() == 4567,
                "aggregate reward progress retained");
        var quests = player.getQuestState().snapshot();
        check(
                quests.size() == 3
                        && quests.get("a_complete").progress() == 30
                        && quests.get("a_complete").status().name().equals("COMPLETED")
                        && quests.get("m_pending").progress() == 88
                        && quests.get("m_pending").status().name().equals("REWARD_PENDING")
                        && quests.get("z_active").progress() == 17
                        && quests.get("z_active").status().name().equals("ACTIVE"),
                "aggregate all quest states independently retained");
        check(
                player.getDailyQuestState().cycleId().orElseThrow().toString().equals("2026-10-25"),
                "aggregate daily cycle retained");
        check(
                player.getSettings().getVisibility().getMode() == VisibilityMode.HIDDEN
                        && player.getSettings()
                                .getVisibility()
                                .getAddedPlayers()
                                .equals(Set.of(FIRST, LAST)),
                "aggregate visibility independent retained");
    }

    private static void assertExtensions(YamlConfiguration yaml) {
        check(
                yaml.getBoolean("settings.future-extension.enabled")
                        && "operator-value"
                                .equals(yaml.getString("settings.future-extension.label")),
                "unknown Settings extension preserved");
        check(
                "visibility".equals(yaml.getString("settings.visibility.future-extension.keep"))
                        && "preserved".equals(yaml.getString("future-root.value")),
                "unknown visibility and root extensions preserved");
        for (String path :
                List.of(
                        "economy.custom.keep",
                        "social.custom",
                        "rewards.online.custom",
                        "quests.daily.custom",
                        "quests.custom"))
            check(yaml.contains(path), "unrelated nested extension preserved " + path);
    }

    private static void candidateValidation(Path directory) throws Exception {
        FilePlayerRepository repository = repository(directory, new PersistenceFaults(Fault.NONE));
        Path file = directory.resolve(OWNER + ".yml");
        String ambiguous = CURRENT + "settings: {sounds: true}\n'settings.sounds': false\n";
        Files.writeString(file, ambiguous);
        CorePlayer loaded = repository.findByUniqueId(OWNER).orElseThrow();
        loaded.getSettings().setSoundsEnabled(true);
        expectFailure(
                () -> repository.save(loaded),
                "noncanonical candidate cannot claim aggregate commit");
        check(
                Files.readString(file).equals(ambiguous),
                "candidate refusal leaves source byte-exact");
    }

    private static void faults(Path directory) throws Exception {
        for (Fault fault :
                List.of(
                        Fault.WRITE,
                        Fault.CLOSE,
                        Fault.INVALID_YAML,
                        Fault.ATOMIC_IO,
                        Fault.UNSUPPORTED,
                        Fault.REPLACE,
                        Fault.RESTORE,
                        Fault.BACKUP,
                        Fault.MIGRATION_BACKUP,
                        Fault.TEMP_CLEANUP)) {
            PersistenceFaults access = new PersistenceFaults(Fault.NONE);
            FilePlayerRepository repository = repository(directory.resolve(fault.name()), access);
            Path file = directory.resolve(fault.name()).resolve(OWNER + ".yml");
            Files.writeString(file, BASE);
            repository.initialize();
            PlayerService players = new PlayerService(repository, quiet());
            CorePlayer player = players.loadPlayer(OWNER, "Renamed");
            PlayerSettingsService settings = new PlayerSettingsService(players);
            int[] notifications = {0};
            settings.addChangeListener(id -> notifications[0]++);
            byte[] before = Files.readAllBytes(file);
            access.fault = fault;
            boolean success = fault == Fault.UNSUPPORTED || fault == Fault.TEMP_CLEANUP;
            if (success)
                check(
                        settings.setSoundsEnabled(OWNER, false),
                        "supported successful commit " + fault);
            else
                expectFailure(
                        () -> settings.setSoundsEnabled(OWNER, false),
                        "real save failure propagates " + fault);
            check(
                    player.getSettings().isSoundsEnabled() != success,
                    "failed Settings mutation rolls back " + fault);
            check(
                    notifications[0] == (success ? 1 : 0),
                    "only committed mutations notify " + fault);
            check(
                    repository.findUniqueIdsByName("Renamed").contains(OWNER) == success
                            && repository.findUniqueIdsByName("CodecProbe").contains(OWNER)
                                    != success,
                    "index publication follows commit " + fault);
            if (fault == Fault.RESTORE) {
                check(
                        Arrays.equals(
                                before, Files.readAllBytes(SafeFileWriter.recoveryFile(file))),
                        "unrecoverable replacement retains exact recovery copy");
                access.fault = Fault.NONE;
                expectFailure(
                        () -> repository.findByUniqueId(OWNER), "unresolved recovery blocks read");
                expectFailure(
                        () -> players.unloadPlayer(OWNER), "unresolved recovery blocks unload");
                check(players.isLoaded(OWNER), "failed unload retains loaded profile");
            } else if (!success) {
                check(
                        Arrays.equals(before, Files.readAllBytes(file)),
                        "failed write retains exact source " + fault);
                expectFailure(
                        () -> players.unloadPlayer(OWNER),
                        "failed unload still propagates " + fault);
                check(players.isLoaded(OWNER), "failed unload does not evict " + fault);
            } else
                check(
                        !yaml(Files.readString(file)).getBoolean("settings.sounds"),
                        "successful persisted value " + fault);
            if (fault != Fault.TEMP_CLEANUP)
                check(!Files.exists(access.candidate), "temporary candidate removed " + fault);
        }
    }

    private static void migration(Path directory) throws Exception {
        FilePlayerRepository repository = repository(directory, new PersistenceFaults(Fault.NONE));
        Path file = directory.resolve(OWNER + ".yml");
        String source = "# exact original bytes: ä\r\n" + BASE.replace("\n", "\r\n");
        Files.writeString(file, source);
        byte[] before = Files.readAllBytes(file);
        CorePlayer player = repository.findByUniqueId(OWNER).orElseThrow();
        Path backup = file.resolveSibling(file.getFileName() + ".vapeecore-pre-migration.bak");
        check(
                Arrays.equals(before, Files.readAllBytes(file)) && !Files.exists(backup),
                "legacy read/index no eager migration");
        repository.save(player);
        check(
                Files.exists(backup) && Arrays.equals(before, Files.readAllBytes(backup)),
                "legacy migration backup exact bytes");
        check(
                yaml(Files.readString(file)).getInt("schema-version") == 1,
                "current player version unchanged");
        repository.save(player);
        check(
                Arrays.equals(before, Files.readAllBytes(backup)),
                "later save retains original migration backup");
    }

    private static void refusedSources(Path directory) throws Exception {
        FilePlayerRepository repository = repository(directory, new PersistenceFaults(Fault.NONE));
        Path file = directory.resolve(OWNER + ".yml");
        for (String source :
                List.of(
                        "schema-version: 2\n" + BASE,
                        "schema-version: null\n" + BASE,
                        "schema-version: -1\n" + BASE,
                        "schema-version: '1'\n" + BASE,
                        CURRENT + "name: Duplicate\n",
                        "[]\n",
                        CURRENT + "settings: [broken\n",
                        CURRENT + "economy: {coins: -1}\n",
                        CURRENT + "social: {ignored: [invalid]}\n")) {
            Files.writeString(file, source);
            PlayerService players = new PlayerService(repository, quiet());
            expectFailure(
                    () -> players.loadPlayer(OWNER, "Replacement"),
                    "unsafe/future source refuses load");
            check(
                    !players.isLoaded(OWNER) && Files.readString(file).equals(source),
                    "refused source never becomes default profile");
        }
        Files.writeString(file, CURRENT);
        PlayerService players = new PlayerService(repository, quiet());
        players.loadPlayer(OWNER, "CodecProbe");
        String future = "schema-version: 2\n" + BASE + "future-root: keep\n";
        Files.writeString(file, future);
        expectFailure(
                () -> new PlayerSettingsService(players).setSoundsEnabled(OWNER, false),
                "future disk blocks stale aggregate mutation");
        check(
                players.getPlayer(OWNER).orElseThrow().getSettings().isSoundsEnabled()
                        && Files.readString(file).equals(future),
                "future refusal retains runtime and exact disk");
    }

    private static PlayerSettings read(Path directory, String text, Capture capture)
            throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve(OWNER + ".yml");
        Files.writeString(file, text);
        byte[] before = Files.readAllBytes(file);
        capture.messages.clear();
        PlayerSettings settings =
                PlayerSettingsYamlCodec.read(
                        OWNER, file, yaml(text), PlayerFileSchema.read(text), capture.logger);
        check(
                Arrays.equals(before, Files.readAllBytes(file)),
                "codec read leaves exact source bytes");
        try (var files = Files.list(directory)) {
            check(files.count() == 1, "codec read creates no file");
        }
        return settings;
    }

    private static String warning(Path directory, String path, String reason) {
        return "Invalid player setting '"
                + path
                + "' for "
                + OWNER
                + " in "
                + directory.resolve(OWNER + ".yml")
                + ": "
                + reason
                + "; using its default value.";
    }

    private static FilePlayerRepository repository(Path directory, PersistenceFaults access) {
        FilePlayerRepository repository = new FilePlayerRepository(directory, quiet(), access);
        repository.initialize();
        return repository;
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static Map<String, Object> leaves(YamlConfiguration yaml) {
        Map<String, Object> leaves = new LinkedHashMap<>();
        yaml.getValues(true)
                .forEach(
                        (path, value) -> {
                            if (!(value instanceof org.bukkit.configuration.ConfigurationSection))
                                leaves.put(path, value);
                        });
        return leaves;
    }

    private static Logger quiet() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void expectFailure(Runnable action, String contract) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            check(true, contract);
            return;
        }
        throw new AssertionError(contract);
    }

    private static void check(boolean condition, String contract) {
        checks++;
        if (!condition) throw new AssertionError(contract);
    }

    private static final class Capture extends Handler {
        private final Logger logger = quiet();
        private final List<String> messages = new ArrayList<>();

        private Capture() {
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue())
                messages.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}

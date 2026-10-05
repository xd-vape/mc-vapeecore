package dev.vapee.core.player.repository;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.persistence.PersistenceFaults;
import dev.vapee.core.persistence.SafeFileWriter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static dev.vapee.core.persistence.PersistenceFaults.Fault;

public final class PlayerSchemaPersistenceHarness {
    private static int checks;
    private static final String BASE = "name: Legacy\nfirst-join: 1000\nlast-join: 2000\n";

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-player-schema-");
        try {
            roundTrip(directory.resolve("roundtrip"));
            versionsAndCore(directory.resolve("versions"));
            for (Fault fault : List.of(Fault.NONE, Fault.UNSUPPORTED, Fault.ATOMIC_IO, Fault.WRITE,
                    Fault.CLOSE, Fault.INVALID_YAML, Fault.REPLACE, Fault.RESTORE, Fault.BACKUP, Fault.MIGRATION_BACKUP)) {
                failure(directory.resolve(fault.name()), fault);
            }
            migrationBackupConflict(directory.resolve("backup-conflict"));
            changedWhileSaving(directory.resolve("changed"));
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("PlayerSchemaPersistenceHarness passed " + checks + " checks.");
    }

    private static void roundTrip(Path directory) throws Exception {
        FilePlayerRepository repository = repository(directory, new PersistenceFaults(Fault.NONE));
        UUID id = UUID.randomUUID(); UUID ignored = UUID.randomUUID(); UUID visible = UUID.randomUUID();
        Path file = directory.resolve(id + ".yml");
        String original = BASE + "future-feature: {keep-me: true, nested: [a, null, 42], 'literal.key': value}\n"
                + "unknown-null: null\nsettings:\n  scoreboard: false\n  sounds: wrong\n"
                + "  extension: {keep: yes}\n  visibility:\n    extra: {keep: true}\n"
                + "    show-friends: true\n    added-players: ['" + visible + "']\n"
                + "economy: {coins: 9223372036854775807, extension: {keep: true}}\n"
                + "social: {ignored: ['" + ignored + "'], extra: keep}\n"
                + "rewards:\n  extension: keep\n  online: {processed-playtime-ticks: 123456, extra: keep}\n"
                + "quests:\n  extra: keep\n  daily: {cycle-id: '2026-10-05', extra: keep}\n"
                + "  active:\n    future_id: {progress: 23, status: REWARD_PENDING, not-owned: remove}\n"
                + "    broken: {progress: -1, status: ACTIVE}\n";
        Files.writeString(file, original);
        repository.initialize();
        CorePlayer player = repository.findByUniqueId(id).orElseThrow();
        check(Files.readString(file).equals(original) && !Files.exists(migration(file)), "legacy load/index has no eager rewrite/backup");
        check(!player.getSettings().isScoreboardEnabled() && player.getSettings().isSoundsEnabled(), "known preference/default semantics");
        check(player.getWallet().getCoins() == Long.MAX_VALUE && player.getSocial().isIgnoring(ignored), "core data preserved");
        check(player.getQuestState().snapshot().size() == 1 && player.getDailyQuestState().cycleId().isPresent(), "valid unknown quest ID retained; invalid entry skipped");
        repository.save(player);
        check(Files.readString(migration(file)).equals(original), "pre-migration backup contains exact original bytes");
        Map<Object, Object> saved = PlayerFileSchema.read(Files.readString(file));
        check(PlayerFileSchema.version(saved) == PlayerFileSchema.CURRENT_VERSION, "normal save materializes current schema");
        Map<Object, Object> old = PlayerFileSchema.read(original);
        check(java.util.Objects.equals(saved.get("future-feature"), old.get("future-feature")) && saved.containsKey("unknown-null")
                && saved.get("unknown-null") == null, "unknown root subtree including null/dotted key preserved");
        YamlConfiguration yaml = yaml(file);
        for (String path : List.of("settings.extension", "settings.visibility.extra", "economy.extension",
                "social.extra", "rewards.extension", "rewards.online.extra", "quests.extra", "quests.daily.extra")) {
            check(yaml.contains(path), "open nested field preserved: " + path);
        }
        check(yaml.getBoolean("settings.sounds") && !yaml.contains("quests.active.broken")
                && !yaml.contains("quests.active.future_id.not-owned"), "canonical known fields override invalid/closed namespace data");
        CorePlayer loaded = repository.findByUniqueId(id).orElseThrow();
        check(loaded.getQuestState().snapshot().equals(player.getQuestState().snapshot())
                && loaded.getOnlineRewardProgress().getProcessedPlaytimeTicks().equals(player.getOnlineRewardProgress().getProcessedPlaytimeTicks())
                && loaded.getDailyQuestState().cycleId().equals(player.getDailyQuestState().cycleId())
                && loaded.getSettings().getVisibility().getAddedPlayers().equals(java.util.Set.of(visible))
                && loaded.getWallet().getCoins() == player.getWallet().getCoins()
                && loaded.getName().equals(player.getName()) && loaded.getFirstJoin().equals(player.getFirstJoin())
                && loaded.getLastJoin().equals(player.getLastJoin()) && loaded.getSocial().getIgnoredPlayers().equals(player.getSocial().getIgnoredPlayers()),
                "load-save-load aggregate equivalence");
        byte[] backup = Files.readAllBytes(migration(file));
        Files.writeString(file, Files.readString(file) + "external-extension: {added-after-load: true}\n");
        repository.save(loaded);
        check(yaml(file).getBoolean("external-extension.added-after-load"), "save refreshes unknown preservation source after prior load");
        check(java.util.Arrays.equals(backup, Files.readAllBytes(migration(file))), "normal current save leaves migration backup unchanged");
        CorePlayer cleared = new CorePlayer(loaded.getUniqueId(), loaded.getName(), loaded.getFirstJoin(),
                loaded.getLastJoin(), loaded.getSettings(), loaded.getWallet(), loaded.getSocial(),
                loaded.getOnlineRewardProgress(), dev.vapee.core.quest.PlayerQuestState.empty(), loaded.getDailyQuestState());
        repository.save(cleared);
        check(!yaml(file).contains("quests.active") && yaml(file).contains("quests.extra"), "removed assignments never resurrect from source");

        UUID optionalId = UUID.randomUUID(); Path optional = directory.resolve(optionalId + ".yml");
        Files.writeString(optional, BASE + "rewards: {online: {processed-playtime-ticks: -1, extra: keep}}\n"
                + "quests: {daily: {cycle-id: invalid, extra: keep}}\nsettings: broken\n");
        CorePlayer invalidOptional = repository.findByUniqueId(optionalId).orElseThrow();
        repository.save(invalidOptional);
        check(!yaml(optional).contains("rewards.online.processed-playtime-ticks") && yaml(optional).contains("rewards.online.extra")
                && !yaml(optional).contains("quests.daily.cycle-id") && yaml(optional).contains("quests.daily.extra")
                && yaml(optional).getBoolean("settings.scoreboard"), "invalid optional owned fields removed/canonicalized, unknown siblings retained");
        UUID aliasId = UUID.randomUUID(); Path alias = directory.resolve(aliasId + ".yml");
        Files.writeString(alias, BASE + "settings: &shared {sounds: false}\nunknown-alias: *shared\n");
        CorePlayer aliased = repository.findByUniqueId(aliasId).orElseThrow(); aliased.getSettings().setSoundsEnabled(true);
        repository.save(aliased);
        check(yaml(alias).getBoolean("settings.sounds") && !yaml(alias).getBoolean("unknown-alias.sounds"), "owned overlay does not mutate aliased unknown subtree");
        UUID dottedId = UUID.randomUUID(); Path dotted = directory.resolve(dottedId + ".yml");
        String ambiguous = BASE + "settings: {sounds: true}\n'settings.sounds': false\n";
        Files.writeString(dotted, ambiguous);
        CorePlayer dottedPlayer = repository.findByUniqueId(dottedId).orElseThrow();
        dottedPlayer.getSettings().setSoundsEnabled(true);
        try {
            repository.save(dottedPlayer);
            check(repository.findByUniqueId(dottedId).orElseThrow().getSettings().isSoundsEnabled(), "literal dotted unknown key cannot override canonical owned value");
        } catch (IllegalStateException expected) {
            check(Files.readString(dotted).equals(ambiguous), "ambiguous owned path refuses save without changing disk");
        }
    }

    private static void versionsAndCore(Path directory) throws Exception {
        FilePlayerRepository repository = repository(directory, new PersistenceFaults(Fault.NONE));
        UUID id = UUID.randomUUID(); Path file = directory.resolve(id + ".yml");
        PlayerService service = new PlayerService(repository, logger());
        for (String version : List.of("2", "9223372036854775807", "-1", "null", "'1'", "1.5", "true", "{}")) {
            String source = "schema-version: " + version + "\n" + BASE + "future-feature: {keep-me: true}\n";
            Files.writeString(file, source);
            expectFailure(() -> service.loadPlayer(id, "Changed"), "normal PlayerService refuses version " + version);
            check(!service.isLoaded(id) && Files.readString(file).equals(source) && !Files.exists(migration(file)),
                    "refused load cannot create default/cache/write version " + version);
        }
        for (String version : List.of("", "schema-version: 0\n", "schema-version: 1\n")) {
            Files.writeString(file, version + BASE);
            CorePlayer player = repository.findByUniqueId(id).orElseThrow();
            check(player.getWallet().getCoins() == 0 && player.getSettings().isSoundsEnabled()
                    && player.getSocial().getIgnoredPlayers().isEmpty() && player.getQuestState().isEmpty(), "missing optional defaults " + version);
        }
        for (String source : List.of("name: MissingTimes\n", "first-join: 0\nlast-join: 0\n",
                BASE + "economy: {coins: -1}\n", BASE + "economy: broken\n", BASE + "social: {ignored: [bad]}\n")) {
            Files.writeString(file, source);
            expectFailure(() -> service.loadPlayer(id, "Changed"), "invalid/missing required core refuses load");
            check(Files.readString(file).equals(source) && !service.isLoaded(id), "invalid core is not default-overwritten");
        }
        Files.writeString(file, "schema-version: 1\n" + BASE);
        service.loadPlayer(id, "Loaded");
        String future = "schema-version: " + (PlayerFileSchema.CURRENT_VERSION + 1) + "\n" + BASE + "future-feature: {keep-me: true}\n";
        Files.writeString(file, future);
        expectFailure(() -> new PlayerSettingsService(service).setSoundsEnabled(id, false), "already loaded mutation refuses future disk");
        check(service.getPlayer(id).orElseThrow().getSettings().isSoundsEnabled(), "settings exact runtime rollback after refusal");
        expectFailure(() -> service.unloadPlayer(id), "future save-before-unload refused");
        check(service.isLoaded(id) && Files.readString(file).equals(future), "future bytes retained and cache not evicted");
        repository.initialize();
        check(repository.findUniqueIdsByName("Legacy").isEmpty(), "future identity metadata skipped from startup index");
        Files.writeString(file, BASE + "economy: {coins: -1}\n");
        expectFailure(() -> service.savePlayer(id), "fresh save source rejects newly corrupted known core");
        check(yaml(file).getLong("economy.coins") == -1, "known corrupt source is not blindly repaired by stale aggregate");
        Files.delete(file);
        Files.writeString(SafeFileWriter.recoveryFile(file), BASE);
        expectFailure(() -> new PlayerService(repository, logger()).loadPlayer(id, "Default"), "missing destination with recovery copy is not new player");
        check(!Files.exists(file) && Files.readString(SafeFileWriter.recoveryFile(file)).equals(BASE), "missing recovery case preserves evidence");
    }

    private static void failure(Path directory, Fault fault) throws Exception {
        PersistenceFaults access = new PersistenceFaults(fault);
        FilePlayerRepository repository = repository(directory, access);
        UUID id = UUID.randomUUID(); Path file = directory.resolve(id + ".yml");
        Files.writeString(file, BASE + "future-feature: {keep-me: true}\n");
        String original = Files.readString(file);
        repository.initialize();
        PlayerService service = new PlayerService(repository, logger());
        service.loadPlayer(id, "NewName");
        boolean success = fault == Fault.NONE || fault == Fault.UNSUPPORTED;
        if (success) service.unloadPlayer(id);
        else expectFailure(() -> service.unloadPlayer(id), "player fault propagates " + fault);
        check(service.isLoaded(id) != success, "save-before-unload " + fault);
        check(repository.findUniqueIdsByName("NewName").contains(id) == success
                && repository.findUniqueIdsByName("Legacy").contains(id) != success, "index publish after commit only " + fault);
        if (success) {
            check(yaml(file).getInt("schema-version") == 1 && yaml(file).getBoolean("future-feature.keep-me"), "migration success " + fault);
            check(Files.readString(migration(file)).equals(original), "migration backup exact " + fault);
        } else if (fault == Fault.RESTORE) {
            check(Files.readString(SafeFileWriter.recoveryFile(file)).equals(original), "failed player recovery keeps original backup");
            access.fault = Fault.NONE;
            expectFailure(() -> repository.findByUniqueId(id), "recovery marker blocks reading damaged target");
            expectFailure(() -> service.savePlayer(id), "recovery marker blocks further writes");
        } else check(Files.readString(file).equals(original), "failed player save preserves original " + fault);
        if (fault == Fault.REPLACE) {
            check(access.events.contains("restore"), "failed migration fallback restores original");
            access.fault = Fault.NONE;
            service.unloadPlayer(id);
            check(yaml(file).getInt("schema-version") == 1 && Files.readString(migration(file)).equals(original),
                    "migration retries reuse identical original backup");
        }
        if (fault == Fault.MIGRATION_BACKUP) check(!access.events.contains("atomic"), "migration backup failure precedes commit");
        check(access.candidate == null || !Files.exists(access.candidate), "player candidate cleanup " + fault);
    }

    private static void migrationBackupConflict(Path directory) throws Exception {
        PersistenceFaults access = new PersistenceFaults(Fault.NONE);
        FilePlayerRepository repository = repository(directory, access);
        UUID id = UUID.randomUUID(); Path file = directory.resolve(id + ".yml");
        Files.writeString(file, BASE); Files.writeString(migration(file), "previous recovery evidence");
        CorePlayer player = repository.findByUniqueId(id).orElseThrow();
        expectFailure(() -> repository.save(player), "different pre-migration backup cannot be overwritten");
        check(Files.readString(file).equals(BASE) && Files.readString(migration(file)).equals("previous recovery evidence"), "migration conflict preserves both files");
        Files.writeString(file, "schema-version: 1\n" + BASE);
        repository.save(player);
        check(!access.events.contains("migration") && Files.readString(migration(file)).equals("previous recovery evidence"), "current save does not create/replace migration backup");
        UUID fresh = UUID.randomUUID();
        new PlayerService(repository, logger()).loadPlayer(fresh, "NewPlayer");
        check(yaml(directory.resolve(fresh + ".yml")).getInt("schema-version") == 1
                && !Files.exists(migration(directory.resolve(fresh + ".yml"))), "new player directly writes current schema without migration backup");
    }

    private static void changedWhileSaving(Path directory) throws Exception {
        PersistenceFaults access = new PersistenceFaults(Fault.NONE);
        FilePlayerRepository repository = repository(directory, access);
        UUID id = UUID.randomUUID(); Path file = directory.resolve(id + ".yml"); Files.writeString(file, BASE);
        CorePlayer player = repository.findByUniqueId(id).orElseThrow();
        String external = "schema-version: 2\n" + BASE + "future-feature: {keep-me: true}\n";
        access.onClosed = () -> { try { Files.writeString(file, external); } catch (Exception e) { throw new RuntimeException(e); } };
        expectFailure(() -> repository.save(player), "disk change during candidate write aborts commit");
        check(Files.readString(file).equals(external) && !Files.exists(migration(file)), "new external data untouched");
        Files.writeString(file, BASE);
        access.onClosed = () -> { try {
            Files.writeString(access.candidate, Files.readString(access.candidate).replace("coins: 0", "coins: 99"));
        } catch (Exception e) { throw new RuntimeException(e); } };
        expectFailure(() -> repository.save(player), "syntactically valid but different candidate rejected");
        check(Files.readString(file).equals(BASE), "valid-looking damaged candidate cannot replace original");
    }

    private static FilePlayerRepository repository(Path directory, PersistenceFaults access) {
        FilePlayerRepository repository = new FilePlayerRepository(directory, logger(), access); repository.initialize(); return repository;
    }
    private static Path migration(Path file) { return file.resolveSibling(file.getFileName() + ".vapeecore-pre-migration.bak"); }
    private static YamlConfiguration yaml(Path file) { return YamlConfiguration.loadConfiguration(file.toFile()); }
    private static Logger logger() { Logger logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false); return logger; }
    private static void expectFailure(Runnable action, String label) {
        boolean failed = false; try { action.run(); } catch (RuntimeException expected) { failed = true; } check(failed, label);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

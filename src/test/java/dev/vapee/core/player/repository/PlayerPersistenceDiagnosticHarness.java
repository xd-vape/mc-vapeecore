package dev.vapee.core.player.repository;

import static dev.vapee.core.persistence.PersistenceFaults.Fault;
import static dev.vapee.core.player.repository.PersistenceDiagnosticFixture.contains;
import static dev.vapee.core.player.repository.PersistenceDiagnosticFixture.failure;
import static dev.vapee.core.player.repository.PersistenceDiagnosticFixture.field;
import static dev.vapee.core.player.repository.PersistenceDiagnosticFixture.strict;

import dev.vapee.core.economy.EconomyResult;
import dev.vapee.core.persistence.SafeFileWriter;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestDefinitionRegistry;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.quest.QuestProgressKey;
import dev.vapee.core.quest.QuestService;
import dev.vapee.core.quest.QuestStatus;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.reward.RewardShutdownFixture;
import dev.vapee.core.reward.RewardSource;
import dev.vapee.core.social.IgnoreResult;
import dev.vapee.core.social.SocialService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistence results observed through actual immediate and deferred domain services. */
public final class PlayerPersistenceDiagnosticHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            storage();
            index();
            economy();
            settingsSocial();
            schema();
            deferred();
            modules();
            unload();
            uncertain();
        } else
            switch (args[0]) {
                case "storage" -> storage();
                case "index" -> index();
                case "economy" -> economy();
                case "settings" -> settingsSocial();
                case "schema" -> schema();
                case "deferred" -> deferred();
                case "unload" -> unload();
                case "uncertain" -> uncertain();
                default -> throw new AssertionError(args[0]);
            }
        System.out.println("PlayerPersistenceDiagnosticHarness passed " + checks + " checks.");
    }

    private static void storage() throws Exception {
        try (var f = new PersistenceDiagnosticFixture()) {
            var aggregate = f.load(f.a, "Alpha", 10);
            f.load(f.b, "Beta", 0);
            byte[] prior = f.bytes(f.a);
            f.economy.addCoinsDeferred(f.a, 11);
            f.access.perPlayer.put(f.a, Fault.ATOMIC_IO);
            f.throwSevere = true;
            RuntimeException error = failure(() -> f.players.savePlayer(f.a));
            IOException original = f.access.errors.getFirst();
            check(
                    error instanceof IllegalStateException && error.getCause() == original,
                    "repository preserves exact original IOException as cause");
            check(
                    Arrays.asList(error.getSuppressed()).contains(f.diagnostics.getLast()),
                    "repository attaches throwing SEVERE as secondary diagnostic");
            check(
                    error.getMessage().contains("save player data")
                            && error.getMessage().contains(f.file(f.a).toString()),
                    "persistence failure retains operation and path context");
            check(
                    Arrays.equals(prior, f.bytes(f.a))
                            && aggregate.getWallet().getCoins() == 21
                            && f.players.getPlayer(f.a).orElseThrow() == aggregate,
                    "G4 unchanged file and original loaded aggregate distinguished");
            f.economy.addCoinsDeferred(f.b, 22);
            f.players.savePlayer(f.b);
            check(
                    f.stored(f.b) == 22 && f.cold(f.b).getWallet().getCoins() == 22,
                    "independent profile can still commit after diagnostic failure");
            f.access.perPlayer.clear();
            f.access.fault(Fault.NONE);
            f.throwSevere = false;
            Files.delete(f.file(f.a));
            Files.createDirectory(f.file(f.a));
            f.throwSevere = true;
            error = failure(() -> f.repository.findByUniqueId(f.a));
            check(
                    error != null
                            && error.getMessage().contains("Path is not a regular file")
                            && error.getCause() == null,
                    "invalid player path keeps intended no-cause classification");
            check(
                    Arrays.asList(error.getSuppressed()).contains(f.diagnostics.getLast()),
                    "invalid player path diagnostic remains secondary");
            Files.delete(f.file(f.a));
            Files.writeString(
                    f.file(f.a),
                    "schema-version: 1\n"
                            + "name: Alpha\n"
                            + "first-join: 1000\n"
                            + "last-join: 2000\n"
                            + "economy: {coins: -1}\n");
            error = failure(() -> f.repository.findByUniqueId(f.a));
            check(
                    error != null
                            && error.getCause() != null
                            && error.getCause()
                                    .getMessage()
                                    .contains("balance must not be negative"),
                    "invalid domain file retains validation classification");
            check(
                    containsType(error, IllegalArgumentException.class),
                    "invalid player values preserve original validation cause");
            check(f.stored(f.a) == -1, "invalid data never repaired by error reporting");
            var method =
                    FilePlayerRepository.class.getDeclaredMethod("invalidStorage", String.class);
            method.setAccessible(true);
            var intended =
                    (IllegalStateException)
                            method.invoke(f.repository, "controlled invalid storage");
            check(
                    intended.getMessage().equals("controlled invalid storage")
                            && intended.getSuppressed().length == 1,
                    "adjacent invalidStorage constructor guards its diagnostic");
            Path path = f.directory.resolve("not-a-directory");
            Files.writeString(path, "operator bytes");
            FilePlayerRepository invalid = new FilePlayerRepository(path, f.logger);
            error = failure(invalid::initialize);
            check(
                    error != null
                            && error.getCause() instanceof IOException
                            && error.getMessage().contains("initialize player storage"),
                    "actual invalid storage preserves filesystem cause");
            check(
                    Files.readString(path).equals("operator bytes"),
                    "invalid storage path content preserved");
        }
    }

    private static void index() throws Exception {
        for (Fault mode :
                List.of(
                        Fault.NONE,
                        Fault.UNSUPPORTED,
                        Fault.TEMP_CLEANUP,
                        Fault.BACKUP_CLEANUP,
                        Fault.ATOMIC_IO))
            try (var f = new PersistenceDiagnosticFixture()) {
                var p = f.load(f.a, "OldName", 10);
                byte[] prior = f.bytes(f.a);
                p.updateName("NewName");
                f.access.fault(mode);
                f.throwWarning = true;
                f.throwSevere = true;
                RuntimeException error = failure(() -> f.repository.save(p));
                boolean success = mode != Fault.ATOMIC_IO;
                check(
                        (error == null) == success,
                        "name-index save completion matches actual commit result");
                check(
                        f.repository.findUniqueIdsByName("newname").contains(f.a) == success
                                && f.repository.findUniqueIdsByName("oldname").contains(f.a)
                                        != success,
                        "name index publishes only confirmed successful commit");
                check(
                        Files.isRegularFile(f.file(f.a))
                                && f.yaml(f.a)
                                        .getString("name")
                                        .equals(success ? "NewName" : "OldName"),
                        "canonical UUID filename and stored name agree");
                if (success) {
                    check(
                            f.access.commits.equals(List.of(f.file(f.a))),
                            "rename commits once without replay");
                    reconcile(f, mode, prior);
                    check(
                            f.cold(f.a).getName().equals("NewName"),
                            "independent cold name agrees after confirmed commit");
                } else
                    check(
                            Arrays.equals(prior, f.bytes(f.a))
                                    && contains(error, f.access.errors.getFirst()),
                            "failed precommit rename preserves bytes and original IO");
            }
    }

    private static void economy() throws Exception {
        for (String operation : List.of("add", "remove", "set"))
            for (Fault mode :
                    List.of(
                            Fault.NONE,
                            Fault.UNSUPPORTED,
                            Fault.TEMP_CLEANUP,
                            Fault.BACKUP_CLEANUP,
                            Fault.ATOMIC_IO))
                try (var f = new PersistenceDiagnosticFixture()) {
                    var p = f.load(f.a, "Economy", 10);
                    byte[] prior = f.bytes(f.a);
                    f.access.fault(mode);
                    f.throwWarning = true;
                    f.throwSevere = true;
                    EconomyResult[] result = {null};
                    long expected =
                            operation.equals("add") ? 17 : operation.equals("remove") ? 7 : 42;
                    RuntimeException error =
                            failure(
                                    () ->
                                            result[0] =
                                                    switch (operation) {
                                                        case "add" -> f.economy.addCoins(f.a, 7);
                                                        case "remove" ->
                                                                f.economy.removeCoins(f.a, 3);
                                                        default -> f.economy.setCoins(f.a, 42);
                                                    });
                    boolean success = mode != Fault.ATOMIC_IO;
                    check(
                            (error == null) == success,
                            "Economy " + operation + " return follows actual commit boundary");
                    check(
                            p.getWallet().getCoins() == (success ? expected : 10)
                                    && f.stored(f.a) == (success ? expected : 10),
                            "Economy memory and actual file agree for " + operation + " " + mode);
                    check(
                            result[0] == (success ? EconomyResult.SUCCESS : null),
                            "Economy reports no false successful result");
                    check(
                            f.access.commits.size() == (success ? 1 : 0),
                            "monetary operation not repeated or replayed");
                    check(
                            f.access.events.stream().filter(e -> e.equals("atomic")).count() == 1,
                            "one atomic attempt for one immediate operation");
                    if (success) {
                        reconcile(f, mode, prior);
                        check(
                                f.cold(f.a).getWallet().getCoins() == expected,
                                "Economy cold read agrees with retained committed wallet");
                    } else {
                        check(
                                Arrays.equals(prior, f.bytes(f.a)),
                                "real precommit failure retains exact prior bytes");
                        check(
                                contains(error, f.access.errors.getFirst())
                                        && contains(error, f.diagnostics.getLast()),
                                "Economy genuine failure preserves IO and secondary diagnostic");
                    }
                }
    }

    private static void settingsSocial() throws Exception {
        for (Fault mode : List.of(Fault.ATOMIC_IO, Fault.TEMP_CLEANUP))
            try (var f = new PersistenceDiagnosticFixture()) {
                var p = f.load(f.a, "Settings", 10);
                PlayerSettingsService settings = new PlayerSettingsService(f.players);
                SocialService social = new SocialService(f.players, f.logger);
                social.activatePlayer(f.a);
                int[] events = {0, 0};
                settings.addChangeListener(
                        id -> {
                            check(
                                    !f.yaml(id).getBoolean("settings.sounds"),
                                    "Settings observer sees already committed new file");
                            events[0]++;
                        });
                social.addRelationshipListener(
                        (owner, target) -> {
                            check(
                                    social.isIgnoring(owner, target)
                                            == p.getSocial().isIgnoring(target),
                                    "Social observer sees published committed snapshot");
                            events[1]++;
                        });
                f.access.fault(mode);
                f.throwWarning = true;
                f.throwSevere = true;
                boolean success = mode == Fault.TEMP_CLEANUP;
                RuntimeException error = failure(() -> settings.setSoundsEnabled(f.a, false));
                check(
                        (error == null) == success
                                && p.getSettings().isSoundsEnabled() != success
                                && f.yaml(f.a).getBoolean("settings.sounds") != success,
                        "Settings rolls back only genuine failed commit");
                check(
                        events[0] == (success ? 1 : 0),
                        "Settings emits exactly one success event only after commit");
                IgnoreResult[] result = {null};
                error = failure(() -> result[0] = social.ignore(f.a, f.b));
                check(
                        (error == null) == success
                                && p.getSocial().isIgnoring(f.b) == success
                                && social.isIgnoring(f.a, f.b) == success,
                        "Social ignore memory and published snapshot follow commit");
                check(
                        f.yaml(f.a).getStringList("social.ignored").contains(f.b.toString())
                                        == success
                                && events[1] == (success ? 1 : 0),
                        "Social failed commit produces no false observer event");
                if (!success) {
                    f.access.fault(Fault.NONE);
                    f.throwSevere = false;
                    social.ignore(f.a, f.b);
                    f.access.fault(Fault.ATOMIC_IO);
                    f.throwSevere = true;
                }
                int before = events[1];
                error = failure(() -> social.unignore(f.a, f.b));
                check(
                        (error == null) == success
                                && p.getSocial().isIgnoring(f.b) != success
                                && social.isIgnoring(f.a, f.b) != success,
                        "Social unignore restores exact prior membership on real failure");
                check(
                        events[1] == before + (success ? 1 : 0)
                                && f.yaml(f.a)
                                                .getStringList("social.ignored")
                                                .contains(f.b.toString())
                                        != success,
                        "Social unignore notification and disk share result boundary");
            }
    }

    private static void schema() throws Exception {
        try (var f = new PersistenceDiagnosticFixture()) {
            var p = f.load(f.a, "Schema", 10);
            String canonical = Files.readString(f.file(f.a));
            for (String invalid :
                    List.of(
                            canonical.replace("schema-version: 1", "schema-version: 2"),
                            canonical.replace("schema-version: 1", "schema-version: nope"),
                            "name: [broken\n")) {
                Files.writeString(f.file(f.a), invalid);
                f.throwSevere = true;
                RuntimeException error = failure(() -> f.repository.save(p));
                check(
                        error != null && Files.readString(f.file(f.a)).equals(invalid),
                        "invalid or future schema refused despite failed diagnostic");
                check(f.access.commits.isEmpty(), "invalid source cannot publish a replacement");
            }
            f.throwSevere = false;
            Files.writeString(f.file(f.a), canonical);
            f.reset();
            byte[] prior = f.bytes(f.a);
            f.access.fault(Fault.INVALID_YAML);
            check(
                    failure(() -> f.repository.save(p)) != null
                            && Arrays.equals(prior, f.bytes(f.a)),
                    "actual malformed candidate validation protects prior file");
            f.access.fault(Fault.NONE);
            f.access.delegate.onClosed =
                    () -> {
                        try {
                            Files.writeString(f.file(f.a), canonical + "external: {keep: true}\n");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    };
            RuntimeException error = failure(() -> f.repository.save(p));
            check(
                    error != null
                            && Files.readString(f.file(f.a)).endsWith("external: {keep: true}\n"),
                    "actual source-change conflict remains visible and protected");
            check(
                    f.access.commits.isEmpty(),
                    "source-change conflict does not commit stale aggregate");
            f.access.delegate.onClosed = () -> {};
            String legacy =
                    canonical.replace("schema-version: 1\n", "")
                            + "extension: {nested: [one, 42], unknown: true}\n";
            Files.writeString(f.file(f.a), legacy);
            f.reset();
            f.access.fault(Fault.TEMP_CLEANUP);
            f.throwWarning = true;
            f.repository.save(p);
            Path migration = f.file(f.a).resolveSibling(f.a + ".yml.vapeecore-pre-migration.bak");
            check(
                    Arrays.equals(
                            legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            Files.readAllBytes(migration)),
                    "legacy migration backup preserves exact original bytes");
            check(
                    f.yaml(f.a).getInt("schema-version") == 1
                            && f.yaml(f.a).getBoolean("extension.unknown"),
                    "schema 1 and unknown extension survive guarded cleanup");
            Set<String> names;
            try (var paths = Files.list(f.directory)) {
                names =
                        paths.map(x -> x.getFileName().toString())
                                .collect(java.util.stream.Collectors.toSet());
            }
            check(
                    names.equals(Set.of(f.a + ".yml", migration.getFileName().toString())),
                    "no new schema or persistence file format introduced");
            f.repository.save(p);
            check(
                    Files.readString(migration).equals(legacy),
                    "normal current save does not rewrite migration evidence");
            Files.writeString(f.file(f.a), legacy);
            Files.writeString(migration, "conflicting operator evidence");
            prior = f.bytes(f.a);
            check(
                    failure(() -> f.repository.save(p)) != null
                            && Arrays.equals(prior, f.bytes(f.a))
                            && Files.readString(migration).equals("conflicting operator evidence"),
                    "conflicting migration evidence still blocks replacement");
            Path recovery = SafeFileWriter.recoveryFile(f.file(f.a));
            Files.writeString(recovery, "unresolved recovery bytes");
            check(
                    failure(() -> f.repository.findByUniqueId(f.a)) != null
                            && failure(() -> f.repository.save(p)) != null,
                    "unresolved recovery blocks repository reads and writes");
            check(
                    Files.readString(recovery).equals("unresolved recovery bytes"),
                    "recovery evidence never silently deleted by diagnostics");
        }
    }

    private static void deferred() throws Exception {
        for (boolean quest : List.of(false, true))
            for (Fault mode : List.of(Fault.ATOMIC_IO, Fault.TEMP_CLEANUP))
                try (var f = new PersistenceDiagnosticFixture()) {
                    f.load(f.a, "DeferredA", 0);
                    f.load(f.b, "DeferredB", 0);
                    RewardService rewards = new RewardService(f.economy, f.players, f.logger);
                    QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
                    registry.replaceAll(
                            List.of(
                                    new QuestDefinition(
                                            "diagnostic",
                                            "Diagnostic",
                                            "Diagnostic",
                                            QuestProgressKey.of("test:diagnostic"),
                                            1,
                                            5)));
                    QuestService quests = new QuestService(registry, f.players, rewards, f.logger);
                    if (quest) {
                        for (UUID id : List.of(f.a, f.b)) {
                            quests.assignQuest(id, "diagnostic");
                            quests.addProgress(id, QuestProgressKey.of("test:diagnostic"), 1);
                        }
                    } else {
                        rewards.grantCoins(f.a, 11, RewardSource.SYSTEM, "diagnostic:A");
                        rewards.grantCoins(f.b, 22, RewardSource.SYSTEM, "diagnostic:B");
                    }
                    f.access.perPlayer.put(f.a, mode);
                    f.throwWarning = true;
                    f.throwSevere = true;
                    Object service = quest ? quests : rewards;
                    RuntimeException error = failure(() -> strict(service, "flushAllOrThrow"));
                    boolean success = mode == Fault.TEMP_CLEANUP;
                    check(
                            (error == null) == success,
                            "strict deferred flush follows true repository outcome");
                    if (!success)
                        check(
                                contains(error, f.access.errors.getFirst())
                                        && f.diagnostics.stream().anyMatch(x -> contains(error, x)),
                                "strict deferred error retains repository IO and diagnostic"
                                        + " causes");
                    check(
                            f.stored(f.b) == (quest ? 5 : 22),
                            "strict batch still commits independent later UUID");
                    check(
                            f.stored(f.a) == (success ? (quest ? 5 : 11) : 0),
                            "strict deferred actual failed and committed bytes distinguished");
                    @SuppressWarnings("unchecked")
                    Set<UUID> dirty = (Set<UUID>) field(service, "dirtyPlayers");
                    check(
                            dirty.contains(f.a) != success && !dirty.contains(f.b),
                            "strict deferred dirty markers follow persistence result");
                    if (quest)
                        check(
                                f.players
                                                .getPlayer(f.a)
                                                .orElseThrow()
                                                .getQuestState()
                                                .snapshot()
                                                .get("diagnostic")
                                                .status()
                                        == QuestStatus.COMPLETED,
                                "Quest completion status remains original shared aggregate");
                    f.access.perPlayer.clear();
                    f.throwSevere = false;
                    f.throwWarning = false;
                    int commits = f.access.commits.size();
                    strict(service, "flushAllOrThrow");
                    check(
                            f.access.commits.size() == commits + (success ? 0 : 1),
                            "deferred retry attempts unresolved UUID only");
                    check(
                            f.players.getPlayer(f.a).orElseThrow().getWallet().getCoins()
                                            == (quest ? 5 : 11)
                                    && f.cold(f.a).getWallet().getCoins() == (quest ? 5 : 11),
                            "deferred retry never duplicates reward and cold read agrees");
                }
        try (var f = new PersistenceDiagnosticFixture()) {
            f.load(f.a, "Public", 0);
            RewardService rewards = new RewardService(f.economy, f.players, f.logger);
            rewards.grantCoins(f.a, 11, RewardSource.SYSTEM, "public");
            f.access.fault(Fault.ATOMIC_IO);
            f.throwSevere = true;
            check(
                    failure(rewards::flushAll) == null
                            && ((Set<?>) field(rewards, "dirtyPlayers")).contains(f.a),
                    "public Reward remains log-and-continue with retained dirty work");
        }
    }

    private static void modules() throws Exception {
        for (boolean quest : List.of(false, true))
            for (Fault mode : List.of(Fault.ATOMIC_IO, Fault.TEMP_CLEANUP))
                try (var old = new RewardShutdownFixture();
                        var d = new PersistenceDiagnosticFixture()) {
                    old.start();
                    old.pair();
                    var actual =
                            new FilePlayerRepository(
                                    old.directory.resolve("players"), d.logger, d.access);
                    dev.vapee.core.module.ModuleRuntimeFixture.set(
                            old.players, "repository", actual);
                    var rewards = old.module.getRewardService();
                    var q =
                            new QuestModule(
                                    old.runtime.plugin,
                                    old.playerOwner,
                                    old.module,
                                    old.runtime.messages);
                    Object service;
                    Runnable disable;
                    if (quest) {
                        q.enable();
                        q.getDefinitionRegistry()
                                .replaceAll(
                                        List.of(
                                                new QuestDefinition(
                                                        "diagnostic",
                                                        "Diagnostic",
                                                        "Diagnostic",
                                                        QuestProgressKey.of("test:diagnostic"),
                                                        1,
                                                        5)));
                        service = q.getQuestService();
                        for (UUID id : List.of(old.a, old.b)) {
                            q.getQuestService().assignQuest(id, "diagnostic");
                            q.getQuestService()
                                    .addProgress(id, QuestProgressKey.of("test:diagnostic"), 1);
                        }
                        disable = q::disable;
                    } else {
                        service = rewards;
                        rewards.grantCoins(old.a, 11, RewardSource.SYSTEM, "module:A");
                        rewards.grantCoins(old.b, 22, RewardSource.SYSTEM, "module:B");
                        disable = old.module::disable;
                    }
                    d.access.perPlayer.put(old.a, mode);
                    d.throwSevere = true;
                    d.throwWarning = true;
                    RuntimeException error = failure(disable);
                    boolean success = mode == Fault.TEMP_CLEANUP;
                    check(
                            (error == null) == success,
                            "actual module shutdown preserves corrected repository completion");
                    if (!success)
                        check(
                                contains(error, d.access.errors.getFirst())
                                        && d.diagnostics.stream().anyMatch(x -> contains(error, x)),
                                "actual module incomplete error retains original IO and"
                                        + " diagnostic");
                    check(
                            ((Set<?>) field(service, "dirtyPlayers")).contains(old.a) != success,
                            "actual module keeps only genuinely failed dirty obligation");
                    check(
                            old.cold(old.b).getWallet().getCoins() == (quest ? 5 : 22),
                            "actual module shutdown still commits independent B");
                    check(
                            quest
                                    ? failure(q::getQuestService) != null
                                    : failure(old.module::getRewardService) != null,
                            "retired module getter stays closed after corrected persistence"
                                    + " result");
                    d.access.perPlayer.clear();
                    d.throwSevere = false;
                    d.throwWarning = false;
                    disable.run();
                    check(
                            old.cold(old.a).getWallet().getCoins() == (quest ? 5 : 11),
                            "actual module explicit retry saves original aggregate without replay");
                    if (quest) old.module.disable();
                }
    }

    private static void unload() throws Exception {
        for (Fault mode : List.of(Fault.ATOMIC_IO, Fault.TEMP_CLEANUP))
            try (var f = new PersistenceDiagnosticFixture()) {
                var original = f.load(f.a, "Unload", 10);
                f.economy.addCoinsDeferred(f.a, 7);
                f.access.fault(mode);
                f.throwSevere = true;
                f.throwWarning = true;
                RuntimeException error = failure(() -> f.players.unloadPlayer(f.a));
                boolean success = mode == Fault.TEMP_CLEANUP;
                check(
                        (error == null) == success && f.players.isLoaded(f.a) != success,
                        "save-before-eviction keeps cache on genuine failure only");
                check(
                        f.stored(f.a) == (success ? 17 : 10),
                        "unload actual file follows established save result");
                if (!success)
                    check(
                            f.players.getPlayer(f.a).orElseThrow() == original
                                    && original.getWallet().getCoins() == 17,
                            "failed unload retains original unsaved aggregate identity");
                else
                    check(
                            f.cold(f.a).getWallet().getCoins() == 17,
                            "successful eviction handoff has independently persisted value");
            }
        try (var f = new PersistenceDiagnosticFixture()) {
            f.load(f.a, "FallbackA", 0);
            f.load(f.b, "FallbackB", 0);
            f.economy.addCoinsDeferred(f.a, 11);
            f.economy.addCoinsDeferred(f.b, 22);
            f.access.perPlayer.put(f.a, Fault.ATOMIC_IO);
            f.throwSevere = true;
            RuntimeException error = failure(() -> strict(f.players, "saveAllOrThrow"));
            check(
                    error != null
                            && contains(error, f.access.errors.getFirst())
                            && f.stored(f.b) == 22
                            && f.players.isLoaded(f.a),
                    "Player strict fallback retains original cause and attempts independent"
                            + " profiles");
            f.access.perPlayer.clear();
            f.throwSevere = false;
            strict(f.players, "saveAllOrThrow");
            check(
                    f.cold(f.a).getWallet().getCoins() == 11,
                    "Player original cache retry remains compatible");
        }
    }

    private static void uncertain() throws Exception {
        try (var f = new PersistenceDiagnosticFixture()) {
            var p = f.load(f.a, "Before", 10);
            p.updateName("Attempted");
            f.access.atomicAfterEffect = true;
            f.throwSevere = true;
            RuntimeException error = failure(() -> f.economy.addCoins(f.a, 7));
            check(
                    error != null && contains(error, f.access.errors.getFirst()),
                    "uncertain effectful external failure remains failed return");
            check(
                    f.stored(f.a) == 17 && p.getWallet().getCoins() == 10,
                    "uncertain physical effect and existing domain rollback reported separately");
            check(
                    f.repository.findUniqueIdsByName("Attempted").isEmpty()
                            && f.repository.findUniqueIdsByName("Before").contains(f.a),
                    "uncertain external move cannot publish confirmed name index");
            check(
                    f.cold(f.a).getWallet().getCoins() == 17 && f.access.commits.size() == 1,
                    "uncertain file observation is not manufactured commit success or monetary"
                            + " retry");
        }
    }

    private static void reconcile(PersistenceDiagnosticFixture f, Fault mode, byte[] prior)
            throws Exception {
        if (mode == Fault.BACKUP_CLEANUP) {
            Path backup = SafeFileWriter.recoveryFile(f.file(f.a));
            check(
                    Arrays.equals(prior, Files.readAllBytes(backup)),
                    "successful fallback leaves exact old recovery evidence");
            check(
                    failure(() -> f.repository.findByUniqueId(f.a)) != null,
                    "retained obsolete marker requires explicit reconciliation before cold read");
            f.reconcileSuccessfulBackup(f.a);
        }
    }

    private static boolean containsType(Throwable root, Class<? extends Throwable> type) {
        if (root == null) return false;
        if (type.isInstance(root)) return true;
        if (containsType(root.getCause(), type)) return true;
        for (Throwable s : root.getSuppressed()) if (containsType(s, type)) return true;
        return false;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}

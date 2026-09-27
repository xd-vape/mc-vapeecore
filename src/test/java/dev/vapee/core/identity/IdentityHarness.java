package dev.vapee.core.identity;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.reload.ReloadParticipant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class IdentityHarness {

    private static int checks;

    public static void main(String[] args) throws Exception {
        domain();
        lifecycle();
        Path directory = Files.createTempDirectory("vapeecore-identity-");
        try {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID third = UUID.randomUUID();
            UUID malformed = UUID.randomUUID();
            Files.writeString(directory.resolve(first + ".yml"), yaml("_ImVentex_", 12500));
            Files.writeString(directory.resolve(second + ".yml"), yaml("Collision", 4200));
            Files.writeString(directory.resolve(third + ".yml"), yaml("collision", 300));
            Files.writeString(directory.resolve("not-a-uuid.yml"), yaml("Phantom", 1));
            Files.writeString(directory.resolve(malformed + ".yml"), "name: Broken\n");
            FilePlayerRepository repository = new FilePlayerRepository(directory, logger());
            repository.initialize();
            PlayerService players = new PlayerService(repository, logger());
            PlayerIdentityService identities = new PlayerIdentityService(players);
            EconomyService economy = new EconomyService(players);

            check(repository.findUniqueIdsByName("_imventex_").equals(Set.of(first)),
                    "startup index is case-insensitive");
            check(repository.findUniqueIdsByName("COLLISION").equals(Set.of(second, third)),
                    "startup index preserves collisions");
            check(repository.findUniqueIdsByName("Phantom").isEmpty(), "foreign filename is ignored");
            check(repository.findUniqueIdsByName("Broken").isEmpty(),
                    "malformed identity metadata is not indexed");
            check(identities.findByName("_IMVENTEX_").status() == PlayerLookupStatus.FOUND,
                    "known offline name resolves");
            check(identities.findByName("collision").status() == PlayerLookupStatus.AMBIGUOUS,
                    "duplicate name is ambiguous");
            check(identities.findByName("missing").status() == PlayerLookupStatus.NOT_FOUND,
                    "unknown name is absent");
            check(identities.resolve(first.toString()).identity().orElseThrow().uniqueId().equals(first),
                    "known UUID resolves");
            check(identities.resolve(UUID.randomUUID().toString()).status() == PlayerLookupStatus.NOT_FOUND,
                    "unknown UUID does not create player");
            check(!players.isLoaded(first) && economy.getCoins(first).isEmpty()
                            && economy.getKnownCoins(first).orElseThrow() == 12500L,
                    "offline profile read leaves loaded cache and existing economy semantics unchanged");
            check(economy.getKnownCoins(UUID.randomUUID()).isEmpty(), "unknown wallet is absent");

            CorePlayer loaded = players.loadPlayer(first, "NewName");
            check(identities.findByName("NewName").identity().orElseThrow().uniqueId().equals(first),
                    "loaded name change is visible before save");
            check(identities.findByName("_ImVentex_").status() == PlayerLookupStatus.NOT_FOUND,
                    "stale disk name cannot resolve loaded player");
            economy.addCoinsDeferred(first, 500L);
            check(economy.getKnownCoins(first).orElseThrow() == 13000L,
                    "loaded wallet takes priority over persisted wallet");
            players.savePlayer(first);
            check(repository.findUniqueIdsByName("NewName").equals(Set.of(first))
                            && repository.findUniqueIdsByName("_ImVentex_").isEmpty(),
                    "successful save updates index and removes old name");
            check(!repository.findUniqueIdsByName("NewName").isEmpty(),
                    "name lookup uses prebuilt index");

            Path failedPath = directory.resolve(UUID.randomUUID() + ".yml");
            Files.createDirectory(failedPath);
            CorePlayer failed = new CorePlayer(UUID.fromString(failedPath.getFileName().toString().replace(".yml", "")),
                    "FailedSave", Instant.EPOCH, Instant.EPOCH,
                    dev.vapee.core.player.settings.PlayerSettings.defaults(),
                    dev.vapee.core.economy.CoinWallet.empty(),
                    dev.vapee.core.player.social.PlayerSocial.empty());
            boolean rejected = false;
            try {
                repository.save(failed);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            check(rejected && repository.findUniqueIdsByName("FailedSave").isEmpty(),
                    "failed replace cannot claim a persisted index update");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        System.out.println("IdentityHarness passed " + checks + " checks.");
    }

    private static void domain() {
        UUID id = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(id, "Player", Instant.EPOCH, Instant.EPOCH);
        check(identity.equals(new PlayerIdentity(id, "Player", Instant.EPOCH, Instant.EPOCH)),
                "identity is immutable value snapshot");
        reject(() -> new PlayerIdentity(id, " ", Instant.EPOCH, Instant.EPOCH));
        reject(() -> new PlayerIdentity(id, "Player", Instant.ofEpochSecond(2), Instant.EPOCH));
        check(PlayerLookupResult.ambiguous().identity().isEmpty(), "ambiguous result has no random identity");
    }

    private static void lifecycle() throws Exception {
        check(CoreModule.class.isAssignableFrom(IdentityModule.class)
                        && !ReloadParticipant.class.isAssignableFrom(IdentityModule.class),
                "Identity is a CoreModule, not a ReloadParticipant");
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(core.indexOf("moduleManager.register(economyModule)")
                        < core.indexOf("moduleManager.register(identityModule)")
                        && core.indexOf("moduleManager.register(identityModule)")
                        < core.indexOf("moduleManager.register(rewardModule)"),
                "Identity registers directly after Economy");
        check(core.split("moduleManager.register\\(", -1).length - 1 == 23,
                "twenty-three modules register");
        String source = Files.readString(Path.of("src/main/java/dev/vapee/core/identity/IdentityModule.java"));
        check(!source.contains("runTask") && !source.contains("registerEvents")
                        && source.contains("setExecutor(null)") && source.contains("setTabCompleter(null)"),
                "Identity owns no tasks/listeners and deregisters command on shutdown");
        check(core.contains("presentationModule, dailyQuestModule)"),
                "six reload participants remain unchanged");
    }

    private static String yaml(String name, long coins) {
        return "name: '" + name + "'\nfirst-join: 0\nlast-join: 0\neconomy:\n  coins: " + coins + "\n";
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("IdentityHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected validation failure");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

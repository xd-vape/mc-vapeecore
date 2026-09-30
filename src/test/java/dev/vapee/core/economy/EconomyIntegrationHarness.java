package dev.vapee.core.economy;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.reload.ReloadParticipant;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Real file persistence plus source-level module wiring checks; Paper validates actual enable. */
public final class EconomyIntegrationHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        testPersistence();
        testModuleWiring();
        System.out.println("EconomyIntegrationHarness passed " + checks + " checks.");
    }

    private static void testPersistence() throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-economy-harness-");
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        try {
            UUID id = UUID.randomUUID();
            Path file = directory.resolve(id + ".yml");
            String base = "name: Offline\nfirst-join: 1000\nlast-join: 1000\n";
            Files.writeString(file, base);
            FilePlayerRepository repository = new FilePlayerRepository(directory, logger);
            repository.initialize();
            PlayerService players = new PlayerService(repository, logger);
            EconomyService economy = new EconomyService(players);
            check(economy.getKnownCoins(id).orElseThrow() == 0L && !players.isLoaded(id),
                    "legacy missing economy defaults zero without loading");
            Files.writeString(file, base + "economy: {}\n");
            check(economy.getKnownCoins(id).orElseThrow() == 0L, "missing coins key defaults zero");
            Files.writeString(file, base + "economy:\n  coins: 1250\n");
            check(economy.getKnownCoins(id).orElseThrow() == 1250L, "known offline actual file read");
            check(players.findKnownIdsByName("offline").equals(java.util.Set.of(id)), "indexed full case-insensitive name");
            check(players.findKnownIdsByName("Off").isEmpty(), "index no partial match");
            players.loadPlayer(id, "Offline");
            var player = players.getPlayer(id).orElseThrow();
            player.getSettings().setSoundsEnabled(false);
            UUID ignored = UUID.randomUUID();
            player.getSocial().ignore(ignored);
            player.getOnlineRewardProgress().setProcessedPlaytimeTicks(1234L);
            economy.setCoins(id, Long.MAX_VALUE);
            check(repository.findByUniqueId(id).orElseThrow().getWallet().getCoins() == Long.MAX_VALUE,
                    "max long durable roundtrip");
            players.unloadPlayer(id);
            check(economy.getKnownCoins(id).orElseThrow() == Long.MAX_VALUE, "offline max long read");
            var reloaded = repository.findByUniqueId(id).orElseThrow();
            check(!reloaded.getSettings().isSoundsEnabled() && reloaded.getSocial().isIgnoring(ignored)
                    && reloaded.getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow() == 1234L,
                    "coin writes preserve settings social and rewards");
            check(YamlConfiguration.loadConfiguration(file.toFile()).getLong("economy.coins") == Long.MAX_VALUE,
                    "unchanged economy.coins key");
            for (String bad : List.of("-1", "1.5", "nope")) {
                Files.writeString(file, base + "economy:\n  coins: " + bad + "\n");
                String previous = Files.readString(file);
                boolean rejected = false;
                try { repository.findByUniqueId(id); } catch (IllegalStateException expected) { rejected = true; }
                check(rejected && Files.readString(file).equals(previous), "invalid persisted wallet controlled/non-destructive " + bad);
            }
            check(economy.getKnownCoins(UUID.randomUUID()).isEmpty(), "unknown file empty");
        } finally {
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        }
    }

    private static void testModuleWiring() throws Exception {
        check(CoreModule.class.isAssignableFrom(EconomyModule.class), "real core module");
        check(!ReloadParticipant.class.isAssignableFrom(EconomyModule.class), "no reload participant");
        String source = Files.readString(Path.of("src/main/java/dev/vapee/core/economy/EconomyModule.java"));
        check(source.contains("new EconomyService(playerService)") && source.contains("playerModule.getPlayerService()"),
                "service and command use PlayerModule boundary");
        check(source.contains("newCoinsCommand.setExecutor(executor)") && source.contains("newCoinsCommand.setTabCompleter(executor)"),
                "executor and completer registration");
        String disable = source.substring(source.indexOf("public void disable()"));
        check(disable.contains("coinsCommand.setExecutor(null)") && disable.contains("coinsCommand.setTabCompleter(null)")
                && disable.contains("coinsCommand = null") && disable.contains("economyService = null"),
                "disable clears command hooks and services");
        check(disable.contains("Objects.requireNonNull(economyService"), "service unavailable after disable");
        check(!source.contains("Scheduler") && !source.contains("IdentityModule") && !source.contains("PresentationModule"),
                "no scheduler or inverted dependency");
        var yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/plugin.yml").toFile());
        check(yaml.getBoolean("permissions.vapeecore.economy.admin.children.vapeecore.economy.coins"),
                "admin child grants base");
        check(yaml.getString("commands.coins.permission").equals("vapeecore.economy.coins")
                && !yaml.contains("commands.coins.aliases"), "same command gate no aliases");
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(core.indexOf("moduleManager.register(economyModule)") < core.indexOf("moduleManager.register(identityModule)"),
                "economy still before identity");
        check(core.split("moduleManager.register\\(", -1).length - 1 == 25, "25 modules unchanged");
    }

    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
}

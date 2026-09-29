package dev.vapee.core.clan;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.reload.ReloadPlan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Logger;

public final class ClanIntegrationHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("vapeecore-clan-integration-");
        try {
            Path data = temp.resolve("plugins").resolve("VapeeCore");
            Files.createDirectories(data);
            Path clanFile = data.resolve("clans.yml");
            ClanService missing = new ClanService(new FileClanRepository(clanFile),
                    ClanLimits::defaults, Clock.systemUTC(), UUID::randomUUID);
            check(missing.getClan(UUID.randomUUID()).isEmpty() && !Files.exists(clanFile),
                    "missing clans.yml enables empty without eager rewrite");
            Path configFile = data.resolve("config.yml");
            Files.writeString(configFile, Files.readString(Path.of("src/main/resources/config.yml")));
            var constructor = ConfigService.class.getDeclaredConstructor(Path.class, Logger.class);
            constructor.setAccessible(true);
            ConfigService config = constructor.newInstance(configFile, logger());
            config.load();
            check(config.getClanLimits().equals(ClanLimits.defaults()), "config defaults");
            ClanService service = new ClanService(new FileClanRepository(clanFile),
                    config::getClanLimits, Clock.systemUTC(), UUID::randomUUID);
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();
            check(service.createClan(owner, "Sample Clan", "SAMPLE") == ClanResult.SUCCESS
                    && Files.readString(clanFile).contains("schema-version: 1"),
                    "service persists versioned runtime file");
            String original = Files.readString(configFile);
            Files.writeString(configFile, original.replace("max-members: 25", "max-members: 1")
                    .replace("max-outgoing-invites: 25", "max-outgoing-invites: 2"));
            ReloadPlan newLimits = config.prepareReload();
            check(config.getClanLimits().maxMembers() == 25, "prepare does not publish limits");
            newLimits.apply();
            check(config.getClanLimits().maxMembers() == 1
                    && config.getClanLimits().maxOutgoingInvites() == 2,
                    "custom limits apply atomically");
            check(service.inviteMember(owner, target) == ClanResult.MEMBER_LIMIT_REACHED
                    && service.getClanOf(owner).isPresent(),
                    "reload changes future mutations without deleting existing clan");
            newLimits.rollback();
            check(config.getClanLimits().equals(ClanLimits.defaults())
                    && service.inviteMember(owner, target) == ClanResult.SUCCESS,
                    "rollback restores limits consumed by same service");
            Files.writeString(configFile, original.replace("max-members: 25", "max-members: -2")
                    .replace("min-length: 3", "min-length: 40"));
            config.prepareReload().apply();
            check(config.getClanLimits().maxMembers() == 25
                    && config.getClanLimits().minNameLength() == 3
                    && config.getClanLimits().maxNameLength() == 24,
                    "invalid values and reversed range safely fall back");
            String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
            int friend = core.indexOf("moduleManager.register(friendModule)");
            int clan = core.indexOf("moduleManager.register(clanModule)");
            int reward = core.indexOf("moduleManager.register(rewardModule)");
            check(friend >= 0 && friend < clan && clan < reward, "Clan registers after Friend before Reward");
            check(core.split("moduleManager.register\\(", -1).length - 1 == 25, "25 modules registered");
            check(core.contains("presentationModule, dailyQuestModule")
                    && !core.contains("presentationModule, clanModule"), "six reload participants unchanged");
            String plugin = Files.readString(Path.of("src/main/resources/plugin.yml")).replace("\r\n", "\n");
            check(plugin.contains("  clan:\n") && plugin.contains("      - clans\n"), "command and alias declared");
            check(plugin.contains("  vapeecore.clan.use:\n")
                    && plugin.substring(plugin.indexOf("  vapeecore.clan.use:\n")).startsWith(
                    "  vapeecore.clan.use:\n    description: Allows using the clan system\n    default: true"),
                    "single clan permission defaults true");
            String moduleSource = Files.readString(Path.of("src/main/java/dev/vapee/core/clan/ClanModule.java"));
            check(moduleSource.contains("closeOpenInventories") && moduleSource.contains("HandlerList.unregisterAll")
                    && moduleSource.contains("setTabCompleter(null)"), "module lifecycle cleanup");
            check(!moduleSource.contains("runTask") && !moduleSource.contains("ReloadParticipant"),
                    "no scheduler or seventh reload participant");
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("ClanIntegrationHarness passed " + checks + " checks.");
    }
    private static Logger logger() {
        Logger result = Logger.getLogger("ClanIntegrationHarness-" + System.nanoTime());
        result.setUseParentHandlers(false);
        return result;
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}

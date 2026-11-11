package dev.vapee.core.rank;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import net.luckperms.api.LuckPerms;
import org.bukkit.Server;

import java.nio.file.Files;
import java.util.Map;

public final class RankModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("vapeecore-rank-lifecycle-");
        try (var fixture = new ModuleRuntimeFixture("unused")) {
            var server = ModuleRuntimeFixture.proxy(Server.class, (method, parameters) -> {
                throw new AssertionError("Rank lifecycle should not access Bukkit commands: " + method);
            });
            ModuleRuntimeFixture.set(fixture.plugin, "server", server);
            ModuleRuntimeFixture.set(fixture.plugin, "dataFolder", directory.toFile());
            var luckPerms = ModuleRuntimeFixture.proxy(LuckPerms.class, (method, parameters) -> {
                throw new AssertionError("Lifecycle should not query LuckPerms: " + method);
            });
            var permissions = ModuleRuntimeFixture.dependency(PermissionModule.class,
                    Map.of("luckPermsService", new LuckPermsService(luckPerms)));
            var module = new RankModule(fixture.plugin, new ConfigService(fixture.plugin), permissions);
            RankService previous = null;
            for (int phase = 0; phase < 2; phase++) {
                module.enable();
                var service = module.getRankService();
                check(service != previous && module.getStaffHierarchyService() != null,
                        "rank and staff services are published without a command");
                previous = service;
                module.disable();
                check(inactive(module::getRankService) && inactive(module::getStaffHierarchyService),
                        "disable clears both service references");
            }
        } finally {
            Files.deleteIfExists(directory);
        }
        System.out.println("RankModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static boolean inactive(Runnable getter) {
        try {
            getter.run();
            return false;
        } catch (NullPointerException expected) {
            return true;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

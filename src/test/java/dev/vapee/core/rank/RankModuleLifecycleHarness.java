package dev.vapee.core.rank;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.rank.command.RankCommand;
import net.luckperms.api.LuckPerms;
import org.bukkit.Server;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RankModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("vapeecore-rank-lifecycle-");
        try (var fixture = new ModuleRuntimeFixture("rank")) {
            var requested = new ArrayList<String>();
            var server = ModuleRuntimeFixture.proxy(Server.class, (method, parameters) -> switch (method) {
                case "getPluginCommand" -> {
                    String label = (String) parameters[0];
                    requested.add(label);
                    yield label.equals("rank") ? fixture.command : null;
                }
                case "getOnlinePlayers" -> List.copyOf(fixture.online);
                default -> throw new AssertionError(method);
            });
            ModuleRuntimeFixture.set(fixture.plugin, "server", server);
            ModuleRuntimeFixture.set(fixture.plugin, "dataFolder", directory.toFile());
            var luckPerms = ModuleRuntimeFixture.proxy(LuckPerms.class, (method, parameters) -> {
                throw new AssertionError("Lifecycle should not query LuckPerms: " + method);
            });
            var permissions = ModuleRuntimeFixture.dependency(PermissionModule.class,
                    Map.of("luckPermsService", new LuckPermsService(luckPerms)));
            var module = new RankModule(fixture.plugin, new ConfigService(fixture.plugin),
                    permissions, fixture.messages);
            for (int phase = 0; phase < 2; phase++) {
                requested.clear();
                module.enable();
                check(requested.equals(List.of("rank")), "enable requests only rank");
                check(fixture.command.getExecutor() instanceof RankCommand
                                && fixture.command.getTabCompleter() == fixture.command.getExecutor(),
                        "rank executor and completion are bound");
                check(module.getRankService() != null && module.getStaffHierarchyService() != null,
                        "rank and staff services are published");
                module.disable();
                check(fixture.command.getExecutor() == fixture.plugin && fixture.command.getTabCompleter() == null,
                        "disable releases rank executor and completion");
            }
        } finally {
            Files.deleteIfExists(directory);
        }
        System.out.println("RankModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

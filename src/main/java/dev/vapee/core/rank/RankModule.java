package dev.vapee.core.rank;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.rank.command.RankCommand;
import dev.vapee.core.rank.staff.StaffHierarchyService;
import dev.vapee.core.utility.OnlinePlayerResolver;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class RankModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final PermissionModule permissionModule;
    private final MessageService messageService;

    private RankService rankService;
    private StaffHierarchyService staffHierarchyService;
    private PluginCommand rankCommand;

    public RankModule(
            JavaPlugin plugin,
            ConfigService configService,
            PermissionModule permissionModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.permissionModule = Objects.requireNonNull(permissionModule, "permissionModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Rank";
    }

    @Override
    public void enable() {
        StaffHierarchyService newStaffHierarchyService = new StaffHierarchyService(
                configService::getStaffHierarchyConfig, permissionModule.getLuckPermsService());
        RankService newRankService = new RankService(
                configService,
                permissionModule.getLuckPermsService()
        );
        PluginCommand newRankCommand = Objects.requireNonNull(
                plugin.getCommand("rank"),
                "Command 'rank' is missing from plugin.yml"
        );
        RankCommand rankExecutor = new RankCommand(
                newRankService,
                configService::getServerName,
                messageService,
                new OnlinePlayerResolver(plugin.getServer()::getOnlinePlayers)::resolveExact,
                plugin.getServer()::getOnlinePlayers
        );
        try {
            newRankCommand.setExecutor(rankExecutor);
            newRankCommand.setTabCompleter(rankExecutor);
        } catch (RuntimeException exception) {
            clearCommand(newRankCommand);
            throw exception;
        }

        rankService = newRankService;
        staffHierarchyService = newStaffHierarchyService;
        rankCommand = newRankCommand;
        plugin.getLogger().info("Rank module enabled with LuckPerms as the source of truth.");
    }

    @Override
    public void disable() {
        try {
            clearCommand(rankCommand);
        } finally {
            rankCommand = null;
            rankService = null;
            staffHierarchyService = null;
        }
    }

    public RankService getRankService() {
        return Objects.requireNonNull(rankService, "RankModule is not enabled");
    }

    public StaffHierarchyService getStaffHierarchyService() {
        return Objects.requireNonNull(staffHierarchyService, "RankModule is not enabled");
    }

    private static void clearCommand(PluginCommand command) {
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }
}

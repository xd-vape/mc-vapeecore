package dev.vapee.core.rank;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.rank.staff.StaffHierarchyService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class RankModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final PermissionModule permissionModule;

    private RankService rankService;
    private StaffHierarchyService staffHierarchyService;

    public RankModule(
            JavaPlugin plugin,
            ConfigService configService,
            PermissionModule permissionModule
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.permissionModule = Objects.requireNonNull(permissionModule, "permissionModule");
    }

    @Override
    public String getName() {
        return "Rank";
    }

    @Override
    public void enable() {
        var luckPerms = permissionModule.getLuckPermsService();
        staffHierarchyService = new StaffHierarchyService(configService::getStaffHierarchyConfig, luckPerms);
        rankService = new RankService(configService, luckPerms);
        plugin.getLogger().info("Rank module enabled with LuckPerms as the source of truth.");
    }

    @Override
    public void disable() {
        rankService = null;
        staffHierarchyService = null;
    }

    public RankService getRankService() {
        return Objects.requireNonNull(rankService, "RankModule is not enabled");
    }

    public StaffHierarchyService getStaffHierarchyService() {
        return Objects.requireNonNull(staffHierarchyService, "RankModule is not enabled");
    }

}

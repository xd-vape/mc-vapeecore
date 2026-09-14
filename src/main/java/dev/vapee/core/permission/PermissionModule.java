package dev.vapee.core.permission;

import dev.vapee.core.module.CoreModule;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PermissionModule implements CoreModule {

    private final JavaPlugin plugin;

    private LuckPermsService luckPermsService;

    public PermissionModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String getName() {
        return "Permission";
    }

    @Override
    public void enable() {
        RegisteredServiceProvider<LuckPerms> registration = plugin.getServer()
                .getServicesManager()
                .getRegistration(LuckPerms.class);

        if (registration == null) {
            String message = "LuckPerms is declared as a dependency, but its service is not available.";
            plugin.getLogger().severe(message);
            throw new IllegalStateException(message);
        }

        LuckPerms luckPerms = registration.getProvider();
        if (luckPerms == null) {
            String message = "LuckPerms service registration has no provider.";
            plugin.getLogger().severe(message);
            throw new IllegalStateException(message);
        }

        luckPermsService = new LuckPermsService(luckPerms);
        plugin.getLogger().info("Connected to the LuckPerms service.");
    }

    @Override
    public void disable() {
        luckPermsService = null;
    }

    public LuckPermsService getLuckPermsService() {
        return Objects.requireNonNull(luckPermsService, "PermissionModule is not enabled");
    }
}

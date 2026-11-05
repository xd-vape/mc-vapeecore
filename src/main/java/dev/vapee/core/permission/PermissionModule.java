package dev.vapee.core.permission;

import dev.vapee.core.module.CoreModule;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.function.Supplier;

public final class PermissionModule implements CoreModule {

    private final JavaPlugin plugin;
    private final Supplier<CommandVisibilityConfig> visibilityConfig;

    private LuckPermsService luckPermsService;
    private CommandVisibilityListener commandVisibility;

    public PermissionModule(JavaPlugin plugin) {
        this(plugin, CommandVisibilityConfig::defaults);
    }

    public PermissionModule(JavaPlugin plugin, Supplier<CommandVisibilityConfig> visibilityConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.visibilityConfig = Objects.requireNonNull(visibilityConfig, "visibilityConfig");
    }

    @Override
    public String getName() {
        return "Permission";
    }

    @Override
    public void enable() {
        if (luckPermsService != null || commandVisibility != null)
            throw new IllegalStateException("PermissionModule is already enabled or awaiting listener cleanup");
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

        var service = new LuckPermsService(luckPerms);
        commandVisibility = new CommandVisibilityListener(plugin, visibilityConfig,
                label -> plugin.getServer().getCommandMap().getCommand(label));
        try {
            plugin.getServer().getPluginManager().registerEvents(commandVisibility, plugin);
            plugin.getLogger().info("Connected to the LuckPerms service.");
            luckPermsService = service;
        } catch (RuntimeException failure) {
            try { disable(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override
    public void disable() {
        try {
            if (commandVisibility != null) {
                commandVisibility.stop();
                HandlerList.unregisterAll(commandVisibility);
                commandVisibility = null;
            }
        } finally { luckPermsService = null; }
    }

    public LuckPermsService getLuckPermsService() {
        return Objects.requireNonNull(luckPermsService, "PermissionModule is not enabled");
    }
}

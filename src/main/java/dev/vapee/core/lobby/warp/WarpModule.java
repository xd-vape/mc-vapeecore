package dev.vapee.core.lobby.warp;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.lobby.warp.command.WarpCommand;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.NavigableMap;
import java.util.Objects;

public final class WarpModule implements CoreModule {

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final CommandHelpRenderer commandHelpRenderer;

    private WarpConfig warpConfig;
    private WarpService warpService;
    private PluginCommand warpCommand;

    public WarpModule(
            JavaPlugin plugin,
            MessageService messageService,
            CommandHelpRenderer commandHelpRenderer
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.commandHelpRenderer = Objects.requireNonNull(commandHelpRenderer, "commandHelpRenderer");
    }

    @Override
    public String getName() {
        return "Warp";
    }

    @Override
    public void enable() {
        WarpConfig newWarpConfig = new WarpConfig(plugin);
        NavigableMap<String, WarpPoint> loadedWarps = newWarpConfig.initialize();
        WarpService newWarpService = new WarpService(
                newWarpConfig,
                plugin.getServer()::getWorld,
                loadedWarps
        );
        PluginCommand newWarpCommand = Objects.requireNonNull(
                plugin.getCommand("warp"),
                "Command 'warp' is missing from plugin.yml"
        );
        WarpCommand executor = new WarpCommand(plugin, newWarpService, messageService, commandHelpRenderer);

        try {
            newWarpCommand.setExecutor(executor);
            newWarpCommand.setTabCompleter(executor);
        } catch (RuntimeException exception) {
            newWarpCommand.setExecutor(null);
            newWarpCommand.setTabCompleter(null);
            newWarpService.clear();
            throw exception;
        }

        warpConfig = newWarpConfig;
        warpService = newWarpService;
        warpCommand = newWarpCommand;
        plugin.getLogger().info("Warp module enabled with " + loadedWarps.size() + " configured warp(s).");
    }

    @Override
    public void disable() {
        if (warpCommand != null) {
            warpCommand.setExecutor(null);
            warpCommand.setTabCompleter(null);
        }
        if (warpService != null) {
            warpService.clear();
        }
        warpCommand = null;
        warpService = null;
        warpConfig = null;
    }

    public WarpService getWarpService() {
        return Objects.requireNonNull(warpService, "WarpModule is not enabled");
    }
}

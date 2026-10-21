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

    private final java.util.function.Consumer<BindingStage> bindingCheckpoint;
    enum BindingStage { EXECUTOR_BOUND, COMPLETER_BOUND }

    private WarpConfig warpConfig;
    private WarpService warpService;
    private PluginCommand warpCommand;

    public WarpModule(
            JavaPlugin plugin,
            MessageService messageService,
            CommandHelpRenderer commandHelpRenderer
    ) {
        this(plugin, messageService, commandHelpRenderer, stage -> { });
    }

    WarpModule(JavaPlugin plugin, MessageService messageService, CommandHelpRenderer commandHelpRenderer,
               java.util.function.Consumer<BindingStage> bindingCheckpoint) {
        this.bindingCheckpoint = Objects.requireNonNull(bindingCheckpoint, "bindingCheckpoint");
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
            bindingCheckpoint.accept(BindingStage.EXECUTOR_BOUND);
            newWarpCommand.setTabCompleter(executor);
            bindingCheckpoint.accept(BindingStage.COMPLETER_BOUND);
            warpConfig = newWarpConfig;
            warpService = newWarpService;
            warpCommand = newWarpCommand;
            plugin.getLogger().info("Warp module enabled with " + loadedWarps.size() + " configured warp(s).");
        } catch (RuntimeException exception) {
            cleanup(newWarpCommand, newWarpService, exception);
            clearRuntime();
            throw exception;
        }
    }

    @Override
    public void disable() {
        RuntimeException failure = cleanup(warpCommand, warpService, null);
        clearRuntime();
        if (failure != null) throw failure;
    }

    private RuntimeException cleanup(PluginCommand command, WarpService service, RuntimeException failure) {
        if (command != null) {
            failure = attempt(failure, () -> command.setExecutor(null));
            failure = attempt(failure, () -> command.setTabCompleter(null));
        }
        if (service != null) failure = attempt(failure, service::clear);
        return failure;
    }

    // Same small module-local best-effort pattern as UtilityModule; no shared binding framework.
    private static RuntimeException attempt(RuntimeException failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException exception) {
            if (failure == null) return exception;
            if (failure != exception) failure.addSuppressed(exception);
        }
        return failure;
    }

    private void clearRuntime() {
        warpCommand = null;
        warpService = null;
        warpConfig = null;
    }

    public WarpService getWarpService() {
        return Objects.requireNonNull(warpService, "WarpModule is not enabled");
    }
}

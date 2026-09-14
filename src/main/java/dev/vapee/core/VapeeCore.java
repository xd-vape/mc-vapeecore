package dev.vapee.core;

import dev.vapee.core.command.CoreCommand;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.listener.PlayerJoinListener;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.ModuleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class VapeeCore extends JavaPlugin {

    private ConfigService configService;
    private MessageService messageService;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        configService = new ConfigService(this);
        configService.load();

        messageService = new MessageService(configService);
        moduleManager = new ModuleManager(getLogger());
        moduleManager.enableAll();

        registerCommands();
        registerListeners();

        getLogger().info("VapeeCore " + getPluginMeta().getVersion()
                + " enabled with " + moduleManager.getModules().size() + " module(s)."
        );
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }

        getLogger().info("VapeeCore disabled.");
    }

    private void registerCommands() {
        PluginCommand coreCommand = Objects.requireNonNull(
                getCommand("vapeecore"),
                "Command 'vapeecore' is missing from plugin.yml"
        );
        coreCommand.setExecutor(new CoreCommand(this, configService, messageService));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(configService, messageService),
                this
        );
    }
}

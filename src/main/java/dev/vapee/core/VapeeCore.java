package dev.vapee.core;

import dev.vapee.core.command.CoreCommand;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class VapeeCore extends JavaPlugin {

    private ConfigService configService;
    private MessageService messageService;
    private ModuleManager moduleManager;
    private PlayerModule playerModule;

    @Override
    public void onEnable() {
        configService = new ConfigService(this);
        configService.load();

        messageService = new MessageService(configService);
        moduleManager = new ModuleManager(getLogger());

        playerModule = new PlayerModule(this, configService, messageService);
        moduleManager.register(playerModule);
        moduleManager.enableAll();

        registerCommands(playerModule.getPlayerService());

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

    private void registerCommands(PlayerService playerService) {
        PluginCommand coreCommand = Objects.requireNonNull(
                getCommand("vapeecore"),
                "Command 'vapeecore' is missing from plugin.yml"
        );
        coreCommand.setExecutor(new CoreCommand(
                this,
                configService,
                messageService,
                moduleManager,
                playerService
        ));
    }
}

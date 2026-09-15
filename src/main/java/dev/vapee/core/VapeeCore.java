package dev.vapee.core;

import dev.vapee.core.command.CoreCommand;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class VapeeCore extends JavaPlugin {

    private ConfigService configService;
    private MessageService messageService;
    private ModuleManager moduleManager;
    private PermissionModule permissionModule;
    private PlayerModule playerModule;
    private EconomyModule economyModule;
    private LobbyModule lobbyModule;

    @Override
    public void onEnable() {
        configService = new ConfigService(this);
        configService.load();

        messageService = new MessageService(configService);
        moduleManager = new ModuleManager(getLogger());

        permissionModule = new PermissionModule(this);
        playerModule = new PlayerModule(this, configService, messageService);
        economyModule = new EconomyModule(this, playerModule, messageService);
        lobbyModule = new LobbyModule(this, messageService);
        moduleManager.register(permissionModule);
        moduleManager.register(playerModule);
        moduleManager.register(economyModule);
        moduleManager.register(lobbyModule);
        moduleManager.enableAll();

        registerCommands(
                playerModule.getPlayerService(),
                permissionModule.getLuckPermsService()
        );

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

    private void registerCommands(PlayerService playerService, LuckPermsService luckPermsService) {
        PluginCommand coreCommand = Objects.requireNonNull(
                getCommand("vapeecore"),
                "Command 'vapeecore' is missing from plugin.yml"
        );
        coreCommand.setExecutor(new CoreCommand(
                this,
                configService,
                messageService,
                moduleManager,
                playerService,
                luckPermsService
        ));
    }
}

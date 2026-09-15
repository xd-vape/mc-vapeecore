package dev.vapee.core;

import dev.vapee.core.chat.ChatModule;
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
import dev.vapee.core.presentation.PresentationModule;
import dev.vapee.core.privatemessage.PrivateMessageModule;
import dev.vapee.core.reload.ReloadService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class VapeeCore extends JavaPlugin {

    private ConfigService configService;
    private MessageService messageService;
    private ModuleManager moduleManager;
    private PermissionModule permissionModule;
    private PlayerModule playerModule;
    private EconomyModule economyModule;
    private LobbyModule lobbyModule;
    private ChatModule chatModule;
    private PrivateMessageModule privateMessageModule;
    private PresentationModule presentationModule;

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
        chatModule = new ChatModule(this, permissionModule, messageService);
        privateMessageModule = new PrivateMessageModule(this, playerModule, messageService);
        presentationModule = new PresentationModule(
                this,
                configService,
                messageService,
                permissionModule,
                playerModule,
                economyModule,
                lobbyModule
        );
        moduleManager.register(permissionModule);
        moduleManager.register(playerModule);
        moduleManager.register(economyModule);
        moduleManager.register(lobbyModule);
        moduleManager.register(chatModule);
        moduleManager.register(privateMessageModule);
        moduleManager.register(presentationModule);
        moduleManager.enableAll();

        ReloadService reloadService = new ReloadService(
                getLogger(),
                List.of(configService, lobbyModule, chatModule, privateMessageModule, presentationModule)
        );
        registerCommands(
                playerModule.getPlayerService(),
                permissionModule.getLuckPermsService(),
                reloadService
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

    private void registerCommands(
            PlayerService playerService,
            LuckPermsService luckPermsService,
            ReloadService reloadService
    ) {
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
                luckPermsService,
                reloadService
        ));
    }
}

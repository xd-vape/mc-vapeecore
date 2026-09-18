package dev.vapee.core;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.blackjack.BlackjackModule;
import dev.vapee.core.chat.ChatModule;
import dev.vapee.core.command.CoreCommand;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.experience.LobbyExperienceModule;
import dev.vapee.core.lobby.warp.WarpModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.presentation.PresentationModule;
import dev.vapee.core.privatemessage.PrivateMessageModule;
import dev.vapee.core.reload.ReloadService;
import dev.vapee.core.settings.SettingsModule;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.utility.UtilityModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class VapeeCore extends JavaPlugin {

    private ConfigService configService;
    private MessageService messageService;
    private CommandHelpRenderer commandHelpRenderer;
    private ModuleManager moduleManager;
    private PermissionModule permissionModule;
    private PlayerModule playerModule;
    private SocialModule socialModule;
    private EconomyModule economyModule;
    private LobbyModule lobbyModule;
    private ChatModule chatModule;
    private PrivateMessageModule privateMessageModule;
    private PresentationModule presentationModule;
    private SettingsModule settingsModule;
    private ActivityModule activityModule;
    private UtilityModule utilityModule;
    private BlackjackModule blackjackModule;
    private WarpModule warpModule;
    private LobbyExperienceModule lobbyExperienceModule;

    @Override
    public void onEnable() {
        configService = new ConfigService(this);
        configService.load();

        messageService = new MessageService(configService);
        commandHelpRenderer = new CommandHelpRenderer(messageService);
        moduleManager = new ModuleManager(getLogger());

        permissionModule = new PermissionModule(this);
        playerModule = new PlayerModule(this, configService, messageService);
        socialModule = new SocialModule(this, playerModule, messageService);
        economyModule = new EconomyModule(this, playerModule, messageService, commandHelpRenderer);
        lobbyModule = new LobbyModule(this, playerModule, messageService);
        chatModule = new ChatModule(this, permissionModule, socialModule, messageService);
        privateMessageModule = new PrivateMessageModule(this, playerModule, socialModule, messageService);
        presentationModule = new PresentationModule(
                this,
                configService,
                messageService,
                permissionModule,
                playerModule,
                economyModule,
                lobbyModule
        );
        settingsModule = new SettingsModule(
                this,
                playerModule,
                presentationModule,
                messageService
        );
        activityModule = new ActivityModule(this, playerModule);
        utilityModule = new UtilityModule(
                this,
                lobbyModule,
                activityModule,
                messageService
        );
        blackjackModule = new BlackjackModule(
                this,
                activityModule,
                messageService,
                commandHelpRenderer,
                playerId -> lobbyModule.getLobbyPlayerStateService().isBuildMode(playerId)
        );
        warpModule = new WarpModule(this, messageService, commandHelpRenderer);
        lobbyExperienceModule = new LobbyExperienceModule(
                this,
                lobbyModule,
                playerModule,
                settingsModule,
                warpModule,
                messageService
        );
        moduleManager.register(permissionModule);
        moduleManager.register(playerModule);
        moduleManager.register(socialModule);
        moduleManager.register(economyModule);
        moduleManager.register(lobbyModule);
        moduleManager.register(chatModule);
        moduleManager.register(privateMessageModule);
        moduleManager.register(presentationModule);
        moduleManager.register(settingsModule);
        moduleManager.register(activityModule);
        moduleManager.register(utilityModule);
        moduleManager.register(blackjackModule);
        moduleManager.register(warpModule);
        moduleManager.register(lobbyExperienceModule);
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
        CoreCommand executor = new CoreCommand(
                this,
                configService,
                messageService,
                moduleManager,
                playerService,
                luckPermsService,
                reloadService,
                commandHelpRenderer
        );
        coreCommand.setExecutor(executor);
        coreCommand.setTabCompleter(executor);
    }
}

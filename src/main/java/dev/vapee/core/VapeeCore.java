package dev.vapee.core;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.blackjack.BlackjackModule;
import dev.vapee.core.chat.ChatModule;
import dev.vapee.core.clan.ClanModule;
import dev.vapee.core.clan.Clan;
import dev.vapee.core.command.CoreCommand;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.config.ConfigEvolution;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.experience.LobbyExperienceModule;
import dev.vapee.core.lobby.warp.WarpModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.moderation.ModerationModule;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.module.ShutdownReport;
import dev.vapee.core.onlinereward.OnlineRewardModule;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.presentation.PresentationModule;
import dev.vapee.core.presence.PresenceModule;
import dev.vapee.core.privatemessage.PrivateMessageModule;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.quest.daily.DailyQuestModule;
import dev.vapee.core.rank.RankModule;
import dev.vapee.core.reload.ReloadService;
import dev.vapee.core.reward.RewardModule;
import dev.vapee.core.settings.SettingsModule;
import dev.vapee.core.seat.SeatModule;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.utility.UtilityModule;
import dev.vapee.core.visibility.VisibilityModule;
import dev.vapee.core.worlddisplay.WorldDisplayModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class VapeeCore extends JavaPlugin {

    private ConfigService configService;
    private MessageService messageService;
    private dev.vapee.core.i18n.LocalizationModule localizationModule;
    private CommandHelpRenderer commandHelpRenderer;
    private ModuleManager moduleManager;
    private RuntimeException shutdownDiagnosticFailure;
    private PermissionModule permissionModule;
    private RankModule rankModule;
    private PlayerModule playerModule;
    private SocialModule socialModule;
    private EconomyModule economyModule;
    private IdentityModule identityModule;
    private ModerationModule moderationModule;
    private FriendModule friendModule;
    private PresenceModule presenceModule;
    private ClanModule clanModule;
    private RewardModule rewardModule;
    private OnlineRewardModule onlineRewardModule;
    private QuestModule questModule;
    private DailyQuestModule dailyQuestModule;
    private LobbyModule lobbyModule;
    private VisibilityModule visibilityModule;
    private ChatModule chatModule;
    private PrivateMessageModule privateMessageModule;
    private PresentationModule presentationModule;
    private SettingsModule settingsModule;
    private ActivityModule activityModule;
    private UtilityModule utilityModule;
    private SeatModule seatModule;
    private WorldDisplayModule worldDisplayModule;
    private BlackjackModule blackjackModule;
    private WarpModule warpModule;
    private LobbyExperienceModule lobbyExperienceModule;

    @Override
    public void onEnable() {
        new ConfigEvolution(this).evolveAll();
        configService = new ConfigService(this);
        configService.load();

        localizationModule = new dev.vapee.core.i18n.LocalizationModule(this);
        messageService = new MessageService(configService, localizationModule.service());
        commandHelpRenderer = new CommandHelpRenderer(messageService);
        moduleManager = new ModuleManager(getLogger());

        permissionModule = new PermissionModule(this);
        rankModule = new RankModule(this, configService, permissionModule, messageService);
        playerModule = new PlayerModule(this, configService, messageService);
        socialModule = new SocialModule(this, playerModule, messageService);
        economyModule = new EconomyModule(this, playerModule, rankModule, messageService, commandHelpRenderer);
        identityModule = new IdentityModule(this, playerModule, rankModule, economyModule, messageService);
        moderationModule = new ModerationModule(this, identityModule, rankModule, messageService);
        friendModule = new FriendModule(this, configService, playerModule, socialModule,
                identityModule, messageService, commandHelpRenderer);
        presenceModule = new PresenceModule(this, playerModule::getPlayerService,
                playerModule::getPlayerSettingsService, socialModule::getSocialService,
                friendModule::getFriendService, messageService);
        clanModule = new ClanModule(this, configService, identityModule, messageService, commandHelpRenderer);
        rewardModule = new RewardModule(this, playerModule, economyModule);
        onlineRewardModule = new OnlineRewardModule(
                this,
                configService,
                playerModule,
                rewardModule,
                messageService
        );
        questModule = new QuestModule(this, playerModule, rewardModule, messageService);
        dailyQuestModule = new DailyQuestModule(this, playerModule, questModule, messageService);
        lobbyModule = new LobbyModule(this, playerModule, messageService);
        visibilityModule = new VisibilityModule(this, playerModule, socialModule, friendModule, lobbyModule);
        chatModule = new ChatModule(this, permissionModule, rankModule, socialModule, messageService);
        privateMessageModule = new PrivateMessageModule(this, playerModule, socialModule, messageService, moderationModule);
        presentationModule = new PresentationModule(
                this,
                configService,
                messageService,
                permissionModule,
                rankModule,
                playerModule,
                economyModule,
                lobbyModule,
                uniqueId -> clanModule.getClanService().getClanOf(uniqueId).map(Clan::tag)
        );
        settingsModule = new SettingsModule(
                this,
                playerModule,
                presentationModule,
                identityModule,
                visibilityModule,
                lobbyModule,
                messageService
        );
        activityModule = new ActivityModule(this, playerModule);
        utilityModule = new UtilityModule(
                this,
                lobbyModule,
                activityModule,
                rankModule,
                messageService
        );
        seatModule = new SeatModule(this, lobbyModule, activityModule);
        worldDisplayModule = new WorldDisplayModule(this);
        blackjackModule = new BlackjackModule(
                this,
                activityModule,
                seatModule,
                worldDisplayModule,
                lobbyModule,
                messageService,
                commandHelpRenderer,
                questModule
        );
        warpModule = new WarpModule(this, messageService, commandHelpRenderer);
        lobbyExperienceModule = new LobbyExperienceModule(
                this,
                lobbyModule,
                playerModule,
                settingsModule,
                warpModule,
                visibilityModule,
                activityModule,
                friendModule,
                utilityModule,
                messageService
        );
        moduleManager.register(localizationModule);
        moduleManager.register(permissionModule);
        moduleManager.register(rankModule);
        moduleManager.register(playerModule);
        moduleManager.register(socialModule);
        moduleManager.register(economyModule);
        moduleManager.register(identityModule);
        moduleManager.register(moderationModule);
        moduleManager.register(friendModule);
        moduleManager.register(presenceModule);
        moduleManager.register(clanModule);
        moduleManager.register(rewardModule);
        moduleManager.register(onlineRewardModule);
        moduleManager.register(questModule);
        moduleManager.register(dailyQuestModule);
        moduleManager.register(lobbyModule);
        moduleManager.register(visibilityModule);
        moduleManager.register(chatModule);
        moduleManager.register(privateMessageModule);
        moduleManager.register(presentationModule);
        moduleManager.register(settingsModule);
        moduleManager.register(activityModule);
        moduleManager.register(utilityModule);
        moduleManager.register(seatModule);
        moduleManager.register(worldDisplayModule);
        moduleManager.register(blackjackModule);
        moduleManager.register(warpModule);
        moduleManager.register(lobbyExperienceModule);
        moduleManager.enableAll();

        ReloadService reloadService = new ReloadService(
                getLogger(),
                List.of(configService, localizationModule, lobbyModule, chatModule, privateMessageModule,
                        presentationModule, dailyQuestModule)
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
        ShutdownReport report = moduleManager == null ? null : moduleManager.getLastCleanupReport().orElse(null);
        java.util.logging.Level level = java.util.logging.Level.INFO;
        String message;
        if (report == null) {
            message = "VapeeCore disabled; no module cleanup attempt was recorded.";
        } else if (!report.allCallsReturnedNormally()) {
            level = java.util.logging.Level.SEVERE;
            message = "VapeeCore cleanup incomplete during " + report.phase() + ": "
                    + report.incompleteOutcomes().stream().map(ShutdownReport.ModuleOutcome::name).toList()
                    + ". No automatic retry; unresolved in-memory data may be lost at JVM exit.";
        } else if (report.startupFailure() != null) {
            level = java.util.logging.Level.WARNING;
            message = "VapeeCore disabled after startup failure; predecessor rollback calls returned normally.";
        } else if (!report.diagnostics().isEmpty()) {
            level = java.util.logging.Level.WARNING;
            message = "VapeeCore module shutdown calls returned normally, but lifecycle diagnostics failed.";
        } else {
            message = "VapeeCore disabled; all module shutdown calls returned normally.";
        }
        try {
            getLogger().log(level, message);
        } catch (RuntimeException diagnostic) {
            shutdownDiagnosticFailure = diagnostic;
            if (moduleManager != null) moduleManager.recordCompletionDiagnostic(diagnostic);
        }
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
                commandHelpRenderer,
                new dev.vapee.core.quest.daily.command.CoreQuestCommand(
                        dailyQuestModule.getDailyQuestService(), playerService, messageService,
                        () -> getServer().getOnlinePlayers(), java.time.Instant::now,
                        new dev.vapee.core.command.OnlineStaffTargetGuard(rankModule.getStaffHierarchyService(), getLogger()))
        );
        coreCommand.setExecutor(executor);
        coreCommand.setTabCompleter(executor);
    }
}

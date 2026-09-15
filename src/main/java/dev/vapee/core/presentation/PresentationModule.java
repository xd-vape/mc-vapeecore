package dev.vapee.core.presentation;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presentation.config.PresentationConfig;
import dev.vapee.core.presentation.scoreboard.ScoreboardService;
import dev.vapee.core.presentation.tablist.TablistService;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class PresentationModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final MessageService messageService;
    private final PermissionModule permissionModule;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final LobbyModule lobbyModule;

    private PresentationConfig presentationConfig;
    private PresentationRenderer presentationRenderer;
    private ScoreboardService scoreboardService;
    private TablistService tablistService;
    private PresentationService presentationService;
    private PresentationListener presentationListener;
    private BukkitTask updateTask;

    public PresentationModule(
            JavaPlugin plugin,
            ConfigService configService,
            MessageService messageService,
            PermissionModule permissionModule,
            PlayerModule playerModule,
            EconomyModule economyModule,
            LobbyModule lobbyModule
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.permissionModule = Objects.requireNonNull(permissionModule, "permissionModule");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.economyModule = Objects.requireNonNull(economyModule, "economyModule");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
    }

    @Override
    public String getName() {
        return "Presentation";
    }

    @Override
    public void enable() {
        LuckPermsService luckPermsService = permissionModule.getLuckPermsService();
        PlayerSettingsService playerSettingsService = playerModule.getPlayerSettingsService();
        EconomyService economyService = economyModule.getEconomyService();
        LobbyService lobbyService = lobbyModule.getLobbyService();

        PresentationConfig newPresentationConfig = new PresentationConfig(plugin);
        newPresentationConfig.initialize();
        PresentationRenderer newPresentationRenderer = new PresentationRenderer(
                plugin,
                configService,
                messageService,
                luckPermsService,
                economyService,
                newPresentationConfig
        );
        ScoreboardService newScoreboardService = new ScoreboardService(
                plugin,
                newPresentationConfig,
                playerSettingsService,
                lobbyService
        );
        TablistService newTablistService = new TablistService(newPresentationConfig);
        PresentationService newPresentationService = new PresentationService(
                plugin,
                newPresentationConfig,
                newPresentationRenderer,
                newScoreboardService,
                newTablistService
        );

        PresentationListener newPresentationListener = null;
        BukkitTask newUpdateTask = null;
        if (newPresentationConfig.isEnabled()) {
            newPresentationListener = new PresentationListener(plugin, newPresentationService);
            try {
                plugin.getServer().getPluginManager().registerEvents(newPresentationListener, plugin);
                newUpdateTask = plugin.getServer().getScheduler().runTaskTimer(
                        plugin,
                        newPresentationService::updateAll,
                        1L,
                        newPresentationConfig.getUpdateIntervalTicks()
                );
            } catch (RuntimeException exception) {
                if (newUpdateTask != null) {
                    newUpdateTask.cancel();
                }
                HandlerList.unregisterAll(newPresentationListener);
                newPresentationService.removeAll();
                throw exception;
            }
        }

        presentationConfig = newPresentationConfig;
        presentationRenderer = newPresentationRenderer;
        scoreboardService = newScoreboardService;
        tablistService = newTablistService;
        presentationService = newPresentationService;
        presentationListener = newPresentationListener;
        updateTask = newUpdateTask;

        if (newPresentationConfig.isEnabled()) {
            plugin.getLogger().info("Presentation module enabled with an update interval of "
                    + newPresentationConfig.getUpdateIntervalTicks() + " tick(s)."
            );
        } else {
            plugin.getLogger().info("Presentation module enabled but UI updates are disabled in presentation.yml.");
        }
    }

    @Override
    public void disable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (presentationListener != null) {
            HandlerList.unregisterAll(presentationListener);
        }

        try {
            if (presentationService != null) {
                presentationService.removeAll();
            }
        } finally {
            updateTask = null;
            presentationListener = null;
            presentationService = null;
            tablistService = null;
            scoreboardService = null;
            presentationRenderer = null;
            presentationConfig = null;
        }
    }

    public PresentationService getPresentationService() {
        return Objects.requireNonNull(presentationService, "PresentationModule is not enabled");
    }
}

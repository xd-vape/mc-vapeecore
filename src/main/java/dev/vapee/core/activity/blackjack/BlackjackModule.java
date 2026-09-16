package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.blackjack.ui.BlackjackModeListener;
import dev.vapee.core.activity.blackjack.ui.BlackjackModeMenu;
import dev.vapee.core.activity.blackjack.ui.BlackjackTableListener;
import dev.vapee.core.activity.blackjack.ui.BlackjackTableMenu;
import dev.vapee.core.activity.navigation.ActivityCatalog;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class BlackjackModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ActivityModule activityModule;
    private final LobbyModule lobbyModule;
    private final MessageService messageService;

    private ActivityService activityService;
    private ActivityCatalog activityCatalog;
    private BlackjackService blackjackService;
    private BlackjackModeMenu modeMenu;
    private BlackjackTableMenu tableMenu;
    private BlackjackModeListener modeListener;
    private BlackjackTableListener tableListener;

    public BlackjackModule(
            JavaPlugin plugin,
            ActivityModule activityModule,
            LobbyModule lobbyModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Blackjack";
    }

    @Override
    public void enable() {
        ActivityService newActivityService = activityModule.getActivityService();
        ActivityCatalog newActivityCatalog = activityModule.getActivityCatalog();
        LobbyService newLobbyService = lobbyModule.getLobbyService();
        BlackjackService newBlackjackService = new BlackjackService(
                plugin,
                newActivityService,
                newLobbyService,
                messageService
        );
        BlackjackActivityType activityType = new BlackjackActivityType(newBlackjackService);
        ActivityResult typeResult = newActivityService.registerActivityType(activityType);
        if (typeResult != ActivityResult.SUCCESS) {
            throw new IllegalStateException("Could not register blackjack activity type: " + typeResult);
        }

        BlackjackModeMenu newModeMenu = new BlackjackModeMenu(plugin);
        BlackjackTableMenu newTableMenu = new BlackjackTableMenu(plugin, newBlackjackService);
        newBlackjackService.setTableRefresher(newTableMenu::refreshSession);
        BlackjackEntryPoint entryPoint = new BlackjackEntryPoint(
                newBlackjackService,
                newModeMenu,
                newTableMenu
        );
        BlackjackModeListener newModeListener = new BlackjackModeListener(newBlackjackService, newTableMenu);
        BlackjackTableListener newTableListener = new BlackjackTableListener(newBlackjackService);

        boolean catalogRegistered = false;
        try {
            newActivityCatalog.register(entryPoint);
            catalogRegistered = true;
            plugin.getServer().getPluginManager().registerEvents(newModeListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newTableListener, plugin);
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newTableListener);
            HandlerList.unregisterAll(newModeListener);
            try {
                newTableMenu.closeOpenInventories();
                newModeMenu.closeOpenInventories();
                newBlackjackService.shutdown();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            if (catalogRegistered) {
                newActivityCatalog.unregister(BlackjackActivityType.KEY);
            }
            ActivityResult unregisterResult = newActivityService.unregisterActivityType(BlackjackActivityType.KEY);
            if (unregisterResult != ActivityResult.SUCCESS
                    && unregisterResult != ActivityResult.ACTIVITY_TYPE_NOT_FOUND) {
                exception.addSuppressed(new IllegalStateException(
                        "Could not roll back blackjack activity type: " + unregisterResult
                ));
            }
            throw exception;
        }

        activityService = newActivityService;
        activityCatalog = newActivityCatalog;
        blackjackService = newBlackjackService;
        modeMenu = newModeMenu;
        tableMenu = newTableMenu;
        modeListener = newModeListener;
        tableListener = newTableListener;
        plugin.getLogger().info("Blackjack module enabled (Free Play, solo/public, no waiting).");
    }

    @Override
    public void disable() {
        if (tableListener != null) {
            HandlerList.unregisterAll(tableListener);
        }
        if (modeListener != null) {
            HandlerList.unregisterAll(modeListener);
        }
        cleanup("close blackjack table inventories", () -> {
            if (tableMenu != null) {
                tableMenu.closeOpenInventories();
            }
        });
        cleanup("close blackjack mode inventories", () -> {
            if (modeMenu != null) {
                modeMenu.closeOpenInventories();
            }
        });
        cleanup("shut down blackjack sessions and venues", () -> {
            if (blackjackService != null) {
                blackjackService.shutdown();
            }
        });
        cleanup("unregister blackjack entry point", () -> {
            if (activityCatalog != null) {
                activityCatalog.unregister(BlackjackActivityType.KEY);
            }
        });
        cleanup("unregister blackjack activity type", () -> {
            if (activityService != null) {
                ActivityResult result = activityService.unregisterActivityType(BlackjackActivityType.KEY);
                if (result != ActivityResult.SUCCESS && result != ActivityResult.ACTIVITY_TYPE_NOT_FOUND) {
                    throw new IllegalStateException("Could not unregister blackjack activity type: " + result);
                }
            }
        });

        tableListener = null;
        modeListener = null;
        tableMenu = null;
        modeMenu = null;
        blackjackService = null;
        activityCatalog = null;
        activityService = null;
    }

    public BlackjackService getBlackjackService() {
        return Objects.requireNonNull(blackjackService, "BlackjackModule is not enabled");
    }

    private void cleanup(String description, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not " + description + ".", exception);
        }
    }
}

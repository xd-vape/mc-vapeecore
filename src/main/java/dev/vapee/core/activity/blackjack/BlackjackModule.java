package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.blackjack.command.BlackjackCommand;
import dev.vapee.core.activity.blackjack.table.BlackjackSeatService;
import dev.vapee.core.activity.blackjack.table.BlackjackTableConfig;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import dev.vapee.core.activity.blackjack.ui.BlackjackTableListener;
import dev.vapee.core.activity.blackjack.ui.BlackjackTableMenu;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class BlackjackModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ActivityModule activityModule;
    private final MessageService messageService;
    private final CommandHelpRenderer commandHelpRenderer;
    private final Predicate<UUID> buildModeCheck;

    private ActivityService activityService;
    private BlackjackTableConfig tableConfig;
    private BlackjackTableService tableService;
    private BlackjackSeatService seatService;
    private BlackjackService blackjackService;
    private BlackjackTableMenu tableMenu;
    private BlackjackTableListener tableListener;
    private PluginCommand blackjackCommand;

    public BlackjackModule(
            JavaPlugin plugin,
            ActivityModule activityModule,
            MessageService messageService,
            CommandHelpRenderer commandHelpRenderer,
            Predicate<UUID> buildModeCheck
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.commandHelpRenderer = Objects.requireNonNull(commandHelpRenderer, "commandHelpRenderer");
        this.buildModeCheck = Objects.requireNonNull(buildModeCheck, "buildModeCheck");
    }

    @Override
    public String getName() {
        return "Blackjack";
    }

    @Override
    public void enable() {
        ActivityService newActivityService = activityModule.getActivityService();
        BlackjackTableConfig newTableConfig = new BlackjackTableConfig(plugin);
        newTableConfig.initialize();
        BlackjackSeatService newSeatService = new BlackjackSeatService(plugin, newActivityService);
        BlackjackTableService newTableService = new BlackjackTableService(
                newActivityService,
                newTableConfig,
                newSeatService,
                worldName -> plugin.getServer().getWorld(worldName) != null,
                plugin.getLogger()
        );
        BlackjackService newBlackjackService = new BlackjackService(
                plugin,
                newActivityService,
                newTableService,
                newSeatService,
                messageService,
                buildModeCheck
        );
        BlackjackActivityType activityType = new BlackjackActivityType(newBlackjackService);
        ActivityResult typeResult = newActivityService.registerActivityType(activityType);
        if (typeResult != ActivityResult.SUCCESS) {
            throw new IllegalStateException("Could not register blackjack activity type: " + typeResult);
        }

        BlackjackTableMenu newTableMenu = null;
        BlackjackTableListener newTableListener = null;
        PluginCommand newBlackjackCommand = null;

        try {
            newTableService.activateConfiguredTables();
            int staleSeats = newSeatService.cleanupStaleSeats();
            if (staleSeats > 0) {
                plugin.getLogger().info("Removed " + staleSeats + " stale blackjack seat entity/entities.");
            }
            newTableMenu = new BlackjackTableMenu(plugin, newBlackjackService);
            newBlackjackService.setTableRefresher(newTableMenu::refreshSession);
            newTableListener = new BlackjackTableListener(
                    plugin,
                    newBlackjackService,
                    newTableService,
                    newSeatService,
                    newTableMenu
            );
            newBlackjackCommand = Objects.requireNonNull(
                    plugin.getCommand("blackjack"),
                    "Command 'blackjack' is missing from plugin.yml"
            );
            BlackjackCommand commandExecutor = new BlackjackCommand(
                    plugin,
                    newTableConfig,
                    newTableService,
                    messageService,
                    commandHelpRenderer
            );
            plugin.getServer().getPluginManager().registerEvents(newTableListener, plugin);
            newBlackjackCommand.setExecutor(commandExecutor);
            newBlackjackCommand.setTabCompleter(commandExecutor);
        } catch (RuntimeException exception) {
            if (newTableListener != null) {
                HandlerList.unregisterAll(newTableListener);
            }
            if (newBlackjackCommand != null) {
                newBlackjackCommand.setExecutor(null);
                newBlackjackCommand.setTabCompleter(null);
            }
            try {
                if (newTableMenu != null) {
                    newTableMenu.closeOpenInventories();
                }
                newBlackjackService.shutdown();
                newTableService.shutdown();
                newSeatService.shutdown();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
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
        tableConfig = newTableConfig;
        tableService = newTableService;
        seatService = newSeatService;
        blackjackService = newBlackjackService;
        tableMenu = newTableMenu;
        tableListener = newTableListener;
        blackjackCommand = newBlackjackCommand;
        plugin.getLogger().info("Blackjack module enabled with "
                + newTableService.getDefinitions().size() + " physical table(s) (Free Play).");
    }

    @Override
    public void disable() {
        if (tableListener != null) {
            HandlerList.unregisterAll(tableListener);
        }
        if (blackjackCommand != null) {
            blackjackCommand.setExecutor(null);
            blackjackCommand.setTabCompleter(null);
        }
        cleanup("close blackjack table inventories", () -> {
            if (tableMenu != null) {
                tableMenu.closeOpenInventories();
            }
        });
        cleanup("stop blackjack UI updates", () -> {
            if (blackjackService != null) {
                blackjackService.shutdown();
            }
        });
        cleanup("shut down blackjack sessions and venues", () -> {
            if (tableService != null) {
                tableService.shutdown();
            }
        });
        cleanup("remove blackjack seat entities", () -> {
            if (seatService != null) {
                seatService.shutdown();
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

        blackjackCommand = null;
        tableListener = null;
        tableMenu = null;
        blackjackService = null;
        seatService = null;
        tableService = null;
        tableConfig = null;
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

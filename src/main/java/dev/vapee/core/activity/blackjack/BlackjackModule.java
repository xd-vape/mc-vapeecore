package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.blackjack.command.BlackjackCommand;
import dev.vapee.core.activity.blackjack.interaction.BlackjackTableListener;
import dev.vapee.core.activity.blackjack.presentation.BlackjackInventoryService;
import dev.vapee.core.activity.blackjack.presentation.BlackjackWorldViewService;
import dev.vapee.core.activity.blackjack.table.BlackjackSeatService;
import dev.vapee.core.activity.blackjack.table.BlackjackTableConfig;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.seat.SeatModule;
import dev.vapee.core.seat.SeatPositionResolver;
import dev.vapee.core.worlddisplay.WorldDisplayModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public final class BlackjackModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ActivityModule activityModule;
    private final SeatModule seatModule;
    private final WorldDisplayModule worldDisplayModule;
    private final LobbyModule lobbyModule;
    private final MessageService messageService;
    private final CommandHelpRenderer commandHelpRenderer;

    private ActivityService activityService;
    private BlackjackTableConfig tableConfig;
    private BlackjackTableService tableService;
    private BlackjackSeatService seatService;
    private BlackjackService blackjackService;
    private BlackjackInventoryService inventoryService;
    private BlackjackWorldViewService worldViewService;
    private BlackjackTableListener tableListener;
    private PluginCommand blackjackCommand;

    public BlackjackModule(
            JavaPlugin plugin,
            ActivityModule activityModule,
            SeatModule seatModule,
            WorldDisplayModule worldDisplayModule,
            LobbyModule lobbyModule,
            MessageService messageService,
            CommandHelpRenderer commandHelpRenderer
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
        this.seatModule = Objects.requireNonNull(seatModule, "seatModule");
        this.worldDisplayModule = Objects.requireNonNull(worldDisplayModule, "worldDisplayModule");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.commandHelpRenderer = Objects.requireNonNull(commandHelpRenderer, "commandHelpRenderer");
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
        BlackjackSeatService newSeatService = new BlackjackSeatService(
                plugin,
                newActivityService,
                seatModule.getSeatService()
        );
        SeatPositionResolver seatPositionResolver = new SeatPositionResolver();
        BlackjackTableService newTableService = new BlackjackTableService(
                newActivityService,
                newTableConfig,
                newSeatService,
                worldName -> plugin.getServer().getWorld(worldName) != null,
                seat -> seat.blockPosition().map(position -> {
                    var world = plugin.getServer().getWorld(position.worldName());
                    return world != null && seatPositionResolver.resolve(
                            world.getBlockAt(position.x(), position.y(), position.z()),
                            seat.position().yaw()
                    ).isPresent();
                }).orElse(true),
                plugin.getLogger()
        );
        BlackjackInventoryService newInventoryService = new BlackjackInventoryService(
                plugin,
                lobbyModule.getLobbyPlayerStateService()
        );
        BlackjackService newBlackjackService = new BlackjackService(
                plugin,
                newActivityService,
                newTableService,
                newSeatService,
                messageService,
                playerId -> lobbyModule.getLobbyPlayerStateService().isBuildMode(playerId),
                newInventoryService
        );
        BlackjackActivityType activityType = new BlackjackActivityType(newBlackjackService);
        ActivityResult typeResult = newActivityService.registerActivityType(activityType);
        if (typeResult != ActivityResult.SUCCESS) {
            throw new IllegalStateException("Could not register blackjack activity type: " + typeResult);
        }

        BlackjackWorldViewService newWorldViewService = null;
        BlackjackTableListener newTableListener = null;
        PluginCommand newBlackjackCommand = null;

        try {
            newWorldViewService = new BlackjackWorldViewService(
                    plugin,
                    worldDisplayModule.getWorldDisplayService(),
                    newTableService,
                    newBlackjackService
            );
            BlackjackWorldViewService activeWorldView = newWorldViewService;
            newTableService.setLifecycleCallbacks(activeWorldView);
            newBlackjackService.setTableRefresher(session -> {
                newInventoryService.refreshSession(session);
                activeWorldView.refresh(session);
            });
            newTableService.activateConfiguredTables();
            newTableListener = new BlackjackTableListener(
                    newBlackjackService,
                    newTableService,
                    newInventoryService,
                    seatPositionResolver
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
                    commandHelpRenderer,
                    seatPositionResolver
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
                newBlackjackService.shutdown();
                newTableService.shutdown();
                if (newWorldViewService != null) newWorldViewService.shutdown();
                newInventoryService.shutdown();
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
        inventoryService = newInventoryService;
        worldViewService = newWorldViewService;
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
        cleanup("remove blackjack world displays", () -> {
            if (worldViewService != null) worldViewService.shutdown();
        });
        cleanup("remove blackjack inventory items", () -> {
            if (inventoryService != null) inventoryService.shutdown();
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
        worldViewService = null;
        inventoryService = null;
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

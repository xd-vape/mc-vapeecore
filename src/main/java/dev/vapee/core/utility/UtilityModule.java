package dev.vapee.core.utility;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.utility.command.BuildCommand;
import dev.vapee.core.utility.command.FeedCommand;
import dev.vapee.core.utility.command.FlyCommand;
import dev.vapee.core.utility.command.GameModeCommand;
import dev.vapee.core.utility.command.HealCommand;
import dev.vapee.core.utility.command.SpeedCommand;
import dev.vapee.core.utility.command.TeleportCommand;
import dev.vapee.core.utility.command.TeleportHereCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UtilityModule implements CoreModule {

    private final JavaPlugin plugin;
    private final LobbyModule lobbyModule;
    private final ActivityModule activityModule;
    private final MessageService messageService;

    private final List<PluginCommand> registeredCommands = new ArrayList<>();
    private UtilityService utilityService;
    private UtilityListener utilityListener;

    public UtilityModule(
            JavaPlugin plugin,
            LobbyModule lobbyModule,
            ActivityModule activityModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Utility";
    }

    @Override
    public void enable() {
        LobbyService lobbyService = lobbyModule.getLobbyService();
        LobbyPlayerStateService lobbyPlayerStateService = lobbyModule.getLobbyPlayerStateService();
        ActivityService activityService = activityModule.getActivityService();
        UtilityService newUtilityService = new UtilityService(plugin);
        UtilityListener newUtilityListener = new UtilityListener(plugin, newUtilityService);

        registerCommand("build", new BuildCommand(
                plugin,
                lobbyService,
                lobbyPlayerStateService,
                activityService,
                newUtilityService,
                messageService
        ));
        registerCommand("fly", new FlyCommand(
                plugin, newUtilityService, lobbyPlayerStateService, activityService, messageService));
        registerCommand("speed", new SpeedCommand(
                plugin, newUtilityService, activityService, messageService));
        registerCommand("gamemode", new GameModeCommand(
                plugin, newUtilityService, lobbyService, lobbyPlayerStateService, activityService, messageService));
        registerCommand("tp", new TeleportCommand(
                plugin, newUtilityService, activityService, messageService));
        registerCommand("tphere", new TeleportHereCommand(
                plugin, newUtilityService, activityService, messageService));
        registerCommand("heal", new HealCommand(
                plugin, newUtilityService, activityService, messageService));
        registerCommand("feed", new FeedCommand(
                plugin, newUtilityService, activityService, messageService));

        plugin.getServer().getPluginManager().registerEvents(newUtilityListener, plugin);
        utilityService = newUtilityService;
        utilityListener = newUtilityListener;
        plugin.getLogger().info("Utility module enabled with 8 command(s).");
    }

    @Override
    public void disable() {
        if (utilityListener != null) {
            utilityListener.disable();
            HandlerList.unregisterAll(utilityListener);
        }
        if (utilityService != null) {
            utilityService.cleanup();
        }
        for (PluginCommand command : registeredCommands) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        registeredCommands.clear();
        utilityListener = null;
        utilityService = null;
    }

    public UtilityService getUtilityService() {
        if (utilityService == null) {
            throw new IllegalStateException("Utility module is not enabled");
        }
        return utilityService;
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(
                plugin.getCommand(name),
                "Command '" + name + "' is missing from plugin.yml"
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        registeredCommands.add(command);
    }
}

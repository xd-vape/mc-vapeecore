package dev.vapee.core.utility;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.utility.command.BuildCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class UtilityModule implements CoreModule {

    private final JavaPlugin plugin;
    private final LobbyModule lobbyModule;
    private final ActivityModule activityModule;
    private final MessageService messageService;

    private PluginCommand buildCommand;

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
        PluginCommand newBuildCommand = Objects.requireNonNull(
                plugin.getCommand("build"),
                "Command 'build' is missing from plugin.yml"
        );
        BuildCommand executor = new BuildCommand(
                plugin,
                lobbyService,
                lobbyPlayerStateService,
                activityService,
                messageService
        );
        newBuildCommand.setExecutor(executor);
        newBuildCommand.setTabCompleter(executor);
        buildCommand = newBuildCommand;
        plugin.getLogger().info("Utility module enabled with /build.");
    }

    @Override
    public void disable() {
        if (buildCommand != null) {
            buildCommand.setExecutor(null);
            buildCommand.setTabCompleter(null);
        }
        buildCommand = null;
    }
}

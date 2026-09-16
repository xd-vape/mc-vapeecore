package dev.vapee.core.social;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.social.command.IgnoreCommand;
import dev.vapee.core.social.command.IgnoreListCommand;
import dev.vapee.core.social.command.UnignoreCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SocialModule implements CoreModule {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final MessageService messageService;

    private SocialService socialService;
    private SocialListener socialListener;
    private PluginCommand ignoreCommand;
    private PluginCommand unignoreCommand;
    private PluginCommand ignoreListCommand;

    public SocialModule(JavaPlugin plugin, PlayerModule playerModule, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Social";
    }

    @Override
    public void enable() {
        PlayerService newPlayerService = playerModule.getPlayerService();
        SocialService newSocialService = new SocialService(newPlayerService, plugin.getLogger());
        SocialListener newSocialListener = new SocialListener(newSocialService);
        PluginCommand newIgnoreCommand = requireCommand("ignore");
        PluginCommand newUnignoreCommand = requireCommand("unignore");
        PluginCommand newIgnoreListCommand = requireCommand("ignorelist");

        IgnoreCommand newIgnoreExecutor = new IgnoreCommand(
                plugin.getServer(),
                newSocialService,
                messageService,
                plugin.getLogger()
        );
        UnignoreCommand newUnignoreExecutor = new UnignoreCommand(
                newSocialService,
                messageService,
                plugin.getLogger()
        );
        IgnoreListCommand newIgnoreListExecutor = new IgnoreListCommand(newSocialService, messageService);

        try {
            plugin.getServer().getPluginManager().registerEvents(newSocialListener, plugin);
            newIgnoreCommand.setExecutor(newIgnoreExecutor);
            newIgnoreCommand.setTabCompleter(newIgnoreExecutor);
            newUnignoreCommand.setExecutor(newUnignoreExecutor);
            newUnignoreCommand.setTabCompleter(newUnignoreExecutor);
            newIgnoreListCommand.setExecutor(newIgnoreListExecutor);
            for (CorePlayer loadedPlayer : newPlayerService.getLoadedPlayers()) {
                newSocialService.activatePlayer(loadedPlayer.getUniqueId());
            }
        } catch (RuntimeException exception) {
            clearCommand(newIgnoreCommand);
            clearCommand(newUnignoreCommand);
            clearCommand(newIgnoreListCommand);
            HandlerList.unregisterAll(newSocialListener);
            newSocialService.clearSnapshots();
            throw exception;
        }

        socialService = newSocialService;
        socialListener = newSocialListener;
        ignoreCommand = newIgnoreCommand;
        unignoreCommand = newUnignoreCommand;
        ignoreListCommand = newIgnoreListCommand;
        plugin.getLogger().info("Social module enabled.");
    }

    @Override
    public void disable() {
        if (socialListener != null) {
            HandlerList.unregisterAll(socialListener);
        }
        clearCommand(ignoreCommand);
        clearCommand(unignoreCommand);
        clearCommand(ignoreListCommand);
        if (socialService != null) {
            socialService.clearSnapshots();
        }

        ignoreListCommand = null;
        unignoreCommand = null;
        ignoreCommand = null;
        socialListener = null;
        socialService = null;
    }

    public SocialService getSocialService() {
        return Objects.requireNonNull(socialService, "SocialModule is not enabled");
    }

    private PluginCommand requireCommand(String name) {
        return Objects.requireNonNull(
                plugin.getCommand(name),
                "Command '" + name + "' is missing from plugin.yml"
        );
    }

    private void clearCommand(PluginCommand command) {
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }
}

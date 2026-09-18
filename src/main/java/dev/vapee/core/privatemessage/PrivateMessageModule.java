package dev.vapee.core.privatemessage;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.privatemessage.command.MessageCommand;
import dev.vapee.core.privatemessage.command.ReplyCommand;
import dev.vapee.core.privatemessage.config.PrivateMessageConfig;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PrivateMessageModule implements CoreModule, ReloadParticipant {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final SocialModule socialModule;
    private final MessageService messageService;

    private PrivateMessageConfig privateMessageConfig;
    private PrivateMessageService privateMessageService;
    private PrivateMessageListener privateMessageListener;
    private PluginCommand messageCommand;
    private PluginCommand replyCommand;

    public PrivateMessageModule(
            JavaPlugin plugin,
            PlayerModule playerModule,
            SocialModule socialModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.socialModule = Objects.requireNonNull(socialModule, "socialModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "PrivateMessage";
    }

    @Override
    public void enable() {
        PlayerSettingsService playerSettingsService = playerModule.getPlayerSettingsService();
        SocialService socialService = socialModule.getSocialService();
        PrivateMessageConfig newConfig = new PrivateMessageConfig(plugin);
        newConfig.initialize();
        PrivateMessageService newService = new PrivateMessageService(
                plugin.getServer(),
                playerSettingsService,
                socialService,
                messageService,
                plugin.getLogger(),
                newConfig
        );
        PrivateMessageListener newListener = new PrivateMessageListener(newService);
        PluginCommand newMessageCommand = requireCommand("msg");
        PluginCommand newReplyCommand = requireCommand("reply");

        try {
            plugin.getServer().getPluginManager().registerEvents(newListener, plugin);
            MessageCommand newMessageExecutor = new MessageCommand(
                    plugin.getServer(),
                    newService,
                    messageService,
                    plugin.getLogger()
            );
            newMessageCommand.setExecutor(newMessageExecutor);
            newMessageCommand.setTabCompleter(newMessageExecutor);
            ReplyCommand newReplyExecutor = new ReplyCommand(
                    newService,
                    messageService,
                    plugin.getLogger()
            );
            newReplyCommand.setExecutor(newReplyExecutor);
            newReplyCommand.setTabCompleter(newReplyExecutor);
        } catch (RuntimeException exception) {
            newMessageCommand.setExecutor(null);
            newMessageCommand.setTabCompleter(null);
            newReplyCommand.setExecutor(null);
            newReplyCommand.setTabCompleter(null);
            HandlerList.unregisterAll(newListener);
            newService.clearConversations();
            throw exception;
        }

        privateMessageConfig = newConfig;
        privateMessageService = newService;
        privateMessageListener = newListener;
        messageCommand = newMessageCommand;
        replyCommand = newReplyCommand;
        plugin.getLogger().info("Private-message module enabled.");
    }

    @Override
    public void disable() {
        if (privateMessageListener != null) {
            HandlerList.unregisterAll(privateMessageListener);
        }
        if (messageCommand != null) {
            messageCommand.setExecutor(null);
            messageCommand.setTabCompleter(null);
        }
        if (replyCommand != null) {
            replyCommand.setExecutor(null);
            replyCommand.setTabCompleter(null);
        }
        if (privateMessageService != null) {
            privateMessageService.clearConversations();
        }

        replyCommand = null;
        messageCommand = null;
        privateMessageListener = null;
        privateMessageService = null;
        privateMessageConfig = null;
    }

    public PrivateMessageService getPrivateMessageService() {
        return Objects.requireNonNull(privateMessageService, "PrivateMessageModule is not enabled");
    }

    @Override
    public String getReloadName() {
        return "private-messages.yml";
    }

    @Override
    public ReloadPlan prepareReload() {
        PrivateMessageConfig activeConfig = Objects.requireNonNull(
                privateMessageConfig,
                "PrivateMessageModule is not enabled"
        );
        PrivateMessageService activeService = Objects.requireNonNull(
                privateMessageService,
                "PrivateMessageModule is not enabled"
        );
        PrivateMessageConfig.State previousConfigState = activeConfig.getState();
        PrivateMessageConfig.State preparedConfigState = activeConfig.prepareReloadState();
        PrivateMessageService.RuntimeState previousRuntimeState = activeService.getState();
        PrivateMessageService.RuntimeState preparedRuntimeState = activeService.prepareState(preparedConfigState);

        return ReloadPlan.of(
                () -> {
                    activeConfig.applyState(preparedConfigState);
                    activeService.applyState(preparedRuntimeState);
                },
                () -> {
                    activeConfig.applyState(previousConfigState);
                    activeService.applyState(previousRuntimeState);
                }
        );
    }

    private PluginCommand requireCommand(String name) {
        return Objects.requireNonNull(
                plugin.getCommand(name),
                "Command '" + name + "' is missing from plugin.yml"
        );
    }
}

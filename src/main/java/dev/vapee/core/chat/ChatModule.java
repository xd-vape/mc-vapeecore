package dev.vapee.core.chat;

import dev.vapee.core.chat.config.ChatConfig;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ChatModule implements CoreModule, ReloadParticipant {

    private final JavaPlugin plugin;
    private final PermissionModule permissionModule;
    private final SocialModule socialModule;
    private final MessageService messageService;

    private ChatConfig chatConfig;
    private ChatService chatService;
    private ChatListener chatListener;

    public ChatModule(
            JavaPlugin plugin,
            PermissionModule permissionModule,
            SocialModule socialModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissionModule = Objects.requireNonNull(permissionModule, "permissionModule");
        this.socialModule = Objects.requireNonNull(socialModule, "socialModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Chat";
    }

    @Override
    public void enable() {
        LuckPermsService luckPermsService = permissionModule.getLuckPermsService();
        SocialService socialService = socialModule.getSocialService();
        ChatConfig newChatConfig = new ChatConfig(plugin);
        newChatConfig.initialize();
        ChatService newChatService = new ChatService(
                luckPermsService,
                newChatConfig,
                messageService,
                plugin.getLogger()
        );
        ChatListener newChatListener = new ChatListener(newChatService, socialService);

        try {
            plugin.getServer().getPluginManager().registerEvents(newChatListener, plugin);
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newChatListener);
            throw exception;
        }

        chatConfig = newChatConfig;
        chatService = newChatService;
        chatListener = newChatListener;
        plugin.getLogger().info("Chat module enabled.");
    }

    @Override
    public void disable() {
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
        }

        chatListener = null;
        chatService = null;
        chatConfig = null;
    }

    public ChatService getChatService() {
        return Objects.requireNonNull(chatService, "ChatModule is not enabled");
    }

    @Override
    public String getReloadName() {
        return "chat.yml";
    }

    @Override
    public ReloadPlan prepareReload() {
        ChatConfig activeConfig = Objects.requireNonNull(chatConfig, "ChatModule is not enabled");
        ChatService activeService = Objects.requireNonNull(chatService, "ChatModule is not enabled");
        ChatConfig.State previousConfigState = activeConfig.getState();
        ChatConfig.State preparedConfigState = activeConfig.prepareReloadState();
        ChatService.RuntimeState previousRuntimeState = activeService.getState();
        ChatService.RuntimeState preparedRuntimeState = activeService.prepareState(preparedConfigState);

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
}

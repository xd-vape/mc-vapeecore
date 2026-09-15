package dev.vapee.core.chat;

import dev.vapee.core.chat.config.ChatConfig;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

public final class ChatListener implements Listener {

    private final ChatConfig chatConfig;
    private final ChatRenderer chatRenderer;

    public ChatListener(ChatConfig chatConfig, ChatService chatService) {
        this.chatConfig = Objects.requireNonNull(chatConfig, "chatConfig");
        ChatService validatedChatService = Objects.requireNonNull(chatService, "chatService");
        this.chatRenderer = ChatRenderer.viewerUnaware(validatedChatService::render);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!chatConfig.isEnabled()) {
            return;
        }
        event.renderer(chatRenderer);
    }
}

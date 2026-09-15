package dev.vapee.core.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

public final class ChatListener implements Listener {

    private final ChatService chatService;
    private final ChatRenderer chatRenderer;

    public ChatListener(ChatService chatService) {
        this.chatService = Objects.requireNonNull(chatService, "chatService");
        this.chatRenderer = ChatRenderer.viewerUnaware(this.chatService::render);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!chatService.isEnabled()) {
            return;
        }
        event.renderer(chatRenderer);
    }
}

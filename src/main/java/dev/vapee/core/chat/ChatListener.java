package dev.vapee.core.chat;

import dev.vapee.core.social.SocialService;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

public final class ChatListener implements Listener {

    private final ChatService chatService;
    private final SocialService socialService;
    private final ChatRenderer chatRenderer;

    public ChatListener(ChatService chatService, SocialService socialService) {
        this.chatService = Objects.requireNonNull(chatService, "chatService");
        this.socialService = Objects.requireNonNull(socialService, "socialService");
        this.chatRenderer = ChatRenderer.viewerUnaware(this.chatService::render);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        event.viewers().removeIf(viewer -> viewer instanceof Player player
                && socialService.isIgnoring(player.getUniqueId(), event.getPlayer().getUniqueId())
        );
        if (!chatService.isEnabled()) {
            return;
        }
        event.renderer(chatRenderer);
    }
}

package dev.vapee.core.moderation;

import dev.vapee.core.message.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads only immutable committed facts on chat threads. Feedback is a one-shot main-thread task. */
public final class ModerationMuteChatListener implements Listener {
    private final ModerationMuteProjection projection;
    private final Consumer<Runnable> scheduleFeedback;
    private final Function<UUID, Player> onlinePlayer;
    private final MessageService messages;
    private final Logger logger;
    private volatile boolean active = true;

    ModerationMuteChatListener(ModerationMuteProjection projection, Consumer<Runnable> scheduleFeedback,
                               Function<UUID, Player> onlinePlayer, MessageService messages, Logger logger) {
        this.projection = Objects.requireNonNull(projection);
        this.scheduleFeedback = Objects.requireNonNull(scheduleFeedback);
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer);
        this.messages = Objects.requireNonNull(messages);
        this.logger = Objects.requireNonNull(logger);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!active || event.isCancelled()) return;
        UUID target = event.getPlayer().getUniqueId();
        var mute = projection.getActiveMute(target);
        if (mute.isEmpty()) return;
        ModerationRecord record = mute.get();
        event.setCancelled(true);
        try {
            scheduleFeedback.accept(() -> {
                if (!active) return;
                try {
                    Player player = onlinePlayer.apply(target);
                    if (player != null && player.isOnline()) messages.send(player, ModerationComponents.muteNotice(record));
                } catch (RuntimeException exception) {
                    logger.log(Level.SEVERE, "Mute chat feedback failed [target=" + target + "]", exception);
                }
            });
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Mute chat feedback scheduling failed [target=" + target + "]", exception);
        }
    }

    void deactivate() { active = false; }
}

package dev.vapee.core.moderation;

import dev.vapee.core.message.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads only immutable committed facts on chat threads. Feedback is a one-shot main-thread task. */
public final class ModerationMuteChatListener implements Listener {
    static final long FEEDBACK_INTERVAL_NANOS = java.time.Duration.ofSeconds(10).toNanos();
    private final ModerationMuteProjection projection;
    private final Consumer<Runnable> scheduleFeedback;
    private final Function<UUID, Player> onlinePlayer;
    private final MessageService messages;
    private final Logger logger;
    private final LongSupplier feedbackClock;
    private final ConcurrentMap<UUID, Feedback> feedback = new ConcurrentHashMap<>();
    private record Feedback(Player session, ModerationRecord record, long at) { }
    private volatile boolean active = true;

    ModerationMuteChatListener(ModerationMuteProjection projection, Consumer<Runnable> scheduleFeedback,
                               Function<UUID, Player> onlinePlayer, MessageService messages, Logger logger) {
        this(projection, scheduleFeedback, onlinePlayer, messages, logger, System::nanoTime);
    }

    ModerationMuteChatListener(ModerationMuteProjection projection, Consumer<Runnable> scheduleFeedback,
                               Function<UUID, Player> onlinePlayer, MessageService messages, Logger logger,
                               LongSupplier feedbackClock) {
        this.projection = Objects.requireNonNull(projection);
        this.scheduleFeedback = Objects.requireNonNull(scheduleFeedback);
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer);
        this.messages = Objects.requireNonNull(messages);
        this.logger = Objects.requireNonNull(logger);
        this.feedbackClock = Objects.requireNonNull(feedbackClock);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!active || event.isCancelled()) return;
        reconcileFeedback();
        Player session = event.getPlayer();
        UUID target = session.getUniqueId();
        var mute = projection.getActiveMute(target);
        if (mute.isEmpty()) return;
        ModerationRecord record = mute.get();
        event.setCancelled(true);
        long now = feedbackClock.getAsLong();
        Feedback next = new Feedback(session, record, now);
        Feedback claimed = feedback.compute(target, (id, previous) -> {
            if (!active) return null;
            if (previous != null && previous.session == session && previous.record.equals(record)
                    && now - previous.at < FEEDBACK_INTERVAL_NANOS) return previous;
            return next;
        });
        if (claimed != next) return;
        if (!active || projection.getActiveMute(target).filter(record::equals).isEmpty()) {
            feedback.remove(target, next); return;
        }
        try {
            scheduleFeedback.accept(() -> {
                if (!active || feedback.get(target) != next) return;
                try {
                    Player player = onlinePlayer.apply(target);
                    if (player != session || !player.isOnline()
                            || projection.getActiveMute(target).filter(record::equals).isEmpty()) {
                        feedback.remove(target, next); return;
                    }
                    messages.sendRaw(player, ModerationComponents.muteReminder(record));
                } catch (RuntimeException exception) {
                    feedback.remove(target, next);
                    logger.log(Level.SEVERE, "Mute chat feedback failed [target=" + target + "]", exception);
                }
            });
        } catch (RuntimeException exception) {
            feedback.remove(target, next);
            logger.log(Level.SEVERE, "Mute chat feedback scheduling failed [target=" + target + "]", exception);
        }
    }

    /** Also called after committed publications; expiry is checked lazily without a recurring task. */
    void reconcileFeedback() {
        feedback.forEach((target, previous) -> {
            if (projection.getActiveMute(target).filter(previous.record::equals).isEmpty())
                feedback.remove(target, previous);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player session = event.getPlayer();
        feedback.computeIfPresent(session.getUniqueId(), (id, previous) -> previous.session == session ? null : previous);
    }

    void deactivate() { active = false; feedback.clear(); }
}

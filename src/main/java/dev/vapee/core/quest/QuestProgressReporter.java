package dev.vapee.core.quest;

import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Application boundary: domain progress first, best-effort chat feedback afterwards. */
public final class QuestProgressReporter {
    private final QuestService quests;
    private final BiConsumer<UUID, Component> feedback;
    private final Logger logger;
    private boolean feedbackEnabled = true;

    public QuestProgressReporter(QuestService quests, MessageService messages,
                                 Function<UUID, Player> onlinePlayer, Logger logger) {
        this(quests, (id, message) -> {
            Player player = onlinePlayer.apply(id);
            if (player != null && player.isOnline()) messages.send(player, message);
        }, logger);
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(onlinePlayer, "onlinePlayer");
    }

    QuestProgressReporter(QuestService quests, BiConsumer<UUID, Component> feedback, Logger logger) {
        this.quests = Objects.requireNonNull(quests, "quests");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public QuestProgressResult report(UUID playerId, QuestProgressKey key, long amount) {
        QuestProgressResult result = quests.addProgress(playerId, key, amount);
        if (!feedbackEnabled || result.status() != QuestProgressResult.Status.PROCESSED) return result;
        // QuestService views follow persisted ID order, also when several quests complete together.
        for (QuestView view : quests.getActiveQuests(playerId).orElse(java.util.List.of())) {
            String id = view.definition().id();
            boolean reached = result.reachedQuestIds().contains(id);
            boolean completed = result.completedQuestIds().contains(id);
            if (!completed && !(reached && result.rewardPendingQuestIds().contains(id))) continue;
            Component message = Component.text(completed && !reached
                            ? "Quest reward delivered: " : "Daily quest complete: ", NamedTextColor.GREEN)
                    .append(Component.text(view.definition().name(), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text(completed
                            ? "+" + view.definition().rewardCoins() + " Coins"
                            : "Your reward is pending and will be retried.",
                            completed ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
            try {
                feedback.accept(playerId, message);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Quest feedback failed for " + playerId + " quest " + id
                        + "; committed progress and rewards remain unchanged.", exception);
            }
        }
        return result;
    }

    void silence() {
        feedbackEnabled = false;
    }
}

package dev.vapee.core.quest;

import java.util.Objects;

public record PlayerQuestProgress(
        String questId,
        long progress,
        QuestStatus status,
        QuestDistinctProgress distinct
) {

    public PlayerQuestProgress {
        questId = QuestDefinition.requireValidId(questId);
        if (progress < 0L) {
            throw new IllegalArgumentException("Quest progress must not be negative");
        }
        Objects.requireNonNull(status, "status");
    }

    public PlayerQuestProgress(String questId, long progress, QuestStatus status) {
        this(questId, progress, status, null);
    }

    PlayerQuestProgress with(long newProgress, QuestStatus newStatus) {
        return new PlayerQuestProgress(questId, newProgress, newStatus, distinct);
    }
}

package dev.vapee.core.quest;

import java.util.Objects;

public record PlayerQuestProgress(
        String questId,
        long progress,
        QuestStatus status
) {

    public PlayerQuestProgress {
        questId = QuestDefinition.requireValidId(questId);
        if (progress < 0L) {
            throw new IllegalArgumentException("Quest progress must not be negative");
        }
        Objects.requireNonNull(status, "status");
    }

    PlayerQuestProgress with(long newProgress, QuestStatus newStatus) {
        return new PlayerQuestProgress(questId, newProgress, newStatus);
    }
}

package dev.vapee.core.quest;

import java.util.Objects;

public record QuestView(
        QuestDefinition definition,
        long currentProgress,
        long target,
        QuestStatus status
) {

    public QuestView {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(status, "status");
        if (target != definition.target()) {
            throw new IllegalArgumentException("Quest view target must match its definition");
        }
        if (currentProgress < 0L || currentProgress > target) {
            throw new IllegalArgumentException("Quest view progress must be between zero and target");
        }
    }
}

package dev.vapee.core.quest;

import java.util.List;
import java.util.Objects;

public record QuestProgressResult(
        Status status,
        int updatedQuests,
        List<String> completedQuestIds,
        List<String> rewardPendingQuestIds,
        List<String> reachedQuestIds
) {

    public QuestProgressResult {
        Objects.requireNonNull(status, "status");
        completedQuestIds = List.copyOf(Objects.requireNonNull(completedQuestIds, "completedQuestIds"));
        rewardPendingQuestIds = List.copyOf(Objects.requireNonNull(
                rewardPendingQuestIds,
                "rewardPendingQuestIds"
        ));
        reachedQuestIds = List.copyOf(Objects.requireNonNull(reachedQuestIds, "reachedQuestIds"));
        if (updatedQuests < 0) {
            throw new IllegalArgumentException("updatedQuests must not be negative");
        }
        if (status != Status.PROCESSED
                && (updatedQuests != 0
                || !completedQuestIds.isEmpty()
                || !rewardPendingQuestIds.isEmpty()
                || !reachedQuestIds.isEmpty())) {
            throw new IllegalArgumentException("Only processed results may contain quest changes");
        }
    }

    public QuestProgressResult(Status status, int updatedQuests, List<String> completedQuestIds,
                               List<String> rewardPendingQuestIds) {
        this(status, updatedQuests, completedQuestIds, rewardPendingQuestIds, List.of());
    }

    static QuestProgressResult processed(int updatedQuests, List<String> completedQuestIds,
                                         List<String> rewardPendingQuestIds, List<String> reachedQuestIds) {
        return new QuestProgressResult(Status.PROCESSED, updatedQuests, completedQuestIds,
                rewardPendingQuestIds, reachedQuestIds);
    }

    static QuestProgressResult playerNotLoaded() {
        return unchanged(Status.PLAYER_NOT_LOADED);
    }

    static QuestProgressResult noMatchingQuest() {
        return unchanged(Status.NO_MATCHING_QUEST);
    }

    static QuestProgressResult noChange() {
        return unchanged(Status.NO_CHANGE);
    }

    static QuestProgressResult processed(
            int updatedQuests,
            List<String> completedQuestIds,
            List<String> rewardPendingQuestIds
    ) {
        return new QuestProgressResult(
                Status.PROCESSED,
                updatedQuests,
                completedQuestIds,
                rewardPendingQuestIds
        );
    }

    private static QuestProgressResult unchanged(Status status) {
        return new QuestProgressResult(status, 0, List.of(), List.of());
    }

    public enum Status {
        PROCESSED,
        NO_MATCHING_QUEST,
        NO_CHANGE,
        PLAYER_NOT_LOADED
    }
}

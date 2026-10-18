package dev.vapee.core.quest;

public enum QuestAssignmentResult {
    ASSIGNED,
    ALREADY_ASSIGNED,
    REPLACED,
    CLEARED,
    NO_CHANGE,
    PLAYER_NOT_LOADED,
    DEFINITION_NOT_FOUND,
    PENDING_REWARD,
    PERSISTENCE_FAILED,
    DUPLICATE_QUEST_ID
}

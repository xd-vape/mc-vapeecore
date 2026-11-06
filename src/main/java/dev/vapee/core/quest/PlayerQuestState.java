package dev.vapee.core.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class PlayerQuestState {

    private final TreeMap<String, PlayerQuestProgress> activeQuests;
    // Runtime measurement identity only; never serialized as player progress.
    private Object assignmentToken = new Object();

    private PlayerQuestState(TreeMap<String, PlayerQuestProgress> activeQuests) {
        this.activeQuests = activeQuests;
    }

    public static PlayerQuestState empty() {
        return new PlayerQuestState(new TreeMap<>());
    }

    public static PlayerQuestState of(Collection<PlayerQuestProgress> progressEntries) {
        Objects.requireNonNull(progressEntries, "progressEntries");
        TreeMap<String, PlayerQuestProgress> activeQuests = new TreeMap<>();
        for (PlayerQuestProgress progress : progressEntries) {
            PlayerQuestProgress validatedProgress = Objects.requireNonNull(progress, "progress");
            PlayerQuestProgress duplicate = activeQuests.putIfAbsent(
                    validatedProgress.questId(),
                    validatedProgress
            );
            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "Duplicate player quest state: " + validatedProgress.questId()
                );
            }
        }
        return new PlayerQuestState(activeQuests);
    }

    public Map<String, PlayerQuestProgress> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(activeQuests));
    }

    public boolean isEmpty() {
        return activeQuests.isEmpty();
    }

    Optional<PlayerQuestProgress> find(String questId) {
        return Optional.ofNullable(activeQuests.get(QuestDefinition.requireValidId(questId)));
    }

    List<PlayerQuestProgress> progressEntries() {
        return List.copyOf(activeQuests.values());
    }

    Object assignmentToken() { return assignmentToken; }

    boolean assign(String questId) {
        String validatedQuestId = QuestDefinition.requireValidId(questId);
        if (activeQuests.containsKey(validatedQuestId)) {
            return false;
        }
        activeQuests.put(
                validatedQuestId,
                new PlayerQuestProgress(validatedQuestId, 0L, QuestStatus.ACTIVE)
        );
        assignmentToken = new Object();
        return true;
    }

    void replaceAssignments(Collection<String> questIds) {
        Objects.requireNonNull(questIds, "questIds");
        TreeMap<String, PlayerQuestProgress> replacements = new TreeMap<>();
        for (String questId : questIds) {
            String validatedQuestId = QuestDefinition.requireValidId(questId);
            PlayerQuestProgress duplicate = replacements.putIfAbsent(
                    validatedQuestId,
                    new PlayerQuestProgress(validatedQuestId, 0L, QuestStatus.ACTIVE)
            );
            if (duplicate != null) {
                throw new IllegalArgumentException("Duplicate quest assignment: " + validatedQuestId);
            }
        }
        activeQuests.clear();
        activeQuests.putAll(replacements);
        assignmentToken = new Object();
    }

    /** Validated snapshot replacement, used by explicit administrative reconciliation and rollback. */
    void restore(Collection<PlayerQuestProgress> entries) {
        PlayerQuestState validated = of(entries);
        activeQuests.clear();
        activeQuests.putAll(validated.activeQuests);
        assignmentToken = new Object();
    }

    boolean clear() {
        if (activeQuests.isEmpty()) {
            return false;
        }
        activeQuests.clear();
        assignmentToken = new Object();
        return true;
    }

    void update(PlayerQuestProgress progress) {
        PlayerQuestProgress validatedProgress = Objects.requireNonNull(progress, "progress");
        if (!activeQuests.containsKey(validatedProgress.questId())) {
            throw new IllegalArgumentException(
                    "Quest is not assigned: " + validatedProgress.questId()
            );
        }
        activeQuests.put(validatedProgress.questId(), validatedProgress);
    }
}

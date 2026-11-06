package dev.vapee.core.quest.daily;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable configuration metadata; definitions, assignments and rewards remain in their existing owners. */
public record DailyQuestSelection(
        Map<String, Quest> quests, Long maxPerCategory, Long maxPerGroup) {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Quest LEGACY = new Quest(true, null, null);

    public DailyQuestSelection {
        quests = Map.copyOf(Objects.requireNonNull(quests, "quests"));
        quests.keySet().forEach(DailyQuestSelection::requireIdentifier);
        if (maxPerCategory != null && maxPerCategory <= 0
                || maxPerGroup != null && maxPerGroup <= 0)
            throw new IllegalArgumentException("Selection limits must be positive whole numbers");
    }

    public static DailyQuestSelection unrestricted() {
        return new DailyQuestSelection(Map.of(), null, null);
    }

    public Quest quest(String id) {
        return quests.getOrDefault(id, LEGACY);
    }

    public boolean constrained() {
        return maxPerCategory != null
                || maxPerGroup != null
                || quests.values().stream().anyMatch(q -> !q.enabled());
    }

    static String requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches())
            throw new IllegalArgumentException(
                    "expected a technical identifier matching " + IDENTIFIER.pattern());
        return value;
    }

    /** Null category/group means absent, and therefore does not share a synthetic bucket. */
    public record Quest(boolean enabled, String category, String group) {
        public Quest {
            if (category != null) requireIdentifier(category);
            if (group != null) requireIdentifier(group);
        }
    }
}

package dev.vapee.core.quest;

import java.util.Objects;
import java.util.regex.Pattern;

public record QuestDefinition(
        String id,
        String name,
        String description,
        QuestProgressKey progressKey,
        long target,
        long rewardCoins
) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public QuestDefinition {
        id = requireValidId(id);
        name = requireVisibleText(name, "name");
        description = requireVisibleText(description, "description");
        Objects.requireNonNull(progressKey, "progressKey");
        if (target <= 0L) {
            throw new IllegalArgumentException("Quest target must be positive");
        }
        if (rewardCoins <= 0L) {
            throw new IllegalArgumentException("Quest reward coins must be positive");
        }
    }

    static String requireValidId(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Quest IDs must match " + ID_PATTERN.pattern());
        }
        return id;
    }

    private static String requireVisibleText(String value, String field) {
        String validatedValue = Objects.requireNonNull(value, field);
        if (validatedValue.isBlank()) {
            throw new IllegalArgumentException("Quest " + field + " must not be blank");
        }
        return validatedValue;
    }
}

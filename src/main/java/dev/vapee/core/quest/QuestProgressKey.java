package dev.vapee.core.quest;

import java.util.regex.Pattern;

public record QuestProgressKey(String value) {

    private static final Pattern VALID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9:._-]{0,127}");

    public QuestProgressKey {
        if (value == null || !VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Quest progress keys must match " + VALID_PATTERN.pattern()
            );
        }
    }

    public static QuestProgressKey of(String value) {
        return new QuestProgressKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

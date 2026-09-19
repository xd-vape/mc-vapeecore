package dev.vapee.core.activity.blackjack.presentation;

import java.util.Locale;
import java.util.Optional;

public enum BlackjackAction {
    DEAL,
    HIT,
    STAND,
    DOUBLE,
    LEAVE;

    public String persistentId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BlackjackAction> fromPersistentId(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

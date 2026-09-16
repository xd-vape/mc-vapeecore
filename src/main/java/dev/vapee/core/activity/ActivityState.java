package dev.vapee.core.activity;

public enum ActivityState {
    AVAILABLE,
    ACTIVE,
    RESETTING,
    CLOSED;

    boolean canTransitionTo(ActivityState target) {
        if (target == null) {
            return false;
        }

        return switch (this) {
            case AVAILABLE -> target == ACTIVE || target == CLOSED;
            case ACTIVE -> target == RESETTING || target == CLOSED;
            case RESETTING -> target == AVAILABLE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}

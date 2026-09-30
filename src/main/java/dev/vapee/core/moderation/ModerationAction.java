package dev.vapee.core.moderation;

public enum ModerationAction {
    WARNING, MUTE, BAN, KICK;

    public boolean supportsActiveState() {
        return this == MUTE || this == BAN;
    }
}

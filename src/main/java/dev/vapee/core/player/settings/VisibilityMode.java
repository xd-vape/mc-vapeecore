package dev.vapee.core.player.settings;

/** Quick lobby policy; advanced filter preferences are independent of this choice. */
public enum VisibilityMode {
    ALL, FILTERED, HIDDEN;

    public VisibilityMode next() {
        return switch (this) { case ALL -> FILTERED; case FILTERED -> HIDDEN; case HIDDEN -> ALL; };
    }
}

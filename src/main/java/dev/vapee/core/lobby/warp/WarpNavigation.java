package dev.vapee.core.lobby.warp;

/** Presentation policy for the compass navigator, independent of warp existence. */
public record WarpNavigation(boolean visible, int order) {

    public static final WarpNavigation DEFAULT = new WarpNavigation(true, 0);

    public WarpNavigation {
        if (order < 0) {
            throw new IllegalArgumentException("navigator order must not be negative");
        }
    }
}

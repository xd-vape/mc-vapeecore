package dev.vapee.core.player.settings;

import java.util.Objects;

/** Durable choices. Runtime effects and ownership stay in the gadget module. */
public record GadgetPreferences(Size size, boolean glow, Trail trail) {
    public static final GadgetPreferences DEFAULT = new GadgetPreferences(Size.NORMAL, false, Trail.NONE);

    public enum Size { TINY, SMALL, NORMAL, TALL, GIANT, HUGE, MASSIVE, COLOSSAL }
    public enum Trail { HEARTS, FLAME, CLOUD, END_ROD, HAPPY_VILLAGER, NONE }

    public GadgetPreferences {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(trail, "trail");
    }
}

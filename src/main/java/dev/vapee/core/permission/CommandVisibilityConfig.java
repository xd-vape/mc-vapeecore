package dev.vapee.core.permission;

/** Optional presentation settings; never an execution or permission policy. */
public record CommandVisibilityConfig(boolean enabled, boolean hideProfile) {
    public static CommandVisibilityConfig defaults() {
        return new CommandVisibilityConfig(true, true);
    }
}

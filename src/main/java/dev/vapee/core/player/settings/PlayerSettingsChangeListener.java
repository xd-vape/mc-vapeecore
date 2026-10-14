package dev.vapee.core.player.settings;

/** Main-thread notification after successful persistence; observer failures cannot undo the commit. */
@FunctionalInterface
public interface PlayerSettingsChangeListener {
    void onSettingsCommitted(java.util.UUID owner);
}

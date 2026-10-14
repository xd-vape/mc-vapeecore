package dev.vapee.core.lobby.warp;

/** Main-thread notification after successful persistence; observer failures cannot undo the commit. */
@FunctionalInterface
public interface WarpChangeListener {
    void onWarpsChanged();
}

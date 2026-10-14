package dev.vapee.core.clan;

/** Main-thread notification after successful persistence; observer failures cannot undo the commit. */
@FunctionalInterface
public interface ClanChangeListener {
    void onClansChanged(java.util.Set<java.util.UUID> affected);
}

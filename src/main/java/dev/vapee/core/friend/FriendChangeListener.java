package dev.vapee.core.friend;

/** Main-thread notification after successful persistence; observer failures cannot undo the commit. */
@FunctionalInterface
public interface FriendChangeListener {
    void onFriendsChanged(java.util.Set<java.util.UUID> affected);
}

package dev.vapee.core.player.settings;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Main-thread-owned player preferences; exposed UUID sets are immutable snapshots. */
public final class PlayerVisibilitySettings {
    private boolean allPlayersVisible;
    private boolean showFriends;
    private boolean showStaff;
    private boolean showAddedUsers;
    private boolean showGameParticipants;
    private final Set<UUID> addedPlayers = new HashSet<>();

    private PlayerVisibilitySettings(boolean allPlayersVisible) {
        this.allPlayersVisible = allPlayersVisible;
    }

    public static PlayerVisibilitySettings defaults() { return new PlayerVisibilitySettings(true); }

    public boolean isAllPlayersVisible() { return allPlayersVisible; }
    public void setAllPlayersVisible(boolean value) { allPlayersVisible = value; }
    public boolean isShowFriends() { return showFriends; }
    public void setShowFriends(boolean value) { showFriends = value; }
    public boolean isShowStaff() { return showStaff; }
    public void setShowStaff(boolean value) { showStaff = value; }
    public boolean isShowAddedUsers() { return showAddedUsers; }
    public void setShowAddedUsers(boolean value) { showAddedUsers = value; }
    public boolean isShowGameParticipants() { return showGameParticipants; }
    public void setShowGameParticipants(boolean value) { showGameParticipants = value; }
    public Set<UUID> getAddedPlayers() { return Set.copyOf(addedPlayers); }
    public boolean includesAddedPlayer(UUID target) { return addedPlayers.contains(Objects.requireNonNull(target, "target")); }

    public AddedVisiblePlayerResult addPlayer(UUID owner, UUID target) {
        UUID checkedOwner = Objects.requireNonNull(owner, "owner");
        UUID checkedTarget = Objects.requireNonNull(target, "target");
        if (checkedOwner.equals(checkedTarget)) return AddedVisiblePlayerResult.CANNOT_ADD_SELF;
        return addedPlayers.add(checkedTarget) ? AddedVisiblePlayerResult.SUCCESS : AddedVisiblePlayerResult.ALREADY_ADDED;
    }

    public AddedVisiblePlayerResult removePlayer(UUID target) {
        return addedPlayers.remove(Objects.requireNonNull(target, "target"))
                ? AddedVisiblePlayerResult.SUCCESS : AddedVisiblePlayerResult.NOT_ADDED;
    }
}

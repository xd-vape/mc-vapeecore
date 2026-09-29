package dev.vapee.core.player.settings;

import java.util.Set;
import java.util.UUID;

public final class PlayerVisibilitySettingsHarness {
    private static int checks;

    public static void main(String[] args) {
        PlayerVisibilitySettings settings = PlayerVisibilitySettings.defaults();
        check(settings.isAllPlayersVisible(), "all players are visible by default");
        check(!settings.isShowFriends() && !settings.isShowStaff()
                && !settings.isShowAddedUsers() && !settings.isShowGameParticipants(),
                "all filters default off");
        check(settings.getAddedPlayers().isEmpty(), "added players default empty");

        settings.setAllPlayersVisible(false);
        settings.setShowFriends(true);
        settings.setShowStaff(true);
        settings.setShowAddedUsers(true);
        settings.setShowGameParticipants(true);
        check(!settings.isAllPlayersVisible() && settings.isShowFriends() && settings.isShowStaff()
                && settings.isShowAddedUsers() && settings.isShowGameParticipants(),
                "all boolean preferences mutate independently");

        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        check(settings.addPlayer(owner, owner) == AddedVisiblePlayerResult.CANNOT_ADD_SELF,
                "self cannot be added");
        check(settings.addPlayer(owner, target) == AddedVisiblePlayerResult.SUCCESS
                && settings.includesAddedPlayer(target), "target can be added");
        check(settings.addPlayer(owner, target) == AddedVisiblePlayerResult.ALREADY_ADDED,
                "duplicate add is explicit");
        Set<UUID> snapshot = settings.getAddedPlayers();
        check(snapshot.equals(Set.of(target)), "read access returns expected snapshot");
        boolean immutable = false;
        try {
            snapshot.add(UUID.randomUUID());
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "added-player snapshot is immutable");
        check(settings.removePlayer(target) == AddedVisiblePlayerResult.SUCCESS
                && !settings.includesAddedPlayer(target), "target can be removed");
        check(settings.removePlayer(target) == AddedVisiblePlayerResult.NOT_ADDED,
                "missing remove is explicit");
        System.out.println("PlayerVisibilitySettingsHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

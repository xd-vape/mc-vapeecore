package dev.vapee.core.player.settings;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class PlayerSettingsService {

    private final PlayerService playerService;

    public PlayerSettingsService(PlayerService playerService) {
        this.playerService = Objects.requireNonNull(playerService, "playerService");
    }

    public Optional<PlayerSettings> getSettings(UUID uniqueId) {
        return playerService.getPlayer(uniqueId).map(CorePlayer::getSettings);
    }

    public Optional<Boolean> isScoreboardEnabled(UUID uniqueId) {
        return getSettings(uniqueId).map(PlayerSettings::isScoreboardEnabled);
    }

    public Optional<Boolean> areSoundsEnabled(UUID uniqueId) {
        return getSettings(uniqueId).map(PlayerSettings::isSoundsEnabled);
    }

    public Optional<Boolean> arePrivateMessagesEnabled(UUID uniqueId) {
        return getSettings(uniqueId).map(PlayerSettings::isPrivateMessagesEnabled);
    }

    public Optional<Boolean> areLobbyPlayersVisible(UUID uniqueId) {
        return getSettings(uniqueId).map(PlayerSettings::isLobbyPlayersVisible);
    }

    public Optional<Boolean> areLobbyFriendsVisible(UUID uniqueId) {
        return getSettings(uniqueId).map(settings -> settings.getVisibility().isShowFriends());
    }

    public Optional<Boolean> areLobbyStaffVisible(UUID uniqueId) {
        return getSettings(uniqueId).map(settings -> settings.getVisibility().isShowStaff());
    }

    public Optional<Boolean> areLobbyAddedUsersVisible(UUID uniqueId) {
        return getSettings(uniqueId).map(settings -> settings.getVisibility().isShowAddedUsers());
    }

    public Optional<Boolean> areLobbyGameParticipantsVisible(UUID uniqueId) {
        return getSettings(uniqueId).map(settings -> settings.getVisibility().isShowGameParticipants());
    }

    public Optional<Set<UUID>> getLobbyAddedVisiblePlayers(UUID uniqueId) {
        return getSettings(uniqueId).map(settings -> settings.getVisibility().getAddedPlayers());
    }

    public Optional<Boolean> isLobbyAddedVisiblePlayer(UUID owner, UUID target) {
        UUID checkedTarget = Objects.requireNonNull(target, "target");
        return getSettings(owner).map(settings -> settings.getVisibility().includesAddedPlayer(checkedTarget));
    }

    public Optional<Boolean> areFriendRequestsEnabled(UUID uniqueId) {
        return getSettings(uniqueId).map(PlayerSettings::isFriendRequestsEnabled);
    }

    public Optional<Boolean> areKnownFriendRequestsEnabled(UUID uniqueId) {
        return playerService.findKnownPlayer(Objects.requireNonNull(uniqueId, "uniqueId"))
                .map(player -> player.getSettings().isFriendRequestsEnabled());
    }

    public boolean setScoreboardEnabled(UUID uniqueId, boolean enabled) {
        return updateSettings(
                uniqueId,
                enabled,
                PlayerSettings::isScoreboardEnabled,
                PlayerSettings::setScoreboardEnabled
        );
    }

    public boolean setSoundsEnabled(UUID uniqueId, boolean enabled) {
        return updateSettings(
                uniqueId,
                enabled,
                PlayerSettings::isSoundsEnabled,
                PlayerSettings::setSoundsEnabled
        );
    }

    public boolean setPrivateMessagesEnabled(UUID uniqueId, boolean enabled) {
        return updateSettings(
                uniqueId,
                enabled,
                PlayerSettings::isPrivateMessagesEnabled,
                PlayerSettings::setPrivateMessagesEnabled
        );
    }

    public boolean setLobbyPlayersVisible(UUID uniqueId, boolean visible) {
        return updateSettings(
                uniqueId,
                visible,
                PlayerSettings::isLobbyPlayersVisible,
                PlayerSettings::setLobbyPlayersVisible
        );
    }

    public boolean setLobbyFriendsVisible(UUID uniqueId, boolean visible) {
        return updateSettings(uniqueId, visible, settings -> settings.getVisibility().isShowFriends(),
                (settings, value) -> settings.getVisibility().setShowFriends(value));
    }

    public boolean setLobbyStaffVisible(UUID uniqueId, boolean visible) {
        return updateSettings(uniqueId, visible, settings -> settings.getVisibility().isShowStaff(),
                (settings, value) -> settings.getVisibility().setShowStaff(value));
    }

    public boolean setLobbyAddedUsersVisible(UUID uniqueId, boolean visible) {
        return updateSettings(uniqueId, visible, settings -> settings.getVisibility().isShowAddedUsers(),
                (settings, value) -> settings.getVisibility().setShowAddedUsers(value));
    }

    public boolean setLobbyGameParticipantsVisible(UUID uniqueId, boolean visible) {
        return updateSettings(uniqueId, visible, settings -> settings.getVisibility().isShowGameParticipants(),
                (settings, value) -> settings.getVisibility().setShowGameParticipants(value));
    }

    public AddedVisiblePlayerResult addLobbyVisiblePlayer(UUID owner, UUID target) {
        UUID checkedOwner = Objects.requireNonNull(owner, "owner");
        UUID checkedTarget = Objects.requireNonNull(target, "target");
        Optional<PlayerSettings> found = getSettings(checkedOwner);
        if (found.isEmpty()) return AddedVisiblePlayerResult.OWNER_NOT_LOADED;
        PlayerVisibilitySettings visibility = found.get().getVisibility();
        AddedVisiblePlayerResult result = visibility.addPlayer(checkedOwner, checkedTarget);
        if (result != AddedVisiblePlayerResult.SUCCESS) return result;
        try {
            playerService.savePlayer(checkedOwner);
        } catch (RuntimeException exception) {
            visibility.removePlayer(checkedTarget);
            throw exception;
        }
        return result;
    }

    public AddedVisiblePlayerResult removeLobbyVisiblePlayer(UUID owner, UUID target) {
        UUID checkedOwner = Objects.requireNonNull(owner, "owner");
        UUID checkedTarget = Objects.requireNonNull(target, "target");
        Optional<PlayerSettings> found = getSettings(checkedOwner);
        if (found.isEmpty()) return AddedVisiblePlayerResult.OWNER_NOT_LOADED;
        PlayerVisibilitySettings visibility = found.get().getVisibility();
        AddedVisiblePlayerResult result = visibility.removePlayer(checkedTarget);
        if (result != AddedVisiblePlayerResult.SUCCESS) return result;
        try {
            playerService.savePlayer(checkedOwner);
        } catch (RuntimeException exception) {
            visibility.addPlayer(checkedOwner, checkedTarget);
            throw exception;
        }
        return result;
    }

    public boolean setFriendRequestsEnabled(UUID uniqueId, boolean enabled) {
        return updateSettings(uniqueId, enabled,
                PlayerSettings::isFriendRequestsEnabled,
                PlayerSettings::setFriendRequestsEnabled);
    }

    private boolean updateSettings(
            UUID uniqueId,
            boolean enabled,
            Predicate<PlayerSettings> currentValue,
            BiConsumer<PlayerSettings, Boolean> update
    ) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        Optional<PlayerSettings> optionalSettings = getSettings(validatedUniqueId);
        if (optionalSettings.isEmpty()) {
            return false;
        }

        PlayerSettings settings = optionalSettings.get();
        boolean previousValue = currentValue.test(settings);
        if (previousValue == enabled) {
            return true;
        }

        update.accept(settings, enabled);
        try {
            playerService.savePlayer(validatedUniqueId);
        } catch (RuntimeException exception) {
            update.accept(settings, previousValue);
            throw exception;
        }
        return true;
    }
}

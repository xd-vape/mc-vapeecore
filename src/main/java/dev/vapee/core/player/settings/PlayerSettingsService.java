package dev.vapee.core.player.settings;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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

    public boolean setScoreboardEnabled(UUID uniqueId, boolean enabled) {
        return updateSettings(uniqueId, settings -> settings.setScoreboardEnabled(enabled));
    }

    public boolean setSoundsEnabled(UUID uniqueId, boolean enabled) {
        return updateSettings(uniqueId, settings -> settings.setSoundsEnabled(enabled));
    }

    public boolean setPrivateMessagesEnabled(UUID uniqueId, boolean enabled) {
        return updateSettings(uniqueId, settings -> settings.setPrivateMessagesEnabled(enabled));
    }

    private boolean updateSettings(UUID uniqueId, Consumer<PlayerSettings> update) {
        Optional<PlayerSettings> settings = getSettings(uniqueId);
        if (settings.isEmpty()) {
            return false;
        }

        update.accept(settings.get());
        playerService.savePlayer(uniqueId);
        return true;
    }
}

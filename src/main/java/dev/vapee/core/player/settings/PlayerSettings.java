package dev.vapee.core.player.settings;

public final class PlayerSettings {

    private static final boolean DEFAULT_SCOREBOARD_ENABLED = true;
    private static final boolean DEFAULT_SOUNDS_ENABLED = true;
    private static final boolean DEFAULT_PRIVATE_MESSAGES_ENABLED = true;
    private static final boolean DEFAULT_FRIEND_REQUESTS_ENABLED = true;
    private static final boolean DEFAULT_FRIEND_PRESENCE_NOTIFICATIONS_ENABLED = false;

    private boolean scoreboardEnabled;
    private boolean soundsEnabled;
    private boolean privateMessagesEnabled;
    private final PlayerVisibilitySettings visibility;
    private boolean friendRequestsEnabled;
    private boolean friendPresenceNotificationsEnabled;

    private PlayerSettings(
            boolean scoreboardEnabled,
            boolean soundsEnabled,
            boolean privateMessagesEnabled,
            boolean friendRequestsEnabled,
            boolean friendPresenceNotificationsEnabled
    ) {
        this.scoreboardEnabled = scoreboardEnabled;
        this.soundsEnabled = soundsEnabled;
        this.privateMessagesEnabled = privateMessagesEnabled;
        this.visibility = PlayerVisibilitySettings.defaults();
        this.friendRequestsEnabled = friendRequestsEnabled;
        this.friendPresenceNotificationsEnabled = friendPresenceNotificationsEnabled;
    }

    public static PlayerSettings defaults() {
        return new PlayerSettings(
                DEFAULT_SCOREBOARD_ENABLED,
                DEFAULT_SOUNDS_ENABLED,
                DEFAULT_PRIVATE_MESSAGES_ENABLED,
                DEFAULT_FRIEND_REQUESTS_ENABLED,
                DEFAULT_FRIEND_PRESENCE_NOTIFICATIONS_ENABLED
        );
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean scoreboardEnabled) {
        this.scoreboardEnabled = scoreboardEnabled;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public void setSoundsEnabled(boolean soundsEnabled) {
        this.soundsEnabled = soundsEnabled;
    }

    public boolean isPrivateMessagesEnabled() {
        return privateMessagesEnabled;
    }

    public void setPrivateMessagesEnabled(boolean privateMessagesEnabled) {
        this.privateMessagesEnabled = privateMessagesEnabled;
    }

    public boolean isLobbyPlayersVisible() {
        return visibility.isAllPlayersVisible();
    }

    public void setLobbyPlayersVisible(boolean lobbyPlayersVisible) {
        visibility.setAllPlayersVisible(lobbyPlayersVisible);
    }

    public PlayerVisibilitySettings getVisibility() { return visibility; }

    public boolean isFriendRequestsEnabled() {
        return friendRequestsEnabled;
    }

    public void setFriendRequestsEnabled(boolean friendRequestsEnabled) {
        this.friendRequestsEnabled = friendRequestsEnabled;
    }

    public boolean isFriendPresenceNotificationsEnabled() {
        return friendPresenceNotificationsEnabled;
    }

    public void setFriendPresenceNotificationsEnabled(boolean enabled) {
        this.friendPresenceNotificationsEnabled = enabled;
    }
}

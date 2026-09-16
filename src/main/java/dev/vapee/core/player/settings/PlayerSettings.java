package dev.vapee.core.player.settings;

public final class PlayerSettings {

    private static final boolean DEFAULT_SCOREBOARD_ENABLED = true;
    private static final boolean DEFAULT_SOUNDS_ENABLED = true;
    private static final boolean DEFAULT_PRIVATE_MESSAGES_ENABLED = true;
    private static final boolean DEFAULT_LOBBY_PLAYERS_VISIBLE = true;

    private boolean scoreboardEnabled;
    private boolean soundsEnabled;
    private boolean privateMessagesEnabled;
    private boolean lobbyPlayersVisible;

    private PlayerSettings(
            boolean scoreboardEnabled,
            boolean soundsEnabled,
            boolean privateMessagesEnabled,
            boolean lobbyPlayersVisible
    ) {
        this.scoreboardEnabled = scoreboardEnabled;
        this.soundsEnabled = soundsEnabled;
        this.privateMessagesEnabled = privateMessagesEnabled;
        this.lobbyPlayersVisible = lobbyPlayersVisible;
    }

    public static PlayerSettings defaults() {
        return new PlayerSettings(
                DEFAULT_SCOREBOARD_ENABLED,
                DEFAULT_SOUNDS_ENABLED,
                DEFAULT_PRIVATE_MESSAGES_ENABLED,
                DEFAULT_LOBBY_PLAYERS_VISIBLE
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
        return lobbyPlayersVisible;
    }

    public void setLobbyPlayersVisible(boolean lobbyPlayersVisible) {
        this.lobbyPlayersVisible = lobbyPlayersVisible;
    }
}

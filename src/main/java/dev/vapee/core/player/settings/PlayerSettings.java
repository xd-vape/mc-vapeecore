package dev.vapee.core.player.settings;

public final class PlayerSettings {

    private static final boolean DEFAULT_SCOREBOARD_ENABLED = true;
    private static final boolean DEFAULT_SOUNDS_ENABLED = true;
    private static final boolean DEFAULT_PRIVATE_MESSAGES_ENABLED = true;

    private boolean scoreboardEnabled;
    private boolean soundsEnabled;
    private boolean privateMessagesEnabled;

    private PlayerSettings(
            boolean scoreboardEnabled,
            boolean soundsEnabled,
            boolean privateMessagesEnabled
    ) {
        this.scoreboardEnabled = scoreboardEnabled;
        this.soundsEnabled = soundsEnabled;
        this.privateMessagesEnabled = privateMessagesEnabled;
    }

    public static PlayerSettings defaults() {
        return new PlayerSettings(
                DEFAULT_SCOREBOARD_ENABLED,
                DEFAULT_SOUNDS_ENABLED,
                DEFAULT_PRIVATE_MESSAGES_ENABLED
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
}

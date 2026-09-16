package dev.vapee.core.lobby.experience;

import java.util.Arrays;
import java.util.Optional;

public enum LobbyItemType {

    NAVIGATOR("navigator"),
    VISIBILITY("visibility"),
    SETTINGS("settings");

    private final String persistentId;

    LobbyItemType(String persistentId) {
        this.persistentId = persistentId;
    }

    public String getPersistentId() {
        return persistentId;
    }

    public static Optional<LobbyItemType> fromPersistentId(String persistentId) {
        return Arrays.stream(values())
                .filter(type -> type.persistentId.equals(persistentId))
                .findFirst();
    }
}

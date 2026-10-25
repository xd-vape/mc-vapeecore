package dev.vapee.core.player.repository;

import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerVisibilitySettings;
import dev.vapee.core.player.settings.VisibilityMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/** Settings mapping only; the repository owns the schema and complete player commit. */
final class PlayerSettingsYamlCodec {
    private PlayerSettingsYamlCodec() {}

    static void write(PlayerSettings settings, YamlConfiguration configuration) {
        configuration.set("settings.scoreboard", settings.isScoreboardEnabled());
        configuration.set("settings.sounds", settings.isSoundsEnabled());
        configuration.set("settings.private-messages", settings.isPrivateMessagesEnabled());
        configuration.set("settings.friend-requests", settings.isFriendRequestsEnabled());
        configuration.set(
                "settings.friend-presence-notifications",
                settings.isFriendPresenceNotificationsEnabled());
        configuration.set("settings.lobby-players-visible", settings.isLobbyPlayersVisible());
        PlayerVisibilitySettings visibility = settings.getVisibility();
        configuration.set("settings.visibility.mode", visibility.getMode().name());
        configuration.set("settings.visibility.show-friends", visibility.isShowFriends());
        configuration.set("settings.visibility.show-staff", visibility.isShowStaff());
        configuration.set("settings.visibility.show-added-users", visibility.isShowAddedUsers());
        configuration.set(
                "settings.visibility.show-game-participants", visibility.isShowGameParticipants());
        configuration.set(
                "settings.visibility.added-players",
                visibility.getAddedPlayers().stream().map(UUID::toString).sorted().toList());
    }

    static PlayerSettings read(
            UUID uniqueId,
            Path playerFile,
            YamlConfiguration configuration,
            Map<Object, Object> source,
            Logger logger) {
        PlayerSettings settings = PlayerSettings.defaults();
        if (!configuration.contains("settings")) {
            return settings;
        }
        if (!configuration.isConfigurationSection("settings")) {
            logInvalidSetting(logger, uniqueId, playerFile, "settings", "expected a YAML section");
            return settings;
        }

        settings.setScoreboardEnabled(
                readBooleanSetting(
                        logger,
                        uniqueId,
                        playerFile,
                        configuration,
                        "settings.scoreboard",
                        settings.isScoreboardEnabled()));
        settings.setSoundsEnabled(
                readBooleanSetting(
                        logger,
                        uniqueId,
                        playerFile,
                        configuration,
                        "settings.sounds",
                        settings.isSoundsEnabled()));
        settings.setPrivateMessagesEnabled(
                readBooleanSetting(
                        logger,
                        uniqueId,
                        playerFile,
                        configuration,
                        "settings.private-messages",
                        settings.isPrivateMessagesEnabled()));
        settings.setFriendRequestsEnabled(
                readBooleanSetting(
                        logger,
                        uniqueId,
                        playerFile,
                        configuration,
                        "settings.friend-requests",
                        settings.isFriendRequestsEnabled()));
        settings.setFriendPresenceNotificationsEnabled(
                readBooleanSetting(
                        logger,
                        uniqueId,
                        playerFile,
                        configuration,
                        "settings.friend-presence-notifications",
                        settings.isFriendPresenceNotificationsEnabled()));
        settings.setLobbyPlayersVisible(
                readBooleanSetting(
                        logger,
                        uniqueId,
                        playerFile,
                        configuration,
                        "settings.lobby-players-visible",
                        settings.isLobbyPlayersVisible()));
        boolean modePresent =
                source.get("settings") instanceof Map<?, ?> rawSettings
                        && rawSettings.get("visibility") instanceof Map<?, ?> rawVisibility
                        && rawVisibility.containsKey("mode");
        readVisibilitySettings(
                logger, uniqueId, playerFile, configuration, settings.getVisibility(), modePresent);
        return settings;
    }

    private static void readVisibilitySettings(
            Logger logger,
            UUID owner,
            Path file,
            YamlConfiguration configuration,
            PlayerVisibilitySettings visibility,
            boolean modePresent) {
        String prefix = "settings.visibility";
        if (!configuration.contains(prefix)) return;
        if (!configuration.isConfigurationSection(prefix)) {
            logInvalidSetting(logger, owner, file, prefix, "expected a YAML section");
            return;
        }
        if (modePresent) {
            Object raw = configuration.get(prefix + ".mode");
            try {
                if (!(raw instanceof String value))
                    throw new IllegalArgumentException("expected mode string");
                visibility.setMode(VisibilityMode.valueOf(value));
            } catch (IllegalArgumentException exception) {
                logInvalidSetting(
                        logger,
                        owner,
                        file,
                        prefix + ".mode",
                        "expected ALL, FILTERED or HIDDEN; using HIDDEN");
                visibility.setMode(VisibilityMode.HIDDEN);
            }
        }
        visibility.setShowFriends(
                readBooleanSetting(
                        logger, owner, file, configuration, prefix + ".show-friends", false));
        visibility.setShowStaff(
                readBooleanSetting(
                        logger, owner, file, configuration, prefix + ".show-staff", false));
        visibility.setShowAddedUsers(
                readBooleanSetting(
                        logger, owner, file, configuration, prefix + ".show-added-users", false));
        visibility.setShowGameParticipants(
                readBooleanSetting(
                        logger,
                        owner,
                        file,
                        configuration,
                        prefix + ".show-game-participants",
                        false));
        String addedKey = prefix + ".added-players";
        if (!configuration.contains(addedKey)) return;
        if (!configuration.isList(addedKey)) {
            logInvalidSetting(logger, owner, file, addedKey, "expected a list of UUID strings");
            return;
        }
        List<?> entries = configuration.getList(addedKey);
        if (entries == null) return;
        for (Object entry : entries) {
            if (!(entry instanceof String text)) {
                logInvalidSetting(logger, owner, file, addedKey, "expected a UUID string entry");
                continue;
            }
            try {
                UUID target = UUID.fromString(text);
                if (!target.toString().equalsIgnoreCase(text)) {
                    logInvalidSetting(logger, owner, file, addedKey, "non-canonical UUID entry");
                    continue;
                }
                if (target.equals(owner)) {
                    logInvalidSetting(logger, owner, file, addedKey, "own UUID is not allowed");
                    continue;
                }
                visibility.addPlayer(owner, target); // Repeated UUIDs are harmlessly deduplicated.
            } catch (IllegalArgumentException exception) {
                logInvalidSetting(logger, owner, file, addedKey, "invalid UUID entry");
            }
        }
    }

    private static boolean readBooleanSetting(
            Logger logger,
            UUID uniqueId,
            Path playerFile,
            YamlConfiguration configuration,
            String key,
            boolean defaultValue) {
        if (!configuration.contains(key)) {
            return defaultValue;
        }

        Object value = configuration.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        logInvalidSetting(logger, uniqueId, playerFile, key, "expected a boolean");
        return defaultValue;
    }

    private static void logInvalidSetting(
            Logger logger, UUID uniqueId, Path playerFile, String key, String reason) {
        logger.warning(
                "Invalid player setting '"
                        + key
                        + "' for "
                        + uniqueId
                        + " in "
                        + playerFile
                        + ": "
                        + reason
                        + "; using its default value.");
    }
}

package dev.vapee.core.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class LuckPermsService {

    private final LuckPerms luckPerms;

    public LuckPermsService(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
    }

    public Optional<String> getPrimaryGroup(UUID uniqueId) {
        return getLoadedUser(uniqueId).map(User::getPrimaryGroup);
    }

    public Optional<String> getPrefix(UUID uniqueId) {
        return getLoadedUser(uniqueId)
                .map(user -> user.getCachedData().getMetaData().getPrefix());
    }

    public Optional<String> getSuffix(UUID uniqueId) {
        return getLoadedUser(uniqueId)
                .map(user -> user.getCachedData().getMetaData().getSuffix());
    }

    public Optional<String> getMetaValue(UUID uniqueId, String key) {
        String validatedKey = requireNonBlank(key, "Meta keys");

        return getLoadedUser(uniqueId)
                .map(user -> user.getCachedData().getMetaData().getMetaValue(validatedKey));
    }

    public Optional<GroupInformation> getGroupInformation(String groupId, String descriptionMetaKey) {
        String validatedGroupId = requireNonBlank(groupId, "Group IDs");
        String validatedMetaKey = requireNonBlank(descriptionMetaKey, "Meta keys");
        Group group = luckPerms.getGroupManager().getGroup(validatedGroupId);
        if (group == null) {
            return Optional.empty();
        }

        return Optional.of(new GroupInformation(
                group.getName(),
                Optional.ofNullable(group.getDisplayName()).filter(value -> !value.isBlank()),
                Optional.ofNullable(group.getCachedData().getMetaData().getMetaValue(validatedMetaKey))
                        .filter(value -> !value.isBlank()),
                group.getWeight()
        ));
    }

    public Optional<List<String>> getTrackGroups(String trackName) {
        Track track = luckPerms.getTrackManager().getTrack(requireNonBlank(trackName, "Track names"));
        return track == null ? Optional.empty() : Optional.of(List.copyOf(track.getGroups()));
    }

    public boolean isUserLoaded(UUID uniqueId) {
        return luckPerms.getUserManager().getUser(requireUniqueId(uniqueId)) != null;
    }

    private Optional<User> getLoadedUser(UUID uniqueId) {
        return Optional.ofNullable(luckPerms.getUserManager().getUser(requireUniqueId(uniqueId)));
    }

    private UUID requireUniqueId(UUID uniqueId) {
        return Objects.requireNonNull(uniqueId, "uniqueId");
    }

    private String requireNonBlank(String value, String description) {
        String validatedValue = Objects.requireNonNull(value, description.toLowerCase());
        if (validatedValue.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return validatedValue;
    }

    public record GroupInformation(
            String id,
            Optional<String> displayName,
            Optional<String> description,
            OptionalInt weight
    ) {

        public GroupInformation {
            Objects.requireNonNull(id, "id");
            displayName = Objects.requireNonNull(displayName, "displayName");
            description = Objects.requireNonNull(description, "description");
            Objects.requireNonNull(weight, "weight");
        }
    }
}

package dev.vapee.core.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;

import java.util.Objects;
import java.util.Optional;
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
        String validatedKey = Objects.requireNonNull(key, "key");
        if (validatedKey.isBlank()) {
            throw new IllegalArgumentException("Meta keys must not be blank");
        }

        return getLoadedUser(uniqueId)
                .map(user -> user.getCachedData().getMetaData().getMetaValue(validatedKey));
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
}

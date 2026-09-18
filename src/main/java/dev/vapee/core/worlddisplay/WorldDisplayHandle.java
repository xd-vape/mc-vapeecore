package dev.vapee.core.worlddisplay;

import java.util.Objects;
import java.util.UUID;

public record WorldDisplayHandle(WorldDisplayKey key, UUID entityId, Type type) {

    public WorldDisplayHandle {
        key = Objects.requireNonNull(key, "key");
        entityId = Objects.requireNonNull(entityId, "entityId");
        type = Objects.requireNonNull(type, "type");
    }

    public enum Type {
        TEXT,
        ITEM
    }
}

package dev.vapee.core.worlddisplay;

import java.util.Objects;

public record WorldDisplayKey(String owner, String id) {

    public WorldDisplayKey {
        owner = requireText(owner, "owner");
        id = requireText(id, "id");
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}

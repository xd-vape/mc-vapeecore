package dev.vapee.core.seat;

import java.util.Objects;

public record SeatKey(String owner, String id) {

    public SeatKey {
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

package dev.vapee.core.activity.blackjack.table;

import java.util.Objects;

public record BlackjackDisplayAnchor(
        String worldName,
        double x,
        double y,
        double z,
        float yaw
) {

    public BlackjackDisplayAnchor {
        if (Objects.requireNonNull(worldName, "worldName").isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("display anchor coordinates and yaw must be finite");
        }
    }
}

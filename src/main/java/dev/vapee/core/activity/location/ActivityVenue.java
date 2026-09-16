package dev.vapee.core.activity.location;

import java.util.Objects;
import java.util.regex.Pattern;

public record ActivityVenue(
        String id,
        String activityKey,
        ActivityArea area,
        ActivityPosition anchor
) {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_-]+");

    public ActivityVenue {
        id = requireKey(id, "id");
        activityKey = requireKey(activityKey, "activityKey");
        area = Objects.requireNonNull(area, "area");
        anchor = Objects.requireNonNull(anchor, "anchor");

        if (!area.worldName().equals(anchor.worldName())) {
            throw new IllegalArgumentException("Venue anchor and area must use the same world");
        }
        if (!area.contains(anchor)) {
            throw new IllegalArgumentException("Venue anchor must be inside the venue area");
        }
    }

    private static String requireKey(String value, String name) {
        String validated = Objects.requireNonNull(value, name);
        if (!KEY_PATTERN.matcher(validated).matches()) {
            throw new IllegalArgumentException(name + " must match [a-z0-9_-]+");
        }
        return validated;
    }
}

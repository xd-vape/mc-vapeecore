package dev.vapee.core.rank;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record RankInfo(
        String id,
        String displayName,
        Optional<String> description,
        OptionalInt weight
) {

    public RankInfo {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        description = Objects.requireNonNull(description, "description")
                .filter(value -> !value.isBlank());
        Objects.requireNonNull(weight, "weight");
    }

    private static String requireNonBlank(String value, String name) {
        String validatedValue = Objects.requireNonNull(value, name);
        if (validatedValue.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return validatedValue;
    }
}

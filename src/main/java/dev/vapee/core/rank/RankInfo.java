package dev.vapee.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record RankInfo(
        String id,
        String displayName,
        Optional<String> description,
        Optional<TextColor> color,
        OptionalInt weight
) {

    public RankInfo {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        description = Objects.requireNonNull(description, "description")
                .filter(value -> !value.isBlank());
        color = Objects.requireNonNull(color, "color");
        Objects.requireNonNull(weight, "weight");
    }

    public Component displayComponent() {
        return Component.text(displayName, effectiveColor());
    }

    public Component colorize(Component component) {
        return Objects.requireNonNull(component, "component").color(effectiveColor());
    }

    public TextColor effectiveColor() {
        return color.orElse(NamedTextColor.WHITE);
    }

    private static String requireNonBlank(String value, String name) {
        String validatedValue = Objects.requireNonNull(value, name);
        if (validatedValue.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return validatedValue;
    }
}

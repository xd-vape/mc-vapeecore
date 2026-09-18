package dev.vapee.core.command.help;

import java.util.List;
import java.util.Objects;

public record CommandHelpPage(
        String title,
        String description,
        List<CommandHelpSection> sections,
        String footer
) {

    public CommandHelpPage {
        title = requireText(title, "title");
        description = optionalText(description);
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        footer = optionalText(footer);
    }

    public CommandHelpPage(String title, List<CommandHelpSection> sections) {
        this(title, null, sections, null);
    }

    public CommandHelpPage(String title, String description, List<CommandHelpSection> sections) {
        this(title, description, sections, null);
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}

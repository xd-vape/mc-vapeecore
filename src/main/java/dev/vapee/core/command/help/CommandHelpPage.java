package dev.vapee.core.command.help;

import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Objects;

public record CommandHelpPage(Component title, Component description, List<CommandHelpSection> sections, Component footer) {
    public CommandHelpPage { Objects.requireNonNull(title, "title"); sections = List.copyOf(Objects.requireNonNull(sections, "sections")); }
    public CommandHelpPage(Component title, List<CommandHelpSection> sections) { this(title, null, sections, null); }
    public CommandHelpPage(Component title, Component description, List<CommandHelpSection> sections) { this(title, description, sections, null); }
    public CommandHelpPage(String title, List<CommandHelpSection> sections) { this(title, null, sections, null); }
    public CommandHelpPage(String title, String description, List<CommandHelpSection> sections) { this(title, description, sections, null); }
    public CommandHelpPage(String title, String description, List<CommandHelpSection> sections, String footer) {
        this(required(title), optional(description), sections, optional(footer));
    }
    private static Component required(String value) {
        String text = Objects.requireNonNull(value, "title").trim();
        if (text.isEmpty()) throw new IllegalArgumentException("title must not be blank");
        return Component.text(text);
    }
    private static Component optional(String value) { return value == null || value.isBlank() ? null : Component.text(value.trim()); }
}

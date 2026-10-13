package dev.vapee.core.command.help;

import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Objects;

public record CommandHelpSection(Component title, List<CommandHelpEntry> entries) {
    public CommandHelpSection { Objects.requireNonNull(title, "title"); entries = List.copyOf(Objects.requireNonNull(entries, "entries")); }
    public CommandHelpSection(String title, List<CommandHelpEntry> entries) { this(required(title), entries); }
    private static Component required(String value) {
        String text = Objects.requireNonNull(value, "title").trim();
        if (text.isEmpty()) throw new IllegalArgumentException("title must not be blank");
        return Component.text(text);
    }
}

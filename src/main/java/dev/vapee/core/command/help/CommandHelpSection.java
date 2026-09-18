package dev.vapee.core.command.help;

import java.util.List;
import java.util.Objects;

public record CommandHelpSection(String title, List<CommandHelpEntry> entries) {

    public CommandHelpSection {
        title = Objects.requireNonNull(title, "title").trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}

package dev.vapee.core.command.help;

import java.util.Objects;

public record CommandHelpEntry(
        String syntax,
        String description,
        String permission,
        String hint
) {

    public CommandHelpEntry {
        syntax = requireText(syntax, "syntax");
        description = requireText(description, "description");
        permission = optionalText(permission);
        hint = optionalText(hint);
    }

    public CommandHelpEntry(String syntax, String description) {
        this(syntax, description, null, null);
    }

    public CommandHelpEntry(String syntax, String description, String permission) {
        this(syntax, description, permission, null);
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

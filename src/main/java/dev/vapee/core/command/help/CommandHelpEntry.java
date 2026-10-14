package dev.vapee.core.command.help;

import net.kyori.adventure.text.Component;
import java.util.Objects;

/** Locale-independent descriptions; syntax and permissions are always stable literal tokens. */
public record CommandHelpEntry(String syntax, Component description, String permission, Component hint) {
    public CommandHelpEntry {
        syntax = text(syntax, "syntax"); description = Objects.requireNonNull(description, "description");
        permission = permission == null || permission.isBlank() ? null : permission.trim();
    }
    public CommandHelpEntry(String syntax, Component description) { this(syntax, description, null, null); }
    public CommandHelpEntry(String syntax, Component description, String permission) { this(syntax, description, permission, null); }
    public CommandHelpEntry(String syntax, String description) { this(syntax, description, null, null); }
    public CommandHelpEntry(String syntax, String description, String permission) { this(syntax, description, permission, null); }
    public CommandHelpEntry(String syntax, String description, String permission, String hint) {
        this(syntax, Component.text(text(description, "description")), permission,
                hint == null || hint.isBlank() ? null : Component.text(hint.trim()));
    }
    private static String text(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return text;
    }
}

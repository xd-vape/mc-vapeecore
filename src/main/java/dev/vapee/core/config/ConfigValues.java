package dev.vapee.core.config;

import org.bukkit.configuration.ConfigurationSection;

/** Narrow scalar checks. Callers retain defaults, diagnostics and domain validation. */
public final class ConfigValues {
    private ConfigValues() { }

    /** Missing keys retain the caller's fallback silently, using Bukkit's existing contains semantics. */
    public static boolean readBoolean(ConfigurationSection config, String path, boolean fallback, Runnable invalid) {
        if (!config.contains(path)) return fallback;
        if (config.get(path) instanceof Boolean value) return value;
        invalid.run();
        return fallback;
    }

    /** Blank strings are valid and returned verbatim. */
    public static String readString(ConfigurationSection config, String path, String fallback, Runnable invalid) {
        if (!config.contains(path)) return fallback;
        if (config.get(path) instanceof String value) return value;
        invalid.run();
        return fallback;
    }

    /** A null raw value is invalid; the caller decides whether missing keys reach this check. */
    public static String nonBlankString(Object raw, String fallback, Runnable invalid) {
        if (raw instanceof String value && !value.isBlank()) return value;
        invalid.run();
        return fallback;
    }
}

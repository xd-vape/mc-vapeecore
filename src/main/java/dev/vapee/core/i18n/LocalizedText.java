package dev.vapee.core.i18n;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable presentation value. Contains no player, locale cache, or persistence authority. */
public record LocalizedText(Map<String, String> values) {
    public LocalizedText {
        values = Map.copyOf(Objects.requireNonNull(values));
        if (!values.containsKey("en") || values.values().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Localized text requires English fallback");
    }
    public static LocalizedText literal(String value) { return new LocalizedText(Map.of("en", Objects.requireNonNull(value))); }
    public String resolve(Locale locale) { return values.getOrDefault(LanguageResolver.resolve(locale).code(), values.get("en")); }
    public String english() { return values.get("en"); }
    public static LocalizedText parse(Object value) {
        if (value instanceof String text) return literal(text);
        Map<?, ?> entries = value instanceof Map<?, ?> map ? map : null;
        if (entries == null) throw new IllegalArgumentException("Expected string or language map with en fallback");
        var strings = new LinkedHashMap<String, String>();
        entries.forEach((key, text) -> {
            if (!(key instanceof String code) || !(text instanceof String string))
                throw new IllegalArgumentException("Localized values must be strings");
            strings.put(code.toLowerCase(Locale.ROOT), string);
        });
        return new LocalizedText(strings);
    }
}

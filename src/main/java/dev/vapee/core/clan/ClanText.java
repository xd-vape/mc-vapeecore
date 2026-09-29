package dev.vapee.core.clan;

import java.util.Locale;
import java.util.Objects;

/** Canonical user-data validation shared by runtime mutations and persistence load. */
final class ClanText {
    private ClanText() { }

    static String name(String value) {
        String raw = Objects.requireNonNull(value, "name");
        if (hasControl(raw)) throw new IllegalArgumentException("Invalid clan name");
        String text = raw.strip();
        if (blank(text)) throw new IllegalArgumentException("Invalid clan name");
        return text;
    }

    static String tag(String value) {
        String raw = Objects.requireNonNull(value, "tag");
        if (hasControl(raw)) throw new IllegalArgumentException("Invalid clan tag");
        String text = raw.strip();
        if (blank(text) || text.codePoints().anyMatch(ClanText::whitespace)) {
            throw new IllegalArgumentException("Invalid clan tag");
        }
        return text;
    }

    static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    static boolean within(String text, int min, int max) {
        int length = text.codePointCount(0, text.length());
        return length >= min && length <= max;
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static boolean blank(String value) { return value.codePoints().allMatch(ClanText::whitespace); }
    private static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}

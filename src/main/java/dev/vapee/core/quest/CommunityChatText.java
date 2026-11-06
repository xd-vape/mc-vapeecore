package dev.vapee.core.quest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Pure, bounded text classification. Only a digest and classification flags leave this method. */
final class CommunityChatText {
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SEPARATOR = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");
    private static final Pattern HELLO = word("hallo|hi|hey|hello");
    private static final Pattern THANKS = word("danke|thanks|thank you|vielen dank");

    private static Pattern word(String words) {
        return Pattern.compile("(?<![\\p{L}\\p{M}\\p{N}_])(?:" + words + ")(?![\\p{L}\\p{M}\\p{N}_])");
    }

    record Message(String fingerprint, boolean hello, boolean thanks, boolean unique) { }

    static Message parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 256) return null;
        String text = SPACE.matcher(Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        if (text.isEmpty() || text.startsWith("/") || text.codePoints().anyMatch(c ->
                Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) return null;
        int[] points = text.codePoints().toArray();
        int letters = 0, alphanumeric = 0, repeated = 0, previous = -1;
        Set<Integer> distinct = new HashSet<>();
        StringBuilder content = new StringBuilder();
        for (int c : points) {
            if (Character.isLetter(c)) letters++;
            if (Character.isLetterOrDigit(c)) { alphanumeric++; distinct.add(c); content.appendCodePoint(c); }
            repeated = c == previous ? repeated + 1 : 1;
            if (repeated > 3 && c != ' ') return null;
            previous = c;
        }
        if (letters < 2) return null;
        String compact = content.toString();
        for (int period = 1; period <= 3 && compact.length() >= period * 3; period++) {
            if (compact.length() % period == 0
                    && compact.equals(compact.substring(0, period).repeat(compact.length() / period))) return null;
        }
        String[] words = text.split(" ");
        if (words.length >= 3 && Arrays.stream(words).distinct().count() == 1) return null;
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(SEPARATOR.matcher(text).replaceAll(" ").strip().getBytes(StandardCharsets.UTF_8)));
            return new Message(digest, HELLO.matcher(text).find(), THANKS.matcher(text).find(),
                    points.length >= 8 && alphanumeric >= 4 && distinct.size() >= 3);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

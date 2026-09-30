package dev.vapee.core.moderation;

import java.util.Objects;

final class ModerationText {
    static final int MAX_REASON_CODE_POINTS = 256;

    private ModerationText() { }

    static String reason(String value) {
        Objects.requireNonNull(value, "reason");
        // Validate before trimming: a leading newline must not silently disappear.
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            if (Character.isISOControl(point) || point == 0x2028 || point == 0x2029
                    || point >= 0xD800 && point <= 0xDFFF) {
                throw new IllegalArgumentException("Reason must be single-line valid Unicode without control characters");
            }
            offset += Character.charCount(point);
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > MAX_REASON_CODE_POINTS) {
            throw new IllegalArgumentException("Reason must contain 1 to 256 Unicode code points after trimming");
        }
        return normalized;
    }
}

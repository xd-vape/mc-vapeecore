package dev.vapee.core.rank.staff;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Security order, LOW to HIGH. Independent of public ranks and LP inheritance. */
public record StaffHierarchyConfig(List<String> protectedGroups) {
    public static final List<String> DEFAULT_GROUPS = List.of("builder", "moderator", "admin", "owner");

    public StaffHierarchyConfig {
        Objects.requireNonNull(protectedGroups, "protectedGroups");
        if (protectedGroups.isEmpty()) throw new IllegalArgumentException("protectedGroups must not be empty");
        var seen = new HashSet<String>();
        protectedGroups = protectedGroups.stream().map(StaffHierarchyConfig::normalizeGroup).toList();
        for (String group : protectedGroups) {
            if (!seen.add(group)) throw new IllegalArgumentException("duplicate protected group");
        }
    }

    public static StaffHierarchyConfig defaults() { return new StaffHierarchyConfig(DEFAULT_GROUPS); }

    public static String normalizeGroup(String group) {
        Objects.requireNonNull(group, "group");
        // Reject controls before trimming: a newline is not benign surrounding space.
        if (group.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("control in group ID");
        String normalized = group.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.codePoints()
                .anyMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) {
            throw new IllegalArgumentException("blank or whitespace in group ID");
        }
        return normalized;
    }

    public int level(String group) { return protectedGroups.indexOf(normalizeGroup(group)); }
}

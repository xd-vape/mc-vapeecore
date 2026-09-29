package dev.vapee.core.clan;

/** Runtime mutation limits; existing persisted clans remain loadable after limits shrink. */
public record ClanLimits(int maxMembers, int maxOutgoingInvites, int maxIncomingInvites,
                         int minNameLength, int maxNameLength, int minTagLength, int maxTagLength) {

    public ClanLimits {
        positive(maxMembers, "maxMembers");
        positive(maxOutgoingInvites, "maxOutgoingInvites");
        positive(maxIncomingInvites, "maxIncomingInvites");
        positive(minNameLength, "minNameLength");
        positive(maxNameLength, "maxNameLength");
        positive(minTagLength, "minTagLength");
        positive(maxTagLength, "maxTagLength");
        if (minNameLength > maxNameLength || minTagLength > maxTagLength) {
            throw new IllegalArgumentException("Minimum length cannot exceed maximum length");
        }
    }

    public static ClanLimits defaults() {
        return new ClanLimits(25, 25, 10, 3, 24, 2, 8);
    }

    private static void positive(int value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}

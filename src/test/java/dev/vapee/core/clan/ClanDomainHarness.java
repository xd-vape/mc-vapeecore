package dev.vapee.core.clan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClanDomainHarness {
    private static int checks;
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    public static void main(String[] args) {
        textAndLimits();
        clanInvariants();
        snapshotInvariants();
        System.out.println("ClanDomainHarness passed " + checks + " checks.");
    }

    private static void textAndLimits() {
        check(ClanRole.values().length == 2 && ClanRole.valueOf("OWNER") == ClanRole.OWNER
                && ClanRole.valueOf("MEMBER") == ClanRole.MEMBER, "only two foundation roles");
        check(ClanText.name("  Café Clan  ").equals("Café Clan"), "Unicode name retains spelling and trims");
        check(ClanText.tag(" VaPe ").equals("VaPe"), "tag retains case and trims");
        check(ClanText.key("VAPEE").equals(ClanText.key("vapee")), "case-insensitive root key");
        check(ClanText.within("😀😀", 2, 2), "length uses Unicode code points");
        check(!ClanText.within("😀😀", 3, 4), "code-point minimum");
        for (String name : List.of("", "  ", "\n", "Good\n", "Bad\u0000Name", "Bad\u007fName"))
            invalid(() -> ClanText.name(name), "invalid name");
        for (String tag : List.of("", " ", "A B", "A\tB", "\tAB", "A\u00a0B", "A\nB", "A\u0000B"))
            invalid(() -> ClanText.tag(tag), "invalid tag");
        ClanLimits defaults = ClanLimits.defaults();
        check(defaults.maxMembers() == 25 && defaults.maxOutgoingInvites() == 25
                && defaults.maxIncomingInvites() == 10 && defaults.minNameLength() == 3
                && defaults.maxNameLength() == 24 && defaults.minTagLength() == 2
                && defaults.maxTagLength() == 8, "documented defaults");
        invalid(() -> new ClanLimits(0, 1, 1, 1, 2, 1, 2), "member limit positive");
        invalid(() -> new ClanLimits(1, -1, 1, 1, 2, 1, 2), "outgoing limit positive");
        invalid(() -> new ClanLimits(1, 1, 0, 1, 2, 1, 2), "incoming limit positive");
        invalid(() -> new ClanLimits(1, 1, 1, 3, 2, 1, 2), "name bounds coherent");
        invalid(() -> new ClanLimits(1, 1, 1, 1, 2, 3, 2), "tag bounds coherent");
    }

    private static void clanInvariants() {
        Clan clan = clan(1, "  Vapee  ", " VAPE ", owner(2), member(1));
        check(clan.id().equals(id(1)) && clan.name().equals("Vapee") && clan.tag().equals("VAPE"),
                "UUID identity and canonical text");
        check(clan.ownerId().equals(id(2)), "owner derived from member role");
        check(clan.members().get(0).playerId().equals(id(1)), "members deterministic UUID order");
        immutable(() -> clan.members().add(member(3)), "member list immutable");
        invalid(() -> clan(1, "Vapee", "VP", member(1)), "zero owner rejected");
        invalid(() -> clan(1, "Vapee", "VP", owner(1), owner(2)), "multiple owners rejected");
        invalid(() -> clan(1, "Vapee", "VP", owner(1), member(1)), "duplicate member rejected");
        invalid(() -> clan(1, "\u0000", "VP", owner(1)), "invalid clan name rejected");
        invalid(() -> clan(1, "Vapee", "V P", owner(1)), "invalid clan tag rejected");
        nullInvalid(() -> new ClanMember(null, ClanRole.MEMBER, NOW), "member UUID required");
        nullInvalid(() -> new ClanInvite(id(1), null, NOW), "invite recipient required");
    }

    private static void snapshotInvariants() {
        Clan a = clan(2, "Vapee", "VP", owner(2), member(3));
        Clan b = clan(1, "Other", "OT", owner(1));
        ClanInvite first = invite(2, 7), second = invite(1, 6);
        ClanSnapshot snapshot = new ClanSnapshot(List.of(a, b), List.of(first, second));
        check(snapshot.clans().get(0).id().equals(id(1)), "clans sorted by UUID");
        check(snapshot.invites().get(0).clanId().equals(id(1)), "invites sorted by clan then recipient");
        immutable(() -> snapshot.clans().clear(), "clan list immutable");
        immutable(() -> snapshot.invites().clear(), "invite list immutable");
        check(ClanSnapshot.empty().clans().isEmpty() && ClanSnapshot.empty().invites().isEmpty(), "empty snapshot");
        invalid(() -> new ClanSnapshot(List.of(a, a), List.of()), "duplicate IDs");
        invalid(() -> new ClanSnapshot(List.of(a, clan(3, "vapee", "XX", owner(8))), List.of()),
                "case-insensitive duplicate name");
        invalid(() -> new ClanSnapshot(List.of(a, clan(3, "Unique", "vp", owner(8))), List.of()),
                "case-insensitive duplicate tag");
        invalid(() -> new ClanSnapshot(List.of(a, clan(3, "Unique", "XX", owner(3))), List.of()),
                "cross-clan duplicate member");
        invalid(() -> new ClanSnapshot(List.of(a), List.of(invite(99, 7))), "unknown invite clan");
        invalid(() -> new ClanSnapshot(List.of(a), List.of(invite(2, 2))), "invite recipient already member");
        invalid(() -> new ClanSnapshot(List.of(a, b), List.of(invite(2, 1))), "invite recipient in other clan");
        invalid(() -> new ClanSnapshot(List.of(a), List.of(first, first)), "duplicate clan/recipient invite");
        ArrayList<Clan> mutable = new ArrayList<>(List.of(a));
        ClanSnapshot copied = new ClanSnapshot(mutable, List.of());
        mutable.clear();
        check(copied.clans().size() == 1, "snapshot makes defensive copy");
        check(new ClanSnapshot(List.of(a), List.of(invite(2, 6), invite(2, 7)))
                .invites().get(0).recipient().equals(id(6)), "recipient tie-break sort");
    }

    private static Clan clan(long id, String name, String tag, ClanMember... members) {
        return new Clan(id(id), name, tag, NOW, List.of(members));
    }
    private static ClanMember owner(long player) { return new ClanMember(id(player), ClanRole.OWNER, NOW); }
    private static ClanMember member(long player) { return new ClanMember(id(player), ClanRole.MEMBER, NOW); }
    private static ClanInvite invite(long clan, long recipient) { return new ClanInvite(id(clan), id(recipient), NOW); }
    private static UUID id(long value) { return new UUID(0, value); }
    private static void invalid(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void nullInvalid(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (NullPointerException expected) { checks++; }
    }
    private static void immutable(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (UnsupportedOperationException expected) { checks++; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

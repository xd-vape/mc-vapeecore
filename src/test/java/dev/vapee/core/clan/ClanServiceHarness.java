package dev.vapee.core.clan;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClanServiceHarness {
    private static int checks;
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    public static void main(String[] args) {
        creationAndNames();
        invitationsAndMembership();
        limitsAndSorting();
        rollback();
        System.out.println("ClanServiceHarness passed " + checks + " checks.");
    }

    private static void creationAndNames() {
        Fixture f = new Fixture();
        eq(f.service.createClan(id(1), "  Café Clan  ", " CC "), ClanResult.SUCCESS, "create");
        Clan first = f.service.getClanOf(id(1)).orElseThrow();
        check(first.id().equals(id(100)) && first.name().equals("Café Clan") && first.tag().equals("CC"),
                "injected UUID and trimmed Unicode name/tag");
        check(first.ownerId().equals(id(1)) && first.members().get(0).role() == ClanRole.OWNER,
                "creator is the sole owner member");
        check(first.createdAt().equals(NOW) && first.members().get(0).joinedAt().equals(NOW), "injected clock");
        eq(f.service.createClan(id(1), "Another", "AN"), ClanResult.ALREADY_IN_CLAN, "one clan per player");
        eq(f.service.createClan(id(2), "café clan", "XX"), ClanResult.NAME_ALREADY_USED, "duplicate name casefold");
        eq(f.service.createClan(id(2), "Another", "cc"), ClanResult.TAG_ALREADY_USED, "duplicate tag casefold");
        eq(f.service.createClan(id(2), " ", "XX"), ClanResult.NAME_INVALID, "blank name");
        eq(f.service.createClan(id(2), "Hi", "XX"), ClanResult.NAME_INVALID, "short name");
        eq(f.service.createClan(id(2), "A\u0000B", "XX"), ClanResult.NAME_INVALID, "control name");
        eq(f.service.createClan(id(2), "Valid", "X"), ClanResult.TAG_INVALID, "short tag");
        eq(f.service.createClan(id(2), "Valid", "X X"), ClanResult.TAG_INVALID, "tag whitespace");
        eq(f.service.createClan(id(2), "Another", "AN"), ClanResult.SUCCESS, "second clan");
        check(f.service.findClanByName("CAFÉ CLAN").orElseThrow().id().equals(id(100)), "find name casefold");
        check(f.service.findClanByTag("an").orElseThrow().id().equals(id(101)), "find tag casefold");
        eq(f.service.renameClan(id(1), "CAFÉ CLAN"), ClanResult.SUCCESS, "case-only rename");
        check(f.service.getClan(id(100)).orElseThrow().name().equals("CAFÉ CLAN"), "rename retains new spelling");
        eq(f.service.renameClan(id(1), "Another"), ClanResult.NAME_ALREADY_USED, "rename collision");
        eq(f.service.renameClan(id(1), "X"), ClanResult.NAME_INVALID, "rename validation");
        eq(f.service.renameClan(id(3), "Unused"), ClanResult.NOT_IN_CLAN, "nonmember rename");
        eq(f.service.changeTag(id(1), "cc"), ClanResult.SUCCESS, "case-only tag change");
        check(f.service.getClan(id(100)).orElseThrow().tag().equals("cc"), "tag retains new spelling");
        eq(f.service.changeTag(id(1), "AN"), ClanResult.TAG_ALREADY_USED, "tag collision");
        eq(f.service.changeTag(id(1), "bad tag"), ClanResult.TAG_INVALID, "tag validation");
        eq(f.service.changeTag(id(3), "NO"), ClanResult.NOT_IN_CLAN, "nonmember tag change");
        check(f.service.getMemberRole(id(1)).orElseThrow() == ClanRole.OWNER, "role read API");
        check(f.service.countMembers(id(100)) == 1 && f.service.isMember(id(1))
                && !f.service.isMember(id(3)), "member count and membership read API");
        check(f.service.getMembers(id(999)).isEmpty() && f.service.getClan(id(999)).isEmpty(),
                "unknown read APIs are empty");
        nullInvalid(() -> f.service.createClan(null, "Valid", "VA"), "null owner programmer error");
        nullInvalid(() -> f.service.renameClan(id(1), null), "null name programmer error");
        nullInvalid(() -> f.service.inviteMember(id(1), null), "null target programmer error");
        Fixture unicode = new Fixture();
        eq(unicode.service.createClan(id(4), "😀😀😀", "😀😀"), ClanResult.SUCCESS,
                "name and tag limits count Unicode code points");
        eq(unicode.service.createClan(id(5), "😀😀", "AB"), ClanResult.NAME_INVALID,
                "Unicode code-point minimum is enforced");
        eq(unicode.service.createClan(id(5), "😀".repeat(25), "AB"), ClanResult.NAME_INVALID,
                "Unicode code-point maximum is enforced");
    }

    private static void invitationsAndMembership() {
        Fixture f = new Fixture();
        f.create(id(1), "First Clan", "FC");
        f.create(id(2), "Second Clan", "SC");
        eq(f.service.inviteMember(id(3), id(4)), ClanResult.NOT_IN_CLAN, "nonmember cannot invite");
        eq(f.service.inviteMember(id(1), id(1)), ClanResult.CANNOT_TARGET_SELF, "self invite");
        eq(f.service.inviteMember(id(1), id(2)), ClanResult.TARGET_ALREADY_MEMBER, "already member target");
        eq(f.service.inviteMember(id(1), id(3)), ClanResult.SUCCESS, "first invite");
        eq(f.service.inviteMember(id(1), id(3)), ClanResult.INVITE_ALREADY_EXISTS, "duplicate invite");
        eq(f.service.inviteMember(id(2), id(3)), ClanResult.SUCCESS, "multiple incoming from different clans");
        check(f.service.getIncomingInvites(id(3)).size() == 2, "incoming read API");
        check(f.service.getOutgoingInvites(id(100)).size() == 1, "outgoing read API");
        eq(f.service.acceptInvite(id(3), id(999)), ClanResult.CLAN_NOT_FOUND, "unknown clan accept");
        eq(f.service.acceptInvite(id(4), id(100)), ClanResult.INVITE_NOT_FOUND, "missing invite accept");
        eq(f.service.acceptInvite(id(3), id(101)), ClanResult.SUCCESS, "accept invite");
        check(f.service.getMemberRole(id(3)).orElseThrow() == ClanRole.MEMBER, "accepted member role");
        eq(f.service.inviteMember(id(3), id(4)), ClanResult.NOT_OWNER, "nonowner cannot invite");
        check(f.service.getIncomingInvites(id(3)).isEmpty()
                && f.service.getOutgoingInvites(id(100)).isEmpty(), "accept removes all incoming invites atomically");
        eq(f.service.acceptInvite(id(3), id(100)), ClanResult.ALREADY_IN_CLAN, "member cannot accept another");
        eq(f.service.inviteMember(id(1), id(4)), ClanResult.SUCCESS, "invite to deny");
        eq(f.service.denyInvite(id(4), id(100)), ClanResult.SUCCESS, "deny only matching invite");
        eq(f.service.denyInvite(id(4), id(100)), ClanResult.INVITE_NOT_FOUND, "deny missing");
        eq(f.service.denyInvite(id(4), id(999)), ClanResult.CLAN_NOT_FOUND, "deny unknown clan");
        eq(f.service.inviteMember(id(1), id(4)), ClanResult.SUCCESS, "invite to cancel");
        eq(f.service.cancelInvite(id(2), id(4)), ClanResult.INVITE_NOT_FOUND, "owner cannot cancel other clan invite");
        eq(f.service.cancelInvite(id(1), id(4)), ClanResult.SUCCESS, "cancel own invite");
        eq(f.service.cancelInvite(id(1), id(4)), ClanResult.INVITE_NOT_FOUND, "cancel missing");
        eq(f.service.leaveClan(id(2)), ClanResult.OWNER_CANNOT_LEAVE, "owner cannot leave");
        eq(f.service.leaveClan(id(3)), ClanResult.SUCCESS, "member leaves");
        check(!f.service.isMember(id(3)) && f.service.countMembers(id(101)) == 1, "leave updates membership");
        eq(f.service.leaveClan(id(3)), ClanResult.NOT_IN_CLAN, "nonmember leave");
        eq(f.service.inviteMember(id(1), id(3)), ClanResult.SUCCESS, "invite after leaving");
        eq(f.service.acceptInvite(id(3), id(100)), ClanResult.SUCCESS, "accept into first clan");
        Instant joined = f.service.getMembers(id(100)).stream().filter(m -> m.playerId().equals(id(3)))
                .findFirst().orElseThrow().joinedAt();
        eq(f.service.kickMember(id(3), id(1)), ClanResult.NOT_OWNER, "nonowner cannot kick");
        eq(f.service.kickMember(id(1), id(1)), ClanResult.CANNOT_TARGET_SELF, "owner cannot kick self");
        eq(f.service.kickMember(id(1), id(4)), ClanResult.TARGET_NOT_MEMBER, "kick requires same-clan member");
        eq(f.service.transferOwnership(id(1), id(4)), ClanResult.TARGET_NOT_MEMBER, "transfer requires same-clan member");
        eq(f.service.transferOwnership(id(1), id(1)), ClanResult.CANNOT_TARGET_SELF, "cannot transfer to self");
        eq(f.service.transferOwnership(id(3), id(1)), ClanResult.NOT_OWNER, "nonowner cannot transfer");
        eq(f.service.transferOwnership(id(1), id(3)), ClanResult.SUCCESS, "transfer ownership");
        check(f.service.getMemberRole(id(1)).orElseThrow() == ClanRole.MEMBER
                && f.service.getMemberRole(id(3)).orElseThrow() == ClanRole.OWNER, "old/new roles swapped");
        check(f.service.getMembers(id(100)).stream().filter(m -> m.playerId().equals(id(3)))
                .findFirst().orElseThrow().joinedAt().equals(joined), "transfer preserves joinedAt");
        eq(f.service.disbandClan(id(1)), ClanResult.NOT_OWNER, "old owner cannot disband");
        eq(f.service.inviteMember(id(3), id(4)), ClanResult.SUCCESS, "new owner can invite");
        eq(f.service.disbandClan(id(3)), ClanResult.SUCCESS, "new owner disbands");
        check(f.service.getClan(id(100)).isEmpty() && !f.service.isMember(id(1))
                && !f.service.isMember(id(3)) && f.service.getIncomingInvites(id(4)).isEmpty(),
                "disband removes clan, members and invites");
        eq(f.service.disbandClan(id(3)), ClanResult.NOT_IN_CLAN, "cannot disband twice");
        Fixture kick = memberFixture();
        eq(kick.service.kickMember(id(1), id(2)), ClanResult.SUCCESS, "owner kicks member");
        check(!kick.service.isMember(id(2)) && kick.service.countMembers(id(100)) == 1,
                "kick updates membership without losing owner");
    }

    private static void limitsAndSorting() {
        Fixture f = new Fixture();
        f.limits.set(new ClanLimits(2, 1, 1, 3, 24, 2, 8));
        f.create(id(1), "First Clan", "FC");
        f.create(id(2), "Second Clan", "SC");
        eq(f.service.inviteMember(id(1), id(9)), ClanResult.SUCCESS, "first outgoing");
        eq(f.service.inviteMember(id(1), id(8)), ClanResult.OUTGOING_INVITE_LIMIT_REACHED, "outgoing cap");
        eq(f.service.inviteMember(id(2), id(9)), ClanResult.INCOMING_INVITE_LIMIT_REACHED, "incoming cap");
        f.limits.set(new ClanLimits(1, 1, 1, 3, 24, 2, 8));
        eq(f.service.acceptInvite(id(9), id(100)), ClanResult.MEMBER_LIMIT_REACHED,
                "accept rechecks current member limit");
        check(f.service.getIncomingInvites(id(9)).size() == 1, "failed accept retains invite");
        f.limits.set(new ClanLimits(2, 1, 1, 3, 24, 2, 8));
        eq(f.service.acceptInvite(id(9), id(100)), ClanResult.SUCCESS, "fill clan");
        eq(f.service.inviteMember(id(1), id(8)), ClanResult.MEMBER_LIMIT_REACHED, "member cap at invitation");
        check(f.service.getMembers(id(100)).get(0).playerId().equals(id(1)), "member UUID order");
        f.limits.set(new ClanLimits(1, 1, 1, 3, 24, 2, 8));
        check(f.service.countMembers(id(100)) == 2, "shrinking limit does not invalidate loaded state");
        eq(f.service.inviteMember(id(1), id(8)), ClanResult.MEMBER_LIMIT_REACHED, "new mutation respects shrunk limit");

        Fixture order = new Fixture();
        order.create(id(1), "First Clan", "FC");
        order.create(id(2), "Second Clan", "SC");
        eq(order.service.inviteMember(id(2), id(9)), ClanResult.SUCCESS, "second-clan invite");
        eq(order.service.inviteMember(id(1), id(9)), ClanResult.SUCCESS, "first-clan invite");
        check(order.service.getIncomingInvites(id(9)).get(0).clanId().equals(id(100)),
                "incoming invites deterministic clan order");
        eq(order.service.inviteMember(id(1), id(8)), ClanResult.SUCCESS, "second outgoing");
        check(order.service.getOutgoingInvites(id(100)).get(0).recipient().equals(id(8)),
                "outgoing invites deterministic recipient order");
        immutable(() -> order.service.getIncomingInvites(id(9)).clear(), "incoming immutable");
        immutable(() -> order.service.getMembers(id(100)).clear(), "members immutable");
        eq(order.service.createClan(id(9), "New Clan", "NC"), ClanResult.SUCCESS,
                "creation with incoming invites succeeds");
        check(order.service.getIncomingInvites(id(9)).isEmpty(), "creation removes all incoming invites");
    }

    private static void rollback() {
        Fixture create = new Fixture();
        create.repository.fail = true;
        storageFailure(() -> create.service.createClan(id(1), "First Clan", "FC"), "create save failure");
        check(!create.service.isMember(id(1)), "create runtime rollback");

        Fixture invite = new Fixture(); invite.create(id(1), "First Clan", "FC"); invite.repository.fail = true;
        storageFailure(() -> invite.service.inviteMember(id(1), id(2)), "invite save failure");
        check(invite.service.getIncomingInvites(id(2)).isEmpty(), "invite runtime rollback");

        Fixture accept = new Fixture(); accept.create(id(1), "First Clan", "FC");
        accept.service.inviteMember(id(1), id(2)); accept.repository.fail = true;
        storageFailure(() -> accept.service.acceptInvite(id(2), id(100)), "accept save failure");
        check(!accept.service.isMember(id(2)) && accept.service.getIncomingInvites(id(2)).size() == 1,
                "accept member/invite rollback");

        Fixture kick = memberFixture(); kick.repository.fail = true;
        storageFailure(() -> kick.service.kickMember(id(1), id(2)), "kick save failure");
        check(kick.service.isMember(id(2)), "kick runtime rollback");

        Fixture transfer = memberFixture(); transfer.repository.fail = true;
        storageFailure(() -> transfer.service.transferOwnership(id(1), id(2)), "transfer save failure");
        check(transfer.service.getMemberRole(id(1)).orElseThrow() == ClanRole.OWNER
                && transfer.service.getMemberRole(id(2)).orElseThrow() == ClanRole.MEMBER,
                "transfer runtime rollback");

        Fixture disband = memberFixture(); disband.repository.fail = true;
        storageFailure(() -> disband.service.disbandClan(id(1)), "disband save failure");
        check(disband.service.getClan(id(100)).isPresent() && disband.service.isMember(id(2)),
                "disband runtime rollback");

        Fixture rename = new Fixture(); rename.create(id(1), "First Clan", "FC"); rename.repository.fail = true;
        storageFailure(() -> rename.service.renameClan(id(1), "New Name"), "rename save failure");
        check(rename.service.getClan(id(100)).orElseThrow().name().equals("First Clan"), "rename rollback");

        Fixture tag = new Fixture(); tag.create(id(1), "First Clan", "FC"); tag.repository.fail = true;
        storageFailure(() -> tag.service.changeTag(id(1), "NN"), "tag save failure");
        check(tag.service.getClan(id(100)).orElseThrow().tag().equals("FC"), "tag rollback");

        Fixture leave = memberFixture(); leave.repository.fail = true;
        storageFailure(() -> leave.service.leaveClan(id(2)), "leave save failure");
        check(leave.service.isMember(id(2)), "leave rollback");

        Fixture cancel = new Fixture(); cancel.create(id(1), "First Clan", "FC");
        cancel.service.inviteMember(id(1), id(2)); cancel.repository.fail = true;
        storageFailure(() -> cancel.service.cancelInvite(id(1), id(2)), "cancel save failure");
        check(cancel.service.getIncomingInvites(id(2)).size() == 1, "cancel rollback");
        storageFailure(() -> cancel.service.denyInvite(id(2), id(100)), "deny save failure");
        check(cancel.service.getIncomingInvites(id(2)).size() == 1, "deny rollback");
    }

    private static Fixture memberFixture() {
        Fixture f = new Fixture();
        f.create(id(1), "First Clan", "FC");
        f.service.inviteMember(id(1), id(2));
        f.service.acceptInvite(id(2), id(100));
        return f;
    }

    private static UUID id(long value) { return new UUID(0, value); }
    private static void eq(ClanResult actual, ClanResult expected, String message) { check(actual == expected, message); }
    private static void storageFailure(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (ClanRepositoryException expected) { checks++; }
    }
    private static void immutable(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (UnsupportedOperationException expected) { checks++; }
    }
    private static void nullInvalid(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (NullPointerException expected) { checks++; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final class Fixture {
        private final MemoryRepository repository = new MemoryRepository();
        private final AtomicReference<ClanLimits> limits = new AtomicReference<>(ClanLimits.defaults());
        private final AtomicLong nextId = new AtomicLong(100);
        private final ClanService service = new ClanService(repository, limits::get,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> id(nextId.getAndIncrement()));
        private void create(UUID owner, String name, String tag) {
            eq(service.createClan(owner, name, tag), ClanResult.SUCCESS, "fixture create");
        }
    }

    private static final class MemoryRepository implements ClanRepository {
        private ClanSnapshot snapshot = ClanSnapshot.empty();
        private boolean fail;
        @Override public ClanSnapshot initialize() { return snapshot; }
        @Override public void save(ClanSnapshot next) {
            if (fail) throw new ClanRepositoryException("Simulated save failure");
            snapshot = next;
        }
    }
}

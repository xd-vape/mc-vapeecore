package dev.vapee.core.clan.gui;

import dev.vapee.core.clan.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public final class ClanMenuHarness {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;
    public static void main(String[] args) throws Exception {
        ClanMenuFixture f = new ClanMenuFixture();
        var owner = f.player("Owner", true);
        var member = f.player("Bravo", true);
        var offline = f.player("Alpha", false);
        var other = f.player("Other", true);
        f.menu.open(member.player);
        check(ClanMenuFixture.holder(member.open).view() == ClanMenuView.OVERVIEW, "clanless overview");
        check(name(member.open, 22).contains("not in a clan"), "clanless explanation");
        check(name(member.open, ClanMenu.ACTION_SLOT).contains("Create"), "create action visible");
        Inventory before = member.open;
        f.click(member, before, ClanMenu.ACTION_SLOT, ClickType.LEFT);
        check(member.closeCalls == 1 && member.received.stream().anyMatch(c -> PLAIN.serialize(c).contains("/clan create")),
                "create prompt closes GUI and suggests command");
        check(f.clans.createClan(owner.id, "Vapee Community", "VAPE") == ClanResult.SUCCESS, "create fixture clan");
        UUID clanId = f.clans.getClanOf(owner.id).orElseThrow().id();
        check(f.clans.inviteMember(owner.id, member.id) == ClanResult.SUCCESS, "invite online member");
        check(f.clans.inviteMember(owner.id, offline.id) == ClanResult.SUCCESS, "invite offline member");
        f.menu.open(member.player, ClanMenuView.INVITES, 0);
        check(ClanMenuFixture.holder(member.open).target(0).orElseThrow().equals(clanId), "incoming maps clan UUID");
        check(name(member.open, 0).contains("Vapee Community") && name(member.open, 0).contains("VAPE"),
                "incoming shows name and tag");
        f.click(member, member.open, 0, ClickType.LEFT);
        check(f.clans.getClanOf(member.id).isPresent(), "incoming left accepts");
        f.menu.open(owner.player);
        check(name(owner.open, 20).contains("Vapee Community"), "overview name");
        check(lore(owner.open, 20).contains("VAPE"), "overview tag");
        check(name(owner.open, 22).contains("2/25") && lore(owner.open, 22).contains("Owner"),
                "overview member count and owner");
        check(name(owner.open, 24).contains("OWNER"), "overview role");
        f.menu.open(member.player);
        check(name(member.open, 24).contains("MEMBER"), "member role");
        f.menu.open(owner.player, ClanMenuView.MEMBERS, 0);
        check(ClanMenuFixture.holder(owner.open).target(0).orElseThrow().equals(owner.id), "owner sorts first");
        check(lore(owner.open, 1).contains("Online") && lore(owner.open, 1).contains("Shift + Right-click"),
                "member online status and owner actions");
        f.menu.open(owner.player, ClanMenuView.INVITES, 0);
        check(ClanMenuFixture.holder(owner.open).target(0).orElseThrow().equals(offline.id),
                "outgoing target maps player UUID");
        check(lore(owner.open, 0).contains("Offline"), "offline status");
        f.click(owner, owner.open, 0, ClickType.RIGHT);
        check(f.clans.getOutgoingInvites(clanId).isEmpty(), "right click cancels invite");
        check(f.clans.inviteMember(owner.id, other.id) == ClanResult.SUCCESS, "invite second clanless player");
        f.menu.open(other.player, ClanMenuView.INVITES, 0);
        f.click(other, other.open, 0, ClickType.RIGHT);
        check(f.clans.getIncomingInvites(other.id).isEmpty(), "right click denies incoming");
        f.menu.open(owner.player, ClanMenuView.MEMBERS, 0);
        f.click(owner, owner.open, 1, ClickType.SHIFT_LEFT);
        check(f.clans.getClanOf(owner.id).orElseThrow().ownerId().equals(member.id), "shift-left transfers ownership");
        f.menu.open(member.player, ClanMenuView.MEMBERS, 0);
        check(ClanMenuFixture.holder(member.open).target(0).orElseThrow().equals(member.id),
                "new owner sorts first after transfer");
        f.click(member, member.open, 1, ClickType.SHIFT_RIGHT);
        check(f.clans.getClanOf(owner.id).isEmpty(), "shift-right kicks member");
        f.menu.open(member.player, ClanMenuView.MEMBERS, 40);
        check(ClanMenuFixture.holder(member.open).page() == 0, "page clamps after list shrink");
        f.click(member, member.open, ClanMenu.REFRESH_SLOT, ClickType.LEFT);
        check(ClanMenuFixture.holder(member.open).view() == ClanMenuView.MEMBERS, "refresh preserves view");
        f.click(member, member.open, ClanMenu.CLOSE_SLOT, ClickType.LEFT);
        check(member.closeCalls >= 2, "close button closes menu");
        var unknown = f.unknown(UUID.randomUUID());
        check(f.clans.inviteMember(member.id, unknown.id) == ClanResult.SUCCESS, "unknown UUID can be invited by domain");
        f.menu.open(member.player, ClanMenuView.INVITES, 0);
        check(name(member.open, 0).contains(unknown.id.toString()), "unknown identity UUID fallback");
        var pages = new ClanMenuFixture(new ClanLimits(80, 80, 80, 3, 24, 2, 8));
        var pagingOwner = pages.player("PagingOwner", true);
        check(pages.clans.createClan(pagingOwner.id, "Paging Clan", "PAGE") == ClanResult.SUCCESS,
                "paging clan fixture");
        UUID pagingClan = pages.clans.getClanOf(pagingOwner.id).orElseThrow().id();
        for (int i = 0; i < 47; i++) {
            var target = pages.player("Player" + i, false);
            check(pages.clans.inviteMember(pagingOwner.id, target.id) == ClanResult.SUCCESS
                    && pages.clans.acceptInvite(target.id, pagingClan) == ClanResult.SUCCESS,
                    "paging member " + i);
        }
        pages.menu.open(pagingOwner.player, ClanMenuView.MEMBERS, 0);
        check(pages.menu.hasNext(pagingOwner.id, ClanMenuView.MEMBERS, 0), "46+ entries have next page");
        check(ClanMenuFixture.holder(pagingOwner.open).target(0).orElseThrow().equals(pagingOwner.id)
                && name(pagingOwner.open, 1).equals("Player0")
                && name(pagingOwner.open, 2).equals("Player1"),
                "owner first then deterministic alphabetical member sort");
        pages.click(pagingOwner, pagingOwner.open, ClanMenu.NEXT_SLOT, ClickType.LEFT);
        check(ClanMenuFixture.holder(pagingOwner.open).page() == 1
                && ClanMenuFixture.holder(pagingOwner.open).target(2).isPresent(),
                "next page contains remaining entries");
        pages.click(pagingOwner, pagingOwner.open, ClanMenu.PREVIOUS_SLOT, ClickType.LEFT);
        check(ClanMenuFixture.holder(pagingOwner.open).page() == 0, "previous page returns to first");
        for (var target : pages.clans.getMembers(pagingClan).stream().filter(m -> !m.playerId().equals(pagingOwner.id))
                .limit(10).toList()) pages.clans.kickMember(pagingOwner.id, target.playerId());
        pages.menu.open(pagingOwner.player, ClanMenuView.MEMBERS, 1);
        check(ClanMenuFixture.holder(pagingOwner.open).page() == 0, "page clamps after external shrink");
        var stale = pages.player("Stale", false);
        pages.clans.inviteMember(pagingOwner.id, stale.id);
        pages.menu.open(pagingOwner.player, ClanMenuView.INVITES, 0);
        Inventory staleMenu = pagingOwner.open;
        pages.clans.cancelInvite(pagingOwner.id, stale.id);
        pages.click(pagingOwner, staleMenu, 0, ClickType.RIGHT);
        check(pages.clans.getOutgoingInvites(pagingClan).isEmpty()
                && pagingOwner.open != staleMenu, "stale invite gives controlled refresh");
        System.out.println("ClanMenuHarness passed " + checks + " checks.");
    }
    private static String name(Inventory inv, int slot) {
        return PLAIN.serialize(ClanMenuFixture.spec(inv, slot).spec.name());
    }
    private static String lore(Inventory inv, int slot) {
        return String.join(" ", ClanMenuFixture.spec(inv, slot).spec.lore().stream().map(PLAIN::serialize).toList());
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}

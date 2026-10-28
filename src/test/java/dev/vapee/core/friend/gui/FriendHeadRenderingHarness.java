package dev.vapee.core.friend.gui;

import dev.vapee.core.ui.*;
import dev.vapee.core.friend.FriendRelation;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.persistence.PersistentDataType;

public final class FriendHeadRenderingHarness {
    private static int checks;
    private static final MenuLayout<FriendMenu.Action> LAYOUT = MenuLayout.builder(27, FriendMenu.Action.class)
            .content(List.of(10, 12)).emptyState(22).button(FriendMenu.Action.PREVIOUS, 3)
            .button(FriendMenu.Action.PAGE_INFO, 4).button(FriendMenu.Action.NEXT, 5).button(FriendMenu.Action.CLOSE, 8).build();
    public static void main(String[] args) throws Exception {
        var profiles = new HeadTestSupport.Profiles();
        var f = new FriendMenuFixture(LAYOUT, (id, item) -> profiles.heads.apply(id,
                HeadTestSupport.item(((FriendMenuFixture.SpecItem) item).spec)));
        var owner = f.player("Owner", true);
        var a = f.player("AOnline", true); var b = f.player("BCached", false); var c = f.player("CMissing", false);
        profiles.online.put(a.id, profiles.profile(a.id, HeadTestSupport.skin(1))); profiles.cached.put(b.id, HeadTestSupport.skin(2));
        for (var target : List.of(a, b, c)) { f.friends.sendRequest(owner.id, target.id); f.friends.acceptRequest(target.id, owner.id); }
        var before = f.friends.getFriends(owner.id);
        f.menu.open(owner.player, FriendMenuView.FRIENDS, 0); var first = owner.open; var firstHolder = FriendMenuFixture.holder(first);
        int slot = LAYOUT.contentSlot(0), secondSlot = LAYOUT.contentSlot(1);
        check(HeadTestSupport.owner(first.getItem(slot)).equals(a.id) && HeadTestSupport.profile(first.getItem(slot)).getId().equals(a.id), "friends entry online head uses exact holder UUID");
        check(HeadTestSupport.skin(first.getItem(slot)).equals(HeadTestSupport.skin(1)), "friends online skin assigned");
        check(HeadTestSupport.profile(first.getItem(secondSlot)).getId().equals(b.id)
                && HeadTestSupport.skin(first.getItem(secondSlot)).equals(HeadTestSupport.skin(2)), "friends offline cached skin assigned");
        check(plain(first.getItem(slot).getItemMeta().displayName()).equals("AOnline")
                && first.getItem(slot).getItemMeta().lore().stream().map(FriendHeadRenderingHarness::plain).toList().equals(List.of("Online", "Shift + Right Click to remove")), "names/localized lore preserved around skin decoration");
        f.menu.open(owner.player, FriendMenuView.FRIENDS, 1); var current = owner.open; var holder = FriendMenuFixture.holder(current);
        check(current != first && holder.getTarget(slot).orElseThrow().equals(c.id)
                && HeadTestSupport.owner(current.getItem(slot)).equals(c.id) && HeadTestSupport.profile(current.getItem(slot)) == null, "new page binds missing-profile fallback to its own UUID");
        profiles.cached.put(c.id, HeadTestSupport.skin(3));
        profiles.online.put(a.id, profiles.profile(a.id, HeadTestSupport.skin(4)));
        check(HeadTestSupport.profile(current.getItem(slot)) == null, "cache availability cannot schedule a delayed inventory write");
        f.menu.refreshActive(owner.id);
        check(owner.open == current && current.getHolder() == holder && HeadTestSupport.skin(current.getItem(slot)).equals(HeadTestSupport.skin(3)), "current page refresh keeps holder identity and observes newly cached skin");
        check(HeadTestSupport.skin(first.getItem(slot)).equals(HeadTestSupport.skin(1)) && !f.menu.isActive(owner.player, first, firstHolder), "replaced inventory cannot receive profile updates");
        f.click(owner, first, slot, ClickType.SHIFT_RIGHT);
        check(f.friends.getFriends(owner.id).equals(before) && owner.open == current, "stale page click retains all friendship data and current inventory");
        owner.open = owner.bottom; profiles.cached.put(c.id, HeadTestSupport.skin(4)); f.menu.refreshActive(owner.id);
        check(HeadTestSupport.skin(current.getItem(slot)).equals(HeadTestSupport.skin(3)), "closed inventory never updated by later cache data");

        var incoming = f.player("Incoming", true); profiles.online.put(incoming.id, profiles.profile(incoming.id, HeadTestSupport.skin(5)));
        f.friends.sendRequest(incoming.id, owner.id); owner.locale = Locale.GERMANY;
        f.menu.open(owner.player, FriendMenuView.INCOMING, 0);
        check(HeadTestSupport.profile(owner.open.getItem(slot)).getId().equals(incoming.id)
                && FriendMenuFixture.holder(owner.open).getTarget(slot).orElseThrow().equals(incoming.id), "incoming head represents request sender UUID");
        check(owner.open.getItem(slot).getItemMeta().lore().stream().map(FriendHeadRenderingHarness::plain).anyMatch("Linksklick zum Annehmen"::equals), "incoming German action lore retained");
        var outgoing = f.player("Outgoing", false); profiles.cached.put(outgoing.id, HeadTestSupport.skin(6));
        f.friends.sendRequest(owner.id, outgoing.id); f.menu.open(owner.player, FriendMenuView.OUTGOING, 0);
        check(HeadTestSupport.profile(owner.open.getItem(slot)).getId().equals(outgoing.id)
                && HeadTestSupport.skin(owner.open.getItem(slot)).equals(HeadTestSupport.skin(6)), "outgoing head uses recipient's cached offline profile");
        check(f.friends.getFriends(owner.id).equals(before) && f.friends.getIncomingRequests(owner.id).size() == 1
                && f.friends.getOutgoingRequests(owner.id).size() == 1, "head renders cannot mutate friends or requests");
        owner.permitted = false; var denied = owner.open; int reads = profiles.cacheReads.size();
        check(!f.menu.open(owner.player, FriendMenuView.FRIENDS, 0) && owner.open == denied && profiles.cacheReads.size() == reads, "permission denial precedes head lookup and inventory replacement"); owner.permitted = true;
        f.menu.open(owner.player, FriendMenuView.FRIENDS, 0);
        var top = owner.open; var item = top.getItem(slot); var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(CachedPlayerHeads.PLAYER_ID, PersistentDataType.STRING, c.id.toString()); item.setItemMeta(meta);
        var foreign = f.player("Foreign", true); f.click(foreign, top, slot, ClickType.SHIFT_RIGHT);
        check(f.friends.getFriends(owner.id).equals(before), "foreign viewer cannot authorize through a head UUID tag");
        f.click(owner, top, slot, ClickType.SHIFT_RIGHT);
        check(f.friends.getRelation(owner.id, a.id) == FriendRelation.NONE && f.friends.getRelation(owner.id, c.id) == FriendRelation.FRIENDS, "forged head tag cannot change holder-authoritative removal target");
        check(profiles.forbidden == 0, "all menu profile work is cache-only with no network or delayed writes");
        System.out.println("FriendHeadRenderingHarness passed " + checks + " checks.");
    }
    private static String plain(net.kyori.adventure.text.Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}

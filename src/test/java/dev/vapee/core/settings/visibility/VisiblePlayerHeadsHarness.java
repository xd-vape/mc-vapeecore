package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.*;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.persistence.PersistentDataType;

public final class VisiblePlayerHeadsHarness {
    private static int checks;
    private static final MenuLayout<VisiblePlayersMenu.Action> LAYOUT = MenuLayout.builder(27, VisiblePlayersMenu.Action.class)
            .content(List.of(10, 12)).emptyState(22).button(VisiblePlayersMenu.Action.PREVIOUS, 3)
            .button(VisiblePlayersMenu.Action.PAGE_INFO, 4).button(VisiblePlayersMenu.Action.NEXT, 5).button(VisiblePlayersMenu.Action.CLOSE, 8).build();
    public static void main(String[] args) throws Exception {
        var profiles = new HeadTestSupport.Profiles();
        var f = new VisibilityMenuFixture(MenuTestLayouts.VISIBILITY, LAYOUT, (id, item) -> profiles.heads.apply(id,
                HeadTestSupport.item(((VisibilityMenuFixture.VisibleSpecItem) item).spec)));
        var owner = f.player("Owner", true); var a = f.player("AOnline", true); var b = f.player("BCached", false); var c = f.player("CMissing", false);
        profiles.online.put(a.id, profiles.profile(a.id, HeadTestSupport.skin(1))); profiles.cached.put(b.id, HeadTestSupport.skin(2));
        for (var target : List.of(a, b, c)) f.settings.addLobbyVisiblePlayer(owner.id, target.id);
        var before = f.settings.getLobbyAddedVisiblePlayers(owner.id).orElseThrow();
        int slot = LAYOUT.contentSlot(0); f.visiblePlayersMenu.open(owner.player, 0); var first = owner.open;
        check(HeadTestSupport.owner(first.getItem(slot)).equals(a.id) && HeadTestSupport.profile(first.getItem(slot)).getId().equals(a.id)
                && HeadTestSupport.skin(first.getItem(slot)).equals(HeadTestSupport.skin(1)), "added online player has authoritative skin and UUID");
        check(HeadTestSupport.profile(first.getItem(LAYOUT.contentSlot(1))).getId().equals(b.id)
                && HeadTestSupport.skin(first.getItem(LAYOUT.contentSlot(1))).equals(HeadTestSupport.skin(2)), "added offline cached profile supported");
        check(plain(first.getItem(slot).getItemMeta().displayName()).equals("AOnline")
                && first.getItem(slot).getItemMeta().lore().stream().map(VisiblePlayerHeadsHarness::plain).anyMatch(s -> s.contains(a.id.toString())), "name/lore/UUID presentation preserved");
        f.visiblePlayersMenu.open(owner.player, 1); var current = owner.open; var holder = VisibilityMenuFixture.visibleHolder(current);
        check(holder.getTarget(slot).orElseThrow().equals(c.id) && HeadTestSupport.owner(current.getItem(slot)).equals(c.id)
                && HeadTestSupport.profile(current.getItem(slot)) == null, "missing offline skin uses ordinary UUID-tagged head on correct page");
        profiles.cached.put(c.id, HeadTestSupport.skin(3));
        check(HeadTestSupport.profile(current.getItem(slot)) == null, "no cache fill callback can modify inventory asynchronously");
        f.visiblePlayersMenu.refreshActive(owner.id);
        check(owner.open == current && current.getHolder() == holder && HeadTestSupport.skin(current.getItem(slot)).equals(HeadTestSupport.skin(3)), "refresh decorates current target in place without replacing holder");
        check(HeadTestSupport.skin(first.getItem(slot)).equals(HeadTestSupport.skin(1)), "replaced page stays unchanged");
        f.visibleClick(owner, first, slot, ClickType.RIGHT);
        check(f.settings.getLobbyAddedVisiblePlayers(owner.id).orElseThrow().equals(before), "stale page cannot remove a visible player");
        owner.open = owner.bottom; profiles.cached.put(c.id, HeadTestSupport.skin(4)); f.visiblePlayersMenu.refreshActive(owner.id);
        check(HeadTestSupport.skin(current.getItem(slot)).equals(HeadTestSupport.skin(3)), "closed inventory never receives later skin results");
        owner.permitted = false; int reads = profiles.cacheReads.size(); f.visiblePlayersMenu.open(owner.player, 0);
        check(owner.open == owner.bottom && profiles.cacheReads.size() == reads, "permission failure does not render any heads"); owner.permitted = true;
        owner.locale = Locale.GERMANY; f.visiblePlayersMenu.open(owner.player, 0);
        check(owner.open.getItem(slot).getItemMeta().lore().stream().map(VisiblePlayerHeadsHarness::plain).anyMatch("Rechtsklick zum Entfernen."::equals), "German removal lore unaffected");
        var top = owner.open; var item = top.getItem(slot); var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(CachedPlayerHeads.PLAYER_ID, PersistentDataType.STRING, c.id.toString()); item.setItemMeta(meta);
        var foreign = f.player("Foreign", true); f.visibleClick(foreign, top, slot, ClickType.RIGHT);
        check(f.settings.getLobbyAddedVisiblePlayers(owner.id).orElseThrow().equals(before), "foreign viewer cannot use head metadata to change owner's visibility");
        f.visibleClick(owner, top, slot, ClickType.RIGHT);
        var remaining = f.settings.getLobbyAddedVisiblePlayers(owner.id).orElseThrow();
        check(!remaining.contains(a.id) && remaining.contains(c.id), "removal uses holder UUID despite forged profile metadata");
        check(profiles.forbidden == 0, "added-player heads perform no downloads or profile mutation");
        System.out.println("VisiblePlayerHeadsHarness passed " + checks + " checks.");
    }
    private static String plain(net.kyori.adventure.text.Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}

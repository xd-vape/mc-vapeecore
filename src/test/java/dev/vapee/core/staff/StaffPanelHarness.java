package dev.vapee.core.staff;

import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.staff.StaffHierarchyConfig;
import dev.vapee.core.ui.HeadTestSupport;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import static dev.vapee.core.staff.StaffPanelTestSupport.*;

public final class StaffPanelHarness {
    private static int checks;
    public static void main(String[] args) {
        accessAndRoster(); layoutAndSkins(); actions(); security(); lifecycleAndPages();
        System.out.println("StaffPanelHarness passed " + checks + " checks.");
    }
    private static void accessAndRoster() {
        Fixture f = new Fixture(); Probe admin = f.admin(), user = f.player("User", "default"), builder = f.player("Builder", "builder");
        f.open(user); check(user.opens == 0 && user.text().contains("You do not have permission to use this command."), "ordinary player denied at executor boundary");
        user.permissions.add(StaffPanel.PERMISSION); user.op = true;
        f.open(user); check(user.opens == 0, "panel node and OP do not turn a nonstaff primary group into staff");
        f.ranks.put(user.id, new RankInfo("owner", "Owner", Optional.empty(), Optional.of(NamedTextColor.RED), OptionalInt.of(Integer.MAX_VALUE)));
        f.open(user); check(user.opens == 0, "display rank/color/weight is never staff eligibility");
        f.open(builder); check(builder.opens == 0, "eligible builder still needs explicit panel node");
        check(f.panel.canOpen(admin.player), "eligible primary group plus node permits panel");
        f.groups.put(user.id, null); check(!f.panel.canOpen(user.player), "unavailable loaded primary group fails closed");
        f.groups.put(user.id, "guide"); f.config.set(new StaffHierarchyConfig(List.of("guide", "admin")));
        check(f.panel.canOpen(user.player) && !f.hierarchy.isStaff(builder.id), "eligibility follows current configured staff groups without hardcoded additions");
        f.config.set(StaffHierarchyConfig.defaults()); f.groups.put(user.id, "default");
        f.list(user); check(user.text().contains("Admin") && user.text().contains("Builder") && !user.text().contains("Owner User"), "public list requires no private permission and only eligible staff");
        f.open(admin); var beforeGroups = new HashMap<>(f.groups); var beforePermissions = Set.copyOf(admin.permissions);
        var beforeRanks = new HashMap<>(f.ranks);
        f.click(admin, StaffPanel.ROSTER_LAYOUT.slot(StaffPanel.Action.STATUS));
        check(f.panel.isPublic(admin.id), "visibility does not mutate inside click handler"); f.drain();
        check(!f.panel.isPublic(admin.id) && f.groups.equals(beforeGroups) && admin.permissions.equals(beforePermissions), "HIDDEN changes presentation only");
        check(f.ranks.equals(beforeRanks) && f.players.values().stream().allMatch(p -> p.appearanceWrites == 0),
                "HIDDEN changes neither rank metadata nor chat/display/tablist/scoreboard appearance");
        f.list(user); check(!user.text().contains("Admin") && user.text().contains("Builder"), "HIDDEN omitted from public roster without leaking hidden counts");
        builder.permissions.add(StaffPanel.PERMISSION); f.open(builder);
        check(builder.holder().content().targets().containsValue(admin.player), "private authorized roster still includes hidden staff");
        builder.invisible.add(admin.id); f.open(builder);
        check(!builder.holder().content().targets().containsValue(admin.player), "private panel respects external canSee boundary");
        user.invisible.add(builder.id); f.list(user); check(!user.text().contains("Builder"), "public roster respects existing visibility boundary");
        f.click(admin, StaffPanel.ROSTER_LAYOUT.slot(StaffPanel.Action.STATUS)); f.drain();
        check(f.panel.isPublic(admin.id) && admin.opReads == 0 && user.opReads == 0, "PUBLIC restores roster without consulting OP status");
        check(f.ranks.equals(beforeRanks) && f.players.values().stream().allMatch(p -> p.appearanceWrites == 0),
                "PUBLIC restoration does not rewrite staff appearance");
        check(f.panel.onTabComplete(user.player, null, "staff", new String[]{""}).equals(List.of("list")), "public completion exposes no staff names or actions");
        f.panel.disable();
    }
    private static void layoutAndSkins() {
        Fixture f = new Fixture(); Probe admin = f.admin(), member = f.player("Literal<red>", "builder");
        f.profiles.online.remove(member.id); f.profiles.cached.put(member.id, HeadTestSupport.skin(2));
        f.open(admin);
        check(StaffPanel.ROSTER_LAYOUT.size() == 27 && StaffPanel.ROSTER_LAYOUT.contentSlots().equals(java.util.stream.IntStream.range(0, 18).boxed().toList()), "27 slots, two fixed content rows");
        check(StaffPanel.ROSTER_LAYOUT.slot(StaffPanel.Action.STATUS) == 22 && StaffPanel.ROSTER_LAYOUT.slot(StaffPanel.Action.CLOSE) == 24
                && StaffPanel.ROSTER_LAYOUT.slot(StaffPanel.Action.MODE) == 19 && StaffPanel.ROSTER_LAYOUT.slot(StaffPanel.Action.REFRESH) == 20, "explicit bottom controls stay separate");
        for (var e : admin.holder().content().targets().entrySet()) {
            ItemStack item = admin.view.getTopInventory().getItem(e.getKey());
            check(HeadTestSupport.owner(item).equals(e.getValue().getUniqueId()) && HeadTestSupport.profile(item).getId().equals(e.getValue().getUniqueId()), "actual profile identity bound to authoritative entry UUID");
            check(item.getItemMeta().displayName().color().equals(NamedTextColor.GOLD), "existing rank name color retained");
        }
        int memberSlot = admin.holder().content().targets().entrySet().stream().filter(e -> e.getValue() == member.player).findFirst().orElseThrow().getKey();
        check(HeadTestSupport.skin(admin.view.getTopInventory().getItem(memberSlot)).equals(HeadTestSupport.skin(2)), "cached skin is used safely when online texture missing");
        check(plain(admin.view.getTopInventory().getItem(memberSlot).getItemMeta().displayName()).equals("Literal<red>"), "literal username is not MiniMessage");
        f.profiles.cached.remove(member.id); f.open(admin);
        ItemStack fallback = admin.view.getTopInventory().getItem(memberSlot);
        check(HeadTestSupport.profile(fallback) == null && HeadTestSupport.owner(fallback).equals(member.id), "missing skin is normal UUID-tagged head fallback");
        f.select(admin, member);
        check(StaffPanel.TARGET_LAYOUT.slot(StaffPanel.Action.TELEPORT) == 10 && StaffPanel.TARGET_LAYOUT.slot(StaffPanel.Action.HISTORY) == 11
                && StaffPanel.TARGET_LAYOUT.slot(StaffPanel.Action.WARN) == 12 && StaffPanel.TARGET_LAYOUT.slot(StaffPanel.Action.MUTE) == 14
                && StaffPanel.TARGET_LAYOUT.slot(StaffPanel.Action.KICK) == 15, "moderation action positions fixed, never packed into gaps");
        check(admin.holder().content().actions().keySet().containsAll(List.of(10, 11, 12, 14, 15, 18, 22, 26)), "allowed actions and bottom navigation rendered at their exact positions");
        admin.permissions.remove("vapeecore.moderation.warn"); f.open(admin); f.select(admin, member);
        check(admin.view.getTopInventory().getItem(12) == null && admin.view.getTopInventory().getItem(14) != null, "missing capability leaves its fixed slot empty");
        f.panel.startUpdates(); Inventory same = admin.view.getTopInventory(); admin.locale = Locale.GERMANY;
        f.messages.localization().localeChanged(admin.player, Locale.GERMANY);
        check(admin.view.getTopInventory() == same && plain(same.getItem(14).getItemMeta().displayName()).equals("Spieler stummschalten"), "localized items update within exact same owned inventory");
        check(f.profiles.forbidden == 0, "head rendering has no blocking profile/network calls"); f.panel.disable();
    }
    private static void actions() {
        for (StaffPanel.Action action : List.of(StaffPanel.Action.TELEPORT, StaffPanel.Action.HISTORY, StaffPanel.Action.WARN, StaffPanel.Action.MUTE, StaffPanel.Action.KICK)) {
            Fixture f = new Fixture(); Probe admin = f.admin(), target = f.player("Target", "default"); f.open(admin); f.select(admin, target);
            check(admin.holder().content().selected() == target.player, "supported nonstaff target selected through player filter");
            int closes = admin.closes; f.click(admin, StaffPanel.TARGET_LAYOUT.slot(action));
            check(f.executed.isEmpty() && admin.closes == closes, "no execution or inventory transition during click"); f.drain();
            if (action == StaffPanel.Action.TELEPORT || action == StaffPanel.Action.HISTORY) {
                check(f.executed.size() == 1 && f.executed.getFirst().action() == action && f.executed.getFirst().target() == target.player, "authorized existing command request uses exact target session");
            } else {
                var clicks = admin.messages.stream().flatMap(c -> clicks(c).stream()).toList();
                check(f.executed.isEmpty() && clicks.size() == 1 && clicks.getFirst().action() == ClickEvent.Action.SUGGEST_COMMAND
                        && clicks.getFirst().value().equals("/" + action.name().toLowerCase(Locale.ROOT) + " " + target.id + " "), "text action only suggests validated UUID command, never console or auto-execution");
            }
            check(!(admin.view.getTopInventory().getHolder() instanceof StaffPanelHolder), "completed action releases owned GUI"); f.panel.disable();
        }
        Fixture f = new Fixture(); Probe admin = f.admin(), target = f.player("Target", "default");
        f.open(admin); f.select(admin, target); f.click(admin, 10); admin.permissions.remove("vapeecore.utility.teleport"); f.drain();
        check(f.executed.isEmpty() && admin.view.getTopInventory().getItem(10) == null, "capability revoke between click and task denies and hides action");
        admin.permissions.add("vapeecore.utility.teleport"); f.open(admin); f.select(admin, target); f.click(admin, 11);
        f.groups.put(target.id, "owner"); f.drain(); check(f.executed.isEmpty(), "promoted target's current hierarchy denies pending action");
        f.click(admin, 10); f.drain();
        check(f.executed.size() == 1 && f.executed.getFirst().action() == StaffPanel.Action.TELEPORT,
                "teleport retains existing self-source policy even for a protected destination"); f.executed.clear();
        f.groups.put(target.id, "default"); f.open(admin); f.select(admin, target); f.click(admin, 11);
        admin.afterClose = () -> admin.permissions.remove("vapeecore.moderation.history"); f.drain();
        check(f.executed.isEmpty(), "close hook cannot bypass final execution authority recheck"); admin.afterClose = () -> { };
        f.open(admin); f.select(admin, admin);
        check(admin.holder().content().actions().keySet().stream().noneMatch(Set.of(10, 12, 14, 15)::contains), "self cannot receive teleport or punitive actions");
        f.panel.disable();
    }
    private static void security() {
        Fixture f = new Fixture(); Probe admin = f.admin(), target = f.player("Builder", "builder"), foreign = f.player("Foreign", "default"); f.open(admin);
        int opens = admin.opens;
        for (ClickType type : ClickType.values()) if (type != ClickType.LEFT) {
            var event = f.click(admin, 22, type, InventoryAction.PICKUP_ALL);
            check(event.isCancelled() && f.queued.isEmpty() && f.panel.isPublic(admin.id), "unsupported click cancelled without action " + type);
        }
        for (InventoryAction action : InventoryAction.values()) if (action != InventoryAction.PICKUP_ALL && action != InventoryAction.NOTHING) {
            var event = f.click(admin, 22, ClickType.LEFT, action);
            check(event.isCancelled() && f.queued.isEmpty(), "unsupported inventory action " + action);
        }
        for (int slot : List.of(-999, 27, 30, 62)) check(f.click(admin, slot).isCancelled() && f.queued.isEmpty(), "bottom/outside route denied " + slot);
        var drag = new InventoryDragEvent(admin.view, null, item(), false, Map.of(0, item(), 28, item()));
        f.panel.onDrag(drag); check(drag.isCancelled() && f.queued.isEmpty(), "mixed top/bottom drag cannot act");
        var creative = new InventoryCreativeEvent(admin.view, InventoryType.SlotType.CONTAINER, 22, item());
        f.panel.onClick(creative); check(creative.isCancelled() && f.queued.isEmpty(), "Creative cannot inject or activate status");
        var swap = new PlayerSwapHandItemsEvent(admin.player, item(), item()); f.panel.onSwap(swap); check(swap.isCancelled(), "offhand event denied");
        var drop = new PlayerDropItemEvent(admin.player, proxy(org.bukkit.entity.Item.class, (m, a) -> null)); f.panel.onDrop(drop); check(drop.isCancelled(), "drop event denied");
        var wrongViewer = new InventoryClickEvent(foreign.view(admin.view.getTopInventory()), InventoryType.SlotType.CONTAINER, 22, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        f.panel.onClick(wrongViewer); check(wrongViewer.isCancelled() && f.queued.isEmpty(), "foreign viewer cannot use legitimate holder/inventory");
        var forgedHolder = new StaffPanelHolder(admin.id); var forged = new Stock(forgedHolder, 27, Component.empty());
        var forgedClick = new InventoryClickEvent(admin.view(forged.inventory), InventoryType.SlotType.CONTAINER, 22, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        f.panel.onClick(forgedClick); check(forgedClick.isCancelled() && f.queued.isEmpty() && admin.opens == opens, "forged unbound holder cannot claim active inventory");
        int targetSlot = admin.holder().content().targets().entrySet().stream().filter(e -> e.getValue() == target.player).findFirst().orElseThrow().getKey();
        admin.view.getTopInventory().setItem(targetSlot, f.profiles.heads.apply(foreign.id, item()));
        f.click(admin, targetSlot); f.drain();
        check(admin.holder().content().selected() == target.player && f.executed.isEmpty(), "forged item profile/PDC cannot change holder's authoritative target");
        f.open(admin);
        var cancelled = new InventoryClickEvent(admin.view, InventoryType.SlotType.CONTAINER, 22, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        cancelled.setCancelled(true); f.panel.onClick(cancelled); check(f.queued.isEmpty(), "already-cancelled interaction cannot trigger control");
        f.click(admin, 22); f.click(admin, 22); check(f.queued.size() == 1, "duplicate click produces one deferred action");
        admin.permissions.remove(StaffPanel.PERMISSION); f.drain();
        check(f.panel.isPublic(admin.id) && !(admin.view.getTopInventory().getHolder() instanceof StaffPanelHolder), "panel permission revoke blocks queued toggle and closes owned view");
        admin.permissions.add(StaffPanel.PERMISSION); f.open(admin); f.click(admin, 22); f.groups.put(admin.id, "default"); f.drain();
        check(f.panel.isPublic(admin.id), "primary-role revocation while panel open cannot toggle status"); f.groups.put(admin.id, "admin");
        f.open(admin); admin.permissionContext = false; f.click(admin, 22); f.drain();
        check(f.panel.isPublic(admin.id) && !(admin.view.getTopInventory().getHolder() instanceof StaffPanelHolder), "effective context loss denies retained panel without reconnect");
        admin.permissionContext = true;
        f.open(admin); f.select(admin, target); f.click(admin, 11); target.online = false; f.players.remove(target.id); f.drain();
        check(f.executed.isEmpty(), "offline target cannot receive queued action");
        target.online = true; f.players.put(target.id, target); f.open(admin); f.select(admin, target); f.click(admin, 11);
        f.player(target.id, "Rejoined", "default"); f.drain();
        check(f.executed.isEmpty(), "target reconnect with same UUID cannot inherit old target session");
        f.panel.disable();
    }
    private static void lifecycleAndPages() {
        Fixture f = new Fixture(); Probe admin = f.admin(), target = f.player("Builder", "builder");
        for (int i = 0; i < 25; i++) f.player("Staff" + String.format(Locale.ROOT, "%02d", i), "builder");
        f.open(admin); Inventory old = admin.view.getTopInventory(); f.click(admin, 26);
        check(admin.view.getTopInventory() == old, "page transition deferred"); f.drain();
        check(admin.holder().content().page() == 1 && admin.view.getTopInventory() != old, "next page acquires distinct inventory identity");
        var staleClick = new InventoryClickEvent(admin.view(old), InventoryType.SlotType.CONTAINER, 22, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        f.panel.onClick(staleClick); check(staleClick.isCancelled() && f.queued.isEmpty(), "stale page click cannot act on current page");
        f.panel.onClose(new InventoryCloseEvent(admin.view(old))); f.click(admin, 22); f.drain();
        check(!f.panel.isPublic(admin.id), "stale close cannot discard current page ownership");
        Inventory current = admin.view.getTopInventory(); f.click(admin, 22);
        var unrelated = new Stock(null, 27, Component.empty()).inventory; admin.replace(unrelated); f.drain();
        check(admin.view.getTopInventory() == unrelated && !f.panel.isPublic(admin.id), "queued action cannot touch replacement inventory");
        f.open(admin); f.click(admin, 22); f.panel.onQuit(new PlayerQuitEvent(admin.player, (Component) null));
        admin.online = false; f.players.remove(admin.id);
        Probe reconnect = f.player(admin.id, admin.name, "admin", StaffPanel.PERMISSION); f.panel.onJoin(new PlayerJoinEvent(reconnect.player, Component.empty())); f.open(reconnect); f.drain();
        check(f.panel.isPublic(reconnect.id) && reconnect.holder() != null, "reconnect resets session-only status and invalidates old queued action");
        f.panel.onCommand(admin.player, null, "staff", new String[0]);
        check(reconnect.view.getTopInventory().getHolder() instanceof StaffPanelHolder, "old viewer session cannot close reconnect's panel through denied command");
        f.click(reconnect, 22); f.drain(); f.panel.onQuit(new PlayerQuitEvent(admin.player, (Component) null)); f.drain();
        check(!f.panel.isPublic(reconnect.id), "stale quit cannot reset reconnect's hidden status");
        f.click(reconnect, 22); f.panel.startUpdates(); f.messages.localization().localeChanged(reconnect.player, Locale.GERMANY); f.drain();
        check(!f.panel.isPublic(reconnect.id), "rerender invalidates previously queued content revision");
        Inventory beforeShutdown = reconnect.view.getTopInventory(); f.click(reconnect, 22); f.panel.disable(); f.drain();
        check(f.panel.isPublic(reconnect.id) && reconnect.view.getTopInventory() != beforeShutdown, "disable closes owned view, clears status and fences pending work");
        reconnect.replace(unrelated); f.panel.disable(); check(reconnect.view.getTopInventory() == unrelated, "repeat disable does not touch unrelated inventory");
        f.createPanel(() -> true); f.open(reconnect); check(reconnect.view.getTopInventory().getHolder() instanceof StaffPanelHolder, "fresh runtime re-enables panel"); f.panel.disable();
        Fixture cancelledOpen = new Fixture(); Probe p = cancelledOpen.admin(); p.cancelOpen = true; cancelledOpen.open(p);
        check(!(p.view.getTopInventory().getHolder() instanceof StaffPanelHolder) && cancelledOpen.queued.isEmpty(), "cancelled open never publishes owned view"); cancelledOpen.panel.disable();
        Fixture revokedOpen = new Fixture(); Probe revoked = revokedOpen.admin(); revoked.afterOpen = () -> revoked.permissions.remove(StaffPanel.PERMISSION);
        revokedOpen.open(revoked); check(!(revoked.view.getTopInventory().getHolder() instanceof StaffPanelHolder), "permission revoked by open hook closes unpublishable view"); revokedOpen.panel.disable();
        Fixture replacedOpen = new Fixture(); Probe replaced = replacedOpen.admin(); Inventory other = new Stock(null, 27, Component.empty()).inventory;
        replaced.afterOpen = () -> replaced.replace(other); replacedOpen.open(replaced);
        check(replaced.view.getTopInventory() == other && replaced.closes == 0, "open-hook replacement is neither adopted nor closed"); replacedOpen.panel.disable();
    }
    private static List<ClickEvent> clicks(Component c) { List<ClickEvent> result = new ArrayList<>(); if (c.clickEvent() != null) result.add(c.clickEvent()); for (Component child : c.children()) result.addAll(clicks(child)); return result; }
    private static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private static ItemStack item() { return HeadTestSupport.item(new dev.vapee.core.ui.UiItemSpec(org.bukkit.Material.STONE, Component.empty(), List.of())); }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}

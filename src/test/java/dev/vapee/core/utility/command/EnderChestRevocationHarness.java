package dev.vapee.core.utility.command;

import dev.vapee.core.command.StaffTargetTestFixture;
import dev.vapee.core.utility.UtilityService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

/** Exercises the issued foreign-view boundary, rather than a copied permission predicate. */
public final class EnderChestRevocationHarness {
    private static int checks;
    public static void main(String[] args) {
        routes(); hierarchy(); identityAndLifecycle();
        System.out.println("EnderChestRevocationHarness passed " + checks + " checks.");
    }

    private static void routes() {
        Fixture f = new Fixture();
        f.viewer.others = false; f.open();
        check(f.viewer.view == null, "base permission cannot issue a foreign view");
        f.viewer.others = true;
        for (ClickType click : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
                ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND,
                ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.WINDOW_BORDER_LEFT)) {
            for (int slot : List.of(0, 27, 53, -999)) {
                f.viewer.others = true; f.open();
                check(f.viewer.view.getTopInventory() == f.owner.chest, "issued view binds live target inventory");
                var allowed = f.click(click, slot); f.command.onInventoryClick(allowed);
                check(!allowed.isCancelled(), "authorized native click route remains usable " + click + "/" + slot);
                f.viewer.others = false;
                var denied = f.click(click, slot); f.command.onInventoryClick(denied);
                check(denied.isCancelled(), "permission revoked: route denied " + click + "/" + slot);
                var repeated = f.click(click, slot); f.command.onInventoryClick(repeated);
                check(repeated.isCancelled() && f.queued.size() == 1 && f.viewer.closes == 0,
                        "repeated same-tick packet denied; no inline close or duplicate task");
                f.viewer.others = true;
                var regrant = f.click(click, slot); f.command.onInventoryClick(regrant);
                check(regrant.isCancelled(), "revoked lease cannot be revived by a regrant before close");
                f.drain(); check(f.viewer.view == null && f.viewer.closes == 1, "owned view released next tick");
                f.viewer.closes = 0;
            }
        }
        f.viewer.others = true; f.open();
        var allowed = f.drag(); f.command.onInventoryDrag(allowed);
        check(!allowed.isCancelled(), "authorized drag remains usable");
        f.viewer.others = false; var denied = f.drag(); f.command.onInventoryDrag(denied);
        check(denied.isCancelled(), "revoked top/bottom drag denied");
        f.drain(); check(f.viewer.view == null, "drag revocation closes owned view");
        f.viewer.others = true; f.open(); f.viewer.context = false;
        var contextual = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(contextual);
        check(contextual.isCancelled(), "effective permission context rechecked without reconnect"); f.drain();
        f.command.disable();
    }

    private static void hierarchy() {
        Fixture f = new Fixture(); f.open();
        f.staff.groups.put(f.owner.id, "owner");
        var denied = f.click(ClickType.SHIFT_LEFT, 27); f.command.onInventoryClick(denied);
        check(denied.isCancelled() && f.viewer.others, "current higher target hierarchy denies retained view despite node");
        f.drain(); check(f.viewer.view == null, "hierarchy revocation releases retained view");
        f.staff.groups.clear(); f.open(); f.staff.groups.put(f.owner.id, null);
        var unavailable = f.click(ClickType.NUMBER_KEY, 0); f.command.onInventoryClick(unavailable);
        check(unavailable.isCancelled(), "unavailable loaded hierarchy fails closed"); f.drain();
        f.staff.groups.clear(); f.open(); f.staff.fail = true;
        var failure = f.drag(); f.command.onInventoryDrag(failure);
        check(failure.isCancelled(), "hierarchy lookup failure denies drag"); f.drain();
        f.command.disable();
    }

    private static void identityAndLifecycle() {
        Fixture f = new Fixture(); f.open(); InventoryView old = f.viewer.view;
        f.viewer.others = false; f.command.onInventoryClick(f.click(ClickType.LEFT, 0));
        Inventory unrelated = inventory(); f.viewer.open(unrelated); f.drain();
        check(f.viewer.view.getTopInventory() == unrelated && f.viewer.closes == 0, "deferred cleanup does not close replacement inventory");
        var foreign = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(foreign);
        check(!foreign.isCancelled(), "unrelated plugin inventory untouched");
        f.viewer.open(f.viewer.chest);
        var personal = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(personal);
        check(!personal.isCancelled(), "ordinary personal Enderchest remains unguarded by foreign admin feature");
        f.viewer.open(f.owner.chest);
        var unissued = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(unissued);
        check(!unissued.isCancelled(), "unissued other-plugin view not adopted");
        f.viewer.others = true; f.viewer.cancelOpen = true; f.open(); f.viewer.others = false;
        var cancelledOpen = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(cancelledOpen);
        check(!cancelledOpen.isCancelled(), "cancelled native open cannot adopt an existing foreign plugin view");
        f.viewer.cancelOpen = false;
        f.viewer.others = true; f.open(); InventoryView current = f.viewer.view;
        f.command.onInventoryClose(new InventoryCloseEvent(old));
        f.viewer.others = false; var stillOwned = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(stillOwned);
        check(stillOwned.isCancelled(), "stale close cannot discard newer lease"); f.drain();
        f.viewer.others = true; f.open();
        Probe impostor = new Probe(f.viewer.id); impostor.open(f.owner.chest);
        var forged = new InventoryClickEvent(impostor.view, InventoryType.SlotType.CONTAINER, 0,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        f.command.onInventoryClick(forged);
        check(!forged.isCancelled() && f.viewer.view != null, "same UUID foreign session not adopted or used to close real view");
        f.sessions.put(impostor.id, impostor.player);
        var replacedSession = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(replacedSession);
        check(replacedSession.isCancelled(), "old viewer session no longer current cannot transfer");
        f.command.onQuit(new PlayerQuitEvent(f.viewer.player, (net.kyori.adventure.text.Component) null));
        f.viewer.online = false; f.drain();
        check(impostor.view != null && impostor.closes == 0, "old quit/queued task cannot close reconnected player's view");
        f.viewer.online = true; f.sessions.put(f.viewer.id, f.viewer.player); f.open();
        f.command.onQuit(new PlayerQuitEvent(f.owner.player, (net.kyori.adventure.text.Component) null));
        f.owner.online = false;
        var quittingOwner = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(quittingOwner);
        check(quittingOwner.isCancelled(), "owner quit denies access before next-tick close"); f.drain();
        check(f.viewer.view == null, "owner quit releases owned foreign view");
        f.owner.online = true; f.open();
        Probe newOwner = new Probe(f.owner.id); f.sessions.put(newOwner.id, newOwner.player);
        var ownerReconnect = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(ownerReconnect);
        check(ownerReconnect.isCancelled(), "owner UUID reconnected as new session cannot retain old chest access"); f.drain();
        f.sessions.put(f.owner.id, f.owner.player); f.open();
        InventoryView closing = f.viewer.view; f.command.onInventoryClose(new InventoryCloseEvent(closing));
        f.viewer.others = false; var closed = f.click(ClickType.LEFT, 0); f.command.onInventoryClick(closed);
        check(!closed.isCancelled(), "closed lease no longer claims view ownership");
        f.viewer.others = true; f.open(); f.command.disable();
        check(f.viewer.view == null, "module disable releases issued foreign view");
        f.viewer.open(unrelated); f.command.disable(); f.drain();
        check(f.viewer.view.getTopInventory() == unrelated, "repeat disable and delayed task preserve unrelated inventory");
        Fixture reenabled = new Fixture(); reenabled.open();
        check(reenabled.viewer.view != null, "new module lifetime can issue views"); reenabled.command.disable();
        f.command.onCommand(f.viewer.player, null, "enderchest", new String[]{"Owner"});
        check(f.viewer.view.getTopInventory() == unrelated, "disabled command cannot issue further access");
    }

    private static final class Fixture {
        final Probe viewer = new Probe(UUID.randomUUID()), owner = new Probe(UUID.randomUUID());
        final Map<UUID, Player> sessions = new HashMap<>();
        final List<Runnable> queued = new ArrayList<>();
        final StaffTargetTestFixture staff = new StaffTargetTestFixture();
        final EnderChestCommand command;
        Fixture() {
            sessions.put(viewer.id, viewer.player); sessions.put(owner.id, owner.player);
            command = new EnderChestCommand(new UtilityService(() -> true, sessions::values, p -> 20),
                    name -> owner.player, sessions::values, (s, c) -> { }, staff.guard, queued::add);
        }
        void open() { command.onCommand(viewer.player, null, "enderchest", new String[]{"Owner"}); }
        InventoryClickEvent click(ClickType click, int slot) {
            return new InventoryClickEvent(viewer.view, InventoryType.SlotType.CONTAINER, slot, click,
                    click == ClickType.DOUBLE_CLICK ? InventoryAction.COLLECT_TO_CURSOR : InventoryAction.MOVE_TO_OTHER_INVENTORY, 1);
        }
        InventoryDragEvent drag() { return new InventoryDragEvent(viewer.view, null, new TestItem(), false, Map.of(0, new TestItem(), 27, new TestItem())); }
        void drain() { var copy = List.copyOf(queued); queued.clear(); copy.forEach(Runnable::run); }
    }
    private static final class Probe {
        final UUID id;
        boolean others = true, context = true, online = true, cancelOpen;
        int closes;
        InventoryView view;
        final Inventory chest = inventory();
        final PlayerInventory bottom = proxy(PlayerInventory.class, (m, a) -> null);
        final Player player;
        Probe(UUID id) {
            this.id = id;
            player = proxy(Player.class, (m, a) -> switch (m) {
                case "getUniqueId" -> id; case "getName" -> "Actor"; case "isOnline" -> online;
                case "hasPermission" -> EnderChestCommand.PERMISSION.equals(a[0]) || (others && context && EnderChestCommand.OTHERS_PERMISSION.equals(a[0]));
                case "getEnderChest" -> chest; case "getInventory" -> bottom; case "getOpenInventory" -> view;
                case "openInventory" -> { if (cancelOpen) yield null; open((Inventory) a[0]); yield view; }
                case "closeInventory" -> { closes++; view = null; yield null; }
                default -> null;
            });
        }
        void open(Inventory top) {
            view = proxy(InventoryView.class, (m, a) -> switch (m) {
                case "getPlayer" -> player; case "getTopInventory" -> top; case "getBottomInventory" -> bottom; default -> null;
            });
        }
    }
    private static Inventory inventory() { return proxy(Inventory.class, (m, a) -> m.equals("getSize") ? 27 : null); }
    private static final class TestItem extends ItemStack { TestItem() { super(); } }
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return switch (m.getName()) {
                case "hashCode" -> System.identityHashCode(p); case "equals" -> p == a[0]; default -> type.getSimpleName();
            };
            Object value = call.apply(m.getName(), a == null ? new Object[0] : a);
            if (value != null || !m.getReturnType().isPrimitive()) return value;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == double.class) return 0D;
            if (m.getReturnType() == float.class) return 0F;
            if (m.getReturnType() == long.class) return 0L;
            return 0;
        }));
    }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}

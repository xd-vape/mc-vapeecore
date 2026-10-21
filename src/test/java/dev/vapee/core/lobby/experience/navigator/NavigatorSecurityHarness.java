package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.ui.MenuTestLayouts;
import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.lobby.experience.LobbyExperienceModule;
import dev.vapee.core.lobby.player.LobbyPlayerMode;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.reload.ReloadParticipant;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.function.Consumer;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;

public final class NavigatorSecurityHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        eventProtection();
        accessRevalidation();
        staleDestinationsAndTeleport();
        lifecycleCleanup();
        System.out.println("NavigatorSecurityHarness passed " + checks + " checks.");
    }

    private static void eventProtection() throws Exception {
        var f = new NavigatorFixture();
        f.destinations(MenuTestLayouts.NAVIGATOR.pageSize() + 1);
        var owner = f.player();
        var other = f.player();
        f.menu.open(owner.player);
        Inventory active = owner.open;
        NavigatorInventoryHolder holder = (NavigatorInventoryHolder) active.getHolder();
        for (ClickType click : new ClickType[]{ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.MIDDLE, ClickType.DROP, ClickType.CONTROL_DROP,
                ClickType.SWAP_OFFHAND, ClickType.CREATIVE, ClickType.UNKNOWN}) {
            check(f.click(owner, active, MenuTestLayouts.NAVIGATOR.contentSlot(0), click).isCancelled() && owner.teleports == 0 && owner.open == active,
                    "non-menu click is inert: " + click);
        }
        for (int slot : new int[]{MenuTestLayouts.NAVIGATOR.size(), MenuTestLayouts.NAVIGATOR.size() + 6, -999, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PAGE_INFO), dev.vapee.core.ui.LayoutTestSupport.unused(MenuTestLayouts.NAVIGATOR)}) {
            check(f.click(owner, active, slot, ClickType.LEFT).isCancelled() && owner.teleports == 0,
                    "bottom/outside/info/unused click cancelled: " + slot);
        }
        check(f.click(owner, active, MenuTestLayouts.NAVIGATOR.size(), ClickType.SHIFT_LEFT).isCancelled(), "bottom shift transfer cancelled");
        check(f.click(other, active, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.RIGHT).isCancelled() && other.teleports == 0 && other.opens == 0,
                "foreign player cannot use owner's inventory");
        check(f.drag(owner, active, MenuTestLayouts.NAVIGATOR.contentSlot(0)).isCancelled(), "top drag cancelled");
        check(!f.drag(owner, active, MenuTestLayouts.NAVIGATOR.size()).isCancelled(), "active bottom-only drag preserves existing safe behavior");
        check(f.drag(other, active, MenuTestLayouts.NAVIGATOR.size()).isCancelled(), "foreign drag cancelled even bottom-only");
        var unboundHolder = new NavigatorInventoryHolder(owner.id, 0, java.util.Map.of(MenuTestLayouts.NAVIGATOR.contentSlot(0), "custom_000"), MenuTestLayouts.NAVIGATOR);
        Inventory unbound = inventory(unboundHolder, MenuTestLayouts.NAVIGATOR.size());
        check(f.click(owner, unbound, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.LEFT).isCancelled() && f.drag(owner, unbound, MenuTestLayouts.NAVIGATOR.size()).isCancelled(),
                "unbound holder cancelled without NPE");
        var forgedHolder = new NavigatorInventoryHolder(owner.id, 0, java.util.Map.of(MenuTestLayouts.NAVIGATOR.contentSlot(0), "custom_000"), MenuTestLayouts.NAVIGATOR);
        Inventory forged = inventory(forgedHolder, MenuTestLayouts.NAVIGATOR.size());
        forgedHolder.bindInventory(forged);
        owner.open = forged;
        check(f.click(owner, forged, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.LEFT).isCancelled() && owner.teleports == 0,
                "currently open forged inventory lacks active registration");
        Inventory mismatched = inventory(holder, MenuTestLayouts.NAVIGATOR.size());
        owner.open = mismatched;
        check(f.click(owner, mismatched, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.RIGHT).isCancelled() && owner.teleports == 0,
                "holder binding requires exact inventory instance");
        owner.open = owner.bottom;
        check(f.click(owner, active, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.LEFT).isCancelled() && owner.teleports == 0,
                "active registry alone does not authorize a closed inventory");
        owner.open = active;
        f.click(owner, active, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.NEXT), ClickType.LEFT);
        Inventory replacement = owner.open;
        check(replacement != active && ((NavigatorInventoryHolder) replacement.getHolder()).getPage() == 1,
                "valid next replaces page");
        for (int slot : new int[]{MenuTestLayouts.NAVIGATOR.contentSlot(0), MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.CLOSE), MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.NEXT)}) {
            check(f.click(owner, active, slot, ClickType.LEFT).isCancelled()
                            && owner.teleports == 0 && owner.open == replacement,
                    "old page cannot teleport, close or page: " + slot);
        }
        check(f.drag(owner, active, MenuTestLayouts.NAVIGATOR.size()).isCancelled(), "stale bottom drag cancelled");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(owner, active)));
        check(f.menu.isActive(owner.player, replacement, (NavigatorInventoryHolder) replacement.getHolder()),
                "old close event cannot forget current page");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(other, replacement)));
        check(f.menu.isActive(owner.player, replacement, (NavigatorInventoryHolder) replacement.getHolder()),
                "foreign close cannot forget owner inventory");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(owner, replacement)));
        check(!f.menu.isActive(owner.player, replacement, (NavigatorInventoryHolder) replacement.getHolder()),
                "exact active close invalidates state");
    }

    private static void accessRevalidation() throws Exception {
        var f = new NavigatorFixture();
        f.destinations(1);
        var player = f.player();
        List<Consumer<NavigatorFixture.TestPlayer>> deny = List.of(
                p -> p.online = false, p -> p.loaded = false, p -> p.currentWorld = f.outside,
                p -> p.mode = LobbyPlayerMode.BUILD, p -> p.participating = true);
        int gate = 0;
        for (Consumer<NavigatorFixture.TestPlayer> change : deny) {
            reset(player, f);
            check(f.access.canAccess(player.player), "all five valid gates allow navigator");
            change.accept(player);
            int opened = player.opens;
            check(!f.menu.open(player.player) && player.opens == opened, "invalid gate denies opening: " + gate);
            reset(player, f);
            f.menu.open(player.player);
            Inventory old = player.open;
            change.accept(player);
            check(f.click(player, old, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.RIGHT).isCancelled() && player.teleports == 0
                            && player.open == player.bottom,
                    "click-time gate denies and closes stale menu: " + gate);
            check(!f.menu.isActive(player.player, old, (NavigatorInventoryHolder) old.getHolder()),
                    "denial forgets active menu: " + gate);
            reset(player, f);
            f.menu.open(player.player);
            change.accept(player);
            check(f.drag(player, player.open, MenuTestLayouts.NAVIGATOR.size()).isCancelled() && player.open == player.bottom,
                    "drag revalidates state and closes stale menu: " + gate++);
        }
        reset(player, f);
        player.denyDuringOpen = true;
        check(!f.menu.open(player.player) && player.open == player.bottom,
                "state changed by open event cannot publish active menu");
        check(!f.access.canAccess(null), "null access fails closed");
    }

    private static void reset(NavigatorFixture.TestPlayer player, NavigatorFixture f) {
        player.online = true;
        player.loaded = true;
        player.currentWorld = f.lobby;
        player.mode = LobbyPlayerMode.NORMAL;
        player.participating = false;
    }

    private static void staleDestinationsAndTeleport() throws Exception {
        var f = new NavigatorFixture();
        f.destinations(MenuTestLayouts.NAVIGATOR.pageSize() + 1);
        var player = f.player();
        f.menu.open(player.player, 1);
        Inventory last = player.open;
        f.warps.setNavigatorVisible("custom_%03d".formatted(MenuTestLayouts.NAVIGATOR.pageSize()), false);
        f.click(player, last, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.LEFT);
        check(player.teleports == 0 && player.open != last
                        && ((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 0,
                "hidden stale destination does not teleport and refresh clamps last page");
        check(player.output().contains("That destination is no longer available."), "hidden controlled feedback");
        f.warps.setNavigatorVisible("custom_%03d".formatted(MenuTestLayouts.NAVIGATOR.pageSize()), true);
        f.menu.open(player.player, 1);
        last = player.open;
        f.warps.removeWarp("custom_%03d".formatted(MenuTestLayouts.NAVIGATOR.pageSize()));
        f.click(player, last, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.RIGHT);
        check(player.teleports == 0 && player.open != last
                        && ((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 0,
                "removed stale destination refreshes without teleport");
        f.menu.open(player.player);
        Inventory originalOrder = player.open;
        f.warps.setNavigatorOrder("custom_000", 100);
        f.warps.setWarp("custom_000", new Location(f.lobby, 321, 75, 1));
        player.teleportAccepted = false;
        f.click(player, originalOrder, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.LEFT);
        check(player.teleports == 1 && player.target.getX() == 321 && player.open == originalOrder,
                "reorder preserves slot ID and uses latest position; cancellation retains usable menu");
        check(player.output().contains("cancelled or failed") && player.fallDistance == null && player.velocity == null,
                "cancelled teleport has controlled error and no success resets");
        f.worlds.remove(f.lobby.getName());
        f.click(player, originalOrder, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.RIGHT);
        check(player.teleports == 1 && player.open == originalOrder && player.output().contains("world is not loaded"),
                "missing world is not loaded and menu remains usable");
        f.worlds.put(f.lobby.getName(), f.lobby);
        player.teleportAccepted = true;
        f.click(player, originalOrder, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.RIGHT);
        check(player.teleports == 2 && player.open == player.bottom
                        && player.cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                        && player.fallDistance == 0F && player.velocity.lengthSquared() == 0,
                "successful same-lobby teleport uses PLUGIN, resets movement and closes navigator");
        f.click(player, originalOrder, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.LEFT);
        check(player.teleports == 2, "repeated old event after success cannot duplicate teleport");
        f.warps.setWarp("custom_000", new Location(f.outside, 111, 80, 1));
        f.warps.setNavigatorOrder("custom_000", 0);
        f.menu.open(player.player);
        player.changeWorldOnTeleport = true;
        Inventory beforeLeave = player.open;
        f.click(player, beforeLeave, MenuTestLayouts.NAVIGATOR.contentSlot(0), ClickType.LEFT);
        check(player.teleports == 3 && player.currentWorld == f.outside && player.open == player.bottom
                        && !f.menu.isActive(player.player, beforeLeave, (NavigatorInventoryHolder) beforeLeave.getHolder()),
                "visible destination may leave lobby with world-event cleanup");
    }

    private static void lifecycleCleanup() throws Exception {
        var f = new NavigatorFixture();
        var owner = f.player();
        f.menu.open(owner.player);
        Inventory quit = owner.open;
        owner.open = owner.bottom;
        f.listener.onPlayerQuit(new PlayerQuitEvent(owner.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        owner.open = quit;
        check(!f.menu.isActive(owner.player, quit, (NavigatorInventoryHolder) quit.getHolder()),
                "quit forgets UUID even after view changed");
        f.menu.open(owner.player);
        Inventory leave = owner.open;
        owner.currentWorld = f.outside;
        f.listener.onPlayerChangedWorld(new PlayerChangedWorldEvent(owner.player, f.lobby));
        check(owner.open == owner.bottom && !f.menu.isActive(owner.player, leave, (NavigatorInventoryHolder) leave.getHolder()),
                "world leave closes and forgets navigator");
        int opened = owner.opens;
        owner.currentWorld = f.lobby;
        f.listener.onPlayerChangedWorld(new PlayerChangedWorldEvent(owner.player, f.outside));
        check(owner.opens == opened, "world entry never opens navigator");
        var other = f.player();
        f.menu.open(owner.player);
        f.menu.open(other.player);
        Inventory first = owner.open;
        Inventory second = other.open;
        owner.failClose = true;
        f.menu.closeOpenInventories();
        check(other.open == other.bottom && !f.logs.isEmpty(), "close failure isolated while other menu closes");
        check(!f.menu.isActive(owner.player, first, (NavigatorInventoryHolder) first.getHolder()),
                "failed close still releases active references");
        other.open = second;
        check(!f.menu.isActive(other.player, second, (NavigatorInventoryHolder) second.getHolder()),
                "cleanup empties entire registry");
        owner.failClose = false;
        f.menu.open(owner.player);
        Inventory unrelated = inventory(null, MenuTestLayouts.NAVIGATOR.size());
        owner.open = unrelated;
        int closed = owner.closes;
        f.menu.closeOpenInventories();
        check(owner.open == unrelated && owner.closes == closed, "cleanup preserves unrelated currently open inventory");
        check(CoreModule.class.isAssignableFrom(LobbyExperienceModule.class)
                        && !ReloadParticipant.class.isAssignableFrom(LobbyExperienceModule.class),
                "LobbyExperience remains existing non-reload module");
        check(java.util.Arrays.stream(LobbyExperienceModule.class.getConstructors()[0].getParameterTypes())
                        .anyMatch(type -> type == ActivityModule.class), "explicit activity dependency");
        check(dev.vapee.core.lobby.experience.LobbyExperienceLifecycleHarness.verifyLifecycle() > 0,
                "actual module rollback and disable release Navigator, callbacks and owned handlers");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

package dev.vapee.core.friend.gui;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.friend.FriendChangeListener;
import dev.vapee.core.friend.FriendMessages;
import dev.vapee.core.friend.FriendRelation;
import dev.vapee.core.module.ModuleRuntimeFixture;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Real rendered views, partial subscriptions and stale inventory authority. */
public final class FriendMenuLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (boolean localeFault : List.of(true, false)) partialStart(localeFault);
        independentShutdown();
        staleViews();
        removalBeforeEffect();
        System.out.println("FriendMenuLifecycleHarness passed " + checks + " checks.");
    }

    private static void partialStart(boolean localeFault) throws Exception {
        var f = new FriendMenuFixture(FriendMenu.DEFAULT_LAYOUT);
        var locale = new ModuleRuntimeFixture.FaultSet<Consumer<Player>>();
        var changes = new ModuleRuntimeFixture.FaultSet<FriendChangeListener>();
        set(f.messages.localization(), "localeListeners", locale);
        set(f.friends, "changeListeners", changes);
        var owner = f.player("Partial", true);
        f.menu.open(owner.player);
        if (localeFault) locale.failAdd = 1;
        else changes.failAdd = 1;
        RuntimeException original = localeFault ? locale.startupFailure : changes.startupFailure;
        check(failure(f.menu::startUpdates) == original, "partial start retains exact failure");
        check(
                locale.isEmpty() && changes.isEmpty(),
                "partial observer registrations removed without relying on flag");
        check(
                owner.open == owner.bottom && f.menu.activeCount() == 0,
                "failed start closes rendered owned view");
        check(
                get(f.menu, "rerenderInventory") == null && get(f.menu, "renderingFor") == null,
                "failed start releases temporary render state");
        locale.failAdd = 0;
        changes.failAdd = 0;
        f.menu.startUpdates();
        f.menu.startUpdates();
        check(
                locale.size() == 1
                        && changes.size() == 1
                        && changes.additions == (localeFault ? 1 : 2),
                "corrected start and repeated start install only one observer per source");
        f.menu.shutdown();
        check(locale.isEmpty() && changes.isEmpty(), "retry shuts down cleanly");
    }

    private static void independentShutdown() throws Exception {
        var f = new FriendMenuFixture(FriendMenu.DEFAULT_LAYOUT);
        var locale = new ModuleRuntimeFixture.FaultSet<Consumer<Player>>();
        var changes = new ModuleRuntimeFixture.FaultSet<FriendChangeListener>();
        set(f.messages.localization(), "localeListeners", locale);
        set(f.friends, "changeListeners", changes);
        f.menu.startUpdates();
        var first = f.player("First", true);
        var second = f.player("Second", true);
        var foreign = f.player("Foreign", true);
        f.menu.open(first.player);
        f.menu.open(second.player);
        f.menu.open(foreign.player);
        foreign.open = foreign.bottom;
        locale.failRemove = 1;
        changes.failRemove = 1;
        RuntimeException failure = failure(f.menu::shutdown);
        check(
                failure.getCause() == locale.cleanupFailure
                        && failure.getSuppressed().length == 1
                        && failure.getSuppressed()[0].getCause() == changes.cleanupFailure,
                "both unsubscribe failures visible independently");
        check(
                locale.isEmpty() && changes.isEmpty(),
                "after-effect faults leave neither observer installed");
        check(
                first.closeCalls == 1 && second.closeCalls == 1 && foreign.closeCalls == 0,
                "observer failures cannot prevent owned view closure or close foreign view");
        check(f.menu.activeCount() == 0, "all view bindings detached despite failures");
        f.menu.shutdown();
        check(
                first.closeCalls == 1 && second.closeCalls == 1,
                "repeat shutdown is quiet and idempotent");
    }

    private static void staleViews() throws Exception {
        var f = new FriendMenuFixture(FriendMenu.DEFAULT_LAYOUT);
        var owner = f.player("Owner", true);
        var sender = f.player("Sender", true);
        f.friends.sendRequest(sender.id, owner.id);
        f.menu.startUpdates();
        f.menu.open(owner.player, FriendMenuView.INCOMING, 0);
        var old = owner.open;
        var holder = FriendMenuFixture.holder(old);
        int target = f.menu.layout().contentSlot(0);
        f.menu.shutdown();
        owner.open = old;
        int writes = f.repository.saves;
        var stale = f.click(owner, old, target, ClickType.LEFT);
        check(
                stale.isCancelled()
                        && f.repository.saves == writes
                        && f.friends.getRelation(owner.id, sender.id)
                                == FriendRelation.INCOMING_REQUEST,
                "old runtime view cannot mutate friendship after shutdown");

        var fresh =
                new FriendMenu(
                        f.friends,
                        f.identities,
                        f.messages,
                        id -> f.players.containsKey(id) ? f.players.get(id).player : null,
                        f::inventory,
                        FriendMenuFixture.SpecItem::new,
                        Logger.getAnonymousLogger());
        var listener =
                new FriendMenuListener(
                        fresh,
                        f.friends,
                        new FriendMessages(f.messages, id -> null),
                        f.messages,
                        Logger.getAnonymousLogger());
        fresh.startUpdates();
        check(
                fresh.open(owner.player, FriendMenuView.INCOMING, 0),
                "fresh runtime opens same authorized view");
        var current = owner.open;
        var forged =
                f.inventory(
                        holder, old.getSize(), net.kyori.adventure.text.Component.text("Forged"));
        owner.open = forged;
        click(listener, f, owner, forged, target);
        check(f.repository.saves == writes, "forged inventory with copied holder has no authority");
        owner.open = old;
        click(listener, f, owner, old, target);
        check(f.repository.saves == writes, "old holder cannot regain authority in fresh runtime");
        owner.open = current;
        owner.permitted = false;
        click(listener, f, owner, current, target);
        check(
                f.repository.saves == writes && owner.open == owner.bottom,
                "current permission still enforced");
        owner.permitted = true;
        fresh.open(owner.player, FriendMenuView.INCOMING, 0);
        click(listener, f, owner, owner.open, fresh.layout().contentSlot(0));
        check(
                f.repository.saves == writes + 1
                        && f.friends.getRelation(owner.id, sender.id) == FriendRelation.FRIENDS,
                "fresh authorized UUID target still commits exactly once");
        fresh.shutdown();
        check(
                ((Set<?>) get(f.messages.localization(), "localeListeners")).isEmpty()
                        && ((Set<?>) get(f.friends, "changeListeners")).isEmpty(),
                "only new runtime observers existed and are released");
    }

    private static void removalBeforeEffect() throws Exception {
        var f = new FriendMenuFixture(FriendMenu.DEFAULT_LAYOUT);
        var blocked =
                new java.util.LinkedHashSet<Consumer<Player>>() {
                    boolean fail = true;

                    @Override
                    public boolean remove(Object listener) {
                        if (fail) {
                            fail = false;
                            throw new IllegalStateException(
                                    "external removal refused before effect");
                        }
                        return super.remove(listener);
                    }
                };
        set(f.messages.localization(), "localeListeners", blocked);
        f.menu.startUpdates();
        var owner = f.player("Blocked", true);
        f.menu.open(owner.player);
        check(
                failure(f.menu::shutdown).getMessage().contains("locale observer"),
                "real residual subscription is reported as incomplete cleanup");
        check(
                blocked.size() == 1
                        && ((Set<?>) get(f.friends, "changeListeners")).isEmpty()
                        && f.menu.activeCount() == 0
                        && owner.open == owner.bottom,
                "refused locale removal cannot prevent other independent cleanup");
        f.menu.shutdown();
        check(blocked.isEmpty(), "retry can remove previously refused subscription");
    }

    private static void click(
            FriendMenuListener listener,
            FriendMenuFixture f,
            FriendMenuFixture.TestPlayer owner,
            org.bukkit.inventory.Inventory inventory,
            int slot) {
        var event =
                new InventoryClickEvent(
                        f.view(owner, inventory),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(event);
        check(event.isCancelled(), "owned, forged and stale GUI interactions always cancelled");
    }

    private static RuntimeException failure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            return exception;
        }
        throw new AssertionError("expected observer lifecycle failure");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}

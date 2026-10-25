package dev.vapee.core.clan.gui;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.clan.ClanChangeListener;
import dev.vapee.core.clan.ClanLimits;
import dev.vapee.core.clan.ClanMessages;
import dev.vapee.core.clan.ClanRepositoryException;
import dev.vapee.core.clan.ClanResult;
import dev.vapee.core.module.ModuleRuntimeFixture;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/** Real rendered Clan views and actual observer collections; cleanup is always production code. */
public final class ClanMenuLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (boolean locale : List.of(true, false)) {
            for (boolean before : List.of(true, false)) partialStart(locale, before);
            refusedRemoval(locale);
        }
        independentShutdown();
        staleViews();
        committedRefreshAndContexts();
        System.out.println("ClanMenuLifecycleHarness passed " + checks + " checks.");
    }

    private static void partialStart(boolean localeFault, boolean before) throws Exception {
        var f = new ClanMenuFixture(ClanLimits.defaults(), ClanMenu.DEFAULT_LAYOUT);
        var refusedLocale = new RefusingSet<Consumer<Player>>();
        var refusedChanges = new RefusingSet<ClanChangeListener>();
        var afterLocale = new ModuleRuntimeFixture.FaultSet<Consumer<Player>>();
        var afterChanges = new ModuleRuntimeFixture.FaultSet<ClanChangeListener>();
        Set<Consumer<Player>> locale = before ? refusedLocale : afterLocale;
        Set<ClanChangeListener> changes = before ? refusedChanges : afterChanges;
        set(f.messages.localization(), "localeListeners", locale);
        set(f.clans, "changeListeners", changes);
        var owner = f.player("Partial", true);
        f.menu.open(owner.player);
        RuntimeException expected;
        if (before) {
            if (localeFault) refusedLocale.failAdd = true;
            else refusedChanges.failAdd = true;
            expected = localeFault ? refusedLocale.failure : refusedChanges.failure;
        } else {
            if (localeFault) afterLocale.failAdd = 1;
            else afterChanges.failAdd = 1;
            expected = localeFault ? afterLocale.startupFailure : afterChanges.startupFailure;
        }
        check(
                failure(f.menu::startUpdates) == expected,
                "partial subscription preserves original exception");
        check(
                locale.isEmpty() && changes.isEmpty(),
                "all partial subscriptions removed before/after effect");
        check(
                owner.open == owner.bottom && f.menu.activeCount() == 0,
                "failed observer start closes actual owned view");
        contextsReleased(f.menu);
        refusedLocale.failAdd = false;
        refusedChanges.failAdd = false;
        afterLocale.failAdd = 0;
        afterChanges.failAdd = 0;
        f.menu.startUpdates();
        var localeCallback = locale.iterator().next();
        var changeCallback = changes.iterator().next();
        f.menu.startUpdates();
        check(
                locale.size() == 1
                        && changes.size() == 1
                        && locale.iterator().next() == localeCallback
                        && changes.iterator().next() == changeCallback,
                "successful repeated start retains exactly the owned callbacks");
        f.menu.shutdown();
        check(locale.isEmpty() && changes.isEmpty(), "retry stops both subscriptions");
    }

    private static void refusedRemoval(boolean localeFault) throws Exception {
        var f = new ClanMenuFixture();
        var locale = new RefusingSet<Consumer<Player>>();
        var changes = new RefusingSet<ClanChangeListener>();
        set(f.messages.localization(), "localeListeners", locale);
        set(f.clans, "changeListeners", changes);
        f.menu.startUpdates();
        var owner = f.player("Refused", true);
        f.menu.open(owner.player);
        Inventory old = owner.open;
        if (localeFault) locale.failRemove = true;
        else changes.failRemove = true;
        RuntimeException failure = failure(f.menu::shutdown);
        check(
                failure.getCause() == (localeFault ? locale.failure : changes.failure),
                "refused unsubscribe remains observable");
        check(
                locale.size() == (localeFault ? 1 : 0) && changes.size() == (localeFault ? 0 : 1),
                "refused unsubscribe is honestly retained; other source removed");
        check(
                owner.open == owner.bottom && f.menu.activeCount() == 0,
                "refused unsubscribe cannot skip owned inventory cleanup");
        check(
                !f.menu.isActive(owner.player, old, (ClanMenuHolder) old.getHolder()),
                "view inert while external unsubscribe refused");
        int additions = locale.additions + changes.additions;
        failure(f.menu::startUpdates);
        check(
                locale.additions + changes.additions == additions,
                "unresolved removal prevents acquiring duplicate callbacks");
        locale.failRemove = false;
        changes.failRemove = false;
        f.menu.startUpdates();
        check(
                locale.size() == 1 && changes.size() == 1,
                "start reconciles previous refused removal before fresh subscriptions");
        f.menu.stopUpdates();
        check(
                locale.isEmpty() && changes.isEmpty(),
                "eventual successful stop removes both callbacks");
        int removals = locale.removals + changes.removals;
        f.menu.shutdown();
        f.menu.shutdown();
        check(
                removals == locale.removals + changes.removals,
                "successful repeated shutdown performs no extra observer removals");
    }

    private static void independentShutdown() throws Exception {
        var f = new ClanMenuFixture();
        var locale = new ModuleRuntimeFixture.FaultSet<Consumer<Player>>();
        var changes = new ModuleRuntimeFixture.FaultSet<ClanChangeListener>();
        set(f.messages.localization(), "localeListeners", locale);
        set(f.clans, "changeListeners", changes);
        f.menu.startUpdates();
        var failed = f.player("FailedClose", true);
        var good = f.player("GoodClose", true);
        var foreign = f.player("Foreign", true);
        f.menu.open(failed.player);
        f.menu.open(good.player);
        f.menu.open(foreign.player);
        failed.failClose = true;
        foreign.open = foreign.bottom;
        var warnings = new ArrayList<LogRecord>();
        ((Logger) get(f.menu, "logger"))
                .addHandler(
                        new Handler() {
                            public void publish(LogRecord record) {
                                warnings.add(record);
                            }

                            public void flush() {}

                            public void close() {}
                        });
        locale.failRemove = 1;
        changes.failRemove = 1;
        RuntimeException failure = failure(f.menu::shutdown);
        check(
                failure.getCause() == locale.cleanupFailure
                        && failure.getSuppressed().length == 1
                        && failure.getSuppressed()[0].getCause() == changes.cleanupFailure,
                "both observer cleanup causes retained independently");
        check(
                locale.isEmpty() && changes.isEmpty(),
                "after-effect removal faults leave actual collections empty");
        check(
                failed.closeCalls == 1 && good.closeCalls == 1 && foreign.closeCalls == 0,
                "one failed close cannot skip other owned views or close foreign inventory");
        check(
                f.menu.activeCount() == 0
                        && warnings.size() == 1
                        && warnings.getFirst().getThrown() != null,
                "all views detached; failed close diagnostic retains cause");
        contextsReleased(f.menu);
        f.menu.shutdown();
        check(
                failed.closeCalls == 1
                        && good.closeCalls == 1
                        && locale.isEmpty()
                        && changes.isEmpty(),
                "shutdown retry releases uncertainty without re-closing stale views");
    }

    private static void staleViews() throws Exception {
        var f = new ClanMenuFixture(ClanLimits.defaults(), ClanMenu.DEFAULT_LAYOUT);
        var inviter = f.player("Inviter", true);
        var owner = f.player("Recipient", true);
        check(
                f.clans.createClan(inviter.id, "Lifecycle Clan", "LIFE") == ClanResult.SUCCESS,
                "real clan fixture");
        UUID clan = f.clans.getClanOf(inviter.id).orElseThrow().id();
        f.clans.inviteMember(inviter.id, owner.id);
        f.menu.startUpdates();
        f.menu.open(owner.player, ClanMenuView.INVITES, 0);
        Inventory old = owner.open;
        var holder = (ClanMenuHolder) old.getHolder();
        int slot = f.menu.layout().contentSlot(0);
        f.menu.shutdown();
        owner.open = old;
        check(
                f.click(owner, old, slot, ClickType.LEFT).isCancelled()
                        && f.clans.getClanOf(owner.id).isEmpty(),
                "old runtime view cannot accept invitation after shutdown");
        var fresh =
                new ClanMenu(
                        f.clans,
                        f.identity,
                        f.messages,
                        id -> f.players.containsKey(id) ? f.players.get(id).player : null,
                        f::inventory,
                        ClanMenuFixture.SpecItem::new,
                        ClanMenuFixture.logger(),
                        ClanMenu.DEFAULT_LAYOUT);
        var listener =
                new ClanMenuListener(
                        fresh,
                        f.clans,
                        new ClanMessages(f.messages, id -> null),
                        f.messages,
                        ClanMenuFixture.logger());
        fresh.startUpdates();
        fresh.open(owner.player, ClanMenuView.INVITES, 0);
        Inventory current = owner.open;
        var forged = f.inventory(holder, old.getSize(), Component.text("Forged"));
        owner.open = forged;
        click(listener, f, owner, forged, slot);
        check(f.clans.getClanOf(owner.id).isEmpty(), "forged copied holder cannot mutate clan");
        owner.open = old;
        click(listener, f, owner, old, slot);
        check(
                f.clans.getClanOf(owner.id).isEmpty(),
                "stale runtime cannot regain authority in new menu");
        owner.open = current;
        listener.onInventoryClose(new InventoryCloseEvent(f.view(owner, old)));
        check(
                fresh.isActive(owner.player, current, (ClanMenuHolder) current.getHolder()),
                "old close event cannot clear new active view");
        owner.permitted = false;
        click(listener, f, owner, current, slot);
        check(
                f.clans.getClanOf(owner.id).isEmpty() && owner.open == owner.bottom,
                "current permission still enforced after re-enable");
        owner.permitted = true;
        fresh.open(owner.player, ClanMenuView.INVITES, 0);
        click(listener, f, owner, owner.open, slot);
        check(
                f.clans.getClanOf(owner.id).orElseThrow().id().equals(clan),
                "fresh authorized mapped clan action succeeds");
        fresh.shutdown();
        check(
                ((Set<?>) get(f.clans, "changeListeners")).isEmpty()
                        && ((Set<?>) get(f.messages.localization(), "localeListeners")).isEmpty(),
                "only fresh runtime subscribed; all callbacks released");
    }

    private static void committedRefreshAndContexts() throws Exception {
        var f = new ClanMenuFixture(ClanLimits.defaults(), ClanMenu.DEFAULT_LAYOUT);
        var owner = f.player("Updates", true);
        f.clans.createClan(owner.id, "Before Clan", "BEFORE");
        f.menu.startUpdates();
        f.menu.open(owner.player);
        Inventory inventory = owner.open;
        int name = f.menu.clanLayout().clanNameSlot();
        var previous = inventory.getItem(name);
        f.clans.renameClan(owner.id, "After Clan");
        check(
                owner.open == inventory
                        && inventory.getItem(name) != previous
                        && PlainTextComponentSerializer.plainText()
                                .serialize(ClanMenuFixture.spec(inventory, name).spec.name())
                                .equals("After Clan"),
                "committed change refreshes actual current Clan view in place");
        previous = inventory.getItem(name);
        f.repository.failNext = true;
        try {
            f.clans.renameClan(owner.id, "Failed Clan");
            throw new AssertionError("failed commit required");
        } catch (ClanRepositoryException expected) {
            check(
                    inventory.getItem(name) == previous,
                    "failed clan commit publishes no success rerender");
        }
        int close = f.menu.layout().slot(ClanMenu.Action.CLOSE);
        f.messages.localization().localeChanged(owner.player, Locale.GERMANY);
        check(
                PlainTextComponentSerializer.plainText()
                        .serialize(ClanMenuFixture.spec(inventory, close).spec.name())
                        .equals("Schließen"),
                "subscribed locale callback refreshes active menu");
        f.menu.targetPresenceChanged(UUID.randomUUID(), false);
        contextsReleased(f.menu);
        f.menu.shutdown();
        var stoppedItem = inventory.getItem(close);
        f.messages.localization().localeChanged(owner.player, Locale.US);
        f.clans.renameClan(owner.id, "Stopped Clan");
        check(
                inventory.getItem(close) == stoppedItem && f.menu.activeCount() == 0,
                "removed callbacks cannot rerender detached old inventory");
        var renderFailure = new IllegalStateException("controlled render failure");
        var broken =
                new ClanMenu(
                        f.clans,
                        f.identity,
                        f.messages,
                        id -> owner.player,
                        f::inventory,
                        spec -> {
                            throw renderFailure;
                        },
                        ClanMenuFixture.logger());
        check(
                failure(() -> broken.open(owner.player)) == renderFailure,
                "real render failure propagates");
        broken.shutdown();
        contextsReleased(broken);
    }

    private static void contextsReleased(ClanMenu menu) {
        check(
                get(menu, "renderingFor") == null
                        && get(menu, "rerenderInventory") == null
                        && get(menu, "presenceTarget") == null,
                "temporary Clan render and presence references released");
    }

    private static void click(
            ClanMenuListener listener,
            ClanMenuFixture f,
            ClanMenuFixture.TestPlayer owner,
            Inventory top,
            int slot) {
        var event =
                new InventoryClickEvent(
                        f.view(owner, top),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(event);
        check(event.isCancelled(), "stale forged and valid Clan clicks remain cancelled");
    }

    private static RuntimeException failure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            return exception;
        }
        throw new AssertionError("expected Clan lifecycle failure");
    }

    private static void check(boolean condition, String contract) {
        checks++;
        if (!condition) throw new AssertionError(contract);
    }

    private static final class RefusingSet<E> extends LinkedHashSet<E> {
        boolean failAdd;
        boolean failRemove;
        int additions;
        int removals;
        final RuntimeException failure =
                new IllegalStateException("external observer operation refused before effect");

        @Override
        public boolean add(E value) {
            additions++;
            if (failAdd) throw failure;
            return super.add(value);
        }

        @Override
        public boolean remove(Object value) {
            removals++;
            if (failRemove) throw failure;
            return super.remove(value);
        }
    }
}

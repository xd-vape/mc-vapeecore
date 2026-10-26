package dev.vapee.core.quest.daily.menu;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.quest.QuestChangeListener;
import dev.vapee.core.quest.QuestCompletionFixture;
import dev.vapee.core.quest.daily.DailyQuestLifecycleFixture.Faults;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public final class DailyQuestMenuLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "observers" -> observerFailures();
                case "countdown" -> countdownFailures();
                case "stale" -> staleCallbacks();
                case "views" -> viewCleanup();
                default -> throw new AssertionError(args[0]);
            }
        } else {
            normal();
            observerFailures();
            countdownFailures();
            staleCallbacks();
            viewCleanup();
            renderIsolation();
        }
        System.out.println("DailyQuestMenuLifecycleHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        var f = new Fixture();
        var menu = f.base.menu;
        menu.startUpdates();
        menu.startUpdates();
        check(f.locales.additions == 1 && f.changes.additions == 1, "idempotent observer start");
        check(f.tasks.isEmpty(), "idle menu has no countdown");
        var a = f.base.player();
        var b = f.base.player();
        menu.open(a.player, 0);
        var top = a.open;
        menu.open(b.player, 0);
        check(f.running() == 1 && f.tasks.size() == 1, "multiple viewers share one countdown");
        check(
                ((DailyQuestInventoryHolder) top.getHolder()).isBoundTo(top)
                        && menu.isActive(
                                a.player, top, (DailyQuestInventoryHolder) top.getHolder()),
                "exact real holder active");
        a.player.closeInventory();
        check(f.running() == 1, "other viewer keeps countdown");
        b.player.closeInventory();
        check(f.running() == 0 && menu.activeCount() == 0, "last close cancels timer");
        menu.open(a.player, 0);
        check(f.running() == 1 && f.tasks.size() == 2, "new view starts one new generation");
        menu.stopUpdates();
        check(
                f.running() == 0 && f.locales.isEmpty() && f.changes.isEmpty(),
                "stop independently releases updates");
        check(
                !menu.isActive(a.player, a.open, (DailyQuestInventoryHolder) a.open.getHolder()),
                "stopped updates revoke interaction authority");
        menu.shutdown();
        check(menu.activeCount() == 0 && a.open == a.bottom, "shutdown detaches owned view");
        menu.startUpdates();
        menu.open(a.player, 0);
        check(
                f.running() == 1 && f.locales.size() == 1 && f.changes.size() == 1,
                "fresh activation without duplicates");
        menu.shutdown();
        clean(f);
    }

    private static void observerFailures() throws Exception {
        for (boolean locale : List.of(false, true))
            for (boolean after : List.of(false, true)) {
                var f = new Fixture();
                var menu = f.base.menu;
                var fault = locale ? f.locales : f.changes;
                fault.failAdd = true;
                fault.addAfter = after;
                check(fails(menu::startUpdates) == fault.fault, "observer add original exception");
                check(
                        f.locales.isEmpty() && f.changes.isEmpty(),
                        "partial subscriptions fully removed");
                check(
                        !menu.hasPendingCleanup() && !menu.isAvailable(),
                        "failed observer start retires menu");
                var p = f.base.player();
                int before = f.base.syncCalls;
                menu.open(p.player, 0);
                check(f.base.syncCalls == before, "failed-start menu cannot synchronize");
                fault.failAdd = false;
                menu.startUpdates();
                menu.open(p.player, 0);
                check(menu.activeCount() == 1, "observer failed-start retry works");
                menu.shutdown();
                clean(f);
            }
        for (boolean locale : List.of(false, true))
            for (boolean after : List.of(false, true)) {
                var f = new Fixture();
                var menu = f.base.menu;
                menu.startUpdates();
                var p = f.base.player();
                menu.open(p.player, 0);
                var previous = p.open;
                var fault = locale ? f.locales : f.changes;
                fault.failRemove = true;
                fault.removeAfter = after;
                check(fails(menu::shutdown) != null, "observer removal error visible");
                check(
                        locale ? f.changes.isEmpty() : f.locales.isEmpty(),
                        "second unsubscribe attempted independently");
                check(
                        fault.size() == (after ? 0 : 1) && menu.hasPendingCleanup(),
                        "uncertain observer remains retryable even after effect");
                check(
                        menu.activeCount() == 0 && p.open != previous && f.running() == 0,
                        "observer error cannot skip owned view cleanup");
                check(
                        get(menu, "renderingFor") == null && get(menu, "rerenderInventory") == null,
                        "transient render context cleared");
                check(
                        fails(menu::startUpdates) != null,
                        "refused cleanup blocks new observer generation");
                fault.failRemove = false;
                menu.shutdown();
                clean(f);
                menu.startUpdates();
                check(
                        f.locales.size() == 1 && f.changes.size() == 1,
                        "retry exact observers then one fresh generation");
                menu.shutdown();
                clean(f);
            }
        var f = new Fixture();
        f.changes.failAdd = true;
        f.changes.addAfter = true;
        f.locales.failRemove = true;
        check(
                fails(f.base.menu::startUpdates) == f.changes.fault,
                "cleanup failure cannot mask observer add failure");
        check(
                f.changes.isEmpty() && f.locales.size() == 1,
                "partial start still removes independent second observer");
        f.locales.failRemove = false;
        f.base.menu.shutdown();
        clean(f);
    }

    private static void countdownFailures() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            var f = new Fixture();
            var menu = f.base.menu;
            menu.startUpdates();
            var p = f.base.player();
            menu.open(p.player, 0);
            var timer = f.tasks.getFirst();
            timer.refuse = mode == 0;
            timer.after = mode == 1;
            timer.ineffective = mode == 2;
            timer.unknown = mode == 3;
            check(fails(menu::shutdown) != null, "countdown cancellation failure visible");
            check(
                    f.locales.isEmpty() && f.changes.isEmpty() && menu.activeCount() == 0,
                    "countdown failure does not skip observers or views");
            check(
                    menu.hasPendingCleanup() == (mode != 1),
                    "exact countdown cancellation handle retained until confirmation");
            int calls = f.base.syncCalls;
            timer.callback.run();
            check(f.base.syncCalls == calls, "refused timer callback inert");
            if (mode != 1) {
                check(
                        fails(menu::startUpdates) != null && f.tasks.size() == 1,
                        "refused countdown blocks second generation");
                timer.refuse = false;
                timer.ineffective = false;
                timer.unknown = false;
            }
            menu.shutdown();
            clean(f);
            menu.startUpdates();
            menu.open(p.player, 0);
            check(f.running() == 1, "countdown retry then new activation");
            menu.shutdown();
            clean(f);
        }
        for (boolean missing : List.of(false, true)) {
            var f = new Fixture();
            var menu = f.base.menu;
            menu.startUpdates();
            var callbacks = new ArrayList<Runnable>();
            set(
                    menu,
                    "countdownScheduler",
                    (DailyQuestMenu.CountdownScheduler)
                            update -> {
                                callbacks.add(update);
                                if (missing) return null;
                                throw new IllegalStateException("schedule before returned handle");
                            });
            var p = f.base.player();
            check(
                    fails(() -> menu.open(p.player, 0)) != null,
                    "countdown missing handle or schedule failure reported");
            var clock = p.open.getItem(34);
            f.base.now = f.base.now.plusSeconds(5);
            callbacks.getFirst().run();
            check(p.open.getItem(34) == clock, "unreturned countdown callback is inert");
            menu.shutdown();
            clean(f);
        }
    }

    private static void staleCallbacks() throws Exception {
        var f = new Fixture();
        var menu = f.base.menu;
        menu.startUpdates();
        var p = f.base.player();
        menu.open(p.player, 0);
        var oldLocale = f.locales.iterator().next();
        var oldQuest = f.changes.iterator().next();
        var oldTask = f.tasks.getFirst();
        var oldTop = p.open;
        menu.shutdown();
        int before = f.base.syncCalls;
        menu.open(p.player, 0);
        check(
                f.base.syncCalls == before && menu.activeCount() == 0,
                "retired menu cannot reopen authoritative views");
        oldLocale.accept(p.player);
        oldQuest.onQuestsCommitted(p.id);
        oldTask.callback.run();
        check(menu.activeCount() == 0, "captured observers cannot reopen retired menu");
        menu.startUpdates();
        menu.open(p.player, 0);
        var fresh = p.open;
        var clock = fresh.getItem(34);
        var quest = fresh.getItem(13);
        f.base.now = f.base.now.plusSeconds(5);
        oldTask.callback.run();
        check(fresh.getItem(34) == clock, "old countdown cannot render fresh activation");
        oldLocale.accept(p.player);
        oldQuest.onQuestsCommitted(p.id);
        check(fresh.getItem(13) == quest, "retired observer cannot refresh new view");
        f.base.listener.onInventoryClose(new InventoryCloseEvent(QuestMenuFixture.view(p, oldTop)));
        check(
                menu.isActive(p.player, fresh, (DailyQuestInventoryHolder) fresh.getHolder()),
                "stale close cannot detach new inventory");
        f.tasks.getLast().callback.run();
        check(fresh.getItem(34) != clock, "current countdown still updates");
        f.locales.iterator().next().accept(p.player);
        check(
                fresh.getItem(13) != quest && p.open == fresh,
                "current locale observer refreshes in place");
        var rendered = fresh.getItem(13);
        f.changes.iterator().next().onQuestsCommitted(p.id);
        check(fresh.getItem(13) != rendered, "current quest observer refreshes in place");
        menu.shutdown();
        clean(f);
    }

    private static void viewCleanup() throws Exception {
        var f = new Fixture();
        var menu = f.base.menu;
        menu.startUpdates();
        var a = f.base.player();
        var b = f.base.player();
        var c = f.base.player();
        menu.open(a.player, 0);
        menu.open(b.player, 0);
        menu.open(c.player, 0);
        var failed = a.open;
        var foreign = QuestMenuFixture.inventory(null, 36);
        b.open = foreign;
        a.failClose = true;
        f.base.domain.logger.addHandler(
                new Handler() {
                    public void publish(LogRecord r) {
                        if (r.getMessage().startsWith("Could not close daily"))
                            throw new IllegalStateException("diagnostic failure");
                    }

                    public void flush() {}

                    public void close() {}
                });
        check(
                fails(menu::shutdown) != null,
                "view close failure reported without skipping viewers");
        check(
                a.open == failed && c.open == c.bottom && c.closes == 1,
                "one failed close does not skip other viewers");
        check(b.open == foreign && b.closes == 0, "foreign current inventory preserved");
        check(
                menu.activeCount() == 0
                        && !menu.isActive(
                                a.player, failed, (DailyQuestInventoryHolder) failed.getHolder()),
                "failed physical close detaches all authority");
        check(
                f.running() == 0 && f.locales.isEmpty() && f.changes.isEmpty(),
                "view error leaves no active timer or observers");
        check(
                get(menu, "renderingFor") == null && get(menu, "rerenderInventory") == null,
                "view cleanup clears transient contexts");
        menu.shutdown();
        check(c.closes == 1, "repeated shutdown has no close effects");
        clean(f);
    }

    private static void renderIsolation() throws Exception {
        var f = new Fixture();
        var menu = f.base.menu;
        menu.startUpdates();
        var a = f.base.player();
        var b = f.base.player();
        menu.open(a.player, 0);
        menu.open(b.player, 0);
        var top = b.open;
        var clock = top.getItem(34);
        int syncs = f.base.syncCalls, saves = f.base.domain.repository.saves, opens = b.opens;
        set(
                menu,
                "onlinePlayer",
                (Function<UUID, Player>)
                        id -> {
                            if (id.equals(a.id))
                                throw new IllegalStateException("one viewer render failure");
                            return f.base.online.get(id).player;
                        });
        f.base.now = f.base.now.plusSeconds(2);
        f.tasks.getFirst().callback.run();
        check(
                menu.activeCount() == 1 && top.getItem(34) != clock,
                "failed countdown viewer cannot stop other updates");
        check(
                f.base.syncCalls == syncs
                        && f.base.domain.repository.saves == saves
                        && b.opens == opens,
                "countdown remains presentation only");
        check(f.running() == 1, "remaining viewer retains shared timer");
        menu.shutdown();
        clean(f);
    }

    private static void clean(Fixture f) {
        check(
                !f.base.menu.hasPendingCleanup()
                        && f.locales.isEmpty()
                        && f.changes.isEmpty()
                        && f.running() == 0,
                "complete menu cleanup confirmed");
    }

    private static RuntimeException fails(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static final class Fixture {
        final QuestMenuFixture base = new QuestMenuFixture(DailyQuestMenu.DEFAULT_LAYOUT);
        final Faults<Consumer<Player>> locales = new Faults<>();
        final Faults<QuestChangeListener> changes = new Faults<>();
        final List<Timer> tasks = new ArrayList<>();

        Fixture() throws Exception {
            base.domain.define(QuestCompletionFixture.definition("daily", "test:key", 100, 1));
            set(base.messages.localization(), "localeListeners", locales);
            set(base.domain.quests, "changeListeners", changes);
            var constructor =
                    Class.forName("dev.vapee.core.quest.daily.menu.DailyQuestMenu$TaskCancellation")
                            .getDeclaredConstructor(BukkitTask.class);
            constructor.setAccessible(true);
            set(
                    base.menu,
                    "countdownScheduler",
                    (DailyQuestMenu.CountdownScheduler)
                            callback -> {
                                var timer = new Timer(callback);
                                tasks.add(timer);
                                try {
                                    return (Runnable) constructor.newInstance(timer);
                                } catch (ReflectiveOperationException e) {
                                    throw new AssertionError(e);
                                }
                            });
        }

        long running() {
            return tasks.stream().filter(t -> !t.cancelled).count();
        }
    }

    private static final class Timer implements BukkitTask {
        final Runnable callback;
        boolean cancelled, refuse, after, ineffective, unknown;

        Timer(Runnable callback) {
            this.callback = callback;
        }

        public int getTaskId() {
            return System.identityHashCode(this);
        }

        public Plugin getOwner() {
            return null;
        }

        public boolean isSync() {
            return true;
        }

        public boolean isCancelled() {
            if (unknown) throw new IllegalStateException("unknown cancel status");
            return cancelled;
        }

        public void cancel() {
            if (refuse) throw new IllegalStateException("countdown cancel refused");
            if (!ineffective) cancelled = true;
            if (after) throw new IllegalStateException("countdown after cancel");
        }
    }
}

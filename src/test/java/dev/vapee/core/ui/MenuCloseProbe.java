package dev.vapee.core.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Reusable fault injection only; each adapter constructs its real feature menu and close listener. */
public final class MenuCloseProbe {
    public record Subject(Consumer<Player> open, Runnable close, IntSupplier activeCount,
                          Consumer<InventoryCloseEvent> onClose) { }

    public final Logger logger = Logger.getAnonymousLogger();
    private final List<LogRecord> logs = new ArrayList<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final Set<UUID> lookedUp = new HashSet<>();
    private Consumer<InventoryCloseEvent> onClose;
    private boolean failLogging;
    private int closeEvents;
    private int checks;

    private MenuCloseProbe() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                logs.add(record);
                if (failLogging) throw new IllegalStateException("injected logging failure");
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }

    public Player lookup(UUID id) {
        lookedUp.add(id);
        Viewer viewer = viewers.get(id);
        if (viewer == null || viewer.absent) return null;
        viewer.failAt("lookup");
        return viewer.player;
    }

    public static int verify(String menuName, Function<MenuCloseProbe, Subject> factory) {
        int total = 0;
        for (String stage : List.of("lookup", "isOnline", "getOpenInventory", "getTopInventory", "closeInventory")) {
            // Swap failure roles as well as insertion order; assertions never rely on HashMap iteration order.
            for (boolean reverse : List.of(false, true)) {
                MenuCloseProbe probe = new MenuCloseProbe();
                Subject subject = factory.apply(probe);
                probe.onClose = subject.onClose();
                Viewer first = probe.viewer(1), second = probe.viewer(2);
                Viewer bad = reverse ? second : first, healthy = reverse ? first : second;
                subject.open().accept(bad.player);
                subject.open().accept(healthy.player);
                probe.check(subject.activeCount().getAsInt() == 2, "two real active views");
                bad.failureStage = stage;
                subject.close().run();
                probe.check(probe.lookedUp.containsAll(Set.of(bad.id, healthy.id)), "both owners attempted: " + stage);
                probe.check(healthy.closes == 1 && healthy.open == healthy.bottom, "healthy owner closed: " + stage);
                probe.check(bad.closes == 0 && bad.open != bad.bottom, "fault actually prevents failing close: " + stage);
                probe.check(subject.activeCount().getAsInt() == 0, "registry cleared: " + stage);
                probe.check(probe.logs.size() == 1 && probe.logs.getFirst().getLevel() == Level.WARNING
                        && probe.logs.getFirst().getMessage().contains(menuName)
                        && probe.logs.getFirst().getMessage().contains(bad.id.toString())
                        && probe.logs.getFirst().getThrown() == bad.failure, "warning retains menu, UUID and original cause");
                probe.check(probe.closeEvents == 1, "healthy close dispatched real close handler during snapshot traversal");
                total += probe.checks;
            }
        }
        MenuCloseProbe probe = new MenuCloseProbe();
        Subject subject = factory.apply(probe);
        probe.onClose = subject.onClose();
        Viewer absent = probe.viewer(1), offline = probe.viewer(2), foreign = probe.viewer(3), healthy = probe.viewer(4);
        for (Viewer viewer : List.of(absent, offline, foreign, healthy)) subject.open().accept(viewer.player);
        absent.absent = true;
        offline.online = false;
        Inventory tracked = foreign.open;
        foreign.open = foreign.bottom;
        subject.close().run();
        probe.check(absent.closes == 0 && offline.closes == 0, "absent and offline owners skipped");
        probe.check(foreign.closes == 0 && foreign.open == foreign.bottom && foreign.open != tracked,
                "foreign replacement B survives tracked A cleanup");
        probe.check(healthy.closes == 1 && probe.closeEvents == 1, "remaining healthy view closed reentrantly");
        probe.check(subject.activeCount().getAsInt() == 0 && probe.logs.isEmpty(), "all skipped bindings released without false warnings");
        subject.close().run();
        probe.check(healthy.closes == 1, "cleanup is idempotent");
        total += probe.checks;

        probe = new MenuCloseProbe();
        subject = factory.apply(probe);
        probe.onClose = subject.onClose();
        Viewer bad = probe.viewer(1);
        subject.open().accept(bad.player);
        bad.failureStage = "closeInventory";
        probe.failLogging = true;
        boolean threw = false;
        try { subject.close().run(); } catch (IllegalStateException expected) {
            threw = expected.getMessage().equals("injected logging failure");
        }
        probe.check(threw, "unexpected logging failure is not silently swallowed");
        probe.check(subject.activeCount().getAsInt() == 0, "outer finally clears even when logging fails");
        return total + probe.checks;
    }

    private Viewer viewer(long suffix) {
        Viewer viewer = new Viewer(new UUID(0, suffix));
        viewers.put(viewer.id, viewer);
        return viewer;
    }

    private final class Viewer {
        final UUID id;
        final Inventory bottom = proxy(Inventory.class, (method, args) -> switch (method) {
            case "getSize" -> 36;
            case "getType" -> InventoryType.CHEST;
            default -> null;
        });
        final RuntimeException failure = new IllegalStateException("injected owner failure");
        final Player player;
        Inventory open = bottom;
        String failureStage;
        boolean absent;
        boolean online = true;
        int closes;

        Viewer(UUID id) {
            this.id = id;
            player = proxy(Player.class, (method, args) -> {
                failAt(method);
                return switch (method) {
                    case "getUniqueId" -> id;
                    case "getName" -> "Cleanup" + id.getLeastSignificantBits();
                    case "isOnline" -> online;
                    case "hasPermission" -> true;
                    case "openInventory" -> { open = (Inventory) args[0]; yield view(open, false); }
                    case "getOpenInventory" -> view(open, true);
                    case "closeInventory" -> {
                        Inventory old = open;
                        onClose.accept(new InventoryCloseEvent(view(old, false)));
                        closeEvents++;
                        closes++;
                        open = bottom;
                        yield null;
                    }
                    default -> null;
                };
            });
        }

        void failAt(String method) { if (method.equals(failureStage)) throw failure; }

        InventoryView view(Inventory top, boolean inject) {
            return proxy(InventoryView.class, (method, args) -> {
                if (inject) failAt(method);
                return switch (method) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> bottom;
                    case "getPlayer" -> player;
                    case "getType" -> InventoryType.CHEST;
                    default -> null;
                };
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Action action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(instance);
                case "equals" -> instance == args[0];
                case "toString" -> type.getSimpleName() + "CleanupProbe";
                default -> null;
            };
            return action.call(method.getName(), args);
        });
    }
    @FunctionalInterface private interface Action { Object call(String method, Object[] args); }
    private void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

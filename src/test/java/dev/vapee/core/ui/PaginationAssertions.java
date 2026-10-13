package dev.vapee.core.ui;
import org.bukkit.inventory.Inventory;
import java.util.*;
import java.util.function.*;
/** Tests actual rendered controls and forged-item resistance, independent of feature-local positions. */
public final class PaginationAssertions {
    private PaginationAssertions() { }
    public static <A extends Enum<A>> int verify(Pagination expected, MenuLayout<A> layout, Inventory top,
            Function<Integer, Optional<A>> actions, A previous, A info, A next, A close,
            IntConsumer click, Supplier<Inventory> current) {
        int checks = 0;
        var visibility = Map.of(previous, expected.hasPrevious(), info, expected.hasMultiplePages(), next, expected.hasNext());
        for (var entry : visibility.entrySet()) {
            int slot = layout.actionSlot(entry.getKey()).orElseThrow(); boolean visible = entry.getValue();
            require((top.getItem(slot) != null) == visible, "rendered control matches pagination: " + entry.getKey()); checks++;
            require(actions.apply(slot).equals(visible ? Optional.of(entry.getKey()) : Optional.empty()), "only visible runtime control resolves: " + entry.getKey()); checks++;
            if (!visible || entry.getKey() == info) {
                if (!visible) top.setItem(slot, top.getItem(layout.slot(close)));
                click.accept(slot);
                require(current.get() == top, "hidden/display-only control cannot reopen or navigate: " + entry.getKey()); checks++;
                if (!visible) top.setItem(slot, null);
            }
        }
        return checks;
    }
    public static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}

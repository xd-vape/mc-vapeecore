package dev.vapee.core.ui;
import java.util.*;
public final class CompositionTestSupport {
    private CompositionTestSupport() { }
    public static <A extends Enum<A>> MenuLayout<A> without(MenuLayout<A> layout, A action) {
        var actions = new LinkedHashMap<>(layout.actions()); actions.remove(action);
        return new MenuLayout<>(layout.size(), layout.contentSlots(), layout.emptySlot(), actions);
    }
}

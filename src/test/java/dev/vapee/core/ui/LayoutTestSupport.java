package dev.vapee.core.ui;

import java.util.*;

/** Alternative geometry fixtures. No feature behavior or security is implemented here. */
public final class LayoutTestSupport {
    private LayoutTestSupport() { }
    public static int unused(MenuLayout<?> layout) {
        return java.util.stream.IntStream.range(0, layout.size())
                .filter(slot -> layout.actionAt(slot).isEmpty() && !layout.isContent(slot)).findFirst()
                .orElse(layout.size());
    }
    public static int emptyContent(MenuLayout<?> layout, java.util.function.IntPredicate hasTarget) {
        return layout.contentSlots().stream().filter(slot -> !hasTarget.test(slot)).findFirst().orElseThrow();
    }
    public static <A extends Enum<A>> MenuLayout<A> paged(MenuLayout<A> original, List<Integer> content) {
        return paged(original, 45, content);
    }
    public static <A extends Enum<A>> MenuLayout<A> paged(MenuLayout<A> original, int size, List<Integer> content) {
        Set<Integer> used = new HashSet<>(content);
        used.add(8);
        Map<A, List<Integer>> actions = new LinkedHashMap<>();
        for (A action : original.actions().keySet()) {
            if (action.name().equals("CLOSE")) { actions.put(action, List.of(8)); continue; }
            int slot = java.util.stream.IntStream.range(0, size).filter(i -> !used.contains(i)).findFirst().orElseThrow();
            used.add(slot);
            actions.put(action, List.of(slot));
        }
        return new MenuLayout<>(size, content, content.getFirst(), actions);
    }
    public static <A extends Enum<A>> MenuLayout<A> move(MenuLayout<A> original, int size,
                                                       Map<A, List<Integer>> replacements) {
        Map<A, List<Integer>> actions = new LinkedHashMap<>(original.actions());
        actions.putAll(replacements);
        return new MenuLayout<>(size, original.contentSlots(), original.emptySlot(), actions);
    }
    public static final List<Integer> SPARSE = List.of(10, 12, 14, 28, 30, 32);
    public static final List<Integer> REORDERED = List.of(32, 10, 30, 12, 28, 14);
    public static final List<Integer> HIGH_CONTENT = List.of(51, 10, 30, 12, 28, 14);
}

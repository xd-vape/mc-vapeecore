package dev.vapee.core.ui;

import java.util.*;

public final class MenuLayoutHarness {
    private enum Action { CLOSE, NEXT }
    private static int checks;
    public static void main(String[] args) {
        var content = new ArrayList<>(LayoutTestSupport.SPARSE);
        var slots = new ArrayList<>(List.of(8));
        var actions = new HashMap<Action, List<Integer>>();
        actions.put(Action.CLOSE, slots); actions.put(Action.NEXT, List.of(44));
        var layout = new MenuLayout<Action>(45, content, 10, actions);
        content.clear(); slots.clear(); actions.clear();
        check(layout.size() == 45 && layout.pageSize() == 6 && layout.contentSlot(3) == 28, "immutable explicit page geometry");
        check(layout.actionAt(8).orElseThrow() == Action.CLOSE && layout.actionAt(11).isEmpty(), "semantic control mapping");
        check(layout.isContent(32) && !layout.isContent(0), "membership is not a range");
        for (int size : new int[]{0, 8, 10, 63}) rejects(() -> new MenuLayout<Action>(size, List.of(), -1, Map.of()), "invalid size " + size);
        rejects(() -> new MenuLayout<Action>(45, List.of(10, 10), 10, Map.of()), "duplicate content");
        rejects(() -> new MenuLayout<Action>(45, List.of(10), 10, Map.of(Action.CLOSE, List.of(10))), "content/control collision");
        rejects(() -> new MenuLayout<Action>(45, List.of(10), 8, Map.of(Action.CLOSE, List.of(8))), "empty/control collision");
        rejects(() -> new MenuLayout<Action>(45, List.of(10), 10, Map.of(Action.CLOSE, List.of(8), Action.NEXT, List.of(8))), "negative control 4: close/next collision");
        rejects(() -> new MenuLayout<Action>(27, List.of(10), 10, Map.of(Action.CLOSE, List.of(40))), "negative control 5: out of bounds");
        rejects(() -> layout.validateTargets(List.of(0)), "target outside explicit content");
        rejects(() -> new MenuLayout<Action>(45, List.of(), -1, Map.of()).requirePaged(), "empty page capacity");
        rejects(() -> new MenuLayout<Action>(45, List.of(), -1, Map.of(Action.CLOSE, List.of(1, 2))).validateActions(Set.of()), "button has multiple positions");
        var pagination = Pagination.of(13, 1, layout.pageSize());
        check(pagination.fromIndex() == 6 && pagination.toIndex() == 12 && pagination.hasNext(), "pagination uses explicit capacity");
        System.out.println("MenuLayoutHarness passed " + checks + " checks.");
    }
    private static void rejects(Runnable work, String reason) {
        try { work.run(); throw new AssertionError("Accepted " + reason); }
        catch (IllegalArgumentException expected) { check(!expected.getMessage().isBlank(), reason); }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

package dev.vapee.core.ui;

import java.util.*;

/** Chest geometry only. Actions belong to each menu; permissions and state belong to its services.
 * Content order is page order, never a numeric slot range. Empty is an exclusive render state. */
public record MenuLayout<A extends Enum<A>>(int size, List<Integer> contentSlots, int emptySlot,
                                           Map<A, List<Integer>> actions) {
    public MenuLayout {
        if (size < 9 || size > 54 || size % 9 != 0)
            throw new IllegalArgumentException("Chest size must be a multiple of 9 between 9 and 54: " + size);
        contentSlots = List.copyOf(contentSlots);
        Set<Integer> occupied = new HashSet<>();
        for (int slot : contentSlots) {
            validateSlot(size, slot);
            if (!occupied.add(slot)) throw new IllegalArgumentException("Duplicate content slot: " + slot);
        }
        Map<A, List<Integer>> copy = new LinkedHashMap<>();
        for (var entry : actions.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "action");
            List<Integer> slots = List.copyOf(entry.getValue());
            if (slots.isEmpty()) throw new IllegalArgumentException("Action has no slots: " + entry.getKey());
            for (int slot : slots) {
                validateSlot(size, slot);
                if (!occupied.add(slot)) throw new IllegalArgumentException("Content/control collision at slot: " + slot);
            }
            copy.put(entry.getKey(), slots);
        }
        actions = Collections.unmodifiableMap(copy);
        if (emptySlot != -1) {
            validateSlot(size, emptySlot);
            if (actions.values().stream().anyMatch(slots -> slots.contains(emptySlot)))
                throw new IllegalArgumentException("Empty state collides with control: " + emptySlot);
        }
        if (!contentSlots.isEmpty() && emptySlot == -1)
            throw new IllegalArgumentException("Paged layout requires an empty-state slot");
    }

    public static void validateSlot(int size, int slot) {
        if (slot < 0 || slot >= size)
            throw new IllegalArgumentException("Slot " + slot + " outside inventory size " + size);
    }

    /** Verify menu-required actions early, including the number of positions per action. */
    public MenuLayout<A> requireActions(Map<A, List<Integer>> required) {
        if (!actions.keySet().equals(required.keySet()))
            throw new IllegalArgumentException("Missing/unexpected menu actions");
        required.forEach((action, slots) -> {
            if (actions.get(action).size() != slots.size())
                throw new IllegalArgumentException("Wrong number of positions for " + action);
        });
        return this;
    }

    public int slot(A action) { return slot(action, 0); }
    public int slot(A action, int position) {
        List<Integer> slots = actions.get(action);
        if (slots == null) throw new IllegalArgumentException("Unknown menu action: " + action);
        return slots.get(position);
    }
    public Optional<A> actionAt(int slot) {
        return actions.entrySet().stream().filter(entry -> entry.getValue().contains(slot))
                .map(Map.Entry::getKey).findFirst();
    }
    public int pageSize() { return contentSlots.size(); }
    public int contentSlot(int indexOnPage) { return contentSlots.get(indexOnPage); }
    public boolean isContent(int slot) { return contentSlots.contains(slot); }

    public void requirePaged() {
        if (contentSlots.isEmpty()) throw new IllegalArgumentException("Paged menu has no content slots");
    }
    public void requireFixed() {
        if (!contentSlots.isEmpty() || emptySlot != -1)
            throw new IllegalArgumentException("Fixed menu cannot declare paged content/empty state");
    }
    public void validateTargets(Collection<Integer> slots) {
        for (int slot : slots) if (!isContent(slot))
            throw new IllegalArgumentException("Target outside explicit content slots: " + slot);
    }
}

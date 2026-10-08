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
    }

    public static void validateSlot(int size, int slot) {
        if (slot < 0 || slot >= size)
            throw new IllegalArgumentException("Slot " + slot + " outside inventory size " + size);
    }

    /** Validate exposed placements, never require a supported action to be visible. */
    public MenuLayout<A> validateActions(Set<A> settings) {
        actions.forEach((action, slots) -> {
            int maximum = settings.contains(action) ? 2 : 1;
            if (slots.size() > maximum)
                throw new IllegalArgumentException("Too many positions for " + action);
        });
        return this;
    }

    public List<Integer> placement(A action) { return actions.getOrDefault(action, List.of()); }
    public OptionalInt actionSlot(A action) { return actionSlot(action, 0); }
    public OptionalInt actionSlot(A action, int position) {
        List<Integer> slots = placement(action);
        return position >= 0 && position < slots.size() ? OptionalInt.of(slots.get(position)) : OptionalInt.empty();
    }

    public static <A extends Enum<A>> Builder<A> builder(int size, Class<A> actionType) {
        return new Builder<>(size, actionType);
    }

    /** Local Java authoring: omitted placements are absent from rendering and click routing. */
    public static final class Builder<A extends Enum<A>> {
        private final int size;
        private final Map<A, List<Integer>> actions;
        private List<Integer> content = List.of();
        private int empty = -1;
        private Builder(int size, Class<A> actionType) {
            this.size = size;
            this.actions = new EnumMap<>(actionType);
        }
        public Builder<A> button(A action, int slot) { return add(action, List.of(slot)); }
        public Builder<A> setting(A action, int icon) { return add(action, List.of(icon)); }
        public Builder<A> setting(A action, int icon, int status) { return add(action, List.of(icon, status)); }
        private Builder<A> add(A action, List<Integer> slots) {
            if (actions.putIfAbsent(Objects.requireNonNull(action), slots) != null)
                throw new IllegalArgumentException("Duplicate action placement: " + action);
            return this;
        }
        public Builder<A> content(List<Integer> slots) { content = List.copyOf(slots); return this; }
        public Builder<A> emptyState(int slot) { empty = slot; return this; }
        public MenuLayout<A> build() { return new MenuLayout<>(size, content, empty, actions); }
    }

    /** Strict lookup for explicitly placed fixture positions. Production rendering uses actionSlot. */
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

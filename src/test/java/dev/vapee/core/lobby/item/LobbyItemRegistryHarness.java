package dev.vapee.core.lobby.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import java.util.List;

/** Synthetic registration + fixture only: the real generic parser/builder/service/listener handle an unseen ID. */
public final class LobbyItemRegistryHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var registry = LobbyItemRegistrations.inactive();
        int[] calls = {0};
        var appearance = new LobbyItemDefinition.Appearance(Material.DIAMOND, Component.text("Fallback"), List.of(), false);
        var defaults = new LobbyItemDefinition(true, 7, appearance, appearance);
        registry.register("synthetic-test", defaults, (player, click) -> { calls[0]++; return true; });
        check(registry.resolve("synthetic-test").orElseThrow().defaults() == defaults, "registered ID resolves its unique Java definition/action");
        rejected(() -> registry.register("synthetic-test", defaults, (p,c) -> false), "duplicate registration rejected");
        for (String id : new String[]{null, "", " ", "UPPER", "has.dot", "has:colon", "a".repeat(65)}) {
            rejected(() -> registry.register(id, defaults, (p,c) -> false), "invalid stable id rejected " + id);
        }
        try (var f = new LobbyItemRegistryFixture(registry, null)) {
            var parsed = f.configure("""
                    items:
                      synthetic-test:
                        enabled: true
                        slot: 7
                        material: DIAMOND
                        name: '<aqua>Synthetic'
                      not-registered:
                        enabled: true
                        slot: 3
                        material: DIAMOND
                        action: synthetic-test
                        command: op somebody
                    """);
            check(parsed.containsKey("synthetic-test") && !parsed.containsKey("not-registered"), "only Java-registered IDs enter immutable presentation map");
            check(f.logs.stream().anyMatch(r -> r.getMessage().contains("items.not-registered") && r.getMessage().contains("ignored")), "unknown YAML ID has clear warning");
            f.refresh();
            check(f.storage[7].getType() == Material.DIAMOND && f.items.getItemId(f.storage[7]).orElseThrow().equals("synthetic-test"), "synthetic item built and placed with exact PDC ID");
            check(PlainTextComponentSerializer.plainText().serialize(f.storage[7].getItemMeta().displayName()).equals("Synthetic"), "synthetic YAML implicit MiniMessage reaches item");
            check(f.storage[3] == null && f.ownedCount() == 1, "unknown configured action has no item placement");
            f.click(f.storage[7], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 0 && f.sounds == 0, "unavailable runtime fails closed");
            registry.activate();
            check(f.click(f.storage[7], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND).isCancelled(), "real main-hand click consumed");
            check(calls[0] == 1 && f.sounds == 1, "synthetic registration handles exactly one click through unchanged generic listener");
            f.click(f.marked("vapeecore", "not-registered"), Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 1 && f.items.getItemId(f.marked("vapeecore", "not-registered")).isEmpty(), "unknown owned PDC safely ignored");
            var unknownReload = f.configure("items:\n  not-registered:\n    slot: 3\n    material: DIAMOND\n    action: synthetic-test\n    command: op somebody\n");
            check(calls[0] == 1 && !unknownReload.containsKey("not-registered"), "active runtime unknown YAML action cannot execute registered behavior");
            f.refresh();
            check(f.ownedCount() == 0 && f.storage[7] == null, "missing synthetic registered id is removed instead of Java default fallback placement");
            f.click(f.marked("vapeecore", "synthetic-test"), Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 1, "removed synthetic id cannot execute its stale PDC action");
            f.configure("items:\n  synthetic-test:\n    slot: 7\n    material: DIAMOND\n"); f.refresh();
            var foreign = f.marked("otherplugin", "synthetic-test");
            check(!f.items.isManagedItem(foreign) && !f.click(foreign, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND).isCancelled(), "foreign namespace does not establish managed identity");
            f.click(f.foreign(), Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 1, "unmarked lookalike cannot invoke Java action");
            f.click(f.storage[7], Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND);
            f.click(f.storage[7], Action.LEFT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 1, "unsupported hand/click ignored");
            f.eligible = false; f.click(f.storage[7], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 1, "ineligible lobby ownership cannot route actions"); f.eligible = true;
            var old = f.storage[7];
            f.configure("items:\n  synthetic-test:\n    enabled: false\n"); f.refresh();
            f.click(old, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 1 && f.storage[7] == null, "disabled item removed and stale item cannot click");
            f.configure("items:\n  synthetic-test:\n    slot: 2\n    material: CLOCK\n    name: '<gold>Changed'\n    action: navigator\n    command: op somebody\n");
            f.storage[2] = foreign; f.refresh(); f.refresh();
            check(f.storage[2].getType() == Material.CLOCK && f.ownedCount() == 1, "new slot/material reconciled without duplicate");
            check(java.util.Arrays.stream(f.storage).filter(item -> item == foreign).count() == 1, "foreign item safely preserved exactly once");
            f.click(f.storage[2], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 2, "known YAML action/command fields cannot replace registered Java behavior");
            registry.deactivate(); f.click(f.storage[2], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(calls[0] == 2, "teardown deactivates registered actions");
            try { registry.register("later", defaults, (p,c) -> false); throw new AssertionError("sealed registration accepted"); }
            catch (IllegalStateException expected) { checks++; }
            check(f.logs.stream().noneMatch(r -> r.getMessage().contains("synthetic-test") && r.getMessage().contains("unregistered")), "synthetic Java ID never warned as unknown");
        }
        System.out.println("LobbyItemRegistryHarness passed " + checks + " checks.");
    }
    private static void rejected(Runnable action, String label) { try { action.run(); throw new AssertionError(label); } catch (IllegalArgumentException expected) { checks++; } }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

package dev.vapee.core.gadgets;

import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.lobby.item.LobbyItemRegistrations;
import dev.vapee.core.lobby.item.LobbyItemRegistryFixture;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.ui.UiItemSpec;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public final class GadgetEquipmentHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (int limit = 1; limit <= 3; limit++) testCapacity(limit);
        testSlots();
        System.out.println("GadgetEquipmentHarness passed " + checks + " checks.");
    }

    private static void testCapacity(int limit) throws Exception {
        try (var fixture = new LobbyItemRegistryFixture(LobbyItemRegistrations.inactive(), null)) {
            fixture.configure("""
                    items:
                      gadgets: {}
                      player-menu: {}
                      visibility: {}
                      navigator: {}
                    """);
            fixture.refresh();
            var equipment = equipment(fixture, limit);
            var chest = fixture.storage[4];
            for (int index = 0; index < limit; index++) {
                check(equipment.equip(fixture.player, "fixture-" + index, icon()) == GadgetEquipment.Result.EQUIPPED,
                        "independent test item fits under limit " + limit);
            }
            check(equipment.count(fixture.player) == limit && equipment.equip(fixture.player, "extra", icon())
                    == GadgetEquipment.Result.AT_LIMIT, "limit enforced at " + limit);
            check(equipment.equip(fixture.player, "fixture-0", icon()) == GadgetEquipment.Result.EQUIPPED
                    && equipment.count(fixture.player) == limit, "repeat equip does not duplicate a gadget");
            check(fixture.storage[4] == chest && fixture.ownedCount() == 4 + limit, "chest and ordinary items remain");
            var other = fixture.storage[3];
            equipment.remove(fixture.player, "fixture-0");
            check(fixture.storage[2] == null && fixture.storage[3] == other
                    && equipment.count(fixture.player) == limit - 1, "removal affects only the requested gadget");
        }
    }

    private static void testSlots() throws Exception {
        try (var fixture = new LobbyItemRegistryFixture(LobbyItemRegistrations.inactive(), null)) {
            fixture.configure("""
                    items:
                      gadgets: {}
                      navigator:
                        slot: 2
                    """);
            var equipment = equipment(fixture, 3);
            var foreign = fixture.foreign();
            fixture.storage[3] = foreign;
            check(equipment.equip(fixture.player, "first", icon()) == GadgetEquipment.Result.EQUIPPED
                    && fixture.storage[2] == null && fixture.storage[3] == foreign && fixture.storage[5] != null,
                    "configured slots are reserved even while empty and foreign items are skipped");
            fixture.heldSlot = 5;
            check(equipment.holds(fixture.player, "first") && !equipment.holds(fixture.player, "other"),
                    "held item resolves only to assigned gadget");
            var saddle = fixture.storage[5];
            fixture.heldSlot = 6;
            fixture.storage[6] = saddle;
            check(!equipment.holds(fixture.player, "first"), "matching marker in a different slot is unauthorized");
            equipment.remove(fixture.player, "first");
            check(fixture.storage[5] == null && fixture.storage[6] == null && fixture.storage[3] == foreign,
                    "removal cleans identical session copies without touching foreign items");
            check(equipment.equip(fixture.player, "first", icon()) == GadgetEquipment.Result.EQUIPPED,
                    "reequip allocates a new session");
            var current = fixture.storage[5];
            fixture.heldSlot = 5;
            fixture.storage[5] = saddle;
            check(!equipment.holds(fixture.player, "first"), "old session marker rejected in the correct slot");
            fixture.storage[5] = current;
            for (int slot : List.of(0, 1, 2, 4, 6, 7, 8)) fixture.storage[slot] = foreign;
            check(equipment.equip(fixture.player, "full", icon()) == GadgetEquipment.Result.NO_SLOT
                    && equipment.count(fixture.player) == 1, "full hotbar rejects with state unchanged");
        }
    }

    private static GadgetEquipment equipment(LobbyItemRegistryFixture fixture, int limit) {
        var messages = new MessageService(() -> "", LocalizationService.bundled(fixture.logger));
        return new GadgetEquipment(fixture.plugin, fixture.items, messages, () -> limit);
    }

    private static UiItemSpec icon() {
        return new UiItemSpec(Material.SADDLE, Component.text("Test gadget"), List.of());
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}

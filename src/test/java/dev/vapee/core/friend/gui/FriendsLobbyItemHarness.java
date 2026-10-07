package dev.vapee.core.friend.gui;

import dev.vapee.core.lobby.item.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.Arrays;

/** Productive registration -> real ItemStack/PDC/event -> existing FriendMenu authorization/open flow. */
public final class FriendsLobbyItemHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var friend = new FriendMenuFixture(); var owner = friend.player("LobbyFriends", true);
        var registry = new LobbyItemRegistry();
        LobbyItemRegistrations.register(registry, () -> null, () -> null, () -> friend.menu, () -> null);
        try (var f = new LobbyItemRegistryFixture(registry, owner.player)) {
            var defaults = registry.resolve("friends").orElseThrow().defaults();
            check(defaults.enabled() && defaults.slot() == 1, "friends enabled in verified free default slot 1");
            check(registry.entries().values().stream().filter(e -> e.defaults().slot() == defaults.slot()).count() == 1, "default slot unique among four production items");
            check(defaults.appearance().material() == Material.PLAYER_HEAD && defaults.appearance().selfHead(), "own-player head defaults");
            var plain = PlainTextComponentSerializer.plainText();
            check(plain.serialize(defaults.appearance().name()).equals("Freunde") && defaults.appearance().lore().size() == 3
                    && plain.serialize(defaults.appearance().lore().getFirst()).equals("Verwalte deine Freunde")
                    && plain.serialize(defaults.appearance().lore().get(1)).isEmpty()
                    && plain.serialize(defaults.appearance().lore().get(2)).equals("Klicke zum Öffnen"), "requested productive name and lore");
            f.refresh(); var item = f.storage[1];
            check(item.getType() == Material.PLAYER_HEAD && ((SkullMeta)item.getItemMeta()).getPlayerProfile() == f.profile, "real builder assigns cached own profile");
            check(f.items.getItemId(item).orElseThrow().equals("friends"), "real PDC stable friends id");
            registry.activate();
            owner.permitted = false;
            int saves = friend.repository.saves;
            f.click(item, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(friend.menu.activeCount() == 0 && owner.open == null, "current denied friend authorization blocks real GUI open");
            check(f.sounds == 0 && friend.repository.saves == saves && owner.closeCalls == 0, "denial has no success sound, mutation or foreign inventory close");
            check(owner.received.size() == 1, "existing Friend denial message reused once");
            owner.permitted = true;
            f.click(item, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            check(owner.open.getHolder() instanceof FriendMenuHolder && friend.menu.activeCount() == 1, "allowed friends head opens actual existing FriendMenu");
            check(f.sounds == 1 && friend.repository.saves == saves, "successful open alone produces UI feedback, no friend save");
            owner.permitted = false;
            var retained = owner.open; f.click(item, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(owner.open == retained && f.sounds == 1 && friend.repository.saves == saves, "revoked permission rechecked at every open, no side effects");
            friend.menu.closeOpenInventories();
            var foreign = f.foreign(); f.storage[7] = foreign;
            f.configure("items:\n  friends:\n    slot: 7\n    material: CLOCK\n    name: '<gold>Andere Freunde'\n    lore: ['<gray>Neue Lore', '']\n");
            f.refresh(); f.refresh();
            check(f.items.getItemId(f.storage[1]).isEmpty() && f.storage[7].getType() == Material.CLOCK && f.ownedCount() == 1, "custom slot/material reload/repeat creates no duplicate");
            check(plain.serialize(f.storage[7].getItemMeta().displayName()).equals("Andere Freunde")
                    && plain.serialize(f.storage[7].getItemMeta().lore().getFirst()).equals("Neue Lore"), "custom name/lore reach item after refresh");
            check(Arrays.stream(f.storage).filter(i -> i == foreign).count() == 1, "custom slot reconciliation preserves foreign item");
            var old = f.storage[7]; f.configure("items:\n  friends:\n    enabled: false\n"); f.refresh();
            check(f.ownedCount() == 0 && f.storage[7] == null, "disabled friends removed");
            owner.permitted = true; f.click(old, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(f.sounds == 1 && friend.menu.activeCount() == 0, "disabled stale friends cannot open or sound");
            f.configure("items:\n  friends:\n    enabled: true\n    slot: 6\n"); f.refresh();
            var beforeRemoval = f.storage[6];
            f.configure("items:\n  test:\n    enabled: true\n    slot: 6\n    material: PLAYER_HEAD\n    head-owner: self\n    name: '<aqua>Freundsse'\n"); f.refresh(); f.refresh();
            check(f.ownedCount() == 0 && f.storage[6] == null, "renamed unregistered YAML id does not resurrect or move friends fallback");
            check(f.logs.stream().anyMatch(r -> r.getMessage().contains("items.test") && r.getMessage().contains("ignored")), "renamed unregistered YAML id warns clearly");
            f.click(beforeRemoval, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(friend.menu.activeCount() == 0 && f.sounds == 1, "renamed friends entry blocks retained managed head behavior");
            f.configure("items:\n  friends:\n    enabled: true\n    slot: 6\n"); f.refresh();
            check(f.items.getItemId(f.storage[6]).orElseThrow().equals("friends"), "restored stable friends id returns at requested slot");
            f.configure("items: {}\n"); f.refresh();
            check(f.ownedCount() == 0 && f.storage[6] == null, "deleting friends from explicit items section removes it on refresh");
            f.click(beforeRemoval, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(friend.menu.activeCount() == 0 && f.sounds == 1, "deleted friends blocks retained managed head behavior");
            check(Arrays.stream(f.storage).filter(i -> i == foreign).count() == 1, "rename/delete refresh preserves displaced foreign item");
            f.configure(""); f.refresh(); registry.deactivate(); f.click(f.storage[1], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(friend.menu.activeCount() == 0 && f.sounds == 1, "unavailable runtime fails closed");
            check(!new FriendsLobbyItemAction(() -> null).handleClick(f.player, Action.RIGHT_CLICK_AIR), "unavailable friend menu adapter no NPE");
            check(f.logs.stream().noneMatch(r -> r.getLevel().intValue() >= 1000), "no runtime error/severe");
        }
        System.out.println("FriendsLobbyItemHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

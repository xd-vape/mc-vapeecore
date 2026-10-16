package dev.vapee.core.lobby.item;

import dev.vapee.core.lobby.config.LobbyItemsConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public final class CoreLobbyCompositionHarness {
    static int checks;
    public static void main(String[] args) throws Exception {
        var registry = LobbyItemRegistrations.inactive();
        try (var f = new LobbyItemRegistryFixture(registry,null)) {
            String source = Files.readString(Path.of("src/main/resources/lobby.yml"));
            var configured = f.configure(source); registry.activate(); f.refresh();
            var enabled = configured.entrySet().stream().filter(e -> e.getValue().enabled()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
            check(enabled.equals(Set.of("player-menu","visibility","navigator")) && f.ownedCount() == 3, "resource default places exactly three primary items");
            check(registry.resolve("friends").isPresent() && registry.resolve("settings").isPresent(), "existing shortcuts remain explicit Java registrations");
            check(!configured.get("friends").enabled() && !configured.get("settings").enabled(), "omitted optional shortcuts stay disabled");
            for (var id : enabled) {
                var definition = configured.get(id);
                check(f.items.getItemId(f.storage[definition.slot()]).orElseThrow().equals(id), "PDC routes actual configured slot " + id);
            }
            var head = f.storage[configured.get("player-menu").slot()];
            check(head.getType() == Material.PLAYER_HEAD && ((org.bukkit.inventory.meta.SkullMeta)head.getItemMeta()).getPlayerProfile() == f.profile, "root head uses cached self skin");
            f.configure("items:\n  friends:\n    enabled: true\n    slot: 3\n  settings:\n    enabled: true\n    slot: 5\n"); f.refresh();
            check(f.ownedCount() == 2 && f.items.getItemId(f.storage[3]).orElseThrow().equals("friends") && f.items.getItemId(f.storage[5]).orElseThrow().equals("settings"), "operator can explicitly enable either preserved registered shortcut");
            f.configure("items:\n  not-registered:\n    enabled: true\n    slot: 3\n    command: op someone\n"); f.refresh();
            check(f.ownedCount() == 0, "unknown composition never creates an action or resurrects disabled shortcuts");
            f.configure(source); f.refresh();
            check(f.ownedCount() == 3, "reload returns to three without stale items or duplicate heads");
            var defaults = LobbyItemsConfig.defaults();
            check(defaults.entrySet().stream().filter(e -> e.getValue().enabled()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).equals(enabled), "missing legacy item section uses the same three-item product defaults");
        }
        System.out.println("CoreLobbyCompositionHarness passed " + checks + " checks.");
    }
    static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
}

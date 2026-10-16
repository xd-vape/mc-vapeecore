package dev.vapee.core.lobby.item;

import static dev.vapee.core.quest.QuestCompletionFixture.*;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.lobby.experience.player.PlayerSelectionListener;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.*;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class PlayerSelectionHarness {
    static int checks;
    public static void main(String[] args) throws Exception {
        var registry = LobbyItemRegistrations.inactive(); boolean[] permitted = {true}; UUID owner = UUID.randomUUID();
        var delegate = proxy(Player.class, (name, values) -> switch(name) {
            case "getUniqueId" -> owner; case "getName" -> "Owner"; case "isOnline", "hasPermission" -> permitted[0]; case "locale" -> Locale.US; default -> DEFAULT;
        });
        try (var f = new LobbyItemRegistryFixture(registry,delegate)) {
            Map<UUID,CorePlayer> data = new HashMap<>(); boolean[] fail = {false}; int[] saves = {0}, applied = {0};
            var players = new PlayerService(new PlayerRepository() {
                public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
                public void save(CorePlayer player) { if (fail[0]) throw new IllegalStateException("injected failure"); saves[0]++; data.put(player.getUniqueId(),player); }
                public boolean exists(UUID id) { return data.containsKey(id); }
            },f.logger);
            players.loadPlayer(owner,"Owner"); var settings = new PlayerSettingsService(players);
            UUID targetId = UUID.randomUUID(); boolean[] online = {true}, sameWorld = {true}, targetEligible = {true};
            Player target = proxy(Player.class, (name, values) -> switch(name) {
                case "getUniqueId" -> targetId; case "getName" -> "<red>Selected"; case "isOnline" -> online[0];
                case "getWorld" -> sameWorld[0] ? f.player.getWorld() : null; default -> DEFAULT;
            });
            players.loadPlayer(targetId,"Selected");
            var listener = new PlayerSelectionListener(registry,f.items,settings,
                    p -> f.eligible && p.isOnline() && players.isLoaded(p.getUniqueId()) && (p.getUniqueId().equals(owner) || targetEligible[0]),
                    p -> applied[0]++, new MessageService(() -> "",LocalizationService.bundled(f.logger)), f.logger);
            f.configure("items:\n  player-menu:\n    enabled: true\n    slot: 0\n"); f.refresh(); var head = f.storage[0];
            var event = event(f.player,target,EquipmentSlot.HAND); listener.onInteract(event);
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow(), "inactive registry cannot select");
            registry.activate(); listener.onInteract(event);
            check(event.isCancelled() && settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow(), "owned registered head adds exact target UUID");
            check(applied[0] == 1, "visibility applied only after successful commit");
            listener.onInteract(event(f.player,target,EquipmentSlot.HAND));
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow() && applied[0] == 2, "second intentional gesture removes same UUID");
            int before = saves[0];
            for (var foreign : List.of(new LobbyItemsConfigHarness.TestItem(Material.PLAYER_HEAD),f.marked("otherplugin","player-menu"),f.marked("vapeecore","friends"))) {
                f.storage[0] = foreign; listener.onInteract(event(f.player,target,EquipmentSlot.HAND));
                check(saves[0] == before, "material/name/foreign/wrong registered ID cannot select");
            }
            f.storage[0] = head;
            listener.onInteract(event(f.player,target,EquipmentSlot.OFF_HAND));
            listener.onInteract(event(f.player,f.player,EquipmentSlot.HAND));
            var cancelled = event(f.player,target,EquipmentSlot.HAND); cancelled.setCancelled(true); listener.onInteract(cancelled);
            check(saves[0] == before, "offhand, self and previously cancelled interaction inert");
            permitted[0] = false; listener.onInteract(event(f.player,target,EquipmentSlot.HAND)); permitted[0] = true;
            online[0] = false; listener.onInteract(event(f.player,target,EquipmentSlot.HAND)); online[0] = true;
            sameWorld[0] = false; listener.onInteract(event(f.player,target,EquipmentSlot.HAND)); sameWorld[0] = true;
            f.eligible = false; listener.onInteract(event(f.player,target,EquipmentSlot.HAND)); f.eligible = true;
            targetEligible[0] = false; listener.onInteract(event(f.player,target,EquipmentSlot.HAND)); targetEligible[0] = true;
            check(saves[0] == before, "permission, stale target, non-lobby, owner/target BUILD or Activity gate prevent mutation");
            players.unloadPlayer(targetId); before = saves[0]; listener.onInteract(event(f.player,target,EquipmentSlot.HAND));
            check(saves[0] == before, "target profile unavailable fails closed"); players.loadPlayer(targetId,"Selected");
            fail[0] = true; listener.onInteract(event(f.player,target,EquipmentSlot.HAND)); fail[0] = false;
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow() && applied[0] == 2, "persistence failure rolls back UUID and does not visually apply fake success");
            f.configure("items:\n  player-menu:\n    enabled: false\n"); listener.onInteract(event(f.player,target,EquipmentSlot.HAND));
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow(), "disabled item stale PDC cannot select");
            check(f.logs.stream().noneMatch(log -> log.getLevel().intValue() >= 1000), "controlled save failure has no runtime SEVERE");
        }
        System.out.println("PlayerSelectionHarness passed " + checks + " checks.");
    }
    static PlayerInteractEntityEvent event(Player owner, Player target, EquipmentSlot hand) { return new PlayerInteractEntityEvent(owner,target,hand); }
    static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
}

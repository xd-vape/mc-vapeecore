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
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class PlayerSelectionHarness {
    static int checks;
    public static void main(String[] args) throws Exception {
        var registry = new LobbyItemRegistry();
        int[] opened = {0};
        var defaults = LobbyItemRegistrations.inactive().resolve("player-menu").orElseThrow().defaults();
        registry.register("player-menu", defaults, (viewer, click) -> {
            if (click != org.bukkit.event.block.Action.RIGHT_CLICK_AIR) return false;
            opened[0]++;
            return true;
        });
        boolean[] permitted = {true}; UUID owner = UUID.randomUUID();
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
            var event = attack(f.player,target); listener.onAttack(event);
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow(), "inactive registry cannot select");
            registry.activate(); listener.onAttack(event);
            check(event.isCancelled() && settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow(), "owned registered head adds exact target UUID");
            check(applied[0] == 1, "visibility applied only after successful commit");
            listener.onAttack(attack(f.player,target));
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow() && applied[0] == 2, "second intentional gesture removes same UUID");
            int before = saves[0];
            for (var foreign : List.of(new LobbyItemsConfigHarness.TestItem(Material.PLAYER_HEAD),f.marked("otherplugin","player-menu"),f.marked("vapeecore","friends"))) {
                f.storage[0] = foreign; listener.onAttack(attack(f.player,target));
                check(saves[0] == before, "material/name/foreign/wrong registered ID cannot select");
            }
            f.storage[0] = head;
            var offhand = event(f.player,target,EquipmentSlot.OFF_HAND);
            listener.onInteract(offhand);
            check(!offhand.isCancelled() && opened[0] == 0, "offhand does not route the player menu");
            var right = event(f.player,target,EquipmentSlot.HAND);
            listener.onInteract(right);
            check(right.isCancelled() && opened[0] == 1 && saves[0] == before, "right-click entity routes menu without changing selection");
            var invalidAttack = new PrePlayerAttackEntityEvent(f.player, target, false);
            listener.onAttack(invalidAttack);
            check(invalidAttack.isCancelled() && saves[0] == before, "Paper-rejected attack never toggles selection");
            listener.onAttack(attack(f.player,f.player));
            var cancelled = attack(f.player,target); cancelled.setCancelled(true); listener.onAttack(cancelled);
            check(saves[0] == before, "offhand, self and previously cancelled interaction inert");
            permitted[0] = false; listener.onAttack(attack(f.player,target)); permitted[0] = true;
            online[0] = false; listener.onAttack(attack(f.player,target)); online[0] = true;
            sameWorld[0] = false; listener.onAttack(attack(f.player,target)); sameWorld[0] = true;
            f.eligible = false; listener.onAttack(attack(f.player,target)); f.eligible = true;
            targetEligible[0] = false; listener.onAttack(attack(f.player,target)); targetEligible[0] = true;
            check(saves[0] == before, "permission, stale target, non-lobby, owner/target BUILD or Activity gate prevent mutation");
            players.unloadPlayer(targetId); before = saves[0]; listener.onAttack(attack(f.player,target));
            check(saves[0] == before, "target profile unavailable fails closed"); players.loadPlayer(targetId,"Selected");
            fail[0] = true; listener.onAttack(attack(f.player,target)); fail[0] = false;
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow() && applied[0] == 2, "persistence failure rolls back UUID and does not visually apply fake success");
            f.configure("items:\n  player-menu:\n    enabled: false\n"); listener.onAttack(attack(f.player,target));
            check(!settings.isLobbyAddedVisiblePlayer(owner,targetId).orElseThrow(), "disabled item stale PDC cannot select");
            check(f.logs.stream().noneMatch(log -> log.getLevel().intValue() >= 1000), "controlled save failure has no runtime SEVERE");
        }
        System.out.println("PlayerSelectionHarness passed " + checks + " checks.");
    }
    static PrePlayerAttackEntityEvent attack(Player owner, Player target) { return new PrePlayerAttackEntityEvent(owner,target,true); }
    static PlayerInteractEntityEvent event(Player owner, Player target, EquipmentSlot hand) { return new PlayerInteractEntityEvent(owner,target,hand); }
    static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
}

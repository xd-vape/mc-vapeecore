package dev.vapee.core.lobby.experience.player;

import static dev.vapee.core.i18n.Translations.tr;
import dev.vapee.core.lobby.item.*;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.*;
import dev.vapee.core.settings.command.SettingsCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;

/** A deliberate entity-use gesture; never hooks, uncancels or causes attack damage. */
public final class PlayerSelectionListener implements Listener {
    private final LobbyItemRegistry registry;
    private final LobbyItemService items;
    private final PlayerSettingsService settings;
    private final Predicate<Player> eligible;
    private final Consumer<Player> applyVisibility;
    private final MessageService messages;
    private final Logger logger;

    public PlayerSelectionListener(LobbyItemRegistry registry, LobbyItemService items, PlayerSettingsService settings,
                                   Predicate<Player> eligible, Consumer<Player> applyVisibility,
                                   MessageService messages, Logger logger) {
        this.registry = Objects.requireNonNull(registry); this.items = Objects.requireNonNull(items);
        this.settings = Objects.requireNonNull(settings); this.eligible = Objects.requireNonNull(eligible);
        this.applyVisibility = Objects.requireNonNull(applyVisibility); this.messages = Objects.requireNonNull(messages);
        this.logger = Objects.requireNonNull(logger);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) return;
        Player viewer = event.getPlayer();
        if (!registry.isActive() || !items.isEnabled("player-menu")
                || !items.getItemId(viewer.getInventory().getItemInMainHand()).filter("player-menu"::equals).isPresent()) return;
        event.setCancelled(true);
        if (!viewer.hasPermission(SettingsCommand.PERMISSION) || !eligible.test(viewer) || !eligible.test(target)
                || !viewer.getWorld().equals(target.getWorld()) || viewer.getUniqueId().equals(target.getUniqueId())) return;
        var selected = settings.isLobbyAddedVisiblePlayer(viewer.getUniqueId(), target.getUniqueId());
        if (selected.isEmpty()) return;
        try {
            var result = selected.get()
                    ? settings.removeLobbyVisiblePlayer(viewer.getUniqueId(), target.getUniqueId())
                    : settings.addLobbyVisiblePlayer(viewer.getUniqueId(), target.getUniqueId());
            if (result != AddedVisiblePlayerResult.SUCCESS) return;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not persist selected lobby player for " + viewer.getUniqueId(), exception);
            messages.send(viewer, tr("visibility.save-failed")); return;
        }
        // Persistence has committed. A presentation failure must not pretend that the selection rolled back.
        try { applyVisibility.accept(viewer); }
        catch (RuntimeException exception) { logger.log(Level.WARNING, "Could not apply committed lobby selection", exception); }
        messages.send(viewer, tr(selected.get() ? "player-menu.selection-removed" : "player-menu.selection-added",
                Map.of("player", Component.text(target.getName(), NamedTextColor.AQUA))).color(NamedTextColor.GRAY));
    }
}

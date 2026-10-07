package dev.vapee.core.visibility;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.item.LobbyItemAction;
import dev.vapee.core.lobby.item.LobbyItemDefinition;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Persisted master toggle and dynamic presentation belong to visibility, including deferred block clicks. */
public final class VisibilityLobbyItemAction implements LobbyItemAction {
    private static final long VISIBILITY_TOGGLE_COOLDOWN_TICKS = 10L;
    private final JavaPlugin plugin;
    private final LobbyService lobbyService;
    private final LobbyItemService lobbyItemService;
    private final VisibilityService visibilityService;
    private final PlayerSettingsService playerSettingsService;
    private final MessageService messageService;
    private final Logger logger;
    private final Consumer<Player> feedback;
    private final Predicate<Player> available;
    private final Set<UUID> visibilityToggleCooldowns = new HashSet<>();

    public VisibilityLobbyItemAction(JavaPlugin plugin, LobbyService lobbyService, LobbyItemService lobbyItemService,
                                     VisibilityService visibilityService, PlayerSettingsService playerSettingsService,
                                     MessageService messageService, Consumer<Player> feedback, Predicate<Player> available) {
        this.plugin = Objects.requireNonNull(plugin);
        this.lobbyService = Objects.requireNonNull(lobbyService);
        this.lobbyItemService = Objects.requireNonNull(lobbyItemService);
        this.visibilityService = Objects.requireNonNull(visibilityService);
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService);
        this.messageService = Objects.requireNonNull(messageService);
        this.feedback = Objects.requireNonNull(feedback);
        this.available = Objects.requireNonNull(available);
        this.logger = plugin.getLogger();
    }

    public LobbyItemDefinition.Appearance appearance(Player player, LobbyItemDefinition definition) {
        return playerSettingsService.areLobbyPlayersVisible(player.getUniqueId()).orElse(true)
                ? definition.appearance() : definition.filteredAppearance();
    }

    private void toggleVisibility(Player player) {
        UUID uniqueId = player.getUniqueId();
        if (!beginVisibilityToggleCooldown(player, uniqueId)) {
            return;
        }

        Optional<Boolean> currentValue = playerSettingsService.areLobbyPlayersVisible(uniqueId);
        if (currentValue.isEmpty()) {
            messageService.send(player, "<red>Your player profile is not available.</red>");
            return;
        }

        boolean saved;
        try {
            saved = playerSettingsService.setLobbyPlayersVisible(uniqueId, !currentValue.get());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not save lobby visibility setting for " + uniqueId + ".", exception);
            messageService.send(player, "<red>Your visibility setting could not be saved. Please try again.</red>");
            return;
        }
        if (!saved) {
            messageService.send(player, "<red>Your player profile is not available.</red>");
            return;
        }

        visibilityService.applyViewerPreference(player);
        lobbyItemService.refreshVisibilityItem(player);
        feedback.accept(player);
    }

    @Override public boolean handleClick(Player player, Action action) {
        if (!available.test(player)) return false;
        if (action != Action.RIGHT_CLICK_BLOCK) {
            toggleVisibility(player);
            return false;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!available.test(player) || !player.isOnline() || !lobbyService.isLobbyWorld(player.getWorld())) {
                return;
            }
            toggleVisibility(player);
        });
        return false;
    }

    private boolean beginVisibilityToggleCooldown(Player player, UUID uniqueId) {
        if (!visibilityToggleCooldowns.add(uniqueId)) {
            return false;
        }

        try {
            player.setCooldown(Material.LIME_DYE, (int) VISIBILITY_TOGGLE_COOLDOWN_TICKS);
            player.setCooldown(Material.GRAY_DYE, (int) VISIBILITY_TOGGLE_COOLDOWN_TICKS);
            plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> visibilityToggleCooldowns.remove(uniqueId),
                    VISIBILITY_TOGGLE_COOLDOWN_TICKS
            );
            return true;
        } catch (RuntimeException exception) {
            visibilityToggleCooldowns.remove(uniqueId);
            throw exception;
        }
    }

}

package dev.vapee.core.visibility;

import dev.vapee.core.player.settings.VisibilityMode;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.item.LobbyItemAction;
import dev.vapee.core.lobby.item.LobbyItemDefinition;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettingsService;
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
    private static final long VISIBILITY_TOGGLE_COOLDOWN_TICKS = 3L;
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
    private boolean closed;

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
        return switch (playerSettingsService.getVisibilityMode(player.getUniqueId())
                .orElse(VisibilityMode.ALL)) {
            case ALL -> definition.appearance();
            case FILTERED -> definition.filteredAppearance();
            case HIDDEN -> definition.variant("hidden");
        };
    }

    private void toggleVisibility(Player player) {
        UUID uniqueId = player.getUniqueId();
        if (!beginVisibilityToggleCooldown(uniqueId)) {
            return;
        }

        var currentValue = playerSettingsService.getVisibilityMode(uniqueId);
        if (currentValue.isEmpty()) {
            messageService.send(player, tr("common.profile-unavailable.message"));
            return;
        }

        boolean saved;
        try {
            saved = playerSettingsService.setVisibilityMode(uniqueId, currentValue.get().next());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not save lobby visibility setting for " + uniqueId + ".", exception);
            messageService.send(player, tr("visibility.save-failed"));
            return;
        }
        if (!saved) {
            messageService.send(player, tr("common.profile-unavailable.message"));
            return;
        }

        visibilityService.applyViewerPreference(player);
        lobbyItemService.refreshVisibilityItem(player);
        feedback.accept(player);
    }

    @Override public boolean handleClick(Player player, Action action) {
        if (closed || !available.test(player)) return false;
        if (!supports(action)) return false;
        Runnable use = () -> toggleVisibility(player);
        if (action == Action.RIGHT_CLICK_AIR) {
            use.run();
            return false;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (closed || !available.test(player) || !player.isOnline() || !lobbyService.isLobbyWorld(player.getWorld())) {
                return;
            }
            use.run();
        });
        return false;
    }

    @Override public boolean supports(Action click) {
        return click == Action.RIGHT_CLICK_AIR || click == Action.RIGHT_CLICK_BLOCK;
    }

    public void close() {
        closed = true;
        visibilityToggleCooldowns.clear();
    }

    private boolean beginVisibilityToggleCooldown(UUID uniqueId) {
        if (!visibilityToggleCooldowns.add(uniqueId)) {
            return false;
        }

        try {
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

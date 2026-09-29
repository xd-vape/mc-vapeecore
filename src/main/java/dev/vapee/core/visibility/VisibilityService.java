package dev.vapee.core.visibility;

import dev.vapee.core.lobby.LobbyService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Lobby-only show/hide application and ownership of VapeeCore's hide state. */
public final class VisibilityService {
    public static final String STAFF_MARKER = "vapeecore.visibility.staff";
    private final Plugin plugin;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;
    private final Predicate<World> lobbyWorld;
    private final VisibilityPolicy policy;
    private final Logger logger;
    private final Map<UUID, Set<UUID>> hidden = new HashMap<>();

    public VisibilityService(JavaPlugin plugin, LobbyService lobby, VisibilityPolicy policy) {
        this(plugin, plugin.getServer()::getOnlinePlayers, lobby::isLobbyWorld, policy, plugin.getLogger());
    }

    public VisibilityService(Plugin plugin, Supplier<? extends Collection<? extends Player>> onlinePlayers,
                             Predicate<World> lobbyWorld, VisibilityPolicy policy, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        this.lobbyWorld = Objects.requireNonNull(lobbyWorld, "lobbyWorld");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void synchronizePlayer(Player player) {
        Player joined = Objects.requireNonNull(player, "player");
        if (!inLobby(joined)) return;
        applyViewerPreference(joined);
        for (Player viewer : onlinePlayers.get()) {
            if (viewer.getUniqueId().equals(joined.getUniqueId()) || !inLobby(viewer)) continue;
            apply(viewer, joined);
        }
    }

    public void applyViewerPreference(Player viewer) {
        Player checked = Objects.requireNonNull(viewer, "viewer");
        if (!inLobby(checked)) return;
        for (Player target : onlinePlayers.get()) {
            if (target.getUniqueId().equals(checked.getUniqueId()) || !inLobby(target)) continue;
            apply(checked, target);
        }
    }

    /** Re-evaluates both directions because viewer preferences are intentionally asymmetric. */
    public void refreshPair(UUID first, UUID second) {
        UUID checkedFirst = Objects.requireNonNull(first, "first");
        UUID checkedSecond = Objects.requireNonNull(second, "second");
        if (checkedFirst.equals(checkedSecond)) return;
        Map<UUID, Player> online = onlineById();
        Player firstPlayer = online.get(checkedFirst);
        Player secondPlayer = online.get(checkedSecond);
        if (firstPlayer == null || secondPlayer == null || !inLobby(firstPlayer) || !inLobby(secondPlayer)) return;
        apply(firstPlayer, secondPlayer);
        apply(secondPlayer, firstPlayer);
    }

    public void restorePlayer(Player player) {
        Player checked = Objects.requireNonNull(player, "player");
        UUID id = checked.getUniqueId();
        Map<UUID, Player> online = onlineById();
        online.put(id, checked);
        for (Map.Entry<UUID, Set<UUID>> entry : List.copyOf(hidden.entrySet())) {
            UUID viewerId = entry.getKey();
            for (UUID targetId : Set.copyOf(entry.getValue())) {
                if (viewerId.equals(id) || targetId.equals(id)) restorePair(online, viewerId, targetId);
            }
        }
    }

    public void restoreAll() {
        Map<UUID, Player> online = onlineById();
        for (Map.Entry<UUID, Set<UUID>> entry : List.copyOf(hidden.entrySet())) {
            for (UUID target : Set.copyOf(entry.getValue())) restorePair(online, entry.getKey(), target);
        }
        hidden.clear();
    }

    private void apply(Player viewer, Player target) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (viewerId.equals(targetId)) return;
        if (policy.shouldShow(viewerId, targetId, target.hasPermission(STAFF_MARKER))) {
            viewer.showPlayer(plugin, target);
            forget(viewerId, targetId);
        } else {
            viewer.hidePlayer(plugin, target);
            hidden.computeIfAbsent(viewerId, ignored -> new HashSet<>()).add(targetId);
        }
    }

    private void restorePair(Map<UUID, Player> online, UUID viewerId, UUID targetId) {
        Player viewer = online.get(viewerId);
        Player target = online.get(targetId);
        if (viewer != null && target != null) {
            try {
                viewer.showPlayer(plugin, target);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Could not restore VapeeCore visibility for " + viewerId
                        + " → " + targetId + ".", exception);
            }
        }
        forget(viewerId, targetId);
    }

    private Map<UUID, Player> onlineById() {
        Map<UUID, Player> result = new HashMap<>();
        for (Player player : onlinePlayers.get()) result.put(player.getUniqueId(), player);
        return result;
    }

    private void forget(UUID viewer, UUID target) {
        Set<UUID> targets = hidden.get(viewer);
        if (targets == null) return;
        targets.remove(target);
        if (targets.isEmpty()) hidden.remove(viewer);
    }

    private boolean inLobby(Player player) { return player.isOnline() && lobbyWorld.test(player.getWorld()); }
}

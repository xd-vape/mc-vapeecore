package dev.vapee.core.lobby;

import dev.vapee.core.lobby.config.LobbyConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;

public final class LobbyService {

    private final JavaPlugin plugin;
    private final LobbyConfig lobbyConfig;

    private LobbySpawn spawn;
    private String warnedMissingWorldName;

    public LobbyService(JavaPlugin plugin, LobbyConfig lobbyConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyConfig = Objects.requireNonNull(lobbyConfig, "lobbyConfig");
        this.spawn = lobbyConfig.getSpawn().orElse(null);

        if (spawn == null) {
            plugin.getLogger().info("Lobby spawn is not configured.");
        } else {
            getSpawnLocation().ifPresent(location ->
                    plugin.getLogger().info("Loaded lobby spawn in world '" + location.getWorld().getName() + "'.")
            );
        }
    }

    public Optional<LobbySpawn> getSpawn() {
        return Optional.ofNullable(spawn);
    }

    public Optional<Location> getSpawnLocation() {
        LobbySpawn currentSpawn = spawn;
        if (currentSpawn == null) {
            return Optional.empty();
        }

        World world = plugin.getServer().getWorld(currentSpawn.worldName());
        if (world == null) {
            logMissingWorldOnce(currentSpawn.worldName());
            return Optional.empty();
        }

        warnedMissingWorldName = null;
        return Optional.of(new Location(
                world,
                currentSpawn.x(),
                currentSpawn.y(),
                currentSpawn.z(),
                currentSpawn.yaw(),
                currentSpawn.pitch()
        ));
    }

    public boolean hasSpawn() {
        return spawn != null;
    }

    public boolean setSpawn(Location location) {
        Location validatedLocation = Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(validatedLocation.getWorld(), "location world");
        LobbySpawn newSpawn = new LobbySpawn(
                world.getName(),
                validatedLocation.getX(),
                validatedLocation.getY(),
                validatedLocation.getZ(),
                validatedLocation.getYaw(),
                validatedLocation.getPitch()
        );

        lobbyConfig.saveSpawn(newSpawn);
        spawn = newSpawn;
        warnedMissingWorldName = null;
        return true;
    }

    public boolean teleportToSpawn(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Optional<Location> spawnLocation = getSpawnLocation();
        if (spawnLocation.isEmpty()) {
            return false;
        }

        boolean teleported = validatedPlayer.teleport(
                spawnLocation.get(),
                PlayerTeleportEvent.TeleportCause.PLUGIN
        );
        if (teleported) {
            validatedPlayer.setFallDistance(0.0F);
            validatedPlayer.setVelocity(new Vector());
        }
        return teleported;
    }

    public boolean isLobbyWorld(World world) {
        Objects.requireNonNull(world, "world");
        LobbySpawn currentSpawn = spawn;
        return currentSpawn != null && currentSpawn.worldName().equals(world.getName());
    }

    private void logMissingWorldOnce(String worldName) {
        if (worldName.equals(warnedMissingWorldName)) {
            return;
        }

        warnedMissingWorldName = worldName;
        plugin.getLogger().warning("Lobby spawn world '" + worldName
                + "' is not loaded; lobby teleports are unavailable."
        );
    }
}

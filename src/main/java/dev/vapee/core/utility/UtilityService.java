package dev.vapee.core.utility;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public final class UtilityService {

    public static final float DEFAULT_WALK_SPEED = 0.2F;
    public static final float DEFAULT_FLY_SPEED = 0.1F;

    private final BooleanSupplier primaryThreadCheck;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final ToDoubleFunction<Player> maximumHealthProvider;
    private final Set<UUID> managedFlightPlayers = new HashSet<>();
    private final Set<UUID> managedSpeedPlayers = new HashSet<>();

    public UtilityService(JavaPlugin plugin) {
        this(
                Objects.requireNonNull(plugin, "plugin").getServer()::isPrimaryThread,
                () -> new ArrayList<>(plugin.getServer().getOnlinePlayers()),
                UtilityService::currentMaximumHealth
        );
    }

    public UtilityService(
            BooleanSupplier primaryThreadCheck,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            ToDoubleFunction<Player> maximumHealthProvider
    ) {
        this.primaryThreadCheck = Objects.requireNonNull(primaryThreadCheck, "primaryThreadCheck");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.maximumHealthProvider = Objects.requireNonNull(maximumHealthProvider, "maximumHealthProvider");
    }

    public boolean toggleFlight(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        UUID playerId = validatedPlayer.getUniqueId();
        if (managedFlightPlayers.remove(playerId)) {
            validatedPlayer.setFlying(false);
            validatedPlayer.setAllowFlight(false);
            return false;
        }

        validatedPlayer.setAllowFlight(true);
        managedFlightPlayers.add(playerId);
        return true;
    }

    public UtilitySpeedResult setSpeed(Player player, int level) {
        requirePrimaryThread();
        if (level < 1 || level > 10) {
            throw new IllegalArgumentException("level must be between 1 and 10");
        }

        Player validatedPlayer = Objects.requireNonNull(player, "player");
        UtilitySpeedType type = speedType(validatedPlayer);
        float value = type == UtilitySpeedType.FLY ? flySpeed(level) : walkSpeed(level);
        if (type == UtilitySpeedType.FLY) {
            validatedPlayer.setFlySpeed(value);
        } else {
            validatedPlayer.setWalkSpeed(value);
        }
        managedSpeedPlayers.add(validatedPlayer.getUniqueId());
        return new UtilitySpeedResult(type, level, value);
    }

    public void setGameMode(Player player, GameMode gameMode) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        GameMode validatedGameMode = Objects.requireNonNull(gameMode, "gameMode");
        clearManagedFlight(validatedPlayer);
        validatedPlayer.setGameMode(validatedGameMode);
        if (validatedGameMode == GameMode.SURVIVAL || validatedGameMode == GameMode.ADVENTURE) {
            validatedPlayer.setFlying(false);
            validatedPlayer.setAllowFlight(false);
        }
    }

    public boolean teleport(Player player, Player target) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Player validatedTarget = Objects.requireNonNull(target, "target");
        return validatedPlayer.teleport(validatedTarget.getLocation());
    }

    public void heal(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        validatedPlayer.setHealth(maximumHealthProvider.applyAsDouble(validatedPlayer));
        validatedPlayer.setFireTicks(0);
        validatedPlayer.setFreezeTicks(0);
    }

    public void feed(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        validatedPlayer.setFoodLevel(20);
        validatedPlayer.setSaturation(20.0F);
        validatedPlayer.setExhaustion(0.0F);
    }

    public void clearManagedFlight(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!managedFlightPlayers.remove(validatedPlayer.getUniqueId())) {
            return;
        }
        if (validatedPlayer.getGameMode() == GameMode.SURVIVAL
                || validatedPlayer.getGameMode() == GameMode.ADVENTURE) {
            validatedPlayer.setFlying(false);
            validatedPlayer.setAllowFlight(false);
        }
    }

    public void normalizeTransientState(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        managedFlightPlayers.remove(validatedPlayer.getUniqueId());
        managedSpeedPlayers.remove(validatedPlayer.getUniqueId());
        validatedPlayer.setWalkSpeed(DEFAULT_WALK_SPEED);
        validatedPlayer.setFlySpeed(DEFAULT_FLY_SPEED);
        if (validatedPlayer.getGameMode() == GameMode.SURVIVAL
                || validatedPlayer.getGameMode() == GameMode.ADVENTURE) {
            validatedPlayer.setFlying(false);
            validatedPlayer.setAllowFlight(false);
        }
    }

    public void cleanupPlayer(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        UUID playerId = validatedPlayer.getUniqueId();
        boolean managedFlight = managedFlightPlayers.remove(playerId);
        boolean managedSpeed = managedSpeedPlayers.remove(playerId);
        if (managedFlight && (validatedPlayer.getGameMode() == GameMode.SURVIVAL
                || validatedPlayer.getGameMode() == GameMode.ADVENTURE)) {
            validatedPlayer.setFlying(false);
            validatedPlayer.setAllowFlight(false);
        }
        if (managedSpeed) {
            validatedPlayer.setWalkSpeed(DEFAULT_WALK_SPEED);
            validatedPlayer.setFlySpeed(DEFAULT_FLY_SPEED);
        }
    }

    public void forgetPlayer(UUID playerId) {
        requirePrimaryThread();
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        managedFlightPlayers.remove(validatedPlayerId);
        managedSpeedPlayers.remove(validatedPlayerId);
    }

    public void cleanup() {
        requirePrimaryThread();
        for (Player player : onlinePlayersSupplier.get()) {
            if (player != null && (managedFlightPlayers.contains(player.getUniqueId())
                    || managedSpeedPlayers.contains(player.getUniqueId()))) {
                cleanupPlayer(player);
            }
        }
        managedFlightPlayers.clear();
        managedSpeedPlayers.clear();
    }

    public boolean hasManagedFlight(UUID playerId) {
        return managedFlightPlayers.contains(Objects.requireNonNull(playerId, "playerId"));
    }

    public boolean hasManagedSpeed(UUID playerId) {
        return managedSpeedPlayers.contains(Objects.requireNonNull(playerId, "playerId"));
    }

    public static UtilitySpeedType speedType(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        GameMode gameMode = validatedPlayer.getGameMode();
        return validatedPlayer.isFlying() || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR
                ? UtilitySpeedType.FLY
                : UtilitySpeedType.WALK;
    }

    public static float walkSpeed(int level) {
        return clampSpeed(DEFAULT_WALK_SPEED + ((level - 1) * (0.8F / 9.0F)));
    }

    public static float flySpeed(int level) {
        return clampSpeed(DEFAULT_FLY_SPEED + ((level - 1) * 0.1F));
    }

    private static float clampSpeed(float speed) {
        return Math.max(-1.0F, Math.min(1.0F, speed));
    }

    private static double currentMaximumHealth(Player player) {
        AttributeInstance maximumHealth = Objects.requireNonNull(
                player.getAttribute(Attribute.MAX_HEALTH),
                "player maximum health attribute"
        );
        return maximumHealth.getValue();
    }

    private void requirePrimaryThread() {
        if (!primaryThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Utility player mutations must run on the primary server thread");
        }
    }
}

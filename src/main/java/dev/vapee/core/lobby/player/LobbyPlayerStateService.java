package dev.vapee.core.lobby.player;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.item.LobbyItemService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class LobbyPlayerStateService {

    private final BooleanSupplier primaryThreadCheck;
    private final Predicate<Player> lobbyPlayerCheck;
    private final Supplier<GameMode> normalGameModeSupplier;
    private final Consumer<Player> lobbyItemApplier;
    private final Consumer<Player> lobbyItemRemover;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Set<UUID> buildPlayers = new HashSet<>();

    public LobbyPlayerStateService(
            JavaPlugin plugin,
            LobbyConfig lobbyConfig,
            LobbyService lobbyService,
            LobbyItemService lobbyItemService
    ) {
        this(
                Objects.requireNonNull(plugin, "plugin").getServer()::isPrimaryThread,
                player -> Objects.requireNonNull(lobbyService, "lobbyService")
                        .isLobbyWorld(player.getWorld()),
                Objects.requireNonNull(lobbyConfig, "lobbyConfig")::getPlayerGameMode,
                Objects.requireNonNull(lobbyItemService, "lobbyItemService")::applyLobbyItems,
                lobbyItemService::removeManagedItems,
                () -> new ArrayList<>(plugin.getServer().getOnlinePlayers())
        );
    }

    LobbyPlayerStateService(
            BooleanSupplier primaryThreadCheck,
            Predicate<Player> lobbyPlayerCheck,
            Supplier<GameMode> normalGameModeSupplier,
            Consumer<Player> lobbyItemApplier,
            Consumer<Player> lobbyItemRemover,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier
    ) {
        this.primaryThreadCheck = Objects.requireNonNull(primaryThreadCheck, "primaryThreadCheck");
        this.lobbyPlayerCheck = Objects.requireNonNull(lobbyPlayerCheck, "lobbyPlayerCheck");
        this.normalGameModeSupplier = Objects.requireNonNull(normalGameModeSupplier, "normalGameModeSupplier");
        this.lobbyItemApplier = Objects.requireNonNull(lobbyItemApplier, "lobbyItemApplier");
        this.lobbyItemRemover = Objects.requireNonNull(lobbyItemRemover, "lobbyItemRemover");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
    }

    public LobbyPlayerMode getMode(UUID playerId) {
        return buildPlayers.contains(Objects.requireNonNull(playerId, "playerId"))
                ? LobbyPlayerMode.BUILD
                : LobbyPlayerMode.NORMAL;
    }

    public boolean isBuildMode(UUID playerId) {
        return getMode(playerId) == LobbyPlayerMode.BUILD;
    }

    public void synchronizeJoin(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        buildPlayers.remove(validatedPlayer.getUniqueId());
        if (lobbyPlayerCheck.test(validatedPlayer)) {
            applyNormalMode(validatedPlayer, true);
        } else {
            lobbyItemRemover.accept(validatedPlayer);
            validatedPlayer.setGameMode(normalGameMode());
        }
    }

    public void synchronize(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyPlayerCheck.test(validatedPlayer)) {
            return;
        }

        if (isBuildMode(validatedPlayer.getUniqueId())) {
            lobbyItemRemover.accept(validatedPlayer);
            validatedPlayer.setGameMode(GameMode.CREATIVE);
            return;
        }
        applyNormalMode(validatedPlayer, true);
    }

    /**
     * Hands the normal lobby inventory to an activity without introducing another lobby mode.
     */
    public boolean relinquishNormalInventory(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (isBuildMode(validatedPlayer.getUniqueId())) {
            return false;
        }
        lobbyItemRemover.accept(validatedPlayer);
        clearInventory(validatedPlayer);
        return true;
    }

    public void enterNormalMode(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        buildPlayers.remove(validatedPlayer.getUniqueId());
        applyNormalMode(validatedPlayer, lobbyPlayerCheck.test(validatedPlayer));
    }

    public void enterBuildMode(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyPlayerCheck.test(validatedPlayer)) {
            throw new IllegalStateException("Build mode can only be entered in the lobby world");
        }
        if (isBuildMode(validatedPlayer.getUniqueId())) {
            return;
        }

        try {
            lobbyItemRemover.accept(validatedPlayer);
            clearInventory(validatedPlayer);
            buildPlayers.add(validatedPlayer.getUniqueId());
            validatedPlayer.setGameMode(GameMode.CREATIVE);
        } catch (RuntimeException exception) {
            buildPlayers.remove(validatedPlayer.getUniqueId());
            try {
                applyNormalMode(validatedPlayer, true);
            } catch (RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    public void exitBuildMode(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        clearInventory(validatedPlayer);
        buildPlayers.remove(validatedPlayer.getUniqueId());
        applyNormalMode(validatedPlayer, lobbyPlayerCheck.test(validatedPlayer));
    }

    public void leaveLobby(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        boolean wasBuilding = buildPlayers.remove(validatedPlayer.getUniqueId());
        if (wasBuilding) {
            clearInventory(validatedPlayer);
            validatedPlayer.setGameMode(normalGameMode());
        }
        lobbyItemRemover.accept(validatedPlayer);
    }

    public void handleQuit(UUID playerId) {
        requirePrimaryThread();
        buildPlayers.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public void refreshNormalGameModes() {
        requirePrimaryThread();
        for (Player player : onlinePlayersSupplier.get()) {
            if (player != null
                    && player.isOnline()
                    && lobbyPlayerCheck.test(player)
                    && !isBuildMode(player.getUniqueId())) {
                player.setGameMode(normalGameMode());
            }
        }
    }

    public void cleanup() {
        requirePrimaryThread();
        for (Player player : onlinePlayersSupplier.get()) {
            if (player == null) {
                continue;
            }
            if (isBuildMode(player.getUniqueId())) {
                clearInventory(player);
                player.setGameMode(normalGameMode());
            }
            lobbyItemRemover.accept(player);
        }
        buildPlayers.clear();
    }

    private void applyNormalMode(Player player, boolean applyLobbyItems) {
        clearInventory(player);
        player.setGameMode(normalGameMode());
        if (applyLobbyItems) {
            lobbyItemApplier.accept(player);
        } else {
            lobbyItemRemover.accept(player);
        }
    }

    private void clearInventory(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        inventory.setHeldItemSlot(0);
    }

    private GameMode normalGameMode() {
        return Objects.requireNonNull(normalGameModeSupplier.get(), "normal lobby game mode");
    }

    private void requirePrimaryThread() {
        if (!primaryThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Lobby player state mutations must run on the primary server thread");
        }
    }
}

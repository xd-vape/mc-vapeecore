package dev.vapee.core.seat;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class SeatListener implements Listener {

    private static final String CASUAL_OWNER_PREFIX = "casual:";

    private final SeatService seatService;
    private final SeatPositionResolver positionResolver;
    private final Predicate<Player> lobbyCheck;
    private final Predicate<UUID> buildModeCheck;
    private final Predicate<UUID> activityParticipantCheck;
    private final Predicate<Location> activityVenueCheck;

    public SeatListener(
            LobbyService lobbyService,
            LobbyPlayerStateService lobbyPlayerStateService,
            ActivityService activityService,
            SeatService seatService,
            SeatPositionResolver positionResolver
    ) {
        this(
                seatService,
                positionResolver,
                player -> lobbyService.isLobbyWorld(player.getWorld()),
                lobbyPlayerStateService::isBuildMode,
                activityService::isParticipating,
                location -> activityService.getVenues().stream()
                        .anyMatch(venue -> venue.area().contains(location))
        );
    }

    SeatListener(
            SeatService seatService,
            SeatPositionResolver positionResolver,
            Predicate<Player> lobbyCheck,
            Predicate<UUID> buildModeCheck,
            Predicate<UUID> activityParticipantCheck,
            Predicate<Location> activityVenueCheck
    ) {
        this.seatService = Objects.requireNonNull(seatService, "seatService");
        this.positionResolver = Objects.requireNonNull(positionResolver, "positionResolver");
        this.lobbyCheck = Objects.requireNonNull(lobbyCheck, "lobbyCheck");
        this.buildModeCheck = Objects.requireNonNull(buildModeCheck, "buildModeCheck");
        this.activityParticipantCheck = Objects.requireNonNull(activityParticipantCheck, "activityParticipantCheck");
        this.activityVenueCheck = Objects.requireNonNull(activityVenueCheck, "activityVenueCheck");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (handleCasualInteraction(
                event.getPlayer(),
                event.getClickedBlock(),
                event.getAction(),
                event.getHand(),
                event.getItem()
        )) {
            event.setCancelled(true);
        }
    }

    boolean handleCasualInteraction(
            Player player,
            Block clickedBlock,
            Action action,
            EquipmentSlot hand,
            ItemStack item
    ) {
        return handleCasualInteraction(player, clickedBlock, action, hand, item == null);
    }

    boolean handleCasualInteraction(
            Player player,
            Block clickedBlock,
            Action action,
            EquipmentSlot hand,
            boolean emptyHand
    ) {
        if (action != Action.RIGHT_CLICK_BLOCK || hand != EquipmentSlot.HAND || clickedBlock == null
                || !emptyHand || player.isSneaking()
                || !lobbyCheck.test(player) || player.isInsideVehicle()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (buildModeCheck.test(playerId) || activityParticipantCheck.test(playerId)
                || seatService.isSeated(playerId) || activityVenueCheck.test(clickedBlock.getLocation())
                || !clickedBlock.getRelative(BlockFace.UP).isPassable()) {
            return false;
        }

        Location seatPosition = positionResolver.resolve(clickedBlock, player.getLocation().getYaw()).orElse(null);
        if (seatPosition == null) {
            return false;
        }
        SeatKey key = casualKey(clickedBlock);
        if (!seatService.reserve(key, SeatType.CASUAL, playerId, seatPosition, null)) {
            return false;
        }
        try {
            if (seatService.mountReserved(player)) {
                return true;
            }
        } catch (RuntimeException exception) {
            seatService.release(key);
            throw exception;
        }
        seatService.release(key);
        return false;
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && seatService.isSeatEntity(event.getDismounted())) {
            seatService.handleDismount(player, event.getDismounted());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (seatService.isSeatEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (seatService.isSeatEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        SeatKey key = casualKey(event.getBlock());
        seatService.getAssignment(key)
                .filter(assignment -> assignment.type() == SeatType.CASUAL)
                .ifPresent(ignored -> seatService.release(key));
    }

    static SeatKey casualKey(Block block) {
        Block validated = Objects.requireNonNull(block, "block");
        return new SeatKey(
                CASUAL_OWNER_PREFIX + validated.getWorld().getUID(),
                validated.getX() + ":" + validated.getY() + ":" + validated.getZ()
        );
    }

    void cleanupPlayer(Player player) {
        seatService.releasePlayer(Objects.requireNonNull(player, "player").getUniqueId());
    }
}

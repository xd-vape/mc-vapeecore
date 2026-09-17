package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.location.ActivityPosition;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BlackjackSeatService {

    public static final double SEAT_ENTITY_Y_OFFSET = -1.70D;

    private final JavaPlugin plugin;
    private final ActivityService activityService;
    private final Logger logger;
    private final NamespacedKey seatKey;
    private final NamespacedKey tableKey;
    private final NamespacedKey seatNumberKey;
    private final Consumer<Runnable> syncExecutor;
    private final Map<String, Map<Integer, UUID>> occupantsByTable = new HashMap<>();
    private final Map<UUID, SeatAssignment> assignmentsByPlayer = new HashMap<>();
    private final Map<UUID, ArmorStand> entitiesByPlayer = new HashMap<>();
    private final Set<UUID> programmaticDismounts = new HashSet<>();

    public BlackjackSeatService(JavaPlugin plugin, ActivityService activityService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.activityService = Objects.requireNonNull(activityService, "activityService");
        this.logger = plugin.getLogger();
        this.seatKey = new NamespacedKey(plugin, "blackjack_seat");
        this.tableKey = new NamespacedKey(plugin, "blackjack_table");
        this.seatNumberKey = new NamespacedKey(plugin, "blackjack_seat_number");
        this.syncExecutor = action -> plugin.getServer().getScheduler().runTask(plugin, action);
    }

    BlackjackSeatService(
            ActivityService activityService,
            Logger logger,
            Consumer<Runnable> syncExecutor
    ) {
        this.plugin = null;
        this.activityService = Objects.requireNonNull(activityService, "activityService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.seatKey = null;
        this.tableKey = null;
        this.seatNumberKey = null;
        this.syncExecutor = Objects.requireNonNull(syncExecutor, "syncExecutor");
    }

    public Optional<SeatAssignment> reserveLowestFreeSeat(
            BlackjackTableDefinition definition,
            UUID playerId
    ) {
        BlackjackTableDefinition validatedDefinition = Objects.requireNonNull(definition, "definition");
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        SeatAssignment existing = assignmentsByPlayer.get(validatedPlayerId);
        if (existing != null) {
            return existing.tableId().equals(validatedDefinition.id())
                    ? Optional.of(existing)
                    : Optional.empty();
        }

        Map<Integer, UUID> occupants = occupantsByTable.computeIfAbsent(
                validatedDefinition.id(),
                ignored -> new HashMap<>()
        );
        for (BlackjackSeat seat : validatedDefinition.seats()) {
            if (occupants.containsKey(seat.number())) {
                continue;
            }
            SeatAssignment assignment = new SeatAssignment(
                    validatedDefinition.id(),
                    seat.number(),
                    seat.position()
            );
            occupants.put(seat.number(), validatedPlayerId);
            assignmentsByPlayer.put(validatedPlayerId, assignment);
            return Optional.of(assignment);
        }
        return Optional.empty();
    }

    public void mountReservedPlayer(Player player) {
        if (plugin == null) {
            throw new IllegalStateException("Seat entity runtime is not available");
        }
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        UUID playerId = validatedPlayer.getUniqueId();
        SeatAssignment assignment = Objects.requireNonNull(
                assignmentsByPlayer.get(playerId),
                "Player has no reserved blackjack seat"
        );
        if (entitiesByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Player already has a blackjack seat entity");
        }

        Location configuredLocation = assignment.position().toLocation(plugin.getServer())
                .orElseThrow(() -> new IllegalStateException(
                        "Blackjack seat world '" + assignment.position().worldName() + "' is not loaded"
                ));
        Location entityLocation = configuredLocation.clone().add(0.0D, SEAT_ENTITY_Y_OFFSET, 0.0D);
        ArmorStand seatEntity = configuredLocation.getWorld().spawn(
                entityLocation,
                ArmorStand.class,
                armorStand -> configureSeatEntity(armorStand, assignment)
        );
        boolean mounted = false;
        try {
            mounted = seatEntity.addPassenger(validatedPlayer);
            if (!mounted) {
                throw new IllegalStateException("The player could not be mounted on the blackjack seat");
            }
            entitiesByPlayer.put(playerId, seatEntity);
        } finally {
            if (!mounted) {
                seatEntity.remove();
            }
        }
    }

    public Optional<SeatAssignment> getAssignment(UUID playerId) {
        return Optional.ofNullable(assignmentsByPlayer.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public Optional<Integer> getSeatNumber(UUID playerId) {
        return getAssignment(playerId).map(SeatAssignment::seatNumber);
    }

    public void releaseSeat(UUID playerId) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        SeatAssignment assignment = assignmentsByPlayer.remove(validatedPlayerId);
        if (assignment != null) {
            Map<Integer, UUID> occupants = occupantsByTable.get(assignment.tableId());
            if (occupants != null) {
                occupants.remove(assignment.seatNumber(), validatedPlayerId);
                if (occupants.isEmpty()) {
                    occupantsByTable.remove(assignment.tableId());
                }
            }
        }

        ArmorStand entity = entitiesByPlayer.remove(validatedPlayerId);
        if (entity == null) {
            return;
        }
        programmaticDismounts.add(validatedPlayerId);
        try {
            for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
                if (passenger.getUniqueId().equals(validatedPlayerId)) {
                    entity.removePassenger(passenger);
                }
            }
            entity.remove();
        } finally {
            programmaticDismounts.remove(validatedPlayerId);
        }
    }

    public void handleDismount(Player player, Entity dismountedEntity) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!isManagedSeat(dismountedEntity)
                || programmaticDismounts.contains(validatedPlayer.getUniqueId())
                || !assignmentsByPlayer.containsKey(validatedPlayer.getUniqueId())) {
            return;
        }
        handleManagedDismount(validatedPlayer);
    }

    void handleManagedDismount(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (programmaticDismounts.contains(validatedPlayer.getUniqueId())
                || !assignmentsByPlayer.containsKey(validatedPlayer.getUniqueId())) {
            return;
        }
        UUID playerId = validatedPlayer.getUniqueId();
        syncExecutor.accept(() -> {
            if (programmaticDismounts.contains(playerId) || !assignmentsByPlayer.containsKey(playerId)) {
                return;
            }
            activityService.leaveCurrentSession(playerId, ActivityLeaveReason.VOLUNTARY);
        });
    }

    public boolean isManagedSeat(Entity entity) {
        return seatKey != null
                && entity != null
                && entity.getPersistentDataContainer().has(seatKey, PersistentDataType.BYTE);
    }

    public void cleanupTable(String tableId) {
        String validatedTableId = Objects.requireNonNull(tableId, "tableId");
        assignmentsByPlayer.entrySet().stream()
                .filter(entry -> entry.getValue().tableId().equals(validatedTableId))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::releaseSeat);
        removeMarkedEntities(validatedTableId);
    }

    public int cleanupStaleSeats() {
        if (plugin == null) {
            return 0;
        }
        return removeMarkedEntities(null);
    }

    public void shutdown() {
        new ArrayList<>(assignmentsByPlayer.keySet()).forEach(this::releaseSeat);
        cleanupStaleSeats();
        occupantsByTable.clear();
        assignmentsByPlayer.clear();
        entitiesByPlayer.clear();
        programmaticDismounts.clear();
    }

    private void configureSeatEntity(ArmorStand armorStand, SeatAssignment assignment) {
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);
        armorStand.setCollidable(false);
        armorStand.setPersistent(false);
        armorStand.setBasePlate(false);
        armorStand.setArms(false);
        armorStand.setMarker(false);
        armorStand.setCanMove(false);
        armorStand.setAI(false);
        PersistentDataContainer data = armorStand.getPersistentDataContainer();
        data.set(seatKey, PersistentDataType.BYTE, (byte) 1);
        data.set(tableKey, PersistentDataType.STRING, assignment.tableId());
        data.set(seatNumberKey, PersistentDataType.INTEGER, assignment.seatNumber());
    }

    private int removeMarkedEntities(String tableId) {
        if (plugin == null) {
            return 0;
        }
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (ArmorStand armorStand : world.getEntitiesByClass(ArmorStand.class)) {
                PersistentDataContainer data = armorStand.getPersistentDataContainer();
                if (!data.has(seatKey, PersistentDataType.BYTE)) {
                    continue;
                }
                if (tableId != null
                        && !tableId.equals(data.get(tableKey, PersistentDataType.STRING))) {
                    continue;
                }
                try {
                    armorStand.remove();
                    removed++;
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not remove a managed blackjack seat entity.", exception);
                }
            }
        }
        return removed;
    }

    public record SeatAssignment(String tableId, int seatNumber, ActivityPosition position) {

        public SeatAssignment {
            tableId = Objects.requireNonNull(tableId, "tableId");
            if (seatNumber < 1 || seatNumber > 5) {
                throw new IllegalArgumentException("seatNumber must be between 1 and 5");
            }
            position = Objects.requireNonNull(position, "position");
        }
    }
}

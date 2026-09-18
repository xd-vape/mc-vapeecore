package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.seat.SeatKey;
import dev.vapee.core.seat.SeatService;
import dev.vapee.core.seat.SeatType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class BlackjackSeatService {

    private static final String OWNER_PREFIX = "blackjack:";

    private final ActivityService activityService;
    private final SeatRuntime seatRuntime;
    private final Function<ActivityPosition, Optional<Location>> locationResolver;
    private final Map<String, Map<Integer, UUID>> occupantsByTable = new HashMap<>();
    private final Map<UUID, SeatAssignment> assignmentsByPlayer = new HashMap<>();

    public BlackjackSeatService(JavaPlugin plugin, ActivityService activityService, SeatService seatService) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        SeatService validatedSeatService = Objects.requireNonNull(seatService, "seatService");
        this.activityService = Objects.requireNonNull(activityService, "activityService");
        this.seatRuntime = new SeatRuntime() {
            public boolean reserve(SeatKey key, UUID playerId, Location location,
                                   Consumer<UUID> dismountHandler) {
                return validatedSeatService.reserve(key, SeatType.MANAGED, playerId, location, dismountHandler);
            }
            public boolean mount(Player player) { return validatedSeatService.mountReserved(player); }
            public boolean releasePlayer(UUID playerId) { return validatedSeatService.releasePlayer(playerId); }
            public int releaseOwner(String owner) { return validatedSeatService.releaseOwner(owner); }
        };
        this.locationResolver = position -> position.toLocation(validatedPlugin.getServer());
    }

    BlackjackSeatService(
            ActivityService activityService,
            SeatRuntime seatRuntime,
            Function<ActivityPosition, Optional<Location>> locationResolver
    ) {
        this.activityService = Objects.requireNonNull(activityService, "activityService");
        this.seatRuntime = Objects.requireNonNull(seatRuntime, "seatRuntime");
        this.locationResolver = Objects.requireNonNull(locationResolver, "locationResolver");
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
            Location location = locationResolver.apply(seat.position()).orElse(null);
            if (location == null || !seatRuntime.reserve(
                    seatKey(validatedDefinition.id(), seat.number()),
                    validatedPlayerId,
                    location,
                    id -> activityService.leaveCurrentSession(id, ActivityLeaveReason.VOLUNTARY)
            )) {
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
        if (occupants.isEmpty()) {
            occupantsByTable.remove(validatedDefinition.id());
        }
        return Optional.empty();
    }

    public void mountReservedPlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!assignmentsByPlayer.containsKey(validatedPlayer.getUniqueId())) {
            throw new IllegalStateException("Player has no reserved blackjack seat");
        }
        if (!seatRuntime.mount(validatedPlayer)) {
            throw new IllegalStateException("The player could not be mounted on the blackjack seat");
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

        seatRuntime.releasePlayer(validatedPlayerId);
    }

    public void cleanupTable(String tableId) {
        String validatedTableId = Objects.requireNonNull(tableId, "tableId");
        assignmentsByPlayer.entrySet().stream()
                .filter(entry -> entry.getValue().tableId().equals(validatedTableId))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::releaseSeat);
        seatRuntime.releaseOwner(owner(validatedTableId));
        occupantsByTable.remove(validatedTableId);
    }

    public void shutdown() {
        assignmentsByPlayer.keySet().stream().toList().forEach(this::releaseSeat);
        occupantsByTable.clear();
        assignmentsByPlayer.clear();
    }

    static SeatKey seatKey(String tableId, int seatNumber) {
        return new SeatKey(owner(tableId), Integer.toString(seatNumber));
    }

    private static String owner(String tableId) {
        return OWNER_PREFIX + Objects.requireNonNull(tableId, "tableId");
    }

    interface SeatRuntime {
        boolean reserve(SeatKey key, UUID playerId, Location location, Consumer<UUID> dismountHandler);

        boolean mount(Player player);

        boolean releasePlayer(UUID playerId);

        int releaseOwner(String owner);
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

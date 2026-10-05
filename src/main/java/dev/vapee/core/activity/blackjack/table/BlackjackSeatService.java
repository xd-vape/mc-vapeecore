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
import java.util.List;
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
            public boolean release(SeatKey key, UUID playerId) {
                // A retained failed assignment must not release a later seat owned by another feature.
                return validatedSeatService.getAssignment(key)
                        .filter(assignment -> assignment.playerId().equals(playerId))
                        .map(assignment -> validatedSeatService.release(key)).orElse(false);
            }
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

        for (BlackjackSeat seat : validatedDefinition.seats()) {
            Optional<SeatAssignment> assignment = reserveSeat(
                    validatedDefinition,
                    seat.number(),
                    validatedPlayerId
            );
            if (assignment.isPresent()) return assignment;
        }
        return Optional.empty();
    }

    public Optional<SeatAssignment> reserveSeat(
            BlackjackTableDefinition definition,
            int seatNumber,
            UUID playerId
    ) {
        BlackjackTableDefinition validatedDefinition = Objects.requireNonNull(definition, "definition");
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        SeatAssignment existing = assignmentsByPlayer.get(validatedPlayerId);
        if (existing != null) {
            return existing.tableId().equals(validatedDefinition.id())
                    && existing.seatNumber() == seatNumber ? Optional.of(existing) : Optional.empty();
        }
        BlackjackSeat seat = validatedDefinition.seats().stream()
                .filter(candidate -> candidate.number() == seatNumber)
                .findFirst()
                .orElse(null);
        if (seat == null) return Optional.empty();
        Map<Integer, UUID> occupants = occupantsByTable.computeIfAbsent(
                validatedDefinition.id(), ignored -> new HashMap<>()
        );
        if (occupants.containsKey(seatNumber)) return Optional.empty();
        Location location = locationResolver.apply(seat.position()).orElse(null);
        if (location == null || !seatRuntime.reserve(
                seatKey(validatedDefinition.id(), seatNumber),
                validatedPlayerId,
                location,
                id -> activityService.leaveCurrentSession(id, ActivityLeaveReason.VOLUNTARY)
        )) {
            if (occupants.isEmpty()) occupantsByTable.remove(validatedDefinition.id());
            return Optional.empty();
        }
        SeatAssignment assignment = new SeatAssignment(validatedDefinition.id(), seatNumber, seat.position());
        occupants.put(seatNumber, validatedPlayerId);
        assignmentsByPlayer.put(validatedPlayerId, assignment);
        return Optional.of(assignment);
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
        SeatAssignment assignment = assignmentsByPlayer.get(validatedPlayerId);
        if (assignment == null) return;
        try {
            seatRuntime.release(seatKey(assignment.tableId(), assignment.seatNumber()), validatedPlayerId);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("BlackjackSeat release failed for "
                    + seatKey(assignment.tableId(), assignment.seatNumber())
                    + ", player=" + validatedPlayerId, exception);
        }
        assignmentsByPlayer.remove(validatedPlayerId);
        Map<Integer, UUID> occupants = occupantsByTable.get(assignment.tableId());
        if (occupants != null) {
            occupants.remove(assignment.seatNumber(), validatedPlayerId);
            if (occupants.isEmpty()) {
                occupantsByTable.remove(assignment.tableId());
            }
        }
    }

    public void cleanupTable(String tableId) {
        String validatedTableId = Objects.requireNonNull(tableId, "tableId");
        releaseAll(assignmentsByPlayer.entrySet().stream()
                .filter(entry -> entry.getValue().tableId().equals(validatedTableId))
                .map(Map.Entry::getKey)
                .toList());
        try {
            seatRuntime.releaseOwner(owner(validatedTableId));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("BlackjackSeat releaseOwner failed for " + owner(validatedTableId), exception);
        }
        occupantsByTable.remove(validatedTableId);
    }

    public void shutdown() {
        releaseAll(assignmentsByPlayer.keySet().stream().toList());
    }

    private void releaseAll(List<UUID> players) {
        IllegalStateException failure = null;
        for (UUID player : players) {
            try {
                releaseSeat(player);
            } catch (RuntimeException exception) {
                if (failure == null) failure = new IllegalStateException("BlackjackSeat cleanup incomplete");
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
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

        boolean release(SeatKey key, UUID playerId);

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

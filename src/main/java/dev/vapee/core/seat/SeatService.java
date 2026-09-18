package dev.vapee.core.seat;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SeatService {

    public static final double SEAT_ENTITY_Y_OFFSET = -1.70D;
    public static final String SEAT_PDC = "seat";
    public static final String OWNER_PDC = "seat_owner";
    public static final String ID_PDC = "seat_id";
    public static final String TYPE_PDC = "seat_type";

    private final BooleanSupplier primaryThreadCheck;
    private final BooleanSupplier pluginEnabledCheck;
    private final Predicate<UUID> playerOnlineCheck;
    private final Consumer<Runnable> delayedExecutor;
    private final SeatEntityGateway entityGateway;
    private final Map<SeatKey, SeatAssignment> assignmentsBySeat = new HashMap<>();
    private final Map<UUID, SeatKey> seatsByPlayer = new HashMap<>();
    private final Map<SeatKey, Consumer<UUID>> dismountHandlers = new HashMap<>();
    private final Set<UUID> programmaticDismounts = new HashSet<>();

    public SeatService(JavaPlugin plugin) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        this.primaryThreadCheck = validatedPlugin.getServer()::isPrimaryThread;
        this.pluginEnabledCheck = validatedPlugin::isEnabled;
        this.playerOnlineCheck = playerId -> {
            Player player = validatedPlugin.getServer().getPlayer(playerId);
            return player != null && player.isOnline();
        };
        this.delayedExecutor = action -> validatedPlugin.getServer().getScheduler()
                .runTask(validatedPlugin, action);
        this.entityGateway = new BukkitSeatEntityGateway(validatedPlugin);
    }

    SeatService(
            BooleanSupplier primaryThreadCheck,
            BooleanSupplier pluginEnabledCheck,
            Predicate<UUID> playerOnlineCheck,
            Consumer<Runnable> delayedExecutor,
            SeatEntityGateway entityGateway
    ) {
        this.primaryThreadCheck = Objects.requireNonNull(primaryThreadCheck, "primaryThreadCheck");
        this.pluginEnabledCheck = Objects.requireNonNull(pluginEnabledCheck, "pluginEnabledCheck");
        this.playerOnlineCheck = Objects.requireNonNull(playerOnlineCheck, "playerOnlineCheck");
        this.delayedExecutor = Objects.requireNonNull(delayedExecutor, "delayedExecutor");
        this.entityGateway = Objects.requireNonNull(entityGateway, "entityGateway");
    }

    public boolean reserve(
            SeatKey key,
            SeatType type,
            UUID playerId,
            Location seatPosition,
            Consumer<UUID> dismountHandler
    ) {
        requirePrimaryThread();
        SeatKey validatedKey = Objects.requireNonNull(key, "key");
        SeatType validatedType = Objects.requireNonNull(type, "type");
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        Location location = validateLocation(seatPosition);
        if (assignmentsBySeat.containsKey(validatedKey) || seatsByPlayer.containsKey(validatedPlayerId)) {
            return false;
        }

        World world = Objects.requireNonNull(location.getWorld(), "seatPosition world");
        SeatAssignment assignment = new SeatAssignment(
                validatedKey,
                validatedType,
                validatedPlayerId,
                world.getUID(),
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                null
        );
        assignmentsBySeat.put(validatedKey, assignment);
        seatsByPlayer.put(validatedPlayerId, validatedKey);
        if (dismountHandler != null) {
            dismountHandlers.put(validatedKey, dismountHandler);
        }
        return true;
    }

    public boolean mountReserved(Player player) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        SeatKey key = seatsByPlayer.get(validatedPlayer.getUniqueId());
        if (key == null || validatedPlayer.isInsideVehicle()) {
            return false;
        }
        SeatAssignment assignment = assignmentsBySeat.get(key);
        if (assignment == null || assignment.isMounted()) {
            return false;
        }

        UUID entityId = entityGateway.spawnAndMount(assignment, validatedPlayer);
        if (entityId == null) {
            return false;
        }
        assignmentsBySeat.put(key, assignment.withSeatEntity(entityId));
        return true;
    }

    public Optional<SeatAssignment> getAssignment(UUID playerId) {
        SeatKey key = seatsByPlayer.get(Objects.requireNonNull(playerId, "playerId"));
        return key == null ? Optional.empty() : Optional.ofNullable(assignmentsBySeat.get(key));
    }

    public Optional<SeatAssignment> getAssignment(SeatKey key) {
        return Optional.ofNullable(assignmentsBySeat.get(Objects.requireNonNull(key, "key")));
    }

    public boolean isOccupied(SeatKey key) {
        return assignmentsBySeat.containsKey(Objects.requireNonNull(key, "key"));
    }

    public boolean isSeated(UUID playerId) {
        return seatsByPlayer.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    public int getAssignmentCount() {
        return assignmentsBySeat.size();
    }

    public boolean releasePlayer(UUID playerId) {
        requirePrimaryThread();
        SeatKey key = seatsByPlayer.get(Objects.requireNonNull(playerId, "playerId"));
        return key != null && release(key);
    }

    public boolean release(SeatKey key) {
        requirePrimaryThread();
        SeatKey validatedKey = Objects.requireNonNull(key, "key");
        SeatAssignment assignment = assignmentsBySeat.remove(validatedKey);
        dismountHandlers.remove(validatedKey);
        if (assignment == null) {
            return false;
        }
        seatsByPlayer.remove(assignment.playerId(), validatedKey);
        assignment.seatEntity().ifPresent(entityId -> {
            programmaticDismounts.add(assignment.playerId());
            try {
                entityGateway.remove(entityId, assignment.playerId());
            } finally {
                programmaticDismounts.remove(assignment.playerId());
            }
        });
        return true;
    }

    public int releaseOwner(String owner) {
        requirePrimaryThread();
        String validatedOwner = requireText(owner, "owner");
        var keys = assignmentsBySeat.keySet().stream()
                .filter(key -> key.owner().equals(validatedOwner))
                .toList();
        keys.forEach(this::release);
        return keys.size();
    }

    public void handleDismount(Player player, Entity dismountedEntity) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Entity validatedEntity = Objects.requireNonNull(dismountedEntity, "dismountedEntity");
        UUID playerId = validatedPlayer.getUniqueId();
        if (programmaticDismounts.contains(playerId) || !entityGateway.isSeatEntity(validatedEntity)) {
            return;
        }
        SeatAssignment assignment = getAssignment(playerId).orElse(null);
        if (assignment == null || assignment.seatEntity().isEmpty()
                || !assignment.seatEntity().orElseThrow().equals(validatedEntity.getUniqueId())) {
            return;
        }

        SeatKey expectedKey = assignment.key();
        UUID expectedEntityId = validatedEntity.getUniqueId();
        delayedExecutor.accept(() -> completeVoluntaryDismount(playerId, expectedKey, expectedEntityId));
    }

    public boolean isSeatEntity(Entity entity) {
        return entity != null && entityGateway.isSeatEntity(entity);
    }

    public int cleanupStaleSeats() {
        requirePrimaryThread();
        return entityGateway.cleanupStaleSeats();
    }

    public void cleanup() {
        requirePrimaryThread();
        new ArrayList<>(assignmentsBySeat.keySet()).forEach(this::release);
        assignmentsBySeat.clear();
        seatsByPlayer.clear();
        dismountHandlers.clear();
        programmaticDismounts.clear();
    }

    private void completeVoluntaryDismount(UUID playerId, SeatKey expectedKey, UUID expectedEntityId) {
        if (!pluginEnabledCheck.getAsBoolean() || !playerOnlineCheck.test(playerId)
                || programmaticDismounts.contains(playerId)) {
            return;
        }
        SeatAssignment current = getAssignment(playerId).orElse(null);
        if (current == null || !current.key().equals(expectedKey)
                || current.seatEntity().isEmpty()
                || !current.seatEntity().orElseThrow().equals(expectedEntityId)) {
            return;
        }
        Consumer<UUID> callback = dismountHandlers.get(expectedKey);
        SeatType type = current.type();
        release(expectedKey);
        if (type == SeatType.MANAGED && callback != null) {
            callback.accept(playerId);
        }
    }

    private Location validateLocation(Location location) {
        Location validated = Objects.requireNonNull(location, "seatPosition").clone();
        if (validated.getWorld() == null) {
            throw new IllegalArgumentException("seatPosition world must not be null");
        }
        if (!Double.isFinite(validated.getX()) || !Double.isFinite(validated.getY())
                || !Double.isFinite(validated.getZ()) || !Float.isFinite(validated.getYaw())
                || !Float.isFinite(validated.getPitch())) {
            throw new IllegalArgumentException("seatPosition coordinates and rotation must be finite");
        }
        return validated;
    }

    private void requirePrimaryThread() {
        if (!primaryThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Seat runtime mutations must run on the primary server thread");
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    interface SeatEntityGateway {
        UUID spawnAndMount(SeatAssignment assignment, Player player);

        void remove(UUID entityId, UUID playerId);

        boolean isSeatEntity(Entity entity);

        int cleanupStaleSeats();
    }

    private static final class BukkitSeatEntityGateway implements SeatEntityGateway {

        private final Server server;
        private final NamespacedKey seatKey;
        private final NamespacedKey ownerKey;
        private final NamespacedKey idKey;
        private final NamespacedKey typeKey;
        private final NamespacedKey legacySeatKey;

        private BukkitSeatEntityGateway(JavaPlugin plugin) {
            this.server = plugin.getServer();
            this.seatKey = new NamespacedKey(plugin, SEAT_PDC);
            this.ownerKey = new NamespacedKey(plugin, OWNER_PDC);
            this.idKey = new NamespacedKey(plugin, ID_PDC);
            this.typeKey = new NamespacedKey(plugin, TYPE_PDC);
            this.legacySeatKey = new NamespacedKey(plugin, "blackjack_seat");
        }

        @Override
        public UUID spawnAndMount(SeatAssignment assignment, Player player) {
            World world = server.getWorld(assignment.worldId());
            if (world == null) {
                throw new IllegalStateException("Seat world '" + assignment.worldName() + "' is not loaded");
            }
            Location location = new Location(
                    world,
                    assignment.x(),
                    assignment.y() + SEAT_ENTITY_Y_OFFSET,
                    assignment.z(),
                    assignment.yaw(),
                    assignment.pitch()
            );
            ArmorStand entity = world.spawn(location, ArmorStand.class);
            boolean mounted = false;
            try {
                configure(entity, assignment);
                mounted = entity.addPassenger(player);
                return mounted ? entity.getUniqueId() : null;
            } finally {
                if (!mounted) {
                    entity.remove();
                }
            }
        }

        @Override
        public void remove(UUID entityId, UUID playerId) {
            Entity entity = server.getEntity(entityId);
            if (entity == null) {
                return;
            }
            for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
                if (passenger.getUniqueId().equals(playerId)) {
                    entity.removePassenger(passenger);
                }
            }
            entity.remove();
        }

        @Override
        public boolean isSeatEntity(Entity entity) {
            return entity instanceof ArmorStand
                    && entity.getPersistentDataContainer().has(seatKey, PersistentDataType.BYTE);
        }

        @Override
        public int cleanupStaleSeats() {
            int removed = 0;
            for (World world : server.getWorlds()) {
                for (ArmorStand armorStand : world.getEntitiesByClass(ArmorStand.class)) {
                    PersistentDataContainer data = armorStand.getPersistentDataContainer();
                    if (!data.has(seatKey, PersistentDataType.BYTE)
                            && !data.has(legacySeatKey, PersistentDataType.BYTE)) {
                        continue;
                    }
                    armorStand.remove();
                    removed++;
                }
            }
            return removed;
        }

        private void configure(ArmorStand entity, SeatAssignment assignment) {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setPersistent(false);
            entity.setBasePlate(false);
            entity.setArms(false);
            entity.setMarker(false);
            entity.setCanMove(false);
            entity.setAI(false);
            PersistentDataContainer data = entity.getPersistentDataContainer();
            data.set(seatKey, PersistentDataType.BYTE, (byte) 1);
            data.set(ownerKey, PersistentDataType.STRING, assignment.key().owner());
            data.set(idKey, PersistentDataType.STRING, assignment.key().id());
            data.set(typeKey, PersistentDataType.STRING, assignment.type().name());
        }
    }
}

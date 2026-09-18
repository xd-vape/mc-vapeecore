package dev.vapee.core.worlddisplay;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class WorldDisplayService {

    public static final String DISPLAY_PDC = "world_display";
    public static final String OWNER_PDC = "world_display_owner";
    public static final String ID_PDC = "world_display_id";
    public static final String TYPE_PDC = "world_display_type";

    private final BooleanSupplier primaryThreadCheck;
    private final DisplayGateway gateway;
    private final Map<WorldDisplayKey, WorldDisplayHandle> handles = new HashMap<>();

    public WorldDisplayService(JavaPlugin plugin) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        this.primaryThreadCheck = validatedPlugin.getServer()::isPrimaryThread;
        this.gateway = new BukkitDisplayGateway(validatedPlugin);
    }

    WorldDisplayService(BooleanSupplier primaryThreadCheck, DisplayGateway gateway) {
        this.primaryThreadCheck = Objects.requireNonNull(primaryThreadCheck, "primaryThreadCheck");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public WorldDisplayHandle createText(
            WorldDisplayKey key,
            Location location,
            Component text,
            Consumer<TextDisplay> configurator
    ) {
        requirePrimaryThread();
        WorldDisplayKey validatedKey = requireAvailableKey(key);
        UUID entityId = gateway.createText(
                validatedKey,
                validateLocation(location),
                Objects.requireNonNull(text, "text"),
                configurator == null ? ignored -> { } : configurator
        );
        return register(validatedKey, entityId, WorldDisplayHandle.Type.TEXT);
    }

    public WorldDisplayHandle createItem(
            WorldDisplayKey key,
            Location location,
            ItemStack item,
            Consumer<ItemDisplay> configurator
    ) {
        requirePrimaryThread();
        WorldDisplayKey validatedKey = requireAvailableKey(key);
        UUID entityId = gateway.createItem(
                validatedKey,
                validateLocation(location),
                Objects.requireNonNull(item, "item").clone(),
                configurator == null ? ignored -> { } : configurator
        );
        return register(validatedKey, entityId, WorldDisplayHandle.Type.ITEM);
    }

    public boolean updateText(WorldDisplayKey key, Component text) {
        requirePrimaryThread();
        WorldDisplayHandle handle = requireType(key, WorldDisplayHandle.Type.TEXT);
        boolean updated = gateway.updateText(handle.entityId(), Objects.requireNonNull(text, "text"));
        if (!updated) {
            handles.remove(handle.key());
        }
        return updated;
    }

    public boolean updateItem(WorldDisplayKey key, ItemStack item) {
        requirePrimaryThread();
        WorldDisplayHandle handle = requireType(key, WorldDisplayHandle.Type.ITEM);
        boolean updated = gateway.updateItem(
                handle.entityId(),
                Objects.requireNonNull(item, "item").clone()
        );
        if (!updated) {
            handles.remove(handle.key());
        }
        return updated;
    }

    public boolean teleport(WorldDisplayKey key, Location location) {
        requirePrimaryThread();
        WorldDisplayHandle handle = handles.get(Objects.requireNonNull(key, "key"));
        if (handle == null) {
            return false;
        }
        boolean teleported = gateway.teleport(handle.entityId(), validateLocation(location));
        if (!teleported) {
            handles.remove(handle.key());
        }
        return teleported;
    }

    public boolean remove(WorldDisplayKey key) {
        requirePrimaryThread();
        WorldDisplayHandle handle = handles.remove(Objects.requireNonNull(key, "key"));
        if (handle == null) {
            return false;
        }
        gateway.remove(handle.entityId());
        return true;
    }

    public int removeOwner(String owner) {
        requirePrimaryThread();
        String validatedOwner = requireText(owner, "owner");
        var keys = handles.keySet().stream()
                .filter(key -> key.owner().equals(validatedOwner))
                .toList();
        keys.forEach(this::remove);
        return keys.size();
    }

    public Optional<WorldDisplayHandle> getHandle(WorldDisplayKey key) {
        return Optional.ofNullable(handles.get(Objects.requireNonNull(key, "key")));
    }

    public int getDisplayCount() {
        return handles.size();
    }

    public int cleanupStaleDisplays() {
        requirePrimaryThread();
        int removed = gateway.cleanupStaleDisplays();
        handles.clear();
        return removed;
    }

    public void cleanup() {
        requirePrimaryThread();
        handles.keySet().stream().toList().forEach(this::remove);
        handles.clear();
    }

    private WorldDisplayKey requireAvailableKey(WorldDisplayKey key) {
        WorldDisplayKey validated = Objects.requireNonNull(key, "key");
        if (handles.containsKey(validated)) {
            throw new IllegalStateException("World display key is already registered: " + validated);
        }
        return validated;
    }

    private WorldDisplayHandle register(
            WorldDisplayKey key,
            UUID entityId,
            WorldDisplayHandle.Type type
    ) {
        WorldDisplayHandle handle = new WorldDisplayHandle(
                key,
                Objects.requireNonNull(entityId, "display entity id"),
                type
        );
        handles.put(key, handle);
        return handle;
    }

    private WorldDisplayHandle requireType(WorldDisplayKey key, WorldDisplayHandle.Type expected) {
        WorldDisplayHandle handle = handles.get(Objects.requireNonNull(key, "key"));
        if (handle == null) {
            throw new IllegalStateException("World display key is not registered: " + key);
        }
        if (handle.type() != expected) {
            throw new IllegalArgumentException("World display " + key + " is " + handle.type()
                    + ", not " + expected);
        }
        return handle;
    }

    private Location validateLocation(Location location) {
        Location validated = Objects.requireNonNull(location, "location").clone();
        if (validated.getWorld() == null) {
            throw new IllegalArgumentException("location world must not be null");
        }
        if (!Double.isFinite(validated.getX()) || !Double.isFinite(validated.getY())
                || !Double.isFinite(validated.getZ()) || !Float.isFinite(validated.getYaw())
                || !Float.isFinite(validated.getPitch())) {
            throw new IllegalArgumentException("location coordinates and rotation must be finite");
        }
        return validated;
    }

    private void requirePrimaryThread() {
        if (!primaryThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("World display mutations must run on the primary server thread");
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    interface DisplayGateway {
        UUID createText(WorldDisplayKey key, Location location, Component text, Consumer<TextDisplay> configurator);

        UUID createItem(WorldDisplayKey key, Location location, ItemStack item, Consumer<ItemDisplay> configurator);

        boolean updateText(UUID entityId, Component text);

        boolean updateItem(UUID entityId, ItemStack item);

        boolean teleport(UUID entityId, Location location);

        void remove(UUID entityId);

        int cleanupStaleDisplays();
    }

    private static final class BukkitDisplayGateway implements DisplayGateway {

        private final Server server;
        private final NamespacedKey displayKey;
        private final NamespacedKey ownerKey;
        private final NamespacedKey idKey;
        private final NamespacedKey typeKey;

        private BukkitDisplayGateway(JavaPlugin plugin) {
            this.server = plugin.getServer();
            this.displayKey = new NamespacedKey(plugin, DISPLAY_PDC);
            this.ownerKey = new NamespacedKey(plugin, OWNER_PDC);
            this.idKey = new NamespacedKey(plugin, ID_PDC);
            this.typeKey = new NamespacedKey(plugin, TYPE_PDC);
        }

        @Override
        public UUID createText(
                WorldDisplayKey key,
                Location location,
                Component text,
                Consumer<TextDisplay> configurator
        ) {
            TextDisplay entity = location.getWorld().spawn(location, TextDisplay.class);
            boolean configured = false;
            try {
                configure(entity, key, WorldDisplayHandle.Type.TEXT);
                entity.text(text);
                configurator.accept(entity);
                configured = true;
                return entity.getUniqueId();
            } finally {
                if (!configured) {
                    entity.remove();
                }
            }
        }

        @Override
        public UUID createItem(
                WorldDisplayKey key,
                Location location,
                ItemStack item,
                Consumer<ItemDisplay> configurator
        ) {
            ItemDisplay entity = location.getWorld().spawn(location, ItemDisplay.class);
            boolean configured = false;
            try {
                configure(entity, key, WorldDisplayHandle.Type.ITEM);
                entity.setItemStack(item.clone());
                configurator.accept(entity);
                configured = true;
                return entity.getUniqueId();
            } finally {
                if (!configured) {
                    entity.remove();
                }
            }
        }

        @Override
        public boolean updateText(UUID entityId, Component text) {
            Entity entity = server.getEntity(entityId);
            if (!(entity instanceof TextDisplay textDisplay)) {
                return false;
            }
            textDisplay.text(text);
            return true;
        }

        @Override
        public boolean updateItem(UUID entityId, ItemStack item) {
            Entity entity = server.getEntity(entityId);
            if (!(entity instanceof ItemDisplay itemDisplay)) {
                return false;
            }
            itemDisplay.setItemStack(item.clone());
            return true;
        }

        @Override
        public boolean teleport(UUID entityId, Location location) {
            Entity entity = server.getEntity(entityId);
            return entity != null && entity.teleport(location);
        }

        @Override
        public void remove(UUID entityId) {
            Entity entity = server.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }

        @Override
        public int cleanupStaleDisplays() {
            int removed = 0;
            for (World world : server.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof TextDisplay) && !(entity instanceof ItemDisplay)) {
                        continue;
                    }
                    if (!entity.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
                        continue;
                    }
                    entity.remove();
                    removed++;
                }
            }
            return removed;
        }

        private void configure(Display entity, WorldDisplayKey key, WorldDisplayHandle.Type type) {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            PersistentDataContainer data = entity.getPersistentDataContainer();
            data.set(displayKey, PersistentDataType.BYTE, (byte) 1);
            data.set(ownerKey, PersistentDataType.STRING, key.owner());
            data.set(idKey, PersistentDataType.STRING, key.id());
            data.set(typeKey, PersistentDataType.STRING, type.name());
        }
    }
}

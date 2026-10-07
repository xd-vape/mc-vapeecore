package dev.vapee.core.lobby.item;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.config.LobbyItemsConfig;
import dev.vapee.core.lobby.experience.LobbyItemListener;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.*;

/** External Paper surfaces only; parser, service, builder, PDC and event dispatch are production code. */
public final class LobbyItemRegistryFixture implements AutoCloseable {
    public final LobbyItemRegistry registry;
    public final LobbyItemService items;
    public final JavaPlugin plugin;
    public final LobbyService lobby;
    public final Player player;
    public final ItemStack[] storage = new ItemStack[36];
    public final com.destroystokyo.paper.profile.PlayerProfile profile;
    public final List<LogRecord> logs = new ArrayList<>();
    public final Logger logger = Logger.getAnonymousLogger();
    public final NamespacedKey key;
    public final LobbyItemListener listener;
    public boolean eligible = true;
    public int sounds;
    private final Path root = Files.createTempDirectory("vapeecore-registry-");
    private final AtomicReference<Map<String, LobbyItemDefinition>> definitions = new AtomicReference<>();

    public LobbyItemRegistryFixture(LobbyItemRegistry registry, Player delegate) throws Exception {
        LobbyItemsConfigHarness.installRegistries();
        this.registry = registry;
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() { public void publish(LogRecord r) { logs.add(r); } public void flush() { } public void close() { } });
        World world = LobbyItemsConfigHarness.proxy(World.class, (name, args) -> name.equals("getName") ? "world" : null);
        plugin = LobbyItemsConfigHarness.allocate(LobbyItemsConfigHarness.TestPlugin.class);
        var description = new PluginDescriptionFile("VapeeCore", "test", plugin.getClass().getName());
        for (var entry : Map.of("logger", logger, "description", description, "pluginMeta", description,
                "server", LobbyItemsConfigHarness.proxy(Server.class, (name, args) -> name.equals("getWorld") ? world : null)).entrySet()) {
            var field = JavaPlugin.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(plugin, entry.getValue());
        }
        var config = new LobbyConfig(root.resolve("lobby.yml"), logger); config.initialize();
        lobby = new LobbyService(plugin, config); lobby.setSpawn(new Location(world, 0, 64, 0));
        var inventory = LobbyItemsConfigHarness.proxy(PlayerInventory.class, (name, args) -> switch (name) {
            case "getContents", "getStorageContents" -> storage.clone();
            case "getItem" -> storage[(int) args[0]];
            case "setItem" -> { storage[(int) args[0]] = (ItemStack) args[1]; yield null; }
            default -> null;
        });
        profile = LobbyItemsConfigHarness.proxy(com.destroystokyo.paper.profile.PlayerProfile.class, (name, args) -> null);
        UUID id = UUID.randomUUID();
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (ignored, method, args) -> {
            return switch (method.getName()) {
                case "getWorld" -> world;
                case "getInventory" -> inventory;
                case "getPlayerProfile" -> profile;
                case "getUniqueId" -> delegate == null ? id : delegate.getUniqueId();
                case "isOnline" -> delegate == null || delegate.isOnline();
                case "hashCode" -> System.identityHashCode(ignored);
                case "equals" -> ignored == args[0];
                case "toString" -> "LobbyTestPlayer";
                default -> delegate == null ? null : method.invoke(delegate, args);
            };
        });
        key = new NamespacedKey(plugin, "lobby_item");
        items = new LobbyItemService(plugin, lobby, registry, definitions::get);
        listener = new LobbyItemListener(lobby, items, registry, p -> eligible, p -> sounds++, logger);
        configure("");
    }

    public Map<String, LobbyItemDefinition> configure(String yaml) throws Exception {
        var config = new YamlConfiguration(); config.loadFromString(yaml);
        var parsed = LobbyItemsConfig.read(config, logger, registry); definitions.set(parsed); return parsed;
    }
    public void scheduler(java.util.function.Consumer<Runnable> immediate, java.util.function.Consumer<Runnable> later) throws Exception {
        var scheduler = LobbyItemsConfigHarness.proxy(org.bukkit.scheduler.BukkitScheduler.class, (name,args) -> {
            if (name.equals("runTask")) immediate.accept((Runnable)args[1]);
            if (name.equals("runTaskLater")) later.accept((Runnable)args[1]);
            return null;
        });
        var server = LobbyItemsConfigHarness.proxy(Server.class, (name,args) -> switch(name) {
            case "getScheduler" -> scheduler;
            case "getWorld" -> player.getWorld();
            default -> null;
        });
        var field=JavaPlugin.class.getDeclaredField("server");field.setAccessible(true);field.set(plugin,server);
    }
    public void refresh() { items.applyLobbyItems(player); }
    public int ownedCount() { return (int) Arrays.stream(storage).filter(items::isManagedItem).count(); }
    public PlayerInteractEvent click(ItemStack item, Action action, EquipmentSlot hand) {
        var event = new PlayerInteractEvent(player, action, item, null, BlockFace.SELF, hand);
        event.setCancelled(false); listener.onPlayerInteract(event); return event;
    }
    public ItemStack marked(String namespace, String id) {
        ItemStack item = new LobbyItemsConfigHarness.TestItem(Material.DIAMOND);
        item.getItemMeta().getPersistentDataContainer().set(new NamespacedKey(namespace, "lobby_item"), PersistentDataType.STRING, id);
        return item;
    }
    public ItemStack foreign() { return new LobbyItemsConfigHarness.TestItem(Material.DIAMOND); }
    @Override public void close() throws Exception {
        try (var paths = Files.walk(root)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
    }
}

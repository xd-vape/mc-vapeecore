package dev.vapee.core.lobby.item;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.config.LobbyItemsConfig;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Actual parser -> production item creation/PDC -> safe production inventory reconciliation. */
public final class LobbyItemsConfigHarness {
    private static int checks;
    private static final List<LogRecord> logs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        installRegistries();
        Logger logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() { public void publish(LogRecord r) { logs.add(r); } public void flush() { } public void close() { } });
        check(LobbyItemsConfig.defaults().size() == 3, "only existing navigator/visibility/settings types");
        var defaults = read("", logger);
        check(defaults.get(LobbyItemType.NAVIGATOR).slot() == 0 && defaults.get(LobbyItemType.VISIBILITY).slot() == 4
                && defaults.get(LobbyItemType.SETTINGS).slot() == 8, "default hotbar layout unchanged");
        check(defaults.get(LobbyItemType.NAVIGATOR).appearance().material() == Material.COMPASS
                && defaults.get(LobbyItemType.SETTINGS).appearance().material() == Material.COMPARATOR, "default materials unchanged");
        check(logs.isEmpty(), "missing legacy item section uses quiet internal defaults");
        var custom = read("""
                items:
                  navigator:
                    slot: 2
                    material: PLAYER_HEAD
                    head-owner: self
                    name: '<gold>My Navigator</gold>'
                    lore: ['<gray>Custom lore</gray>', '']
                  visibility:
                    slot: 6
                    filtered:
                      material: RED_DYE
                      name: '<red>Hidden</red>'
                  settings:
                    enabled: false
                """, logger);
        var appearance = custom.get(LobbyItemType.NAVIGATOR).appearance();
        check(appearance.material() == Material.PLAYER_HEAD && appearance.selfHead(), "configured self head parsed");
        check(plain(appearance.name()).equals("My Navigator") && plain(appearance.lore().getFirst()).equals("Custom lore"), "configured MiniMessage name/lore");
        check(appearance.lore().size() == 2 && plain(appearance.lore().get(1)).isEmpty(), "lore blank line retained");
        check(appearance.name().decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE, "UI text explicitly non-italic");
        check(!custom.get(LobbyItemType.SETTINGS).enabled(), "disabled item parsed");
        var invalid = read("""
                items:
                  navigator:
                    slot: 9
                    material: AIR
                    name: '<red>unclosed'
                    lore: [true]
                    head-owner: url
                    enabled: wrong
                  visibility:
                    slot: 0
                    material: THIS_DOES_NOT_EXIST
                  settings:
                    slot: 0
                """, logger);
        check(invalid.get(LobbyItemType.NAVIGATOR).slot() == 0, "out-of-range slot fallback");
        check(invalid.get(LobbyItemType.VISIBILITY).slot() == 4 && invalid.get(LobbyItemType.SETTINGS).slot() == 8, "duplicate slots use safe distinct fallbacks");
        check(invalid.get(LobbyItemType.NAVIGATOR).appearance().material() == Material.COMPASS
                && invalid.get(LobbyItemType.VISIBILITY).appearance().material() == Material.LIME_DYE, "invalid/air materials fallback");
        check(plain(invalid.get(LobbyItemType.NAVIGATOR).appearance().name()).equals("Warp Navigator"), "invalid MiniMessage fallback");
        check(logs.stream().filter(r -> r.getLevel().intValue() >= 900).count() >= 8, "invalid fields and duplicate slots warn");
        var fractional = read("items:\n  navigator:\n    slot: 1.5\n  visibility:\n    slot: -1\n", logger);
        check(fractional.get(LobbyItemType.NAVIGATOR).slot() == 0 && fractional.get(LobbyItemType.VISIBILITY).slot() == 4, "fractional/negative slot fallback");

        Path root = Files.createTempDirectory("vapeecore-lobby-items-");
        try {
            World world = proxy(World.class, (name, args1) -> name.equals("getName") ? "world" : null);
            JavaPlugin plugin = allocate(TestPlugin.class);
            var description = new PluginDescriptionFile("VapeeCore", "test", TestPlugin.class.getName());
            for (var fieldValue : Map.of("logger", logger, "description", description, "pluginMeta", description,
                    "server", proxy(Server.class, (name, args1) -> name.equals("getWorld") ? world : null)).entrySet()) {
                var field = JavaPlugin.class.getDeclaredField(fieldValue.getKey()); field.setAccessible(true); field.set(plugin, fieldValue.getValue());
            }
            LobbyConfig config = new LobbyConfig(root.resolve("lobby.yml"), logger); config.initialize();
            LobbyService lobby = new LobbyService(plugin, config); lobby.setSpawn(new Location(world, 0, 64, 0));
            UUID id = UUID.randomUUID();
            var players = new PlayerService(new MemoryRepository(), logger); players.loadPlayer(id, "Operator");
            var settings = new PlayerSettingsService(players);
            ItemStack[] storage = new ItemStack[36];
            PlayerInventory inventory = proxy(PlayerInventory.class, (name, args1) -> switch (name) {
                case "getContents", "getStorageContents" -> storage.clone();
                case "getItem" -> storage[(int) args1[0]];
                case "setItem" -> { storage[(int) args1[0]] = (ItemStack) args1[1]; yield null; }
                default -> null;
            });
            var profile = proxy(com.destroystokyo.paper.profile.PlayerProfile.class, (name, args1) -> null);
            Player player = proxy(Player.class, (name, args1) -> switch (name) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getInventory" -> inventory;
                case "getPlayerProfile" -> profile;
                default -> null;
            });
            var current = new AtomicReference<>(defaults);
            var service = new LobbyItemService(plugin, lobby, settings, current::get);
            service.applyLobbyItems(player);
            for (var type : LobbyItemType.values()) check(service.getItemType(storage[defaults.get(type).slot()]).orElseThrow() == type, "default PDC identity " + type);
            check(countOwned(service, storage) == 3, "exact three initial owned items");
            current.set(read("items:\n  navigator:\n    material: CLOCK\n", logger));
            service.applyLobbyItems(player);
            check(storage[0].getType() == Material.CLOCK && service.getItemType(storage[0]).orElseThrow() == LobbyItemType.NAVIGATOR,
                    "one YAML material edit changes existing item without Java or action changes");
            current.set(custom); service.applyLobbyItems(player);
            check(countOwned(service, storage) == 2 && storage[0] == null && storage[4] == null && storage[8] == null, "config change removes old slots and disabled item");
            check(service.getItemType(storage[2]).orElseThrow() == LobbyItemType.NAVIGATOR, "head still carries navigator identity/action");
            check(((SkullMeta) storage[2].getItemMeta()).getPlayerProfile() == profile, "cached online profile assigned without lookup");
            check(plain(storage[2].getItemMeta().displayName()).equals("My Navigator"), "configured name reaches production item");
            service.applyLobbyItems(player);
            check(countOwned(service, storage) == 2, "rejoin/repeated synchronization cannot duplicate items");
            settings.setLobbyPlayersVisible(id, false); service.refreshVisibilityItem(player);
            check(storage[6].getType() == Material.RED_DYE && plain(storage[6].getItemMeta().displayName()).equals("Hidden"), "filtered presentation follows persisted preference");
            ItemStack foreign = new TestItem(Material.DIAMOND);
            storage[6] = foreign; service.refreshVisibilityItem(player);
            check(storage[6] == foreign, "visibility refresh never overwrites foreign item");
            storage[2] = foreign; storage[6] = null; service.applyLobbyItems(player);
            check(Arrays.stream(storage).filter(item -> item == foreign).count() == 1 && storage[2] != foreign, "foreign target safely displaced once");
            check(storage[0] == foreign, "displacement uses free non-reserved storage");
            Arrays.fill(storage, foreign); service.applyLobbyItems(player);
            check(Arrays.stream(storage).allMatch(item -> item == foreign), "full inventory preserves every foreign item and skips owned items");
            Arrays.fill(storage, null); current.set(read("items:\n  navigator:\n    enabled: false\n  visibility:\n    enabled: false\n  settings:\n    enabled: false\n", logger));
            service.applyLobbyItems(player); service.refreshVisibilityItem(player);
            check(countOwned(service, storage) == 0, "all disabled means no items even on visibility refresh");
            current.set(defaults); service.applyLobbyItems(player); storage[10] = foreign; service.removeManagedItems(player);
            check(countOwned(service, storage) == 0 && storage[10] == foreign, "cleanup removes only PDC-owned items");
        } finally {
            try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        System.out.println("LobbyItemsConfigHarness passed " + checks + " checks.");
    }

    private static int countOwned(LobbyItemService service, ItemStack[] contents) { return (int) Arrays.stream(contents).filter(service::isManagedItem).count(); }
    private static Map<LobbyItemType, LobbyItemDefinition> read(String source, Logger logger) throws Exception {
        var yaml = new YamlConfiguration(); yaml.loadFromString(source); return LobbyItemsConfig.read(yaml, logger);
    }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static Object unsafe() throws Exception { var f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); f.setAccessible(true); return f.get(null); }
    private static <T> T allocate(Class<T> type) throws Exception { Object unsafe = unsafe(); return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type)); }
    public static final class TestPlugin extends JavaPlugin { }
    private static final class MemoryRepository implements PlayerRepository {
        private final Map<UUID, CorePlayer> values = new HashMap<>();
        public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(values.get(id)); }
        public void save(CorePlayer player) { values.put(player.getUniqueId(), player); }
        public boolean exists(UUID id) { return values.containsKey(id); }
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (ignored, method, args) -> {
            if (method.getName().equals("toString")) return type.getSimpleName();
            Object result = call.run(method.getName(), args == null ? new Object[0] : args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == void.class) return null;
            if (method.getReturnType() == long.class) return 0L;
            return 0;
        });
    }
    @FunctionalInterface private interface Call { Object run(String name, Object[] args); }

    private static void installRegistries() throws Exception {
        var access = proxy(io.papermc.paper.registry.RegistryAccess.class, (name, args) -> {
            if (!name.equals("getRegistry")) return null;
            boolean items = args[0].toString().contains("item");
            return proxy(Registry.class, (operation, values) -> {
                if (!(operation.equals("get") || operation.equals("getOrThrow"))) return null;
                String key = values[0].toString();
                if (items) return proxy(ItemType.Typed.class, (itemMethod, itemArgs) -> switch (itemMethod) {
                    case "createItemStack" -> new TestItem(Material.valueOf(key.substring(key.indexOf(':') + 1).toUpperCase(Locale.ROOT)));
                    case "getKey" -> NamespacedKey.fromString(key);
                    default -> null;
                });
                return proxy(org.bukkit.block.BlockType.class, (blockMethod, blockArgs) -> blockMethod.equals("isAir") && key.endsWith(":air"));
            });
        });
        var field = Class.forName("io.papermc.paper.registry.RegistryAccessHolder").getDeclaredField("INSTANCE");
        Object unsafe = unsafe(); Class<?> type = unsafe.getClass();
        Object base = type.getMethod("staticFieldBase", java.lang.reflect.Field.class).invoke(unsafe, field);
        long offset = (long) type.getMethod("staticFieldOffset", java.lang.reflect.Field.class).invoke(unsafe, field);
        type.getMethod("putObject", Object.class, long.class, Object.class).invoke(unsafe, base, offset, Optional.of(access));
    }
    private static final class TestItem extends ItemStack {
        private final Material material;
        private ItemMeta meta;
        TestItem(Material material) {
            super(); this.material = material;
            Map<Object, Object> pdcValues = new HashMap<>();
            var pdc = proxy(PersistentDataContainer.class, (name, args) -> switch (name) {
                case "set" -> { pdcValues.put(args[0], args[2]); yield null; }
                case "get" -> pdcValues.get(args[0]);
                case "has" -> pdcValues.containsKey(args[0]);
                default -> null;
            });
            Map<String, Object> values = new HashMap<>();
            Class<? extends ItemMeta> metaType = material == Material.PLAYER_HEAD ? SkullMeta.class : ItemMeta.class;
            meta = proxy(metaType, (name, args) -> {
                if (name.equals("getPersistentDataContainer")) return pdc;
                if (name.equals("setPlayerProfile")) { values.put("profile", args[0]); return null; }
                if (name.equals("getPlayerProfile")) return values.get("profile");
                if (name.equals("displayName") || name.equals("lore")) {
                    if (args.length == 0) return values.get(name);
                    values.put(name, args[0]); return null;
                }
                return null;
            });
        }
        @Override public Material getType() { return material; }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public boolean setItemMeta(ItemMeta value) { meta = value; return true; }
    }
}

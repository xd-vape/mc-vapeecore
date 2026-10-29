package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.config.ConfigFiles;
import dev.vapee.core.i18n.LocalizedText;
import dev.vapee.core.lobby.warp.WarpPoint;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Navigator composition. External edits load at startup; editor commits apply locally. */
public final class NavigatorConfig {
    public enum Type { WARP, MENU, UNAVAILABLE }
    private static final State LEGACY = new State(false, "root", Map.of());
    private static final State UNAVAILABLE = new State(true, "root", Map.of("root",
            new Menu("root", LocalizedText.literal("Navigator"), Map.of())));
    private final Path file;
    private final Logger logger;
    private State state = LEGACY;

    public NavigatorConfig(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("navigator.yml"), plugin.getLogger());
    }

    public NavigatorConfig(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger);
    }

    public void initialize() {
        try {
            new dev.vapee.core.persistence.SafeFileWriter(logger).requireReconciled(file);
            ConfigFiles.copyDefault(file, () -> NavigatorConfig.class.getResourceAsStream("/navigator.yml"),
                    () -> new IllegalStateException("Missing navigator.yml resource"));
            state = parse(Files.readString(file));
        } catch (IOException | RuntimeException failure) {
            state = UNAVAILABLE;
            logger.log(Level.WARNING, "Invalid Navigator V2 configuration at " + file
                    + "; showing an unavailable Navigator to protect gated destinations. The existing file is unchanged.", failure);
        }
    }

    public State getState() { return state; }
    Path file() { return file; }
    void apply(State next) { state = Objects.requireNonNull(next); }
    public static State legacy() { return LEGACY; }
    static State unavailable() { return UNAVAILABLE; }

    public static State parse(String source) {
        return parse(source, false);
    }

    static State parseForEditing(String source) { return parse(source, true); }

    private static State parse(String source, boolean editing) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Map<String, Object> root = mapping(new Yaml(new SafeConstructor(options)).load(source), "navigator");
        keys(root, Set.of("config-version", "enabled", "root", "menus"), "navigator");
        if (integer(root.getOrDefault("config-version", 1), "config-version", 1, 1) != 1)
            throw new IllegalArgumentException("Unsupported navigator version");
        boolean enabled = bool(root.getOrDefault("enabled", false), "enabled");
        if (!enabled && !editing) return LEGACY;
        String rootId = id(root.getOrDefault("root", "root"), "root");
        var menus = new LinkedHashMap<String, Menu>();
        Map<String, Object> definitions = mapping(root.getOrDefault("menus", Map.of()), "menus");
        if (definitions.size() > 128) throw new IllegalArgumentException("Too many Navigator menus");
        definitions.forEach((name, value) -> {
            String path = "menus." + name;
            id(name, path);
            var menu = mapping(value, path);
            keys(menu, Set.of("title", "entries", "size", "icon", "back-slot"), path);
            int size = menu.containsKey("size") ? integer(menu.get("size"), path + ".size", 27, 54) : 0;
            if (size % 9 != 0) throw new IllegalArgumentException("Menu size must be 27, 36, 45 or 54");
            Material menuIcon = Material.matchMaterial(string(menu.getOrDefault("icon", "COMPASS"), path + ".icon"));
            if (!WarpPoint.isDisplayableItem(menuIcon)) throw new IllegalArgumentException("Invalid menu icon");
            int back = integer(menu.getOrDefault("back-slot", -1), path + ".back-slot", -1, 53);
            var entries = new LinkedHashMap<String, Entry>();
            var positions = new HashSet<String>();
            var entryValues = mapping(menu.getOrDefault("entries", Map.of()), path + ".entries");
            if (entryValues.size() > 4096) throw new IllegalArgumentException("Too many entries in " + path);
            entryValues.forEach((entryId, raw) -> {
                String entryPath = path + ".entries." + entryId;
                id(entryId, entryPath);
                var data = mapping(raw, entryPath);
                keys(data, Set.of("type", "page", "slot", "icon", "name", "lore", "warp", "menu", "permission", "count"), entryPath);
                Type type = Type.valueOf(string(data.get("type"), entryPath + ".type"));
                int page = integer(data.getOrDefault("page", 0), entryPath + ".page", 0, 999);
                int slot = integer(data.get("slot"), entryPath + ".slot", 0, size == 0 ? 44 : size - 1);
                if (!positions.add(page + ":" + slot)) throw new IllegalArgumentException("Duplicate page/slot at " + entryPath);
                Material icon = Material.matchMaterial(string(data.get("icon"), entryPath + ".icon"));
                if (!WarpPoint.isDisplayableItem(icon)) throw new IllegalArgumentException("Invalid item icon at " + entryPath);
                LocalizedText label = text(data.get("name"), entryPath + ".name", false);
                Object rawLore = data.getOrDefault("lore", List.of());
                if (!(rawLore instanceof List<?> loreValues)) throw new IllegalArgumentException("Expected lore list at " + entryPath);
                List<LocalizedText> lore = loreValues.stream().map(line -> text(line, entryPath + ".lore", true)).toList();
                String permission = string(data.getOrDefault("permission", ""), entryPath + ".permission");
                if (!permission.isEmpty() && !permission.matches("[a-z0-9][a-z0-9_.-]{0,127}"))
                    throw new IllegalArgumentException("Invalid permission at " + entryPath);
                String target = "";
                if (type == Type.WARP) {
                    target = string(data.get("warp"), entryPath + ".warp");
                    if (!WarpPoint.isValidId(target)) throw new IllegalArgumentException("Invalid warp ID at " + entryPath);
                } else if (type == Type.MENU) target = id(data.get("menu"), entryPath + ".menu");
                if (type != Type.WARP && (data.containsKey("warp") || data.containsKey("count"))
                        || type != Type.MENU && data.containsKey("menu"))
                    throw new IllegalArgumentException("Entry fields do not match type at " + entryPath);
                NavigatorRegion region = null;
                boolean amount = false;
                if (data.containsKey("count")) {
                    var count = mapping(data.get("count"), entryPath + ".count");
                    keys(count, Set.of("world", "min", "max", "stack-amount"), entryPath + ".count");
                    var min = mapping(count.get("min"), entryPath + ".count.min");
                    var max = mapping(count.get("max"), entryPath + ".count.max");
                    keys(min, Set.of("x", "y", "z"), "count.min"); keys(max, Set.of("x", "y", "z"), "count.max");
                    region = new NavigatorRegion(string(count.get("world"), "count.world"),
                            coordinate(min, "x"), coordinate(min, "y"), coordinate(min, "z"),
                            coordinate(max, "x"), coordinate(max, "y"), coordinate(max, "z"));
                    amount = bool(count.getOrDefault("stack-amount", false), "count.stack-amount");
                }
                entries.put(entryId, new Entry(entryId, type, page, slot, icon, label, lore, target, permission, region, amount));
            });
            menus.put(name, new Menu(name, text(menu.get("title"), path + ".title", false), entries, size, menuIcon, back));
        });
        if (editing && menus.isEmpty() && !enabled)
            menus.put(rootId, new Menu(rootId, LocalizedText.literal("Navigator"), Map.of(), 27, Material.COMPASS, -1));
        return new State(enabled, rootId, menus);
    }

    public record Entry(String id, Type type, int page, int slot, Material icon, LocalizedText name,
                        List<LocalizedText> lore, String target, String permission, NavigatorRegion region,
                        boolean stackAmount) {
        public Entry { lore = List.copyOf(lore); }
        boolean allowed(Player viewer) { return permission.isEmpty() || viewer.hasPermission(permission); }
    }

    public record Menu(String id, LocalizedText title, Map<String, Entry> entries, int size, Material icon, int backSlot) {
        public Menu(String id, LocalizedText title, Map<String, Entry> entries) { this(id, title, entries, 0, Material.COMPASS, -1); }
        public Menu {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
            if (size != 0 && (size < 27 || size > 54 || size % 9 != 0)) throw new IllegalArgumentException("Invalid menu size");
            if (!WarpPoint.isDisplayableItem(icon)) throw new IllegalArgumentException("Invalid menu icon");
            int actual = size == 0 ? compatibleSize(entries, backSlot) : size;
            boolean paged = entries.values().stream().map(Entry::page).distinct().count() > 1;
            var positions = new HashSet<String>();
            for (var entry : entries.values()) {
                if (entry.page() < 0 || entry.page() > 999 || entry.slot() < 0 || entry.slot() >= actual
                        || entry.slot() == backSlot || paged && (entry.slot() == actual - 9 || entry.slot() == actual - 1)
                        || !positions.add(entry.page() + ":" + entry.slot())) throw new IllegalArgumentException("Invalid or colliding menu slot");
            }
            if (backSlot < -1 || backSlot >= actual || paged && (backSlot == actual - 9 || backSlot == actual - 1))
                throw new IllegalArgumentException("Invalid Back slot");
        }
        public int inventorySize() { return size == 0 ? compatibleSize(entries, backSlot) : size; }
        private static int compatibleSize(Map<String, Entry> entries, int back) {
            boolean paged = entries.values().stream().map(Entry::page).distinct().count() > 1;
            for (int candidate = 27; candidate <= 54; candidate += 9) {
                final int n = candidate;
                if (back < n && entries.values().stream().allMatch(e -> e.slot() < n
                        && (!paged || e.slot() != n - 9 && e.slot() != n - 1))
                        && (!paged || back != n - 9 && back != n - 1)) return n;
            }
            throw new IllegalArgumentException("No compatible menu geometry");
        }
    }

    public record State(boolean enabled, String root, Map<String, Menu> menus) {
        public State {
            menus = Collections.unmodifiableMap(new LinkedHashMap<>(menus));
            if ((enabled || !menus.isEmpty()) && !menus.containsKey(root))
                throw new IllegalArgumentException("Navigator root menu does not exist: " + root);
            for (Menu menu : menus.values()) for (Entry entry : menu.entries().values()) {
                if (entry.type() == Type.MENU && !menus.containsKey(entry.target()))
                    throw new IllegalArgumentException("Unknown Navigator menu: " + entry.target());
            }
            var complete = new HashSet<String>();
            for (String menu : menus.keySet()) visit(menu, menus, new HashSet<>(), complete);
        }
        private static void visit(String id, Map<String, Menu> menus, Set<String> visiting, Set<String> complete) {
            if (complete.contains(id)) return;
            if (!visiting.add(id)) throw new IllegalArgumentException("Navigator menu cycle at " + id);
            for (Entry entry : menus.get(id).entries().values()) {
                if (entry.type() == Type.MENU) visit(entry.target(), menus, visiting, complete);
            }
            visiting.remove(id); complete.add(id);
        }
    }

    private static Map<String, Object> mapping(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected mapping at " + path);
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> {
            if (!(key instanceof String name)) throw new IllegalArgumentException("Non-string key at " + path);
            result.put(name, item);
        });
        return result;
    }
    private static void keys(Map<String, Object> map, Set<String> allowed, String path) {
        for (String key : map.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unknown field " + path + "." + key);
    }
    private static String string(Object value, String path) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected text at " + path);
        return text;
    }
    private static String id(Object value, String path) {
        String text = string(value, path);
        if (!text.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid ID at " + path);
        return text;
    }
    private static boolean bool(Object value, String path) {
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException("Expected boolean at " + path);
        return flag;
    }
    private static int integer(Object value, String path, int min, int max) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max)
            throw new IllegalArgumentException("Invalid integer at " + path);
        return number.intValue();
    }
    private static double coordinate(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof Number number)) throw new IllegalArgumentException("Missing numeric count bound: " + key);
        return number.doubleValue();
    }
    private static LocalizedText text(Object value, String path, boolean blankAllowed) {
        LocalizedText text = LocalizedText.parse(value);
        if (!blankAllowed && text.values().values().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Blank text at " + path);
        return text;
    }
}

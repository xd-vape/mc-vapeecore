package dev.vapee.core.settings;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.experience.LobbyItemListener;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.visibility.VisibilityService;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Real hotbar event -> item recognition -> lobby policy -> SettingsMenu entry. */
public final class SettingsHotbarSecurityHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        installBlockRegistry();
        var f = new SettingsMenuFixture(); var owner = f.player("Hotbar");
        Path directory = Files.createTempDirectory("vapeecore-hotbar-security-");
        try {
            World world = proxy(World.class, (method, values) -> method.equals("getName") ? "world" : null);
            JavaPlugin plugin = shell(PluginShell.class);
            field(plugin, "logger", f.logger);
            var description = new PluginDescriptionFile("VapeeCore", "test", PluginShell.class.getName());
            field(plugin, "description", description);
            field(plugin, "pluginMeta", description);
            field(plugin, "server", proxy(Server.class, (method, values) -> method.equals("getWorld") ? world : null));
            var config = new LobbyConfig(directory.resolve("lobby.yml"), f.logger); config.initialize();
            var lobby = new LobbyService(plugin, config); lobby.setSpawn(new Location(world, 0, 64, 0));
            var items = new LobbyItemService(plugin, lobby, f.settings);
            NamespacedKey key = new NamespacedKey(plugin, "lobby_item");
            PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (method, values) -> switch (method) {
                case "has" -> key.equals(values[0]) && values[1] == PersistentDataType.STRING;
                case "get" -> key.equals(values[0]) ? "settings" : null;
                default -> null;
            });
            ItemMeta meta = proxy(ItemMeta.class, (method, values) -> method.equals("getPersistentDataContainer") ? pdc : null);
            ItemStack item = new ItemStack() {
                @Override public Material getType() { return Material.COMPARATOR; }
                @Override public ItemMeta getItemMeta() { return meta; }
            };
            int[] sounds = {0};
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (ignored, method, values) -> {
                        if (method.getName().equals("getWorld")) return world;
                        if (method.getName().equals("playSound")) { sounds[0]++; return null; }
                        return method.invoke(owner.player, values);
                    });
            // These dependencies are never invoked by the SETTINGS branch; no service behavior is replaced.
            var listener = new LobbyItemListener(plugin, lobby, items, shell(VisibilityService.class), f.settings,
                    shell(NavigatorMenu.class), f.menu, f.messages);
            check(items.isManagedItem(item) && items.getItemType(item).orElseThrow()
                    == dev.vapee.core.lobby.item.LobbyItemType.SETTINGS, "real settings PDC item recognition");
            check(lobby.isLobbyWorld(player.getWorld()), "valid lobby player");
            int saves = f.repository.saves;
            owner.permitted = false;
            var denied = interact(player, item, EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR);
            listener.onPlayerInteract(denied);
            check(denied.isCancelled(), "denied hotbar interaction cancelled");
            check(owner.opens == 0 && f.menu.activeCount() == 0 && owner.open == owner.bottom,
                    "denied hotbar cannot open or publish settings view");
            check(f.repository.saves == saves && f.settings.getSettings(owner.id).orElseThrow().isScoreboardEnabled(),
                    "denied hotbar does not mutate or save settings");
            check(owner.received.size() == 1 && sounds[0] == 0 && owner.closes == 0,
                    "denied hotbar has one denial without success sound or foreign close");
            check(items.isManagedItem(item), "denial leaves settings item intact");
            owner.permitted = true;
            var allowed = interact(player, item, EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR);
            listener.onPlayerInteract(allowed);
            check(allowed.isCancelled() && owner.opens == 1 && f.menu.activeCount() == 1,
                    "granted legitimate hotbar path opens real SettingsMenu");
            check(sounds[0] == 1 && f.repository.saves == saves, "legitimate hotbar sound without preference save");
            owner.permitted = false; var retained = owner.open;
            f.click(owner, retained, SettingsMenu.SCOREBOARD_SLOT, org.bukkit.event.inventory.ClickType.LEFT);
            check(f.repository.saves == saves && f.menu.activeCount() == 0 && owner.closes == 1,
                    "hotbar-opened settings also denies retained revoked action");
            for (var hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                var ignored = interact(player, item, hand, Action.LEFT_CLICK_AIR);
                listener.onPlayerInteract(ignored);
                check(ignored.isCancelled() && owner.opens == 1, "unsupported interaction remains cancelled " + hand);
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("SettingsHotbarSecurityHarness passed " + checks + " checks.");
    }

    private static PlayerInteractEvent interact(Player player, ItemStack item, EquipmentSlot hand, Action action) {
        return new PlayerInteractEvent(player, action, item, null, BlockFace.SELF, hand);
    }
    // Paper's Material.isAir consults its block registry. As in the existing blackjack
    // fixture, supply only the external registry surface in this isolated Java process.
    private static void installBlockRegistry() throws Exception {
        var access = proxy(io.papermc.paper.registry.RegistryAccess.class, (method, values) -> {
            if (!method.equals("getRegistry")) return null;
            boolean blocks = values[0].toString().contains("block");
            return proxy(Registry.class, (operation, args) -> {
                if (blocks && (operation.equals("get") || operation.equals("getOrThrow"))) {
                    return proxy(org.bukkit.block.BlockType.class, (name, ignored) -> name.equals("isAir") ? false : null);
                }
                return null;
            });
        });
        var holder = Class.forName("io.papermc.paper.registry.RegistryAccessHolder").getDeclaredField("INSTANCE");
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        unsafe.putObject(unsafe.staticFieldBase(holder), unsafe.staticFieldOffset(holder), java.util.Optional.of(access));
    }
    private static void field(Object target, String name, Object value) throws Exception {
        var field = JavaPlugin.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    @SuppressWarnings("unchecked") private static <T> T shell(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }
    public static final class PluginShell extends JavaPlugin { }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, values) -> call.run(method.getName(), values));
    }
    @FunctionalInterface private interface Call { Object run(String method, Object[] values); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

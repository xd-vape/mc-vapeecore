package dev.vapee.core.lobby.item;

import static dev.vapee.core.lobby.item.LobbyItemsConfigHarness.proxy;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.config.ConfigEvolution;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.gadgets.GadgetsModule;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

/** Production registry, menu, reload plan and module lifecycle with Paper event and inventory fixtures. */
public final class GadgetsFoundationHarness {
    private static final String RESOURCE = "src/main/resources/lobby.yml";
    private static final String OPT_IN = "items:\n  gadgets:\n    enabled: true\n";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-gadgets-");
        try {
            var registry = new LobbyItemRegistry();
            LobbyItemRegistrations.register(registry, () -> null, () -> null, () -> null, () -> null);
            var viewer = new Viewer();
            try (var fixture = new LobbyItemRegistryFixture(registry, viewer.delegate)) {
                viewer.player = fixture.player;
                testRuntime(root, fixture, viewer);
                testLegacyMigration(root, fixture);
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("GadgetsFoundationHarness passed " + checks + " checks.");
    }

    private static void testRuntime(Path root, LobbyItemRegistryFixture fixture, Viewer viewer) throws Exception {
        World world = viewer.player.getWorld();
        var manager = proxy(PluginManager.class, (name, args) -> {
            if (name.equals("registerEvents") && args[0].getClass().getSimpleName().equals("GadgetsMenu")) {
                registerMenuEvents((Listener) args[0], fixture.plugin);
                if (viewer.failRegistration) throw new IllegalStateException("fixture registration failure");
            }
            return null;
        });
        var server = proxy(Server.class, (name, args) -> switch (name) {
            case "getWorld" -> world;
            case "getPlayer" -> viewer.player;
            case "getPluginManager" -> manager;
            case "isPrimaryThread" -> true;
            case "getOnlinePlayers" -> List.of(viewer.player);
            case "createInventory" -> {
                viewer.title = (Component) args[2];
                yield inventory((InventoryHolder) args[0], (int) args[1]);
            }
            case "getScheduler" -> throw new AssertionError("Gadgets must not request a scheduler");
            default -> null;
        });
        inject(JavaPlugin.class, fixture.plugin, "server", server);
        inject(JavaPlugin.class, fixture.plugin, "dataFolder", root.toFile());
        var messages = new MessageService(() -> "", LocalizationService.bundled(fixture.logger));
        var players = new PlayerModule(fixture.plugin, new ConfigService(fixture.plugin), messages);
        var playerService = new PlayerService(new PlayerRepository() {
            public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.empty(); }
            public void save(CorePlayer player) { }
            public boolean exists(UUID id) { return false; }
        }, fixture.logger);
        inject(PlayerModule.class, players, "playerService", playerService);
        var lobby = new LobbyModule(fixture.plugin, players, messages);
        Path configPath = root.resolve("lobby.yml");
        var config = new LobbyConfig(configPath, fixture.logger);
        config.initialize();
        var items = new LobbyItemService(fixture.plugin, fixture.lobby, fixture.registry, config::getItems, messages.localization());
        var state = new LobbyPlayerStateService(fixture.plugin, config, fixture.lobby, items);
        state.setItemRefreshEligibility(player -> true);
        inject(LobbyModule.class, lobby, "itemRegistry", fixture.registry);
        inject(LobbyModule.class, lobby, "lobbyConfig", config);
        inject(LobbyModule.class, lobby, "lobbyService", fixture.lobby);
        inject(LobbyModule.class, lobby, "lobbyItemService", items);
        inject(LobbyModule.class, lobby, "lobbyPlayerStateService", state);
        var activities = new ActivityModule(fixture.plugin, players);
        activities.enable();
        var gadgets = new GadgetsModule(fixture.plugin, lobby, players, activities, messages);
        LobbyItemRegistrations.registerGadgets(fixture.registry, gadgets::openMenu);
        fixture.registry.seal();
        fixture.registry.activate();
        var chest = reloadNewInstall(configPath, lobby, fixture);

        check(!gadgets.openMenu(viewer.player), "inactive module cannot open");
        viewer.failRegistration = true;
        try {
            gadgets.enable();
            throw new AssertionError("injected registration failure accepted");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("fixture registration failure") && countMenuListeners() == 0,
                    "failed enable unregisters already acquired handlers");
        }
        viewer.failRegistration = false;
        gadgets.enable();
        try {
            check(countMenuListeners() == 1, "one menu listener after retry");
            check(!gadgets.openMenu(viewer.player), "unloaded player cannot open");
            playerService.loadPlayer(viewer.id, "RegularPlayer");
            check(!viewer.player.hasPermission("vapeecore.gadgets"), "fixture is an ordinary player without permissions");
            fixture.click(chest, Action.LEFT_CLICK_AIR, EquipmentSlot.HAND);
            fixture.click(chest, Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND);
            fixture.click(fixture.foreign(), Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open == viewer.bottom, "left click, offhand and foreign item do not open");
            check(fixture.click(chest, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND).isCancelled(),
                    "managed right-click is consumed");
            Inventory top = viewer.open;
            check(top.getSize() == 27 && plain(viewer.title).equals("Gadgets") && top.getHolder().getInventory() == top,
                    "right-click opens bound 27-slot menu with title");
            check(!gadgets.openMenu(viewer.player) && viewer.open == top, "repeated opening keeps the same inventory");
            int[] slots = {10, 11, 12, 14, 15, 16};
            String[] labels = {"Player Riding", "Size Changer", "Glow", "Trails", "Movement Extras", "Fun Gadgets"};
            for (int index = 0; index < slots.length; index++) {
                int slot = slots[index];
                ItemStack icon = top.getItem(slot);
                check(plain(icon.getItemMeta().displayName()).equals(labels[index])
                                && plain(icon.getItemMeta().lore().getFirst()).equals("Not yet available"),
                        "category is labeled unavailable: " + labels[index]);
                check(click(viewer, top, slot, ClickType.LEFT).isCancelled() && viewer.open == top
                                && top.getItem(slot) == icon,
                        "category is inert and protected: " + labels[index]);
            }
            check(click(viewer, top, 28, ClickType.SHIFT_LEFT).isCancelled()
                            && click(viewer, top, 10, ClickType.NUMBER_KEY).isCancelled()
                            && click(viewer, top, 10, ClickType.SWAP_OFFHAND).isCancelled(),
                    "bottom shift-click, hotbar swap and offhand swap are protected");
            var drag = new InventoryDragEvent(viewer.view(top), chest, chest, false, Map.of(10, chest, 28, chest));
            dispatch(drag);
            check(drag.isCancelled(), "drag into either inventory is blocked");
            check(!click(viewer, viewer.bottom, 0, ClickType.LEFT).isCancelled(), "foreign inventory unaffected");
            var drop = new PlayerDropItemEvent(viewer.player, proxy(Item.class, (name, args) -> chest));
            fixture.listener.onPlayerDropItem(drop);
            var swap = new PlayerSwapHandItemsEvent(viewer.player, chest, null);
            fixture.listener.onPlayerSwapHandItems(swap);
            check(drop.isCancelled() && swap.isCancelled(), "Gadgets hotbar item shares drop and swap protection");

            dispatch(new PlayerChangedWorldEvent(viewer.player, world));
            check(viewer.open == viewer.bottom, "world change closes the menu");
            viewer.locale = Locale.GERMANY;
            fixture.click(chest, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            check(plain(viewer.open.getItem(10).getItemMeta().displayName()).equals("Auf Spielern reiten")
                            && plain(viewer.open.getItem(10).getItemMeta().lore().getFirst()).equals("Noch nicht verfügbar"),
                    "block right-click opens German category and status");
            items.refreshItem(viewer.player, "gadgets");
            check(plain(fixture.storage[4].getItemMeta().lore().getFirst()).startsWith("Rechtsklick"),
                    "Gadgets hotbar lore uses German locale");
            dispatch(new PlayerQuitEvent(viewer.player, Component.empty()));
            check(viewer.open == viewer.bottom, "logout releases the menu");
            check(gadgets.openMenu(viewer.player), "new view opens after quit cleanup");
            dispatch(new InventoryCloseEvent(viewer.view(top)));
            check(!gadgets.openMenu(viewer.player), "stale close event cannot forget current inventory");

            var edited = new org.bukkit.configuration.file.YamlConfiguration();
            edited.load(configPath.toFile());
            edited.set("items.gadgets.enabled", false);
            edited.save(configPath.toFile());
            var disable = lobby.prepareReload();
            disable.apply();
            fixture.configure(Files.readString(configPath));
            check(viewer.open == viewer.bottom && fixture.storage[4] == null && !gadgets.openMenu(viewer.player),
                    "reload closes owned view, removes disabled item and rejects access");
            fixture.click(chest, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open == viewer.bottom, "stale managed chest cannot open after disable");
            disable.rollback();
            check(fixture.storage[4].getType() == Material.CHEST && gadgets.openMenu(viewer.player),
                    "rollback restores original item and menu access");
            gadgetConfigOverrides(fixture);
            Inventory foreign = inventory(null, 27);
            viewer.open = foreign;
            gadgets.disable();
            check(viewer.open == foreign && countMenuListeners() == 0 && !gadgets.openMenu(viewer.player),
                    "disable unregisters listeners without closing a foreign replacement");
            gadgets.enable();
            check(countMenuListeners() == 1 && gadgets.openMenu(viewer.player), "reenable owns a fresh view and one handler");
        } finally {
            gadgets.disable();
            activities.disable();
        }
        check(viewer.open == viewer.bottom && countMenuListeners() == 0, "shutdown closes owned menu and removes handlers");
    }

    private static ItemStack reloadNewInstall(Path path, LobbyModule lobby, LobbyItemRegistryFixture fixture) throws Exception {
        String bundled = Files.readString(Path.of(RESOURCE));
        Files.writeString(path, bundled + """

                spawn:
                  world: world
                  x: 0
                  y: 64
                  z: 0
                  yaw: 0
                  pitch: 0
                """);
        fixture.configure(bundled);
        lobby.prepareReload().apply();
        var chest = fixture.storage[4];
        var name = chest.getItemMeta().displayName();
        check(chest.getType() == Material.CHEST && fixture.items.getItemId(chest).orElseThrow().equals("gadgets"),
                "new install renders registered CHEST at slot four with the shared PDC");
        check(name.color().equals(NamedTextColor.GOLD) && plain(name).equals("Gadgets"), "gold Gadgets item name");
        check(plain(chest.getItemMeta().lore().getFirst()).startsWith("Right-click"), "English hotbar lore");
        check(fixture.storage[0].getType() == Material.PLAYER_HEAD && fixture.storage[1].getType() == Material.LIME_DYE
                        && fixture.storage[8].getType() == Material.COMPASS, "original default item slots and materials remain");
        fixture.refresh();
        check(fixture.ownedCount() == 4, "reapplying items leaves exactly four owned items");
        return fixture.storage[4];
    }

    private static void gadgetConfigOverrides(LobbyItemRegistryFixture fixture) throws Exception {
        check(!fixture.configure("items:\n  visibility: {}\n").get("gadgets").enabled(),
                "explicit existing list without gadgets stays disabled");
        check(!fixture.configure("player:\n  gamemode: ADVENTURE\n").get("gadgets").enabled(),
                "configuration without items also stays disabled");
        var definitions = fixture.configure("""
                items:
                  navigator:
                    slot: 4
                  gadgets:
                    enabled: true
                    slot: 4
                    material: BARREL
                    name: "<green>Custom Gadgets"
                    lore:
                      en: ["Custom lore"]
                      de: ["Eigene Lore"]
                """);
        int gadgetSlot = definitions.get("gadgets").slot();
        check(definitions.get("navigator").slot() == 4 && gadgetSlot != 4, "existing slot claim takes priority");
        var foreign = fixture.foreign();
        fixture.storage[gadgetSlot] = foreign;
        fixture.refresh();
        var gadget = fixture.storage[gadgetSlot];
        check(gadget.getType() == Material.BARREL && plain(gadget.getItemMeta().displayName()).equals("Custom Gadgets")
                        && plain(gadget.getItemMeta().lore().getFirst()).equals("Eigene Lore"), "operator material, name and localized lore");
        check(Arrays.stream(fixture.storage).filter(item -> item == foreign).count() == 1,
                "a foreign item occupying the configured slot is preserved exactly once");
    }

    private static void testLegacyMigration(Path root, LobbyItemRegistryFixture fixture) throws Exception {
        Path path = root.resolve("lobby.yml");
        var evolution = new ConfigEvolution(root, fixture.logger, name -> {
            try { return Files.newInputStream(Path.of(RESOURCE)); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
        Files.writeString(path, "items:\n  navigator:\n    enabled: false\n");
        check(evolution.evolve("lobby.yml"), "legacy lobby schema still evolves");
        var definitions = fixture.configure(Files.readString(path));
        check(!definitions.get("navigator").enabled() && !definitions.get("gadgets").enabled(),
                "migration preserves explicit disable and never inserts the Gadgets opt-in");
        Files.writeString(path, "config-version: 2\n" + OPT_IN);
        check(!evolution.evolve("lobby.yml") && Files.readString(path).equals("config-version: 2\n" + OPT_IN)
                        && fixture.configure(Files.readString(path)).get("gadgets").enabled(),
                "operator opt-in works without rewriting the current schema");
    }

    private static void registerMenuEvents(Listener listener, JavaPlugin plugin) {
        for (var type : List.of(InventoryClickEvent.class, InventoryDragEvent.class, InventoryCloseEvent.class,
                PlayerChangedWorldEvent.class, PlayerQuitEvent.class)) {
            try {
                var method = Arrays.stream(listener.getClass().getDeclaredMethods())
                        .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{type}))
                        .filter(candidate -> candidate.isAnnotationPresent(org.bukkit.event.EventHandler.class))
                        .findFirst().orElseThrow();
                method.setAccessible(true);
                var handlers = (org.bukkit.event.HandlerList) type.getMethod("getHandlerList").invoke(null);
                handlers.register(new RegisteredListener(listener, (target, event) -> {
                    try { method.invoke(target, event); }
                    catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                }, EventPriority.NORMAL, plugin, false));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static long countMenuListeners() {
        return Arrays.stream(InventoryClickEvent.getHandlerList().getRegisteredListeners())
                .filter(listener -> listener.getListener().getClass().getSimpleName().equals("GadgetsMenu")).count();
    }

    private static InventoryClickEvent click(Viewer viewer, Inventory top, int slot, ClickType type) {
        var event = new InventoryClickEvent(viewer.view(top), InventoryType.SlotType.CONTAINER,
                slot, type, InventoryAction.PICKUP_ALL, type == ClickType.NUMBER_KEY ? 4 : -1);
        dispatch(event);
        return event;
    }

    private static void dispatch(Event event) {
        for (var listener : event.getHandlers().getRegisteredListeners()) {
            try { listener.callEvent(event); }
            catch (org.bukkit.event.EventException failure) { throw new AssertionError(failure); }
        }
    }

    private static Inventory inventory(InventoryHolder holder, int size) {
        var items = new ItemStack[size];
        return proxy(Inventory.class, (name, args) -> switch (name) {
            case "getHolder" -> holder;
            case "getSize" -> size;
            case "getType" -> InventoryType.CHEST;
            case "getItem" -> items[(int) args[0]];
            case "setItem" -> { items[(int) args[0]] = (ItemStack) args[1]; yield null; }
            default -> null;
        });
    }

    private static void inject(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static final class Viewer {
        private final UUID id = UUID.randomUUID();
        private final Inventory bottom = inventory(null, 36);
        private Inventory open = bottom;
        private Locale locale = Locale.US;
        private Component title;
        private Player player;
        private boolean failRegistration;
        private final Player delegate = proxy(Player.class, (name, args) -> switch (name) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "locale" -> locale;
            case "getOpenInventory" -> view(open);
            case "openInventory" -> { close(); open = (Inventory) args[0]; yield view(open); }
            case "closeInventory" -> { close(); yield null; }
            default -> null;
        });

        private void close() {
            Inventory previous = open;
            open = bottom;
            dispatch(new InventoryCloseEvent(view(previous)));
        }

        private InventoryView view(Inventory top) {
            return proxy(InventoryView.class, (name, args) -> switch (name) {
                case "getTopInventory" -> top;
                case "getBottomInventory" -> bottom;
                case "getPlayer" -> player;
                case "getInventory" -> (int) args[0] < 0 ? null : (int) args[0] < top.getSize() ? top : bottom;
                case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
                case "getType" -> InventoryType.CHEST;
                case "getSlotType" -> InventoryType.SlotType.CONTAINER;
                default -> null;
            });
        }
    }
}

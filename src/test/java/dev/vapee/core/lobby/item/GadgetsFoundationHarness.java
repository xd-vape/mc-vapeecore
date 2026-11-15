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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
import org.bukkit.util.Vector;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

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
            if (name.equals("registerEvents") && (args[0].getClass().getSimpleName().equals("GadgetsMenu")
                    || args[0].getClass().getSimpleName().equals("PlayerRiding"))) {
                registerMenuEvents((Listener) args[0], fixture.plugin);
                if (viewer.failRegistration) throw new IllegalStateException("fixture registration failure");
            }
            return null;
        });
        var server = proxy(Server.class, (name, args) -> switch (name) {
            case "getWorld" -> world;
            case "getPlayer" -> viewer.player;
            case "getCurrentTick" -> viewer.tick;
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
        inject(org.bukkit.Bukkit.class, null, "server", server);
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
        var visibility = new dev.vapee.core.visibility.VisibilityService(fixture.plugin, fixture.lobby,
                new dev.vapee.core.visibility.VisibilityPolicy(id -> dev.vapee.core.player.settings.PlayerVisibilitySettings.defaults(),
                        (a, b) -> false, (a, b) -> false, (a, b) -> false));
        var social = new dev.vapee.core.social.SocialService(playerService, fixture.logger);
        var utility = new dev.vapee.core.utility.UtilityService(fixture.plugin);
        var gadgets = new GadgetsModule(fixture.plugin, lobby, players, activities, messages,
                () -> visibility, () -> social, () -> utility, id -> false);
        LobbyItemRegistrations.registerGadgets(fixture.registry, gadgets::handleHotbar);
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
            var entry = top.getItem(10);
            check(plain(entry.getItemMeta().displayName()).equals("Player Riding")
                    && entry.getItemMeta().lore().size() == 2
                    && plain(entry.getItemMeta().lore().getFirst()).equals("Carry or ride other players."),
                    "Riding starts with compact unequipped English lore");
            check(plain(top.getItem(22).getItemMeta().displayName()).equals("0/3 equipped"), "default capacity shown");
            viewer.allowRiding = false;
            click(viewer, top, 10, ClickType.LEFT);
            check(fixture.storage[2] == null && plain(top.getItem(10).getItemMeta().lore().get(1)).equals("Locked")
                    && viewer.chat.removeLast().contains("cannot equip"), "individual permission locks the entry with feedback");
            viewer.allowRiding = true;
            var blockedSlots = List.of(2, 3, 5, 6, 7);
            var foreignItem = fixture.foreign();
            blockedSlots.forEach(slot -> fixture.storage[slot] = foreignItem);
            click(viewer, top, 10, ClickType.LEFT);
            check(viewer.chat.removeLast().contains("No free hotbar slot")
                    && blockedSlots.stream().allMatch(slot -> fixture.storage[slot] == foreignItem),
                    "full hotbar gives localized feedback and preserves foreign items");
            blockedSlots.forEach(slot -> fixture.storage[slot] = null);
            ItemStack chestBefore = fixture.storage[4];
            check(click(viewer, top, 10, ClickType.LEFT).isCancelled()
                    && fixture.storage[2].getType() == Material.SADDLE && fixture.storage[4] == chestBefore
                    && plain(top.getItem(10).getItemMeta().lore().getFirst()).equals("Equipped")
                    && plain(top.getItem(22).getItemMeta().displayName()).equals("1/3 equipped"),
                    "left-click equips a separate Saddle, retaining the chest and showing count");
            items.applyLobbyItems(viewer.player);
            check(fixture.storage[2].getType() == Material.SADDLE && fixture.storage[4].getType() == Material.CHEST
                    && fixture.ownedCount() == 5, "shared hotbar refresh restores exactly one saddle");
            ItemStack saddle = fixture.storage[2];
            fixture.heldSlot = 2;
            var saddleDrop = new PlayerDropItemEvent(viewer.player, proxy(Item.class, (name, args) -> fixture.storage[2]));
            fixture.listener.onPlayerDropItem(saddleDrop);
            var saddleSwap = new PlayerSwapHandItemsEvent(viewer.player, saddle, null);
            fixture.listener.onPlayerSwapHandItems(saddleSwap);
            check(saddleDrop.isCancelled() && saddleSwap.isCancelled(), "equipped saddle uses shared drop and swap protection");
            viewer.close();
            var target = exerciseRiding(fixture, viewer, playerService, world, saddle, social);
            saddle = fixture.storage[2];
            fixture.click(saddle, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open == viewer.bottom, "normal right-click with the saddle keeps the riding gesture free");
            fixture.heldSlot = 4;
            fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open != viewer.bottom && fixture.storage[2] == saddle, "chest opens Gadgets while saddle is equipped");
            top = viewer.open;
            check(click(viewer, top, 10, ClickType.LEFT).isCancelled()
                    && fixture.storage[2] == null && fixture.storage[4].getType() == Material.CHEST, "unequipping removes only the saddle");
            int[] slots = {11, 12, 14, 15, 16};
            String[] labels = {"Size Changer", "Glow", "Trails", "Movement Extras", "Fun Gadgets"};
            for (int index = 0; index < slots.length; index++) {
                int slot = slots[index];
                ItemStack icon = top.getItem(slot);
                check(plain(icon.getItemMeta().displayName()).equals(labels[index])
                                && icon.getItemMeta().lore().size() == 2
                                && plain(icon.getItemMeta().lore().get(1)).equals("Not yet available"),
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
                            && viewer.open.getItem(10).getItemMeta().lore().size() == 2
                            && plain(viewer.open.getItem(10).getItemMeta().lore().get(1)).equals("Linksklick zum Ausrüsten.")
                            && plain(viewer.open.getItem(11).getItemMeta().lore().get(1)).equals("Noch nicht verfügbar"),
                    "block right-click opens German category and status");
            items.refreshItem(viewer.player, "gadgets");
            check(plain(fixture.storage[4].getItemMeta().lore().getFirst()).startsWith("Rechtsklick"),
                    "Gadgets hotbar lore uses German locale");
            dispatch(new PlayerQuitEvent(viewer.player, Component.empty()));
            check(viewer.open == viewer.bottom, "logout releases the menu");
            check(gadgets.openMenu(viewer.player), "new view opens after quit cleanup");
            dispatch(new InventoryCloseEvent(viewer.view(top)));
            check(!gadgets.openMenu(viewer.player), "stale close event cannot forget current inventory");

            click(viewer, viewer.open, 10, ClickType.LEFT);
            ItemStack beforeReload = fixture.storage[2];
            fixture.heldSlot = 2;
            target.sneaking = true;
            viewer.tick += 3;
            dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
            check(target.vehicle == viewer.player, "permission loss starts with a live mount");
            viewer.allowGadgets = false;
            dispatch(new org.bukkit.event.player.PlayerItemHeldEvent(viewer.player, 2, 4));
            check(target.vehicle == null && fixture.storage[2] == null && !gadgets.openMenu(viewer.player),
                    "general permission revocation removes equipment, releases mount and blocks menu");
            viewer.allowGadgets = true;
            click(viewer, viewer.open, 10, ClickType.LEFT);
            check(fixture.storage[2] != null && fixture.storage[2] != beforeReload, "new permission grant requires a new equipped item");
            beforeReload = fixture.storage[2];
            target.sneaking = true;
            viewer.tick += 3;
            dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
            check(target.vehicle == viewer.player && beforeReload.getType() == Material.SADDLE, "reload starts with an active mount");
            var edited = new org.bukkit.configuration.file.YamlConfiguration();
            edited.load(configPath.toFile());
            edited.set("items.gadgets.enabled", false);
            edited.set("gadgets.max-equipped-items", 1);
            edited.save(configPath.toFile());
            var disable = lobby.prepareReload();
            disable.apply();
            check(config.getGadgetItemLimit() == 1, "reload applies configured gadget limit");
            fixture.configure(Files.readString(configPath));
            check(target.vehicle == null && viewer.open == viewer.bottom && fixture.storage[2] == null
                            && fixture.storage[4] == null && !gadgets.openMenu(viewer.player),
                    "reload closes owned view, removes disabled item and rejects access");
            fixture.click(chest, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open == viewer.bottom, "stale managed chest cannot open after disable");
            disable.rollback();
            check(config.getGadgetItemLimit() == 3, "rollback restores prior gadget limit");
            edited.set("gadgets.max-equipped-items", 2);
            edited.save(configPath.toFile());
            check(config.prepareReloadState().gadgetItemLimit() == 2, "configuration accepts limit 2");
            edited.set("gadgets.max-equipped-items", 4);
            edited.save(configPath.toFile());
            check(config.prepareReloadState().gadgetItemLimit() == 3, "out-of-range limit safely defaults to 3");
            check(fixture.storage[4].getType() == Material.CHEST && gadgets.openMenu(viewer.player),
                    "rollback restores original item and menu access");
            click(viewer, viewer.open, 10, ClickType.LEFT);
            check(fixture.storage[2].getType() == Material.SADDLE, "saddle equipped before BUILD transition");
            state.enterBuildMode(viewer.player);
            check(state.isBuildMode(viewer.id) && fixture.storage[2] == null && !gadgets.openMenu(viewer.player),
                    "BUILD clears equipment and blocks Gadgets");
            state.exitBuildMode(viewer.player);
            check(fixture.storage[2] == null && gadgets.openMenu(viewer.player), "leaving BUILD does not resurrect equipment");
            click(viewer, viewer.open, 10, ClickType.LEFT);
            var activity = activities.getActivityService();
            var venue = new dev.vapee.core.activity.location.ActivityVenue("test_venue", "test",
                    new dev.vapee.core.activity.location.ActivityArea(world.getName(), 0, 0, 0, 10, 10, 10),
                    new dev.vapee.core.activity.location.ActivityPosition(world.getName(), 5, 5, 5, 0, 0));
            activity.registerActivityType(new dev.vapee.core.activity.ActivityType() {
                public String getKey() { return "test"; }
                public int getMinParticipants() { return 1; }
                public int getMaxParticipants() { return 2; }
                public dev.vapee.core.activity.ActivitySession createSession(UUID id,
                        dev.vapee.core.activity.location.ActivityVenue location) {
                    return new dev.vapee.core.activity.ActivitySession(id, "test", location) { };
                }
            });
            activity.registerVenue(venue);
            var session = activity.createSession("test", "test_venue").session().orElseThrow();
            check(activity.joinSession(viewer.player, session.getSessionId()) == dev.vapee.core.activity.ActivityResult.SUCCESS
                    && fixture.storage[2] == null && !gadgets.openMenu(viewer.player), "Activity handoff clears equipment and blocks Gadgets");
            activity.leaveCurrentSession(viewer.player, dev.vapee.core.activity.ActivityLeaveReason.VOLUNTARY);
            check(fixture.storage[2] == null, "leaving Activity does not resurrect equipment");
            viewer.close();
            gadgets.openMenu(viewer.player);
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


    private static Viewer exerciseRiding(LobbyItemRegistryFixture fixture, Viewer viewer, PlayerService players,
                                         World world, ItemStack saddle, dev.vapee.core.social.SocialService social) {
        var target = new Viewer();
        target.player = target.delegate;
        target.world = world;
        viewer.peer = target;
        target.peer = viewer;
        players.loadPlayer(target.id, "OtherPlayer");
        var forged = fixture.marked("vapeecore", "gadgets", Material.SADDLE);
        fixture.storage[2] = forged;
        var attempt = new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND);
        dispatch(attempt);
        check(!attempt.isCancelled() && target.vehicle == null, "managed marker alone grants no riding authority");
        dispatch(new org.bukkit.event.player.PlayerItemHeldEvent(viewer.player, 2, 4));
        fixture.storage[2] = saddle;
        var stale = new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND);
        dispatch(stale);
        check(target.vehicle == null && viewer.chat.isEmpty(), "replaced item invalidates the session");
        check(stale.isCancelled(), "stale saddle cannot pass through to vanilla");
        fixture.storage[2] = null;
        fixture.heldSlot = 4;
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        click(viewer, viewer.open, 10, ClickType.LEFT);
        viewer.close();
        fixture.heldSlot = 2;
        saddle = fixture.storage[2];

        dispatch(attempt);
        dispatch(new PlayerInteractAtEntityEvent(viewer.player, target.player, new Vector(), EquipmentSlot.HAND));
        check(attempt.isCancelled() && target.vehicle == null && viewer.chat.size() == 1
                && viewer.chat.getFirst().contains("must be sneaking"), "no consent and duplicate event gives one localized denial");
        target.sneaking = true;
        viewer.tick += 3;
        var pickup = new PlayerInteractAtEntityEvent(viewer.player, target.player, new Vector(), EquipmentSlot.HAND);
        dispatch(pickup);
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(pickup.isCancelled() && target.vehicle == viewer.player && viewer.passengers.equals(List.of(target.player)),
                "paired events create exactly one mount in the correct direction");
        var foreignMount = new EntityMountEvent(viewer.player, target.player);
        dispatch(foreignMount);
        check(foreignMount.isCancelled(), "foreign mount cannot introduce a chain");
        dispatch(fixture.click(saddle, Action.LEFT_CLICK_AIR, EquipmentSlot.HAND));
        check(target.vehicle == null && viewer.passengers.isEmpty() && target.velocity != null
                        && target.velocity.distanceSquared(new Vector(0, 0.32, 0.75)) < 0.000001,
                "air click detaches and launches the rider once");
        Vector launched = target.velocity;
        dispatch(fixture.click(saddle, Action.LEFT_CLICK_AIR, EquipmentSlot.HAND));
        check(target.velocity == launched, "air click without rider has no effect");

        target.sneaking = true;
        viewer.tick += 3;
        var attack = new PrePlayerAttackEntityEvent(viewer.player, target.player, true);
        dispatch(attack);
        check(attack.isCancelled() && viewer.vehicle == target.player, "attack intercepted before damage and mounts actor");
        dispatch(new PlayerInputEvent(viewer.player, proxy(org.bukkit.Input.class, (name, args) -> name.equals("isSneak"))));
        check(viewer.vehicle == null && target.passengers.isEmpty(), "fresh sneak input dismounts immediately");

        viewer.tick += 3;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.player, "a new ride starts after dismount");
        var from = new org.bukkit.Location(world, 0, 64, 0);
        dispatch(new PlayerTeleportEvent(target.player, from, from.clone().add(2, 0, 0)));
        check(target.vehicle == null, "teleport of the passenger clears the owned relation");

        target.sneaking = true;
        target.canSee = false;
        viewer.tick += 3;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == null, "mutual visibility is required");
        target.canSee = true;
        target.vehicle = viewer.delegate;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.delegate, "foreign mount remains untouched");
        target.vehicle = null;
        target.flying = true;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == null, "flight prevents mounting");
        target.flying = false;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.player, "mount available again when eligible");
        dispatch(new PlayerQuitEvent(target.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        check(target.vehicle == null && viewer.passengers.isEmpty(), "passenger logout clears mount");
        viewer.tick += 3;
        target.sneaking = true;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.player, "mount available before world transition");
        dispatch(new PlayerChangedWorldEvent(target.player, world));
        check(target.vehicle == null, "world change clears mount");
        viewer.tick += 3;
        target.sneaking = true;
        social.ignore(target.id, viewer.id);
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == null, "reverse ignore blocks interaction");
        social.unignore(target.id, viewer.id);
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.player, "mount available again after ignore removal");
        viewer.passengers.clear();
        target.vehicle = null;
        dispatch(new EntityDismountEvent(target.player, viewer.player));
        viewer.tick += 3;
        target.sneaking = true;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.player, "external dismount forgets both roles");
        dispatch(new PlayerChangedWorldEvent(target.player, world));
        return target;
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
        for (var method : listener.getClass().getDeclaredMethods()) {
            var annotation = method.getAnnotation(org.bukkit.event.EventHandler.class);
            if (annotation == null) continue;
            try {
                var type = method.getParameterTypes()[0];
                method.setAccessible(true);
                var handlers = (org.bukkit.event.HandlerList) type.getMethod("getHandlerList").invoke(null);
                handlers.register(new RegisteredListener(listener, (target, event) -> {
                    try { method.invoke(target, event); }
                    catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                }, annotation.priority(), plugin, annotation.ignoreCancelled()));
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
        private boolean sneaking;
        private boolean allowGadgets = true;
        private boolean allowRiding = true;
        private boolean flying;
        private boolean canSee = true;
        private World world;
        private Entity vehicle;
        private final java.util.ArrayList<Entity> passengers = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> chat = new java.util.ArrayList<>();
        private Vector velocity;
        private int tick;
        private Viewer peer;
        private boolean failRegistration;
        private final Player delegate = proxy(Player.class, (name, args) -> switch (name) {
            case "getUniqueId" -> id;
            case "getName" -> "RidingFixture";
            case "isOnline" -> true;
            case "isSneaking" -> sneaking;
            case "hasPermission" -> args[0].equals(GadgetsModule.USE_PERMISSION) ? allowGadgets
                    : args[0].equals("vapeecore.gadgets.player-riding") && allowRiding;
            case "setSneaking" -> { sneaking = (boolean) args[0]; yield null; }
            case "getWorld" -> world;
            case "isFlying" -> flying;
            case "canSee" -> canSee;
            case "getVehicle" -> vehicle;
            case "getPassengers" -> List.copyOf(passengers);
            case "getCurrentInput" -> proxy(org.bukkit.Input.class, (method, params) -> false);
            case "getYaw" -> 0F;
            case "sendMessage" -> { if (args[0] instanceof Component component) chat.add(plain(component)); yield null; }
            case "setVelocity" -> { velocity = (Vector) args[0]; yield null; }
            case "addPassenger", "removePassenger" -> {
                Entity passenger = (Entity) args[0];
                if (passenger != peer.player) yield false;
                if (name.equals("addPassenger")) {
                    passengers.add(passenger);
                    peer.vehicle = player;
                } else {
                    passengers.remove(passenger);
                    peer.vehicle = null;
                }
                yield true;
            }
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

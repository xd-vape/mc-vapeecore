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
import dev.vapee.core.player.settings.PlayerVisibilitySettings;
import dev.vapee.core.player.settings.VisibilityMode;
import dev.vapee.core.social.SocialService;
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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/** Production registry, menu, reload plan and module lifecycle with Paper event and inventory fixtures. */
public final class GadgetsFoundationHarness {
    private static final String RESOURCE = "src/main/resources/lobby.yml";
    private static final String OPT_IN = "items:\n  gadgets:\n    enabled: true\n";
    private static final NamespacedKey SIZE_KEY = new NamespacedKey("vapeecore", "gadgets_size_changer");
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-gadgets-");
        try {
            var registry = new LobbyItemRegistry();
            LobbyItemRegistrations.register(registry, () -> null, () -> null, () -> null, () -> null);
            var viewer = new Viewer();
            try (var fixture = new LobbyItemRegistryFixture(registry, viewer.delegate, viewer::worldCall)) {
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
        viewer.world = world;
        var manager = proxy(PluginManager.class, (name, args) -> {
            if (name.equals("registerEvents") && (args[0].getClass().getSimpleName().equals("GadgetsMenu")
                    || args[0].getClass().getSimpleName().equals("PlayerRiding")
                    || args[0].getClass().getSimpleName().equals("SizeChanger")
                    || args[0].getClass().getSimpleName().equals("GlowGadget")
                    || args[0].getClass().getSimpleName().equals("ParticleTrails"))) {
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
            case "getScheduler" -> viewer.scheduler;
            default -> null;
        });
        inject(JavaPlugin.class, fixture.plugin, "server", server);
        inject(org.bukkit.Bukkit.class, null, "server", server);
        inject(JavaPlugin.class, fixture.plugin, "dataFolder", root.toFile());
        var messages = new MessageService(() -> viewer.messagePrefix, LocalizationService.bundled(fixture.logger));
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
        var social = new SocialService(playerService, fixture.logger);
        var visibility = new dev.vapee.core.visibility.VisibilityService(fixture.plugin, fixture.lobby,
                new dev.vapee.core.visibility.VisibilityPolicy(id -> playerService.getPlayer(id)
                        .map(player -> player.getSettings().getVisibility()).orElseGet(PlayerVisibilitySettings::defaults),
                        social::isKnownIgnoring, (a, b) -> false, (a, b) -> false));
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
            var target = exerciseRiding(fixture, viewer, playerService, world, saddle, social, messages.localization(), root);
            saddle = viewer.player.getInventory().getItemInMainHand();
            fixture.click(saddle, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open == viewer.bottom, "normal right-click with the saddle keeps the riding gesture free");
            fixture.heldSlot = 4;
            fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open != viewer.bottom && fixture.storage[2] == saddle, "chest opens Gadgets while saddle is equipped");
            top = viewer.open;
            check(click(viewer, top, 10, ClickType.LEFT).isCancelled()
                    && fixture.storage[2] == null && fixture.storage[4].getType() == Material.CHEST, "unequipping removes only the saddle");
            int[] slots = {15, 16};
            String[] labels = {"Movement Extras", "Fun Gadgets"};
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

            exerciseGlow(viewer, top, fixture, world);
            exerciseTrails(viewer, fixture, playerService, social);
            exerciseSizes(viewer, viewer.open, fixture, world);
            exerciseSizeTiers(viewer, fixture);
            top = viewer.open;
            enableGlow(viewer);
            armTrail(viewer);
            dispatch(new PlayerChangedWorldEvent(viewer.player, world));
            check(viewer.open == viewer.bottom && !viewer.modifiers.containsKey(SIZE_KEY) && viewer.potion == null,
                    "world change closes the menu and resets size");
            expectSilentStride(viewer, "world change clears trail selection");
            viewer.locale = Locale.GERMANY;
            fixture.click(chest, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            check(plain(viewer.open.getItem(10).getItemMeta().displayName()).equals("Auf Spielern reiten")
                            && viewer.open.getItem(10).getItemMeta().lore().size() == 2
                            && plain(viewer.open.getItem(10).getItemMeta().lore().get(1)).equals("Linksklick zum Ausrüsten.")
                            && plain(viewer.open.getItem(11).getItemMeta().lore().get(1)).equals("Linksklick, um eine Größe auszuwählen."),
                    "block right-click opens German category and status");
            enableGlow(viewer);
            check(viewer.chat.getLast().equals("Leuchten aktiviert.")
                    && plain(viewer.open.getItem(12).getItemMeta().lore().getFirst()).equals("Aktiv"),
                    "German Glow status and prefix-free feedback");
            var germanTrails = openTrails(viewer);
            check(plain(viewer.title).equals("Partikelspuren"), "German trails menu is localized");
            click(viewer, germanTrails, 10, ClickType.LEFT);
            check(viewer.chat.getLast().equals("Herzen-Partikelspur ausgewählt."), "German selection feedback is prefix-free");
            backToGadgets(viewer);
            var germanSizes = openSizes(viewer);
            check(plain(viewer.title).equals("Größe ändern") && plain(germanSizes.getItem(7).getItemMeta().displayName()).equals("Riesig")
                    && plain(germanSizes.getItem(17).getItemMeta().displayName()).equals("Zurück"), "German submenu");
            viewer.scaleAvailable = false;
            click(viewer, germanSizes, 7, ClickType.LEFT);
            check(viewer.chat.removeLast().equals("Deine Größe kann gerade nicht geändert werden."),
                    "German Size Changer warning is prefix-free");
            viewer.scaleAvailable = true;
            click(viewer, germanSizes, 6, ClickType.LEFT);
            items.refreshItem(viewer.player, "gadgets");
            check(plain(fixture.storage[4].getItemMeta().lore().getFirst()).startsWith("Rechtsklick"),
                    "Gadgets hotbar lore uses German locale");
            dispatch(new PlayerQuitEvent(viewer.player, Component.empty()));
            check(viewer.open == viewer.bottom && !viewer.modifiers.containsKey(SIZE_KEY) && viewer.potion == null, "logout releases menu, size and Glow");
            expectSilentStride(viewer, "logout clears trail selection");
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
            click(viewer, openSizes(viewer), 7, ClickType.LEFT);
            check(!viewer.modifiers.containsKey(SIZE_KEY)
                    && viewer.chat.removeLast().equals("Du kannst deine Größe nur zu Fuß ändern."),
                    "mounted player cannot set a non-normal size");
            click(viewer, viewer.open, 17, ClickType.LEFT);
            viewer.runNavigation();
            enableGlow(viewer);
            check(target.vehicle == viewer.player && viewer.potion != null, "Glow leaves a live Player Riding mount in place");
            armTrail(viewer);
            expectSilentStride(viewer, "a carrier does not emit; mount and equipped Trail coexist");
            check(target.vehicle == viewer.player, "Trail pause leaves the ride intact");
            var edited = new org.bukkit.configuration.file.YamlConfiguration();
            edited.load(configPath.toFile());
            edited.set("items.gadgets.enabled", false);
            edited.set("gadgets.max-equipped-items", 1);
            edited.save(configPath.toFile());
            var disable = lobby.prepareReload();
            disable.apply();
            check(config.getGadgetItemLimit() == 1, "reload applies configured gadget limit");
            expectSilentStride(viewer, "reload clears trail selection");
            fixture.configure(Files.readString(configPath));
            check(target.vehicle == null && viewer.open == viewer.bottom && fixture.storage[2] == null && viewer.potion == null
                            && fixture.storage[4] == null && !gadgets.openMenu(viewer.player),
                    "reload closes owned view, removes disabled item and rejects access");
            fixture.click(chest, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
            check(viewer.open == viewer.bottom, "stale managed chest cannot open after disable");
            disable.rollback();
            check(config.getGadgetItemLimit() == 3, "rollback restores prior gadget limit");
            check(gadgets.openMenu(viewer.player), "can open after rollback");
            click(viewer, openSizes(viewer), 1, ClickType.LEFT);
            edited.set("items.gadgets.enabled", true);
            edited.save(configPath.toFile());
            var reloadWithSize = lobby.prepareReload();
            reloadWithSize.apply();
            check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == viewer.bottom, "reload clears size and submenu");
            check(gadgets.openMenu(viewer.player), "menu can open in the reloaded configuration");
            enableGlow(viewer);
            armTrail(viewer);
            click(viewer, openSizes(viewer), 6, ClickType.LEFT);
            check(viewer.modifiers.containsKey(SIZE_KEY), "new size established after reload");
            reloadWithSize.rollback();
            check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == viewer.bottom && viewer.potion == null, "rollback clears size, Glow and submenu");
            expectSilentStride(viewer, "rollback clears trail selection");
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
            enableGlow(viewer);
            armTrail(viewer);
            click(viewer, openSizes(viewer), 7, ClickType.LEFT);
            check(viewer.modifiers.containsKey(SIZE_KEY) && viewer.potion != null, "saddle, size and Glow coexist");
            stride(viewer, 4);
            check(viewer.emissions.getLast().particle() == Particle.HEART && viewer.potion != null
                            && viewer.modifiers.containsKey(SIZE_KEY) && fixture.storage[2].getType() == Material.SADDLE
                            && fixture.storage[4].getType() == Material.CHEST,
                    "trails emit alongside a live Size Modifier, Glow, Riding Saddle and Gadgets chest");
            state.enterBuildMode(viewer.player);
            check(state.isBuildMode(viewer.id) && fixture.storage[2] == null && !viewer.modifiers.containsKey(SIZE_KEY) && viewer.potion == null
                            && viewer.open == viewer.bottom && !gadgets.openMenu(viewer.player),
                    "BUILD clears equipment and blocks Gadgets");
            expectSilentStride(viewer, "BUILD clears trail selection");
            state.exitBuildMode(viewer.player);
            check(fixture.storage[2] == null && viewer.potion == null && gadgets.openMenu(viewer.player), "leaving BUILD does not resurrect gadgets");
            enableGlow(viewer);
            armTrail(viewer);
            click(viewer, viewer.open, 10, ClickType.LEFT);
            click(viewer, openSizes(viewer), 1, ClickType.LEFT);
            check(viewer.modifiers.containsKey(SIZE_KEY), "temporary size established before entering activity");
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
            check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == viewer.bottom && viewer.potion == null, "Activity clears size, Glow and submenu");
            expectSilentStride(viewer, "Activity clears trail selection");
            activity.leaveCurrentSession(viewer.player, dev.vapee.core.activity.ActivityLeaveReason.VOLUNTARY);
            check(fixture.storage[2] == null, "leaving Activity does not resurrect equipment");
            viewer.close();
            gadgets.openMenu(viewer.player);
            gadgetConfigOverrides(fixture);
            enableGlow(viewer);
            armTrail(viewer);
            click(viewer, openSizes(viewer), 7, ClickType.LEFT);
            check(viewer.modifiers.containsKey(SIZE_KEY), "temporary size established before disable");
            Inventory foreign = inventory(null, 27);
            viewer.open = foreign;
            gadgets.disable();
            check(viewer.open == foreign && countMenuListeners() == 0 && !viewer.modifiers.containsKey(SIZE_KEY)
                    && viewer.potion == null && viewer.renewals.isEmpty() && !gadgets.openMenu(viewer.player),
                    "disable unregisters listeners without closing a foreign replacement");
            viewer.modifiers.put(SIZE_KEY, new AttributeModifier(SIZE_KEY, 0.5, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            gadgets.enable();
            check(!viewer.modifiers.containsKey(SIZE_KEY) && countMenuListeners() == 1 && gadgets.openMenu(viewer.player),
                    "reenable recovers a leftover modifier and owns a fresh view");
        } finally {
            gadgets.disable();
            activities.disable();
        }
        check(viewer.open == viewer.bottom && countMenuListeners() == 0, "shutdown closes owned menu and removes handlers");
        expectSilentStride(viewer, "disable leaves no trail session or emitter");
    }


    private static void enableGlow(Viewer viewer) {
        check(click(viewer, viewer.open, 12, ClickType.LEFT).isCancelled() && viewer.potion != null,
                "protected Glow entry enables own effect");
    }

    private static void exerciseGlow(Viewer viewer, Inventory top, LobbyItemRegistryFixture fixture, World world) {
        var hotbar = fixture.storage.clone();
        var off = top.getItem(12);
        check(viewer.potion == null && off.getType() == Material.GLOW_INK_SAC
                && plain(off.getItemMeta().displayName()).equals("Glow")
                && plain(off.getItemMeta().lore().getFirst()).equals("Make your character glow.")
                && plain(off.getItemMeta().lore().getLast()).equals("Left-click to enable."), "Glow starts off with compact lore");
        viewer.allowGlow = false;
        click(viewer, top, 12, ClickType.LEFT);
        check(viewer.potion == null && plain(top.getItem(12).getItemMeta().lore().getLast()).equals("Locked")
                && viewer.chat.removeLast().equals("You cannot use Glow."), "Glow permission protects the menu action");
        viewer.allowGlow = true;
        enableGlow(viewer);
        check(viewer.open == top && plain(top.getItem(12).getItemMeta().lore().getFirst()).equals("Enabled")
                && plain(top.getItem(12).getItemMeta().lore().getLast()).equals("Left-click to disable.")
                && viewer.chat.removeLast().equals("Glow enabled.") && viewer.renewals.size() == 1,
                "Glow immediately updates the current menu and sends prefix-free feedback");
        check(dev.vapee.core.message.MessageColorAssertions.hasTextColor(top.getItem(12).getItemMeta().lore().getLast(),
                "Left-click", dev.vapee.core.message.MessageStyle.accent()), "Glow uses the central accent in its actions");
        click(viewer, top, 12, ClickType.DOUBLE_CLICK);
        check(viewer.potion != null && viewer.renewals.size() == 1 && Arrays.equals(hotbar, fixture.storage)
                && plain(top.getItem(22).getItemMeta().displayName()).equals("0/3 equipped"), "Glow uses no item or equipment slot");
        viewer.potion = viewer.potion.withDuration(40);
        viewer.renewals.removeFirst().run();
        check(viewer.potion.getDuration() > 40 && viewer.renewals.size() == 1, "idle session renews once before expiring");
        var staleRenewal = viewer.renewals.getFirst();
        click(viewer, top, 12, ClickType.LEFT);
        staleRenewal.run();
        check(viewer.potion == null && viewer.renewals.isEmpty() && viewer.chat.removeLast().equals("Glow disabled."),
                "manual deactivation clears effect and makes stale renewal inert");

        viewer.vetoPotionWrite = true;
        click(viewer, top, 12, ClickType.LEFT);
        check(viewer.potion == null && viewer.renewals.isEmpty()
                && viewer.chat.removeLast().equals("Glow could not be changed."), "cancelled Paper add never establishes ownership");
        viewer.vetoPotionWrite = false;

        viewer.externalFlag = true;
        click(viewer, top, 12, ClickType.LEFT);
        check(viewer.externalFlag && viewer.potion == null && viewer.chat.removeLast().equals("Glow is already controlled by another source."),
                "external flag is left alone");
        viewer.externalFlag = false;
        var foreign = new PotionEffect(PotionEffectType.GLOWING, 600, 1, false, true, true);
        viewer.potion = foreign;
        click(viewer, top, 12, ClickType.LEFT);
        check(viewer.potion == foreign && viewer.renewals.isEmpty(), "external potion is never claimed");
        viewer.potion = null;
        enableGlow(viewer);
        viewer.externalFlag = true;
        click(viewer, top, 12, ClickType.LEFT);
        check(viewer.externalFlag && viewer.potion == null, "removing our potion preserves a concurrent global flag");
        viewer.externalFlag = false;
        enableGlow(viewer);
        viewer.replaceOnRemoval = foreign;
        click(viewer, top, 12, ClickType.LEFT);
        check(viewer.potion == foreign && viewer.renewals.isEmpty(), "cleanup backs out when a listener installs a replacement");
        viewer.potion = null;
        enableGlow(viewer);
        var identical = viewer.potion.withDuration(viewer.potion.getDuration());
        dispatch(new EntityPotionEffectEvent(viewer.player, viewer.potion, identical, EntityPotionEffectEvent.Cause.PLUGIN,
                EntityPotionEffectEvent.Action.CHANGED, true));
        viewer.potion = identical;
        var location = new org.bukkit.Location(world, 0, 65, 0);
        var moved = location.clone().add(1, 0, 0);
        dispatch(new org.bukkit.event.player.PlayerTeleportEvent(viewer.player, location, moved));
        check(viewer.potion == identical && viewer.renewals.isEmpty(), "an observed identical replacement is spared by cleanup");
        var loaded = identical.withDuration(20);
        viewer.potion = loaded;
        dispatch(new org.bukkit.event.player.PlayerJoinEvent(viewer.player, Component.empty()));
        check(viewer.potion == loaded && viewer.renewals.isEmpty(), "join leaves non-owned saved effects alone and never renews them");
        viewer.potion = null;

        enableGlow(viewer);
        viewer.allowGlow = false;
        dispatch(new org.bukkit.event.player.PlayerMoveEvent(viewer.player, location, moved));
        check(viewer.potion == null && viewer.renewals.isEmpty(), "movement reconciles Glow permission revocation");
        viewer.allowGlow = true;
        enableGlow(viewer);
        viewer.allowGadgets = false;
        viewer.renewals.removeFirst().run();
        check(viewer.potion == null && viewer.renewals.isEmpty(), "idle renewal reconciles general permission revocation");
        viewer.allowGadgets = true;

        enableGlow(viewer);
        var cancelledTeleport = new org.bukkit.event.player.PlayerTeleportEvent(viewer.player, location, moved);
        cancelledTeleport.setCancelled(true);
        dispatch(cancelledTeleport);
        check(viewer.potion != null, "cancelled teleport leaves owned Glow active");
        cancelledTeleport.setCancelled(false);
        dispatch(cancelledTeleport);
        check(viewer.potion == null && viewer.renewals.isEmpty(), "teleport clears only owned Glow and cancels renewal");
        viewer.messagePrefix = "<gray>[Lobby]</gray> ";
        enableGlow(viewer);
        check(viewer.chat.removeLast().equals("[Lobby] Glow enabled."), "configured operator prefix still applies");
        viewer.messagePrefix = "";
        click(viewer, viewer.open, 12, ClickType.LEFT);
    }

    private record Emission(Particle particle, List<?> viewers, Player source, double x, double y, int count, boolean force) { }

    private static Inventory openTrails(Viewer viewer) {
        var main = viewer.open;
        check(click(viewer, main, 14, ClickType.LEFT).isCancelled() && viewer.open == main,
                "opening Trails is deferred beyond the protected click");
        viewer.runNavigation();
        check(viewer.open != main && viewer.open.getSize() == 27, "Trails has its own 27-slot menu");
        return viewer.open;
    }

    private static void backToGadgets(Viewer viewer) {
        click(viewer, viewer.open, 22, ClickType.LEFT);
        viewer.runNavigation();
        check(plain(viewer.title).equals("Gadgets"), "back returns to the main Gadgets menu");
    }

    private static void armTrail(Viewer viewer) {
        click(viewer, openTrails(viewer), 10, ClickType.LEFT);
        backToGadgets(viewer);
    }

    private static void stride(Viewer viewer, int ticks) {
        viewer.tick += ticks;
        var from = new Location(viewer.world, 0, 65, 0);
        dispatch(new PlayerMoveEvent(viewer.player, from, from.clone().add(0.35, 0, 0)));
    }

    private static void expectSilentStride(Viewer viewer, String reason) {
        int before = viewer.emissions.size();
        stride(viewer, 4);
        check(viewer.emissions.size() == before, reason);
    }

    private static Viewer otherViewer(World world, double x) {
        var viewer = new Viewer();
        viewer.player = viewer.delegate;
        viewer.world = world;
        viewer.positionX = x;
        return viewer;
    }

    private static void exerciseTrails(Viewer viewer, LobbyItemRegistryFixture fixture, PlayerService players, SocialService social) {
        var main = viewer.open;
        viewer.allowTrails = false;
        click(viewer, main, 14, ClickType.LEFT);
        check(viewer.open == main && viewer.tasks.isEmpty()
                && plain(main.getItem(14).getItemMeta().lore().getLast()).equals("Locked")
                && viewer.chat.removeLast().equals("You cannot use trails."), "denied entry stays in the main menu");
        viewer.allowTrails = true;
        click(viewer, main, 14, ClickType.LEFT);
        viewer.allowTrails = false;
        viewer.runNavigation();
        check(viewer.open == main, "trails navigation rechecks permissions");
        viewer.allowTrails = true;
        click(viewer, main, 14, ClickType.LEFT);
        viewer.close();
        viewer.runNavigation();
        check(viewer.open == viewer.bottom, "a closed view cannot open the deferred submenu");
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        main = viewer.open;
        var submenu = openTrails(viewer);
        check(plain(viewer.title).equals("Trails")
                && plain(submenu.getItem(16).getItemMeta().lore().getFirst()).equals("Selected"), "None is highlighted by default");

        var peer = viewer.peer;
        peer.positionX = 3;
        var far = otherViewer(viewer.world, 33);
        var unloaded = otherViewer(viewer.world, 5);
        World other = proxy(World.class, (name, args) -> name.equals("getName") ? "elsewhere" : null);
        var elsewhere = otherViewer(other, 5);
        var unavailable = otherViewer(viewer.world, 5);
        unavailable.failVisibility = true;
        players.loadPlayer(far.id, "FarTrailViewer");
        players.loadPlayer(elsewhere.id, "OtherWorldViewer");
        players.loadPlayer(unavailable.id, "FailedVisibilityViewer");
        viewer.nearby = List.of(viewer.player, peer.player, far.player, unloaded.player, elsewhere.player, unavailable.player);

        var hotbar = fixture.storage.clone();
        Particle[] effects = {Particle.HEART, Particle.FLAME, Particle.CLOUD, Particle.END_ROD, Particle.HAPPY_VILLAGER};
        String[] labels = {"Hearts", "Flame", "Cloud", "End Rod", "Happy Villager"};
        for (int i = 0; i < effects.length; i++) {
            int slot = 10 + i;
            click(viewer, submenu, slot, ClickType.LEFT);
            check(plain(submenu.getItem(slot).getItemMeta().displayName()).equals(labels[i])
                    && plain(submenu.getItem(slot).getItemMeta().lore().getFirst()).equals("Selected"),
                    "selected Trail is highlighted: " + labels[i]);
            check(viewer.chat.removeLast().equals(labels[i] + " trail selected."), "localized prefix-free trail feedback");
            check(dev.vapee.core.message.MessageColorAssertions.hasTextColor(viewer.lastChat, labels[i],
                    dev.vapee.core.message.MessageStyle.accent()), "selected Trail uses central accent");
            if (i == 0) {
                stride(viewer, 3);
                check(viewer.emissions.isEmpty(), "first emission waits four ticks");
                stride(viewer, 1);
            } else {
                int count = viewer.emissions.size();
                stride(viewer, 3);
                check(viewer.emissions.size() == count, "switching Trail preserves the existing cooldown");
                stride(viewer, 1);
            }
            var emission = viewer.emissions.getLast();
            check(emission.particle() == effects[i] && emission.source() == viewer.player && emission.count() <= 2
                    && !emission.force() && emission.x() == 0 && Math.abs(emission.y() - 65.15) < 0.00001,
                    "correct bounded particle at the previous feet position");
        }
        check(viewer.emissions.getLast().viewers().equals(List.of(viewer.player, peer.player)),
                "receiver list excludes other worlds, range misses, unloaded viewers and failed visibility checks");
        int before = viewer.emissions.size();
        stride(viewer, 0);
        check(viewer.emissions.size() == before, "multiple moves in the same tick cannot emit twice");
        var from = new Location(viewer.world, 0, 65, 0);
        var rotation = from.clone();
        rotation.setYaw(90);
        viewer.tick += 4;
        dispatch(new PlayerMoveEvent(viewer.player, from, from.clone()));
        dispatch(new PlayerMoveEvent(viewer.player, from, rotation));
        dispatch(new PlayerMoveEvent(viewer.player, from, from.clone().add(0.01, 0, 0)));
        dispatch(new PlayerMoveEvent(viewer.player, from, from.clone().add(8, 0, 0)));
        dispatch(new PlayerMoveEvent(viewer.player, from, new Location(other, 0.5, 65, 0)));
        dispatch(new PlayerMoveEvent(viewer.player, from, from.clone().add(Double.NaN, 0, 0)));
        var cancelledMove = new PlayerMoveEvent(viewer.player, from, from.clone().add(0.35, 0, 0));
        cancelledMove.setCancelled(true);
        dispatch(cancelledMove);
        check(viewer.emissions.size() == before, "stillness, rotation, jitter, jumps, other worlds, invalid values and cancelled moves do not emit");

        var preferences = players.getPlayer(peer.id).orElseThrow().getSettings().getVisibility();
        preferences.setMode(VisibilityMode.HIDDEN);
        stride(viewer, 0);
        check(viewer.emissions.getLast().viewers().equals(List.of(viewer.player)), "visibility change applies to the next emission");
        preferences.setMode(VisibilityMode.ALL);
        peer.canSee = false;
        stride(viewer, 4);
        check(viewer.emissions.getLast().viewers().equals(List.of(viewer.player)), "Paper hides veto a Trail even in ALL mode");
        peer.canSee = true;
        social.ignore(peer.id, viewer.id);
        stride(viewer, 4);
        check(viewer.emissions.getLast().viewers().equals(List.of(viewer.player)), "viewer ignoring source gets no Trail");
        social.unignore(peer.id, viewer.id);
        social.ignore(viewer.id, peer.id);
        stride(viewer, 4);
        check(viewer.emissions.getLast().viewers().equals(List.of(viewer.player)), "source ignoring viewer also blocks the Trail");
        social.unignore(viewer.id, peer.id);
        stride(viewer, 4);
        check(viewer.emissions.getLast().viewers().equals(List.of(viewer.player, peer.player)), "eligible viewer returns without reselecting");

        click(peer, submenu, 10, ClickType.LEFT);
        click(viewer, main, 10, ClickType.LEFT);
        check(Arrays.equals(hotbar, fixture.storage), "foreign and stale menu clicks cannot equip gear or change slots");
        viewer.allowTrails = false;
        click(viewer, submenu, 11, ClickType.LEFT);
        check(viewer.chat.removeLast().equals("You cannot use trails."), "selection rechecks a revoked permission");
        expectSilentStride(viewer, "revoked Trail permission clears emission");
        viewer.allowTrails = true;
        click(viewer, submenu, 11, ClickType.LEFT);
        viewer.allowGadgets = false;
        expectSilentStride(viewer, "movement reconciles the general Gadgets permission");
        viewer.allowGadgets = true;
        expectSilentStride(viewer, "permission restoration does not reactivate a Trail");
        click(viewer, submenu, 10, ClickType.LEFT);
        click(viewer, submenu, 16, ClickType.LEFT);
        check(viewer.chat.removeLast().equals("Trails disabled.")
                && plain(submenu.getItem(16).getItemMeta().lore().getFirst()).equals("Selected"), "None deactivates and is highlighted");
        expectSilentStride(viewer, "None is silent");

        viewer.messagePrefix = "<gray>[Lobby]</gray> ";
        click(viewer, submenu, 10, ClickType.LEFT);
        check(viewer.chat.removeLast().equals("[Lobby] Hearts trail selected."), "operator prefix is preserved");
        viewer.messagePrefix = "";
        backToGadgets(viewer);
        check(Arrays.equals(hotbar, fixture.storage) && plain(viewer.open.getItem(22).getItemMeta().displayName()).equals("0/3 equipped"),
                "Trail does not consume a hotbar slot or equipment capacity");
        viewer.vehicle = peer.player;
        expectSilentStride(viewer, "a rider cannot emit in transit");
        viewer.vehicle = null;
        stride(viewer, 4);
        check(viewer.emissions.getLast().particle() == Particle.HEART, "Trail resumes after dismount");
        dispatch(new PlayerTeleportEvent(viewer.player, from, from.clone().add(0.35, 0, 0)));
        expectSilentStride(viewer, "even a short teleport ends the Trail session");
    }

    private static Inventory openSizes(Viewer viewer) {
        var main = viewer.open;
        check(click(viewer, main, 11, ClickType.LEFT).isCancelled() && viewer.open == main,
                "navigation cancels inventory click and waits for next tick");
        viewer.runNavigation();
        check(viewer.open != main && viewer.open.getSize() == 18, "opens a fresh 18-slot Size Changer submenu");
        return viewer.open;
    }

    private static void exerciseSizes(Viewer viewer, Inventory main, LobbyItemRegistryFixture fixture, World world) {
        viewer.allowSize = false;
        check(click(viewer, main, 11, ClickType.LEFT).isCancelled() && viewer.open == main && viewer.tasks.isEmpty()
                && plain(main.getItem(11).getItemMeta().lore().getLast()).equals("Locked")
                && viewer.chat.removeLast().equals("You cannot use Size Changer."), "locked entry and prefix-free denial");
        viewer.allowSize = true;
        click(viewer, main, 11, ClickType.LEFT);
        click(viewer, main, 11, ClickType.LEFT);
        check(viewer.tasks.size() == 1, "duplicate navigation queues only one transition");
        viewer.close();
        viewer.runNavigation();
        check(viewer.open == viewer.bottom, "closed view cannot reopen a stale submenu");

        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        main = viewer.open;
        click(viewer, main, 11, ClickType.LEFT);
        viewer.allowSize = false;
        viewer.runNavigation();
        check(viewer.open == main, "navigation rechecks permission after the click");
        viewer.allowSize = true;
        var hotbar = fixture.storage.clone();
        var submenu = openSizes(viewer);
        check(plain(viewer.title).equals("Size Changer") && submenu.getItem(4).getType() == Material.LIME_DYE,
                "27-slot menu highlights Normal initially");
        var foreignKey = new NamespacedKey("fixture", "external_scale");
        var foreign = new AttributeModifier(foreignKey, 0.2, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        viewer.baseScale = 1.2;
        viewer.modifiers.put(foreignKey, foreign);
        double[] factors = {0.5, 0.75, 1.0, 1.25, 1.5};
        String[] titles = {"Tiny", "Small", "Normal", "Tall", "Giant"};
        int[] slots = {1, 2, 4, 6, 7};
        for (int i = 0; i < factors.length; i++) {
            int slot = slots[i];
            check(plain(submenu.getItem(slot).getItemMeta().displayName()).equals(titles[i]), "localized size " + titles[i]);
            check(click(viewer, submenu, slot, ClickType.LEFT).isCancelled(), "protected size slot " + slot);
            var effect = viewer.modifiers.get(SIZE_KEY);
            check(factors[i] == 1.0 ? effect == null : effect != null && effect.getAmount() == factors[i] - 1.0,
                    "selection applies the expected transient multiplier for " + titles[i]);
            check(submenu.getItem(slot).getType() == Material.LIME_DYE
                    && plain(submenu.getItem(slot).getItemMeta().lore().getLast()).equals("Selected")
                    && viewer.baseScale == 1.2 && viewer.modifiers.get(foreignKey) == foreign,
                    "selection updates highlight and preserves foreign scale");
        }
        int writes = viewer.scaleWrites;
        click(viewer, submenu, 7, ClickType.DOUBLE_CLICK);
        click(viewer, submenu, 7, ClickType.LEFT);
        check(viewer.scaleWrites == writes, "duplicate clicks do not rewrite the modifier");
        check(click(viewer, main, 10, ClickType.LEFT).isCancelled()
                && viewer.scaleWrites == writes && Arrays.equals(hotbar, fixture.storage), "a stale main menu cannot change inventory");
        check(click(viewer.peer, submenu, 7, ClickType.LEFT).isCancelled() && viewer.peer.modifiers.isEmpty(),
                "foreign viewer cannot use another player's submenu");

        var attemptedMount = new org.bukkit.event.entity.EntityMountEvent(viewer.player, viewer.peer.player);
        attemptedMount.setCancelled(true);
        dispatch(attemptedMount);
        check(viewer.modifiers.containsKey(SIZE_KEY), "cancelled mount keeps the chosen scale");
        dispatch(new org.bukkit.event.entity.EntityMountEvent(viewer.player, viewer.peer.player));
        check(!viewer.modifiers.containsKey(SIZE_KEY), "accepted mount clears size");
        click(viewer, submenu, 1, ClickType.LEFT);
        viewer.allowSize = false;
        click(viewer, submenu, 7, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.chat.removeLast().equals("You cannot use Size Changer."),
                "selection rechecks permission and clears the old effect");
        viewer.allowSize = true;
        click(viewer, submenu, 1, ClickType.LEFT);
        viewer.allowGadgets = false;
        var from = new org.bukkit.Location(world, 0, 65, 0);
        var to = from.clone().add(1, 0, 0);
        dispatch(new org.bukkit.event.player.PlayerMoveEvent(viewer.player, from, to));
        click(viewer, submenu, 7, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY), "movement reconciles a lost general permission");
        viewer.allowGadgets = true;
        click(viewer, submenu, 1, ClickType.LEFT);
        var teleport = new org.bukkit.event.player.PlayerTeleportEvent(viewer.player, from, to);
        teleport.setCancelled(true);
        dispatch(teleport);
        check(viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == submenu, "cancelled teleport keeps the scale and submenu");
        teleport.setCancelled(false);
        dispatch(teleport);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == viewer.bottom, "accepted teleport clears scale and submenu");
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        submenu = openSizes(viewer);
        click(viewer, submenu, 1, ClickType.LEFT);
        var kick = new org.bukkit.event.player.PlayerKickEvent(viewer.player, Component.empty(), Component.empty(),
                org.bukkit.event.player.PlayerKickEvent.Cause.PLUGIN);
        kick.setCancelled(true);
        dispatch(kick);
        check(viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == submenu, "cancelled kick keeps effect and menu");
        kick.setCancelled(false);
        dispatch(kick);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == viewer.bottom, "accepted kick clears effect and menu");
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        submenu = openSizes(viewer);
        click(viewer, submenu, 1, ClickType.LEFT);
        var death = new org.bukkit.event.entity.PlayerDeathEvent(viewer.player,
                proxy(org.bukkit.damage.DamageSource.class, (name, args) -> null), new java.util.ArrayList<>(), 0, Component.empty(), false);
        dispatch(death);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.open == viewer.bottom, "death clears effect and menu");
        var leftover = new AttributeModifier(SIZE_KEY, 0.5, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        viewer.modifiers.put(SIZE_KEY, leftover);
        dispatch(new org.bukkit.event.player.PlayerRespawnEvent(viewer.player, from, false, false, false,
                org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH));
        check(!viewer.modifiers.containsKey(SIZE_KEY), "respawn clears an inherited modifier");
        viewer.modifiers.put(SIZE_KEY, leftover);
        dispatch(new org.bukkit.event.player.PlayerJoinEvent(viewer.player, Component.empty()));
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.modifiers.get(foreignKey) == foreign,
                "join recovers stale owned scale and preserves foreign scale");
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        submenu = openSizes(viewer);
        click(viewer, submenu, 6, ClickType.LEFT);
        click(viewer, submenu, 17, ClickType.LEFT);
        viewer.runNavigation();
        check(viewer.open != submenu && plain(viewer.title).equals("Gadgets") && viewer.modifiers.containsKey(SIZE_KEY)
                && Arrays.equals(hotbar, fixture.storage)
                && plain(viewer.open.getItem(22).getItemMeta().displayName()).equals("0/3 equipped"),
                "Back preserves size and Size Changer spends no slot or equipment capacity");
    }

    private static void exerciseSizeTiers(Viewer viewer, LobbyItemRegistryFixture fixture) {
        var main = viewer.open;
        int[] categories = {10, 11, 12, 14, 15, 16};
        var icons = List.of(Material.SADDLE, Material.SLIME_BALL, Material.GLOW_INK_SAC, Material.BLAZE_POWDER,
                Material.FEATHER, Material.FIREWORK_ROCKET);
        check(main.getSize() == 27 && main.getItem(24) == null && main.getItem(22).getType() == Material.PAPER
                && java.util.stream.IntStream.range(0, categories.length)
                        .allMatch(i -> main.getItem(categories[i]).getType() == icons.get(i)), "original main layout, with no Team entry for standard users");
        click(viewer, main, 24, ClickType.LEFT);
        check(viewer.tasks.isEmpty(), "hidden Team slot remains inert for artificial clicks");
        var hotbar = fixture.storage.clone();
        var external = new NamespacedKey("fixture", "external_scale");
        var foreign = viewer.modifiers.get(external);
        var sizes = openSizes(viewer);
        click(viewer, sizes, 4, ClickType.LEFT);
        check(plain(sizes.getItem(11).getItemMeta().lore().getLast()).equals("Locked")
                && plain(sizes.getItem(15).getItemMeta().lore().getLast()).equals("Locked"), "VIP choices stay visible as locked");
        click(viewer, sizes, 11, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.chat.removeLast().equals("You cannot use Size Changer."),
                "a locked size cannot be activated through a direct click");
        viewer.allowSizeVip = true;
        click(viewer, sizes, 11, ClickType.LEFT);
        check(viewer.modifiers.get(SIZE_KEY).getAmount() == 1.0 && sizes.getItem(11).getType() == Material.LIME_DYE,
                "Huge applies 2x and is marked as active");
        viewer.collision = true;
        click(viewer, sizes, 15, ClickType.LEFT);
        check(viewer.modifiers.get(SIZE_KEY).getAmount() == 1.0
                && viewer.chat.removeLast().equals("There isn't enough room for this size."), "collision refusal preserves the current selection");
        viewer.collision = false;
        click(viewer, sizes, 15, ClickType.LEFT);
        check(viewer.modifiers.get(SIZE_KEY).getAmount() == 2.0 && sizes.getItem(15).getType() == Material.LIME_DYE,
                "Massive replaces Huge with the same key at 3x");
        viewer.allowSizeVip = false;
        stride(viewer, 4);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.modifiers.get(external) == foreign && viewer.baseScale == 1.2,
                "VIP revocation removes only the owned modifier");
        click(viewer, sizes, 4, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY), "normal reset stays available without the VIP grant");
        click(viewer, sizes, 17, ClickType.LEFT);
        viewer.runNavigation();
        check(plain(viewer.title).equals("Gadgets"), "standard size back occupies slot 17");

        viewer.allowTeam = true;
        viewer.close();
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        check(plain(viewer.open.getItem(24).getItemMeta().displayName()).equals("Team Gadgets"), "Team grant exposes the category");
        click(viewer, viewer.open, 24, ClickType.LEFT);
        viewer.runNavigation();
        var team = viewer.open;
        var boots = team.getItem(13);
        check(team.getSize() == 27 && boots.getType() == Material.LEATHER_BOOTS
                && ((LeatherArmorMeta) boots.getItemMeta()).getColor().equals(Color.BLUE), "Team menu has blue leather boots");
        click(viewer, team, 13, ClickType.SHIFT_LEFT);
        click(viewer, team, 13, ClickType.LEFT);
        check(viewer.tasks.isEmpty() && viewer.open == team && plain(boots.getItemMeta().lore().getLast()).equals("Locked"),
                "Team alone cannot transfer the icon or open Extreme Size");
        viewer.allowExtreme = true;
        viewer.allowSize = false;
        click(viewer, team, 13, ClickType.LEFT);
        check(viewer.tasks.isEmpty(), "Extreme also requires the base Size Changer grant");
        viewer.allowSize = true;
        click(viewer, team, 13, ClickType.LEFT);
        viewer.allowTeam = false;
        viewer.runNavigation();
        check(viewer.open == viewer.bottom, "queued navigation refuses a revoked source page");
        viewer.allowTeam = true;
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        click(viewer, viewer.open, 24, ClickType.LEFT);
        viewer.runNavigation();
        click(viewer, viewer.open, 13, ClickType.LEFT);
        viewer.runNavigation();
        var extreme = viewer.open;
        check(plain(viewer.title).equals("Extreme Size") && extreme.getItem(11).getType() == Material.LIME_DYE,
                "Extreme opens with Normal selected");
        viewer.loadedCollisionChunks = false;
        click(viewer, extreme, 15, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY), "unchecked volume is rejected without loading chunks");
        viewer.loadedCollisionChunks = true;
        viewer.baseScale = 4.0;
        click(viewer, extreme, 15, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.chat.removeLast().equals("Your current scale cannot safely support this size."),
                "result outside SCALE bounds is refused");
        viewer.baseScale = 1.2;
        click(viewer.peer, extreme, 15, ClickType.LEFT);
        click(viewer, extreme, 15, ClickType.DOUBLE_CLICK);
        check(!viewer.modifiers.containsKey(SIZE_KEY), "foreign and double clicks cannot select Colossal");
        click(viewer, extreme, 15, ClickType.LEFT);
        check(viewer.modifiers.get(SIZE_KEY).getAmount() == 4.0 && viewer.modifiers.get(external) == foreign
                && extreme.getItem(15).getType() == Material.LIME_DYE && Math.abs(viewer.checkedBox.getHeight() - 12.96) < 0.01,
                "Colossal applies 5x, with a collision envelope including existing foreign SCALE");
        click(viewer, extreme, 11, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY) && viewer.modifiers.get(external) == foreign, "Team Normal removes only the owned modifier");
        click(viewer, extreme, 15, ClickType.LEFT);
        viewer.allowExtreme = false;
        click(viewer, extreme, 15, ClickType.LEFT);
        viewer.allowExtreme = true;
        click(viewer, extreme, 15, ClickType.LEFT);
        check(!viewer.modifiers.containsKey(SIZE_KEY), "revocation clears Colossal and invalidates that exact view even after regrant");
        viewer.runNavigation();
        check(viewer.open == viewer.bottom, "revoked view is closed outside the click callback");

        viewer.locale = Locale.GERMANY;
        fixture.click(fixture.storage[4], Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
        click(viewer, viewer.open, 24, ClickType.LEFT);
        viewer.runNavigation();
        click(viewer, viewer.open, 13, ClickType.LEFT);
        viewer.runNavigation();
        check(plain(viewer.title).equals("Extremgröße") && plain(viewer.open.getItem(15).getItemMeta().displayName()).equals("Kolossal"),
                "German Extreme choices are localized");
        viewer.collision = true;
        click(viewer, viewer.open, 15, ClickType.LEFT);
        check(viewer.chat.removeLast().equals("Für diese Größe ist hier nicht genug Platz."), "German collision refusal is prefix-free");
        viewer.collision = false;
        click(viewer, viewer.open, 22, ClickType.LEFT);
        viewer.runNavigation();
        check(plain(viewer.title).equals("Team-Gadgets"), "Extreme Back returns to Team");
        click(viewer, viewer.open, 22, ClickType.LEFT);
        viewer.runNavigation();
        check(plain(viewer.title).equals("Gadgets") && Arrays.equals(hotbar, fixture.storage)
                && plain(viewer.open.getItem(22).getItemMeta().displayName()).equals("0/3 ausgerüstet"), "Team Back restores the main menu without consuming equipment");
        viewer.allowTeam = false;
        viewer.allowExtreme = false;
        viewer.locale = Locale.US;
    }

    private static Viewer exerciseRiding(LobbyItemRegistryFixture fixture, Viewer viewer, PlayerService players,
                                         World world, ItemStack saddle, dev.vapee.core.social.SocialService social,
                                         LocalizationService localization, Path root) throws Exception {
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
                && viewer.chat.getFirst().equals("RidingFixture must be sneaking to be picked up."),
                "duplicate events give one English denial without a feature prefix");
        target.sneaking = true;
        viewer.tick += 3;
        var pickup = new PlayerInteractAtEntityEvent(viewer.player, target.player, new Vector(), EquipmentSlot.HAND);
        dispatch(pickup);
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(pickup.isCancelled() && target.vehicle == viewer.player && viewer.passengers.equals(List.of(target.player)),
                "paired events create exactly one mount in the correct direction");
        check(viewer.chat.getLast().equals("You are now carrying RidingFixture."),
                "pickup feedback starts with the English message body");
        var accent = dev.vapee.core.message.MessageStyle.accent();
        check(dev.vapee.core.message.MessageColorAssertions.hasTextColor(viewer.lastChat, "carrying", accent)
                        && dev.vapee.core.message.MessageColorAssertions.hasTextColor(viewer.lastChat, "RidingFixture", accent),
                "prefix-free feedback retains accent on action and literal name");
        var foreignMount = new EntityMountEvent(viewer.player, target.player);
        dispatch(foreignMount);
        check(foreignMount.isCancelled(), "foreign mount cannot introduce a chain");
        dispatch(fixture.click(saddle, Action.LEFT_CLICK_AIR, EquipmentSlot.HAND));
        check(target.vehicle == null && viewer.passengers.isEmpty() && target.velocity != null
                        && target.velocity.distanceSquared(new Vector(0, 0.32, 0.75)) < 0.000001,
                "air click detaches and launches the rider once");
        check(viewer.chat.getLast().equals("You launched RidingFixture."), "launch feedback has no feature prefix");
        Vector launched = target.velocity;
        dispatch(fixture.click(saddle, Action.LEFT_CLICK_AIR, EquipmentSlot.HAND));
        check(target.velocity == launched, "air click without rider has no effect");

        target.sneaking = true;
        viewer.tick += 3;
        var attack = new PrePlayerAttackEntityEvent(viewer.player, target.player, true);
        dispatch(attack);
        check(attack.isCancelled() && viewer.vehicle == target.player, "attack intercepted before damage and mounts actor");
        check(viewer.chat.getLast().equals("You are now riding RidingFixture."), "mount feedback has no feature prefix");
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
        viewer.locale = Locale.GERMANY;
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.player, "mount available again after ignore removal");
        check(viewer.chat.getLast().equals("Du trägst jetzt RidingFixture."), "German feedback has no feature prefix");
        viewer.passengers.clear();
        target.vehicle = null;
        dispatch(new EntityDismountEvent(target.player, viewer.player));
        viewer.tick += 3;
        target.sneaking = true;
        var originalCatalog = localization.snapshot();
        Path overrides = Files.createDirectory(root.resolve("riding-language-overrides"));
        Files.writeString(overrides.resolve("de.yml"), """
                gadgets:
                  riding:
                    carrying: "Du hast <player> aufgenommen."
                """);
        localization.apply(localization.prepare(overrides));
        viewer.messagePrefix = "<gray>[Lobby]</gray> ";
        dispatch(new PlayerInteractEntityEvent(viewer.player, target.player, EquipmentSlot.HAND));
        check(target.vehicle == viewer.player, "external dismount forgets both roles");
        check(viewer.chat.getLast().equals("[Lobby] Du hast RidingFixture aufgenommen."),
                "configured prefix and operator translation survive without an extra feature prefix");
        viewer.messagePrefix = "";
        viewer.locale = Locale.US;
        localization.apply(originalCatalog);
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
        private String messagePrefix = "";
        private Component lastChat;
        private Component title;
        private Player player;
        private boolean sneaking;
        private boolean allowGadgets = true;
        private boolean allowRiding = true;
        private boolean allowSize = true;
        private boolean allowSizeVip;
        private boolean allowTeam;
        private boolean allowExtreme;
        private boolean loadedCollisionChunks = true;
        private boolean collision;
        private BoundingBox checkedBox;
        private boolean allowGlow = true;
        private boolean allowTrails = true;
        private boolean failVisibility;
        private double positionX;
        private List<Player> nearby = List.of();
        private final List<Emission> emissions = new java.util.ArrayList<>();
        private boolean externalFlag;
        private boolean vetoPotionWrite;
        private PotionEffect potion;
        private PotionEffect replaceOnRemoval;
        private final java.util.List<Runnable> renewals = new java.util.ArrayList<>();
        private boolean scaleAvailable = true;
        private double baseScale = 1.0;
        private int scaleWrites;
        private final Map<net.kyori.adventure.key.Key, AttributeModifier> modifiers = new java.util.HashMap<>();
        private final AttributeInstance scale = proxy(AttributeInstance.class, (name, args) -> switch (name) {
            case "getModifier" -> modifiers.get(args[0]);
            case "getBaseValue" -> baseScale;
            case "getValue" -> {
                double value = baseScale;
                for (var modifier : modifiers.values()) {
                    if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) value *= 1.0 + modifier.getAmount();
                }
                yield Math.clamp(value, 0.0625, 16.0);
            }
            case "addTransientModifier" -> {
                var modifier = (AttributeModifier) args[0];
                if (modifiers.putIfAbsent(modifier.getKey(), modifier) != null) throw new AssertionError("duplicate modifier");
                scaleWrites++;
                yield null;
            }
            case "removeModifier" -> { modifiers.remove(args[0]); scaleWrites++; yield null; }
            default -> throw new AssertionError("unexpected scale operation " + name);
        });
        private final java.util.List<Runnable> tasks = new java.util.ArrayList<>();
        private final BukkitScheduler scheduler = proxy(BukkitScheduler.class, (name, args) -> {
            var queue = switch (name) {
                case "runTask" -> tasks;
                case "runTaskLater" -> renewals;
                default -> throw new AssertionError("unexpected scheduler operation " + name);
            };
            Runnable runnable = (Runnable) args[1];
            queue.add(runnable);
            return proxy(BukkitTask.class, (method, parameters) -> {
                if (method.equals("cancel")) queue.remove(runnable);
                return null;
            });
        });
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
            case "hasPermission" -> switch (args[0].toString()) {
                case GadgetsModule.USE_PERMISSION -> allowGadgets;
                case "vapeecore.gadgets.player-riding" -> allowRiding;
                case "vapeecore.gadgets.size-changer" -> allowSize;
                case "vapeecore.gadgets.size.vip" -> allowSizeVip;
                case "vapeecore.gadgets.team.use" -> allowTeam;
                case "vapeecore.gadgets.team.extreme-size" -> allowExtreme;
                case "vapeecore.gadgets.glow" -> allowGlow;
                case "vapeecore.gadgets.trails" -> allowTrails;
                default -> false;
            };
            case "isGlowing" -> externalFlag || potion != null;
            case "getPotionEffect" -> args[0] == PotionEffectType.GLOWING ? potion : null;
            case "addPotionEffect" -> changePotion((PotionEffect) args[0]);
            case "removePotionEffect" -> { changePotion(null); yield null; }
            case "setGlowing", "getScoreboard", "setScoreboard", "hidePlayer", "showPlayer" ->
                    throw new AssertionError("Glow must not change shared flags, teams or visibility: " + name);
            case "getAttribute" -> scaleAvailable && args[0] == Attribute.SCALE ? scale : null;
            case "setSneaking" -> { sneaking = (boolean) args[0]; yield null; }
            case "getWorld" -> world;
            case "isFlying" -> flying;
            case "getLocation" -> new Location(world, positionX, 65, 0);
            case "wouldCollideUsing" -> { checkedBox = ((BoundingBox) args[0]).clone(); yield collision; }
            case "canSee" -> {
                if (failVisibility) throw new IllegalStateException("visibility lookup failed");
                yield canSee;
            }
            case "getVehicle" -> vehicle;
            case "getPassengers" -> List.copyOf(passengers);
            case "getCurrentInput" -> proxy(org.bukkit.Input.class, (method, params) -> false);
            case "getYaw" -> 0F;
            case "sendMessage" -> {
                if (args[0] instanceof Component component) {
                    lastChat = component;
                    chat.add(plain(component));
                }
                yield null;
            }
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

        private Object worldCall(String name, Object[] args) {
            return switch (name) {
                case "getNearbyPlayers" -> nearby;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "isChunkLoaded" -> loadedCollisionChunks;
                case "spawnParticle" -> {
                    if (args.length != 13 || !(args[1] instanceof List<?> recipients) || !(args[2] instanceof Player source)) {
                        throw new AssertionError("Trail must send with an explicit receiver list and source");
                    }
                    emissions.add(new Emission((Particle) args[0], List.copyOf(recipients), source,
                            (double) args[3], (double) args[4], (int) args[6], (boolean) args[12]));
                    yield null;
                }
                default -> null;
            };
        }

        private boolean changePotion(PotionEffect next) {
            var action = next == null ? EntityPotionEffectEvent.Action.REMOVED
                    : potion == null ? EntityPotionEffectEvent.Action.ADDED : EntityPotionEffectEvent.Action.CHANGED;
            var event = new EntityPotionEffectEvent(player, potion, next, EntityPotionEffectEvent.Cause.PLUGIN, action, true);
            event.setCancelled(vetoPotionWrite);
            if (next == null && replaceOnRemoval != null) {
                var replacement = replaceOnRemoval;
                replaceOnRemoval = null;
                changePotion(replacement);
            }
            dispatch(event);
            if (event.isCancelled()) return false;
            potion = next;
            return true;
        }

        private void runNavigation() {
            var scheduled = List.copyOf(tasks);
            tasks.clear();
            scheduled.forEach(Runnable::run);
        }

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

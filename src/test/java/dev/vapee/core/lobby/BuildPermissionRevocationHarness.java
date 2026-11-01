package dev.vapee.core.lobby;

import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;

/** Real state lifecycle and event handlers, with effective-permission/context changes at runtime. */
public final class BuildPermissionRevocationHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (Fixture f = new Fixture()) {
            f.state.enterBuildMode(f.player);
            check(!f.state.hasBuildAuthority(f.player), "owned state alone grants no authority");
            f.permission = true;
            check(f.state.hasBuildAuthority(f.player), "effective grant authorizes existing BUILD");
            BlockBreakEvent authorized = f.breakEvent(); f.listener.onBlockBreak(authorized);
            check(!authorized.isCancelled(), "authorized BUILD break remains usable");
            var authorizedCreative = f.creative(); f.listener.onInventoryClick(authorizedCreative);
            check(!authorizedCreative.isCancelled(), "authorized Creative inventory remains usable");
            f.permission = false;
            BlockBreakEvent denied = f.breakEvent(); f.listener.onBlockBreak(denied);
            check(denied.isCancelled(), "revocation immediately denies block break");
            var place = new BlockPlaceEvent(f.block, proxy(BlockState.class, (m, a) -> null), f.block,
                    new TestItem(), f.player, true, EquipmentSlot.HAND);
            f.listener.onBlockPlace(place); check(place.isCancelled(), "revocation immediately denies block place");
            var creative = f.creative(); f.listener.onInventoryClick(creative);
            check(creative.isCancelled(), "Creative injection denied before mutation");
            for (ClickType click : List.of(ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY,
                    ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK, ClickType.DROP)) {
                var event = new InventoryClickEvent(f.view, InventoryType.SlotType.CONTAINER, 28, click,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY, 1);
                f.listener.onInventoryClick(event); check(event.isCancelled(), "revoked inventory route " + click);
            }
            var drag = new InventoryDragEvent(f.view, null, new TestItem(), false, Map.of(0, new TestItem()));
            f.listener.onInventoryDrag(drag); check(drag.isCancelled(), "revoked drag denied");
            var drop = new PlayerDropItemEvent(f.player, proxy(Item.class, (m, a) -> null));
            f.listener.onItemDrop(drop); check(drop.isCancelled(), "revoked item drop denied");
            var pickup = new EntityPickupItemEvent(f.player, proxy(Item.class, (m, a) -> null), 0);
            f.listener.onItemPickup(pickup); check(pickup.isCancelled(), "revoked item pickup denied");
            var swap = new PlayerSwapHandItemsEvent(f.player, new TestItem(), new TestItem());
            f.listener.onSwapHands(swap); check(swap.isCancelled(), "revoked offhand swap denied");
            var interact = new PlayerInteractEvent(f.player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                    new TestItem(), f.block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
            f.listener.onInteract(interact); check(interact.isCancelled(), "revoked item use denied");
            var entity = new PlayerInteractEntityEvent(f.player, f.player);
            f.listener.onInteractEntity(entity); check(entity.isCancelled(), "revoked entity item use denied");
            var atEntity = new PlayerInteractAtEntityEvent(f.player, f.player, new org.bukkit.util.Vector());
            f.listener.onInteractAtEntity(atEntity); check(atEntity.isCancelled(), "separate precise entity event denied");
            var armorStand = new PlayerArmorStandManipulateEvent(f.player,
                    proxy(org.bukkit.entity.ArmorStand.class, (m, a) -> null), new TestItem(), new TestItem(), EquipmentSlot.HEAD, EquipmentSlot.HAND);
            f.listener.onArmorStandManipulate(armorStand); check(armorStand.isCancelled(), "separate armorstand inventory transfer denied");
            check(f.queued.size() == 1 && f.state.isBuildMode(f.id), "cleanup deduplicated and deferred out of transaction");
            int before = f.transitions; f.drain();
            check(!f.state.isBuildMode(f.id) && f.mode == GameMode.ADVENTURE && f.clears > 0 && f.items,
                    "next tick restores normal owned mode, inventory and lobby items");
            check(f.transitions == before + 2, "revocation uses existing before/after state notifications");
            f.permission = true; f.state.enterBuildMode(f.player); f.state.synchronize(f.player);
            check(f.mode == GameMode.CREATIVE && f.state.hasBuildAuthority(f.player), "legitimate BUILD synchronization preserved");
            f.permission = false; f.state.synchronize(f.player);
            check(f.mode == GameMode.ADVENTURE && !f.state.isBuildMode(f.id), "synchronize revalidates current permission");
            f.permission = true; f.state.enterBuildMode(f.player); f.permission = false; f.state.refreshNormalGameModes();
            check(!f.state.isBuildMode(f.id) && f.items, "reload reconciliation releases stale owned BUILD");
            f.permission = true; f.state.enterBuildMode(f.player);
            f.permissionContext = false; // Same node granted, effective context no longer applies.
            f.listener.onMove(new PlayerMoveEvent(f.player, new org.bukkit.Location(f.lobbyWorld, 0, 65, 0),
                    new org.bukkit.Location(f.lobbyWorld, 16, 65, 0)));
            f.drain(); check(!f.state.isBuildMode(f.id), "within-world context loss reconciled without reconnect");
            f.permissionContext = true; f.state.enterBuildMode(f.player); f.permission = false;
            f.listener.onBlockBreak(f.breakEvent());
            f.permission = true; f.drain();
            check(f.state.hasBuildAuthority(f.player), "regrant before cleanup preserves newly valid BUILD");
            f.world = f.foreignWorld;
            f.listener.onPlayerChangedWorld(new PlayerChangedWorldEvent(f.player, f.lobbyWorld));
            check(!f.state.isBuildMode(f.id) && f.mode == GameMode.ADVENTURE && !f.items, "world leave releases owned state");
            f.world = f.lobbyWorld;
            f.listener.onPlayerChangedWorld(new PlayerChangedWorldEvent(f.player, f.foreignWorld));
            check(f.items && !f.state.isBuildMode(f.id), "world re-entry is normal, no inherited grant");
            f.state.enterBuildMode(f.player); f.permission = false; f.listener.onBlockBreak(f.breakEvent());
            f.listener.onPlayerQuit(new PlayerQuitEvent(f.player, (net.kyori.adventure.text.Component) null));
            f.state.synchronizeJoin(f.player); f.mode = GameMode.SPECTATOR; f.flight = true; f.drain();
            check(!f.state.isBuildMode(f.id) && f.mode == GameMode.SPECTATOR && f.flight,
                    "quit/reconnect invalidates queued cleanup and preserves independently assigned mode/flight");
            f.state.reconcileBuildAuthority(f.player);
            check(f.mode == GameMode.SPECTATOR && f.flight, "permission absence does not own a staff-assigned mode or flight");
            f.permission = true; f.state.enterBuildMode(f.player); f.mode = GameMode.SURVIVAL; f.flight = true;
            f.permission = false; f.state.reconcileBuildAuthority(f.player);
            check(f.mode == GameMode.SURVIVAL && f.flight && !f.state.isBuildMode(f.id), "externally replaced owned Creative mode retained");
            f.permission = true; f.state.enterBuildMode(f.player); f.permission = false; f.listener.onBlockBreak(f.breakEvent());
            f.listener.disable(); f.state.cleanup(); f.mode = GameMode.SPECTATOR; f.flight = true; f.drain();
            check(f.mode == GameMode.SPECTATOR && f.flight && !f.state.isBuildMode(f.id), "shutdown fences deferred cleanup");
        }
        System.out.println("BuildPermissionRevocationHarness passed " + checks + " checks.");
    }

    private static final class Fixture implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final List<Runnable> queued = new ArrayList<>();
        final Path dir;
        final World lobbyWorld = proxy(World.class, (m, a) -> m.equals("getName") ? "world" : null);
        final World foreignWorld = proxy(World.class, (m, a) -> m.equals("getName") ? "elsewhere" : null);
        World world = lobbyWorld;
        boolean permission, permissionContext = true, flight, items;
        GameMode mode = GameMode.ADVENTURE;
        int clears, transitions;
        final PlayerInventory inventory = proxy(PlayerInventory.class, (m, a) -> {
            if (m.equals("clear")) clears++; return null;
        });
        final Player player = proxy(Player.class, (m, a) -> switch (m) {
            case "getUniqueId" -> id; case "isOnline" -> true; case "getWorld" -> world;
            case "getInventory" -> inventory; case "getGameMode" -> mode;
            case "getAllowFlight" -> flight;
            case "setGameMode" -> { mode = (GameMode) a[0]; yield null; }
            case "hasPermission" -> permission && permissionContext;
            default -> null;
        });
        final InventoryView view = proxy(InventoryView.class, (m, a) -> switch (m) {
            case "getPlayer" -> player; case "getTopInventory", "getBottomInventory" -> inventory; default -> null;
        });
        final Block block = proxy(Block.class, (m, a) -> m.equals("getWorld") ? world : null);
        final LobbyPlayerStateService state;
        final LobbyListener listener;
        Fixture() throws Exception {
            Files.createDirectories(Path.of("target/harness-temp"));
            dir = Files.createTempDirectory(Path.of("target/harness-temp"), "build-revocation-");
            Logger log = Logger.getAnonymousLogger(); log.setUseParentHandlers(false);
            Path file = dir.resolve("lobby.yml");
            Files.writeString(file, "spawn:\n  world: world\n  x: 0\n  y: 64\n  z: 0\n  yaw: 0\n  pitch: 0\n");
            LobbyConfig config = new LobbyConfig(file, log); config.initialize();
            LobbyService lobby = shell(LobbyService.class);
            var spawn = LobbyService.class.getDeclaredField("spawn"); spawn.setAccessible(true); spawn.set(lobby, config.getSpawn().orElseThrow());
            var repo = new FilePlayerRepository(dir.resolve("players"), log); repo.initialize();
            PlayerService players = new PlayerService(repo, log); players.loadPlayer(id, "Builder");
            var ctor = LobbyPlayerStateService.class.getDeclaredConstructor(BooleanSupplier.class, Predicate.class,
                    Supplier.class, Consumer.class, Consumer.class, Supplier.class);
            ctor.setAccessible(true);
            state = ctor.newInstance((BooleanSupplier) () -> true, (Predicate<Player>) p -> world == lobbyWorld,
                    (Supplier<GameMode>) () -> GameMode.ADVENTURE, (Consumer<Player>) p -> items = true,
                    (Consumer<Player>) p -> items = false, (Supplier<Collection<Player>>) () -> List.of(player));
            state.addStateListener(new LobbyPlayerStateListener() {
                public void beforeStateChange(Player p) { transitions++; }
                public void afterStateChange(UUID id) { transitions++; }
            });
            listener = new LobbyListener(config, lobby, players, state, queued::add, () -> true);
        }
        BlockBreakEvent breakEvent() { return new BlockBreakEvent(block, player); }
        InventoryCreativeEvent creative() { return new InventoryCreativeEvent(view, InventoryType.SlotType.CONTAINER, 0, new TestItem()); }
        void drain() { List<Runnable> copy = List.copyOf(queued); queued.clear(); copy.forEach(Runnable::run); }
        public void close() throws Exception {
            if (!dir.toAbsolutePath().normalize().startsWith(Path.of("target/harness-temp").toAbsolutePath().normalize())) throw new AssertionError("fixture path");
            try (var paths = Files.walk(dir)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
    }
    private static final class TestItem extends ItemStack { private TestItem() { super(); } }
    @SuppressWarnings("unchecked") private static <T> T shell(Class<T> type) throws Exception {
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return (T) ((sun.misc.Unsafe) f.get(null)).allocateInstance(type);
    }
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return switch (m.getName()) {
                case "hashCode" -> System.identityHashCode(p); case "equals" -> p == a[0]; default -> type.getSimpleName();
            };
            Object result = call.apply(m.getName(), a == null ? new Object[0] : a);
            if (result != null || !m.getReturnType().isPrimitive()) return result;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == double.class) return 0D;
            if (m.getReturnType() == float.class) return 0F;
            if (m.getReturnType() == long.class) return 0L;
            return 0;
        }));
    }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}

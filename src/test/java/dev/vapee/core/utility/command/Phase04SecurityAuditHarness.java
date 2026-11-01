package dev.vapee.core.utility.command;

import dev.vapee.core.command.StaffTargetTestFixture;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.identity.PlayerProfileService;
import dev.vapee.core.identity.command.ProfileCommand;
import dev.vapee.core.lobby.LobbyListener;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.utility.InvseeService;
import dev.vapee.core.utility.OnlinePlayerResolver;
import dev.vapee.core.utility.UtilityService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.metadata.MetadataValue;

/**
 * AUDIT-01/02 remediation regressions; AUDIT-03 remains an unchanged disclosure characterization.
 * Only existing process-local fixtures and disposable files are used; no server is contacted.
 */
public final class Phase04SecurityAuditHarness {
    private static int checks;
    private static final Logger LOG = Logger.getAnonymousLogger();
    public static void main(String[] args) throws Exception {
        LOG.setUseParentHandlers(false);
        buildRevocation(); retainedEnderChest(); hiddenPresence();
        System.out.println("Phase04SecurityAuditHarness passed " + checks + " checks.");
    }
    private static void buildRevocation() throws Exception {
        // Reuse the established real LobbyPlayerStateService fixture, not a copied authorization rule.
        var probe = construct(Class.forName("dev.vapee.core.lobby.player.LobbyHarness$PlayerProbe"), new Class<?>[]{String.class}, "AuditBuilder");
        Player player = (Player) field(probe, "player");
        var serviceMethod = Class.forName("dev.vapee.core.lobby.player.LobbyHarness").getDeclaredMethod("service", AtomicReference.class, List.class);
        serviceMethod.setAccessible(true);
        Object fixture = serviceMethod.invoke(null, new AtomicReference<>(GameMode.ADVENTURE), List.of(probe));
        var state = (LobbyPlayerStateService) field(fixture, "service");
        var command = new BuildCommand(LOG, p -> true, new BuildCommand.BuildStateAccess() {
            public boolean isBuildMode(UUID id) { return state.isBuildMode(id); }
            public void enterBuildMode(Player p) { state.enterBuildMode(p); }
            public void exitBuildMode(Player p) { state.exitBuildMode(p); }
        }, id -> false, p -> { }, (sender, message) -> { });
        Path dir = Files.createTempDirectory(Path.of("target/harness-temp"), "security-audit-build-");
        try {
            Path file = dir.resolve("lobby.yml");
            Files.writeString(file, "spawn:\n  world: world\n  x: 0\n  y: 64\n  z: 0\n  yaw: 0\n  pitch: 0\n");
            var config = new LobbyConfig(file, LOG); config.initialize();
            // Unused plugin/player-service constructor dependencies are inert, as in existing event fixtures.
            var lobby = shell(LobbyService.class); set(lobby, "spawn", config.getSpawn().orElseThrow());
            List<Runnable> queued = new ArrayList<>();
            var listenerCtor = LobbyListener.class.getDeclaredConstructor(LobbyConfig.class, LobbyService.class,
                    PlayerService.class, LobbyPlayerStateService.class, Consumer.class, BooleanSupplier.class);
            listenerCtor.setAccessible(true);
            var listener = listenerCtor.newInstance(config, lobby, shell(PlayerService.class), state,
                    (Consumer<Runnable>) queued::add, (BooleanSupplier) () -> true);
            Block block = proxy(Block.class, (method, values) -> {
                if (method.getName().startsWith("set")) throw new AssertionError("Diagnostic attempted a world write");
                return method.getName().equals("getWorld") ? player.getWorld() : null;
            });
            check(config.isBlockBreakProtectionEnabled(), "protection enabled in disposable configuration");
            command.onCommand(player, null, "build", new String[0]);
            var denied = new BlockBreakEvent(block, player); listener.onBlockBreak(denied);
            check(!state.isBuildMode(player.getUniqueId()) && denied.isCancelled(), "never-authorized player cannot enter BUILD or break lobby block");
            set(probe, "hasBuildPermission", true); command.onCommand(player, null, "build", new String[0]);
            check(state.isBuildMode(player.getUniqueId()) && player.getGameMode() == GameMode.CREATIVE, "authorized command creates real BUILD state");
            set(probe, "hasBuildPermission", false);
            check(!player.hasPermission(BuildCommand.PERMISSION) && !player.isOp(), "effective permission and OP are false after revocation");
            var retained = new BlockBreakEvent(block, player); listener.onBlockBreak(retained);
            check(retained.isCancelled(), "AUDIT-01 fixed: real protection handler denies stale BUILD actor immediately");
            System.out.println("AUDIT-01 actual: permission=" + player.hasPermission(BuildCommand.PERMISSION)
                    + ", BUILD=" + state.isBuildMode(player.getUniqueId()) + ", mode=" + player.getGameMode()
                    + ", blockBreakCancelled=" + retained.isCancelled());
            command.onCommand(player, null, "build", new String[0]);
            check(queued.size() == 1 && retained.isCancelled(), "denied /build cannot regain authority while owned cleanup is pending");
            state.synchronize(player);
            queued.forEach(Runnable::run);
            check(!state.isBuildMode(player.getUniqueId()) && player.getGameMode() == GameMode.ADVENTURE, "synchronization releases revoked BUILD state through normal lifecycle");
            state.handleQuit(player.getUniqueId());
            var afterQuit = new BlockBreakEvent(block, player); listener.onBlockBreak(afterQuit);
            check(afterQuit.isCancelled(), "quit cleanup removes the retained protection bypass");
        } finally { removeFixture(dir); }
    }
    @SuppressWarnings("unchecked")
    private static void retainedEnderChest() throws Exception {
        Object fixture = construct(Class.forName("dev.vapee.core.utility.command.UtilityCommandHarness$Fixture"), new Class<?>[0]);
        var create = fixture.getClass().getDeclaredMethod("player", String.class, GameMode.class, String[].class); create.setAccessible(true);
        Object viewerProbe = create.invoke(fixture, "AuditViewer", GameMode.SURVIVAL, new String[]{EnderChestCommand.PERMISSION});
        Object ownerProbe = create.invoke(fixture, "AuditOwner", GameMode.SURVIVAL, new String[0]);
        Player viewer = (Player) field(viewerProbe, "player"), owner = (Player) field(ownerProbe, "player");
        var permissions = (Set<String>) field(viewerProbe, "permissions");
        List<Runnable> queued = new ArrayList<>();
        var command = new EnderChestCommand((UtilityService) field(fixture, "service"),
                name -> name.equalsIgnoreCase(owner.getName()) ? owner : viewer,
                () -> List.of(viewer, owner), (sender, message) -> { }, new StaffTargetTestFixture(LOG).guard, queued::add);
        command.onCommand(viewer, null, "enderchest", new String[]{owner.getName()});
        check(field(viewerProbe, "openedInventory") == null, "base permission cannot open another player's chest");
        permissions.add(EnderChestCommand.OTHERS_PERMISSION);
        command.onCommand(viewer, null, "enderchest", new String[]{owner.getName()});
        Inventory chest = (Inventory) field(viewerProbe, "openedInventory");
        check(chest == owner.getEnderChest(), "authorized command opens target's actual shared inventory, not a snapshot");
        permissions.clear(); int writes = (int) field(viewerProbe, "writes");
        command.onCommand(viewer, null, "enderchest", new String[]{owner.getName()});
        check((int) field(viewerProbe, "writes") == writes, "revoked actor cannot reopen through command boundary");
        check(!viewer.hasPermission(EnderChestCommand.OTHERS_PERMISSION) && !viewer.isOp() && field(viewerProbe, "openedInventory") == chest,
                "revoked non-OP actor still has a tracked view requiring interaction-time enforcement");
        InventoryView view = viewer.getOpenInventory();
        Constructor<?> ctor = Arrays.stream(InvseeService.class.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 3).findFirst().orElseThrow();
        ctor.setAccessible(true);
        Object factory = Proxy.newProxyInstance(ctor.getParameterTypes()[2].getClassLoader(), new Class<?>[]{ctor.getParameterTypes()[2]},
                (p, m, a) -> { throw new AssertionError("Should not render an Invsee snapshot"); });
        var invsee = (InvseeService) ctor.newInstance((BooleanSupplier) () -> true, (Function<UUID, Player>) id -> viewer, factory);
        var click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        invsee.onInventoryClick(click);
        command.onInventoryClick(click);
        queued.forEach(Runnable::run);
        check(click.isCancelled() && field(viewerProbe, "openedInventory") == null,
                "AUDIT-02 fixed: foreign view interaction is denied and released next tick");
        System.out.println("AUDIT-02 actual: othersPermission=" + viewer.hasPermission(EnderChestCommand.OTHERS_PERMISSION)
                + ", retainedTargetInventory=" + (field(viewerProbe, "openedInventory") == owner.getEnderChest())
                + ", foreignViewCancelled=" + click.isCancelled());
        check(chest.getHolder() == null, "live fixture chest is not a plugin capability holder");
        // No inventory contents are changed: actual client transfer remains a disposable native acceptance scenario.
    }
    private static void hiddenPresence() throws Exception {
        UUID hiddenId = UUID.randomUUID(), viewerId = UUID.randomUUID();
        List<Component> output = new ArrayList<>();
        MetadataValue vanished = proxy(MetadataValue.class, (method, values) -> method.getName().equals("asBoolean") ? true : null);
        Player hidden = proxy(Player.class, (method, values) -> switch (method.getName()) {
            case "getName" -> "HiddenStaff"; case "getUniqueId" -> hiddenId; case "isOnline" -> true;
            case "getMetadata" -> List.of(vanished); case "getStatistic" -> 123;
            default -> null;
        });
        Player viewer = proxy(Player.class, (method, values) -> switch (method.getName()) {
            case "getName" -> "OrdinaryViewer"; case "getUniqueId" -> viewerId; case "isOnline" -> true;
            case "hasPermission" -> "vapeecore.profile.view".equals(values[0]); case "canSee" -> false;
            case "sendMessage" -> { for (Object v : values) if (v instanceof Component c) output.add(c); yield null; }
            default -> null;
        });
        Path dir = Files.createTempDirectory(Path.of("target/harness-temp"), "security-audit-profile-");
        try {
            var repository = new FilePlayerRepository(dir, LOG); repository.initialize();
            var players = new PlayerService(repository, LOG); players.loadPlayer(hiddenId, "HiddenStaff");
            var constructor = PlayerProfileService.class.getDeclaredConstructor(PlayerIdentityService.class, EconomyService.class, Function.class, Function.class);
            constructor.setAccessible(true);
            var profiles = constructor.newInstance(new PlayerIdentityService(players), new EconomyService(players),
                    (Function<UUID, Player>) id -> hiddenId.equals(id) ? hidden : null,
                    (Function<UUID, Optional<?>>) id -> Optional.empty());
            var messages = new MessageService(() -> "", LocalizationService.bundled(LOG));
            var command = new ProfileCommand(profiles, messages, () -> List.of(hidden), LOG, ZoneId.of("UTC"));
            check(!viewer.canSee(hidden) && hidden.getMetadata("vanished").getFirst().asBoolean() && !viewer.isOp(), "explicit hidden/vanished target and ordinary non-OP viewer");
            check(command.onTabComplete(viewer, null, "profile", new String[]{""}).equals(List.of("HiddenStaff")), "AUDIT-03: public profile completion discloses hidden online name");
            check(new OnlinePlayerResolver(() -> List.of(hidden)).suggest("", viewerId).equals(List.of("HiddenStaff")), "shared online-name resolver also has no viewer privacy context");
            command.onCommand(viewer, null, "profile", new String[]{hiddenId.toString()});
            check(output.stream().map(PlainTextComponentSerializer.plainText()::serialize).anyMatch(s -> s.contains("Status: Online")),
                    "AUDIT-03: explicit known UUID discloses online state despite hidden target");
            System.out.println("AUDIT-03 actual: viewerCanSee=" + viewer.canSee(hidden) + ", vanished="
                    + hidden.getMetadata("vanished").getFirst().asBoolean() + ", completion="
                    + command.onTabComplete(viewer, null, "profile", new String[]{""}) + ", disclosedOnline="
                    + output.stream().map(PlainTextComponentSerializer.plainText()::serialize).anyMatch(s -> s.contains("Status: Online")));
            check(!viewer.hasPermission("vapeecore.economy.admin") && !viewer.hasPermission("vapeecore.admin"), "profile exposure requires no administrative permission");
        } finally { removeFixture(dir); }
    }
    private static Object construct(Class<?> type, Class<?>[] signature, Object... args) throws Exception {
        var ctor = type.getDeclaredConstructor(signature); ctor.setAccessible(true); return ctor.newInstance(args);
    }
    private static Object field(Object instance, String name) throws Exception {
        var f = instance.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(instance);
    }
    private static void set(Object instance, String name, Object value) throws Exception {
        var f = instance.getClass().getDeclaredField(name); f.setAccessible(true); f.set(instance, value);
    }
    private static <T> T shell(Class<T> type) throws Exception {
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }
    @FunctionalInterface private interface Call { Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(p); case "equals" -> p == args[0]; default -> type.getSimpleName();
            };
            Object result = call.invoke(method, args == null ? new Object[0] : args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == float.class) return 0F;
            if (method.getReturnType() == double.class) return 0D;
            return 0;
        }));
    }
    private static void removeFixture(Path dir) throws Exception {
        // Only this method's freshly allocated directory inside the established harness temporary root.
        if (!dir.toAbsolutePath().normalize().startsWith(Path.of("target/harness-temp").toAbsolutePath().normalize()))
            throw new AssertionError("Unsafe fixture cleanup path");
        try (var paths = Files.walk(dir)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}

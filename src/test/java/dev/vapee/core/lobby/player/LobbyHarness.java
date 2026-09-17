package dev.vapee.core.lobby.player;

import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.lobby.item.LobbyItemType;
import dev.vapee.core.lobby.message.LobbyMessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LobbyHarness {

    private static int checks;

    private LobbyHarness() {
    }

    public static void main(String[] args) throws Exception {
        testGameModeConfiguration();
        testLobbyMessages();
        testNormalAndBuildInventoryLifecycle();
        testQuitReconnectAndWorldLeave();
        testRespawnReloadAndCleanupSemantics();
        testProtectionStateAndMainThreadGuard();
        testLobbyItemContract();
        System.out.println("LobbyHarness passed " + checks + " checks.");
    }

    private static void testGameModeConfiguration() throws IOException {
        for (GameMode gameMode : List.of(GameMode.ADVENTURE, GameMode.SURVIVAL, GameMode.CREATIVE)) {
            ConfigFixture fixture = config("player:\n  gamemode: " + gameMode.name().toLowerCase() + "\n");
            check(fixture.config().getPlayerGameMode() == gameMode,
                    gameMode + " is read case-insensitively");
        }

        String invalidContent = "player:\n  gamemode: BANANA\n";
        ConfigFixture invalid = config(invalidContent);
        check(invalid.config().getPlayerGameMode() == GameMode.ADVENTURE,
                "invalid game mode falls back to ADVENTURE");
        check(invalid.logger().warningCount.get() == 1, "invalid game mode emits one warning");
        check(Files.readString(invalid.file()).equals(invalidContent),
                "invalid game mode does not rewrite the live file");
    }

    private static void testLobbyMessages() throws IOException {
        ConfigFixture fixture = config("""
                player:
                  gamemode: ADVENTURE
                messages:
                  join:
                    enabled: true
                    format: "Welcome <name>"
                  quit:
                    enabled: true
                    format: "Goodbye <name>"
                """);
        PlayerProbe player = new PlayerProbe("Ada");
        LobbyMessageService service = new LobbyMessageService(
                fixture.config(),
                (template, resolver) -> MiniMessage.miniMessage().deserialize(template, resolver),
                fixture.logger().logger
        );
        check(plain(service.renderJoinMessage(player.player)).equals("Welcome Ada"),
                "enabled custom join message is rendered");
        check(plain(service.renderQuitMessage(player.player)).equals("Goodbye Ada"),
                "enabled custom quit message is rendered");

        Files.writeString(fixture.file(), """
                player:
                  gamemode: ADVENTURE
                messages:
                  join:
                    enabled: false
                    format: "Hidden <name>"
                  quit:
                    enabled: true
                    format: "Bye <name>"
                """);
        fixture.config().applyState(fixture.config().prepareReloadState());
        check(service.renderJoinMessage(player.player) == null, "disabled join message returns null");
        check(plain(service.renderQuitMessage(player.player)).equals("Bye Ada"),
                "quit message remains configurable after reload");

        LobbyMessageService failingRenderer = new LobbyMessageService(
                fixture.config(),
                (template, resolver) -> {
                    throw new IllegalArgumentException("synthetic renderer failure");
                },
                fixture.logger().logger
        );
        check(plain(failingRenderer.renderQuitMessage(player.player)).equals("[-] Ada"),
                "runtime render failure uses the safe quit fallback");
        check(fixture.logger().warningCount.get() >= 1, "runtime render failure is logged");
    }

    private static void testNormalAndBuildInventoryLifecycle() {
        AtomicReference<GameMode> configuredMode = new AtomicReference<>(GameMode.SURVIVAL);
        PlayerProbe player = new PlayerProbe("Builder");
        player.gameMode = GameMode.CREATIVE;
        player.fillForeignInventory();
        ServiceFixture fixture = service(configuredMode, List.of(player));

        fixture.service.synchronizeJoin(player.player);
        check(fixture.service.getMode(player.id) == LobbyPlayerMode.NORMAL, "join starts in NORMAL");
        check(player.gameMode == GameMode.SURVIVAL, "join replaces a stale CREATIVE game mode");
        check(player.inventoryIsEmpty(), "NORMAL removes storage, armor, offhand and cursor items");
        check(player.closeCount == 1 && player.heldSlot == 0, "NORMAL closes UI and selects slot zero");
        check(player.lobbyItems, "NORMAL applies lobby items");

        fixture.service.enterBuildMode(player.player);
        check(fixture.service.getMode(player.id) == LobbyPlayerMode.BUILD, "enter sets BUILD mode");
        check(player.gameMode == GameMode.CREATIVE, "BUILD always uses CREATIVE");
        check(!player.lobbyItems, "BUILD removes lobby items");
        check(fixture.service.isBuildMode(player.id), "BUILD exposes the protection bypass state");

        player.fillForeignInventory();
        fixture.service.exitBuildMode(player.player);
        check(fixture.service.getMode(player.id) == LobbyPlayerMode.NORMAL, "exit returns to NORMAL");
        check(player.gameMode == GameMode.SURVIVAL, "exit restores the configured normal game mode");
        check(player.inventoryIsEmpty(), "exit removes build storage, armor, offhand and cursor items");
        check(player.lobbyItems, "exit recreates lobby items");
        check(!fixture.service.isBuildMode(player.id), "exit disables the protection bypass state");
    }

    private static void testQuitReconnectAndWorldLeave() {
        AtomicReference<GameMode> configuredMode = new AtomicReference<>(GameMode.ADVENTURE);
        PlayerProbe player = new PlayerProbe("Traveler");
        ServiceFixture fixture = service(configuredMode, List.of(player));

        fixture.service.enterBuildMode(player.player);
        fixture.service.handleQuit(player.id);
        check(fixture.service.getMode(player.id) == LobbyPlayerMode.NORMAL, "quit removes runtime BUILD state");
        player.gameMode = GameMode.CREATIVE;
        fixture.service.synchronizeJoin(player.player);
        check(player.gameMode == GameMode.ADVENTURE && player.lobbyItems,
                "reconnect normalizes game mode and lobby items");

        fixture.service.enterBuildMode(player.player);
        player.fillForeignInventory();
        player.inLobby = false;
        fixture.service.leaveLobby(player.player);
        check(fixture.service.getMode(player.id) == LobbyPlayerMode.NORMAL, "world leave removes BUILD state");
        check(player.gameMode == GameMode.ADVENTURE, "world leave prevents a CREATIVE leak");
        check(player.inventoryIsEmpty(), "world leave removes the temporary build inventory");
        check(!player.lobbyItems, "world leave does not apply lobby items in a foreign world");
    }

    private static void testRespawnReloadAndCleanupSemantics() {
        AtomicReference<GameMode> configuredMode = new AtomicReference<>(GameMode.ADVENTURE);
        PlayerProbe normal = new PlayerProbe("Normal");
        PlayerProbe builder = new PlayerProbe("Build");
        ServiceFixture fixture = service(configuredMode, List.of(normal, builder));

        fixture.service.synchronize(normal.player);
        fixture.service.enterBuildMode(builder.player);
        builder.storagePresent = true;
        fixture.service.synchronize(builder.player);
        check(fixture.service.isBuildMode(builder.id) && builder.gameMode == GameMode.CREATIVE,
                "respawn-style synchronize preserves legitimate BUILD state");
        check(builder.storagePresent, "BUILD synchronization preserves the temporary build inventory");

        configuredMode.set(GameMode.SPECTATOR);
        fixture.service.refreshNormalGameModes();
        check(normal.gameMode == GameMode.SPECTATOR, "reload updates NORMAL lobby players");
        check(builder.gameMode == GameMode.CREATIVE, "reload keeps BUILD players in CREATIVE");

        builder.fillForeignInventory();
        fixture.service.cleanup();
        check(fixture.service.getMode(builder.id) == LobbyPlayerMode.NORMAL, "cleanup clears BUILD state");
        check(builder.gameMode == GameMode.SPECTATOR && builder.inventoryIsEmpty(),
                "cleanup removes build inventory and restores the configured mode");
        check(!builder.lobbyItems, "shutdown cleanup does not reapply the lobby hotbar");
    }

    private static void testProtectionStateAndMainThreadGuard() {
        PlayerProbe player = new PlayerProbe("Operator");
        player.hasBuildPermission = true;
        ServiceFixture fixture = service(new AtomicReference<>(GameMode.ADVENTURE), List.of(player));
        check(player.player.hasPermission("vapeecore.utility.build"), "test player owns build permission");
        check(!fixture.service.isBuildMode(player.id), "permission alone does not enable protection bypass");
        fixture.service.enterBuildMode(player.player);
        check(fixture.service.isBuildMode(player.id), "explicit BUILD state enables protection bypass");

        LobbyPlayerStateService offThread = new LobbyPlayerStateService(
                () -> false,
                ignored -> true,
                () -> GameMode.ADVENTURE,
                ignored -> {
                },
                ignored -> {
                },
                List::of
        );
        expectThrows(IllegalStateException.class, () -> offThread.synchronize(player.player),
                "mutating player state is rejected off the primary thread");
    }

    private static void testLobbyItemContract() {
        check(LobbyItemService.NAVIGATOR_SLOT == 0, "navigator remains in slot 0");
        check(LobbyItemService.VISIBILITY_SLOT == 4, "visibility remains in slot 4");
        check(LobbyItemService.SETTINGS_SLOT == 8, "settings remains in slot 8");
        check(LobbyItemType.NAVIGATOR.getPersistentId().equals("navigator"),
                "navigator persistent id is unchanged");
        check(LobbyItemType.VISIBILITY.getPersistentId().equals("visibility"),
                "visibility persistent id is unchanged");
        check(LobbyItemType.SETTINGS.getPersistentId().equals("settings"),
                "settings persistent id is unchanged");
    }

    private static ServiceFixture service(
            AtomicReference<GameMode> configuredMode,
            List<PlayerProbe> players
    ) {
        List<Player> onlinePlayers = players.stream().map(probe -> probe.player).toList();
        Predicate<Player> lobbyCheck = candidate -> players.stream()
                .filter(probe -> probe.player == candidate)
                .findFirst()
                .map(probe -> probe.inLobby)
                .orElse(false);
        LobbyPlayerStateService service = new LobbyPlayerStateService(
                () -> true,
                lobbyCheck,
                configuredMode::get,
                candidate -> probe(players, candidate).lobbyItems = true,
                candidate -> probe(players, candidate).lobbyItems = false,
                () -> onlinePlayers
        );
        return new ServiceFixture(service);
    }

    private static PlayerProbe probe(List<PlayerProbe> players, Player player) {
        return players.stream()
                .filter(candidate -> candidate.player == player)
                .findFirst()
                .orElseThrow();
    }

    private static ConfigFixture config(String content) throws IOException {
        Path directory = Files.createTempDirectory("vapeecore-lobby-harness-");
        Path file = directory.resolve("lobby.yml");
        Files.writeString(file, content);
        RecordingLogger logger = new RecordingLogger();
        LobbyConfig config = new LobbyConfig(file, logger.logger);
        config.initialize();
        return new ConfigFixture(config, file, logger);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void expectThrows(
            Class<? extends Throwable> expected,
            ThrowingRunnable runnable,
            String message
    ) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            check(expected.isInstance(throwable), message);
            return;
        }
        throw new AssertionError(message + " (no exception thrown)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    default -> null;
                };
            }
            return handler.invoke(method, args == null ? new Object[0] : args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private record ServiceFixture(LobbyPlayerStateService service) {
    }

    private record ConfigFixture(LobbyConfig config, Path file, RecordingLogger logger) {
    }

    private static final class RecordingLogger {
        private final Logger logger = Logger.getAnonymousLogger();
        private final AtomicInteger warningCount = new AtomicInteger();

        private RecordingLogger() {
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        warningCount.incrementAndGet();
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }
    }

    private static final class PlayerProbe {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final World world;
        private final PlayerInventory inventory;
        private final Player player;
        private GameMode gameMode = GameMode.ADVENTURE;
        private boolean inLobby = true;
        private boolean online = true;
        private boolean hasBuildPermission;
        private boolean lobbyItems;
        private boolean storagePresent;
        private boolean armorPresent;
        private boolean offhandPresent;
        private boolean cursorPresent;
        private int closeCount;
        private int heldSlot = -1;

        private PlayerProbe(String name) {
            this.name = name;
            world = proxy(World.class, (method, arguments) -> method.getName().equals("getName")
                    ? (inLobby ? "world" : "foreign")
                    : defaultValue(method.getReturnType()));
            inventory = proxy(PlayerInventory.class, (method, arguments) -> switch (method.getName()) {
                case "clear" -> {
                    storagePresent = false;
                    yield null;
                }
                case "setArmorContents" -> {
                    ItemStack[] contents = (ItemStack[]) arguments[0];
                    armorPresent = contents != null && java.util.Arrays.stream(contents).anyMatch(item -> item != null);
                    yield null;
                }
                case "setItemInOffHand" -> {
                    offhandPresent = arguments[0] != null;
                    yield null;
                }
                case "setHeldItemSlot" -> {
                    heldSlot = (int) arguments[0];
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
            player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "getWorld" -> world;
                case "getInventory" -> inventory;
                case "isOnline" -> online;
                case "getGameMode" -> gameMode;
                case "setGameMode" -> {
                    gameMode = (GameMode) arguments[0];
                    yield null;
                }
                case "closeInventory" -> {
                    closeCount++;
                    yield null;
                }
                case "setItemOnCursor" -> {
                    cursorPresent = arguments[0] != null;
                    yield null;
                }
                case "getItemOnCursor" -> null;
                case "hasPermission" -> hasBuildPermission;
                default -> defaultValue(method.getReturnType());
            });
        }

        private void fillForeignInventory() {
            storagePresent = true;
            armorPresent = true;
            offhandPresent = true;
            cursorPresent = true;
        }

        private boolean inventoryIsEmpty() {
            return !storagePresent && !armorPresent && !offhandPresent && !cursorPresent;
        }
    }
}

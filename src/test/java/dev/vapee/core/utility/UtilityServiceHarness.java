package dev.vapee.core.utility;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UtilityServiceHarness {

    private static int checks;

    private UtilityServiceHarness() {
    }

    public static void main(String[] args) {
        testSpeedMappingAndSelection();
        testFlightAndGameModeOwnership();
        testNormalizationAndCleanup();
        testHealFeedAndTeleport();
        testMainThreadGuard();
        System.out.println("UtilityServiceHarness passed " + checks + " checks.");
    }

    private static void testSpeedMappingAndSelection() {
        check(close(UtilityService.walkSpeed(1), 0.2F), "walk level 1 is the vanilla default");
        check(close(UtilityService.walkSpeed(10), 1.0F), "walk level 10 reaches the Bukkit maximum");
        check(close(UtilityService.flySpeed(1), 0.1F), "flight level 1 is the vanilla default");
        check(close(UtilityService.flySpeed(10), 1.0F), "flight level 10 reaches the Bukkit maximum");

        MutablePlayer survival = new MutablePlayer("Survival", GameMode.SURVIVAL);
        UtilityService service = service(survival);
        UtilitySpeedResult walking = service.setSpeed(survival.player, 5);
        check(walking.type() == UtilitySpeedType.WALK && close(survival.walkSpeed, walking.bukkitValue()),
                "survival changes the walk channel");
        survival.flying = true;
        UtilitySpeedResult flying = service.setSpeed(survival.player, 5);
        check(flying.type() == UtilitySpeedType.FLY && close(survival.flySpeed, flying.bukkitValue()),
                "an actively flying player changes the flight channel");

        for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
            MutablePlayer player = new MutablePlayer(mode.name(), mode);
            check(UtilityService.speedType(player.player) == UtilitySpeedType.FLY,
                    mode + " selects flight speed");
        }
        MutablePlayer adventure = new MutablePlayer("Adventure", GameMode.ADVENTURE);
        check(UtilityService.speedType(adventure.player) == UtilitySpeedType.WALK,
                "adventure selects walk speed when grounded");
        expectIllegalArgument(() -> service.setSpeed(survival.player, 0), "speed level 0 is rejected by service");
        expectIllegalArgument(() -> service.setSpeed(survival.player, 11), "speed level 11 is rejected by service");
    }

    private static void testFlightAndGameModeOwnership() {
        MutablePlayer survival = new MutablePlayer("Flyer", GameMode.SURVIVAL);
        UtilityService service = service(survival);
        check(service.toggleFlight(survival.player), "first flight toggle enables managed flight");
        check(survival.allowFlight && service.hasManagedFlight(survival.id),
                "enabled flight is allowed and tracked by UUID");
        survival.flying = true;
        check(!service.toggleFlight(survival.player), "second flight toggle disables managed flight");
        check(!survival.allowFlight && !survival.flying && !service.hasManagedFlight(survival.id),
                "disabled managed flight clears flying and allowFlight");

        survival.gameMode = GameMode.ADVENTURE;
        service.toggleFlight(survival.player);
        service.setGameMode(survival.player, GameMode.SURVIVAL);
        check(!service.hasManagedFlight(survival.id) && !survival.allowFlight,
                "game mode changes surrender adventure managed flight");

        service.toggleFlight(survival.player);
        service.setGameMode(survival.player, GameMode.CREATIVE);
        check(!service.hasManagedFlight(survival.id) && survival.gameMode == GameMode.CREATIVE,
                "creative game mode takes native ownership without a stale managed UUID");
    }

    private static void testNormalizationAndCleanup() {
        MutablePlayer survival = new MutablePlayer("Reconnect", GameMode.SURVIVAL);
        survival.walkSpeed = 0.9F;
        survival.flySpeed = 0.8F;
        survival.allowFlight = true;
        survival.flying = true;
        UtilityService service = service(survival);
        service.normalizeTransientState(survival.player);
        check(close(survival.walkSpeed, 0.2F) && close(survival.flySpeed, 0.1F),
                "join normalization restores both vanilla speed defaults");
        check(!survival.allowFlight && !survival.flying,
                "join normalization clears survival flight leakage");

        MutablePlayer creative = new MutablePlayer("Creative", GameMode.CREATIVE);
        creative.allowFlight = true;
        creative.flying = true;
        service.normalizeTransientState(creative.player);
        check(creative.allowFlight && creative.flying,
                "join normalization preserves creative native flight");

        service.toggleFlight(survival.player);
        service.setSpeed(survival.player, 7);
        service.cleanupPlayer(survival.player);
        check(!service.hasManagedFlight(survival.id) && !service.hasManagedSpeed(survival.id),
                "quit cleanup removes all UUID state");
        check(close(survival.walkSpeed, 0.2F) && close(survival.flySpeed, 0.1F)
                        && !survival.allowFlight,
                "quit cleanup restores managed movement state");

        service.toggleFlight(survival.player);
        service.setSpeed(survival.player, 8);
        service.cleanup();
        check(!service.hasManagedFlight(survival.id) && !service.hasManagedSpeed(survival.id),
                "module cleanup clears runtime sets");
        check(close(survival.walkSpeed, 0.2F) && close(survival.flySpeed, 0.1F),
                "module cleanup normalizes online managed speeds");
    }

    private static void testHealFeedAndTeleport() {
        MutablePlayer player = new MutablePlayer("Patient", GameMode.SURVIVAL);
        player.health = 3.0D;
        player.maxHealth = 34.0D;
        player.food = 4;
        player.saturation = 1.0F;
        player.exhaustion = 2.0F;
        player.fireTicks = 40;
        player.freezeTicks = 20;
        MutablePlayer target = new MutablePlayer("Target", GameMode.ADVENTURE);
        UtilityService service = service(player, target);

        service.heal(player.player);
        check(close(player.health, 34.0D) && player.fireTicks == 0 && player.freezeTicks == 0,
                "heal uses the current max-health attribute and clears fire/freeze");
        check(player.food == 4 && close(player.saturation, 1.0F),
                "heal leaves hunger unchanged");
        double healthBeforeFeed = player.health;
        service.feed(player.player);
        check(player.food == 20 && close(player.saturation, 20.0F) && close(player.exhaustion, 0.0F),
                "feed restores food, saturation and exhaustion");
        check(close(player.health, healthBeforeFeed), "feed leaves health unchanged");

        player.teleportResult = false;
        check(!service.teleport(player.player, target.player), "teleport propagates a cancelled result");
        player.teleportResult = true;
        check(service.teleport(player.player, target.player), "teleport propagates a successful result");
    }

    private static void testMainThreadGuard() {
        MutablePlayer player = new MutablePlayer("Thread", GameMode.SURVIVAL);
        AtomicBoolean primary = new AtomicBoolean(false);
        UtilityService service = new UtilityService(
                primary::get,
                () -> List.of(player.player),
                ignored -> player.maxHealth
        );
        try {
            service.feed(player.player);
            throw new AssertionError("off-thread mutation should fail");
        } catch (IllegalStateException expected) {
            checks++;
        }
    }

    private static UtilityService service(MutablePlayer... players) {
        List<MutablePlayer> states = List.of(players);
        return new UtilityService(
                () -> true,
                () -> states.stream().map(state -> state.player).toList(),
                player -> states.stream()
                        .filter(state -> state.player == player)
                        .findFirst()
                        .map(state -> state.maxHealth)
                        .orElse(20.0D)
        );
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.00001D;
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static final class MutablePlayer {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final Player player;
        private GameMode gameMode;
        private boolean online = true;
        private boolean allowFlight;
        private boolean flying;
        private float walkSpeed = 0.2F;
        private float flySpeed = 0.1F;
        private double health = 20.0D;
        private double maxHealth = 20.0D;
        private int food = 20;
        private float saturation = 5.0F;
        private float exhaustion;
        private int fireTicks;
        private int freezeTicks;
        private boolean teleportResult = true;

        private MutablePlayer(String name, GameMode gameMode) {
            this.name = name;
            this.gameMode = gameMode;
            this.player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> online;
                case "getGameMode" -> this.gameMode;
                case "setGameMode" -> set(() -> this.gameMode = (GameMode) arguments[0]);
                case "getAllowFlight" -> allowFlight;
                case "setAllowFlight" -> set(() -> allowFlight = (boolean) arguments[0]);
                case "isFlying" -> flying;
                case "setFlying" -> set(() -> flying = (boolean) arguments[0]);
                case "getWalkSpeed" -> walkSpeed;
                case "setWalkSpeed" -> set(() -> walkSpeed = (float) arguments[0]);
                case "getFlySpeed" -> flySpeed;
                case "setFlySpeed" -> set(() -> flySpeed = (float) arguments[0]);
                case "getHealth" -> health;
                case "setHealth" -> set(() -> health = (double) arguments[0]);
                case "getMaxHealth" -> maxHealth;
                case "getFoodLevel" -> food;
                case "setFoodLevel" -> set(() -> food = (int) arguments[0]);
                case "getSaturation" -> saturation;
                case "setSaturation" -> set(() -> saturation = (float) arguments[0]);
                case "getExhaustion" -> exhaustion;
                case "setExhaustion" -> set(() -> exhaustion = (float) arguments[0]);
                case "getFireTicks" -> fireTicks;
                case "setFireTicks" -> set(() -> fireTicks = (int) arguments[0]);
                case "getFreezeTicks" -> freezeTicks;
                case "setFreezeTicks" -> set(() -> freezeTicks = (int) arguments[0]);
                case "getLocation" -> new Location(null, 1.0D, 2.0D, 3.0D);
                case "teleport" -> teleportResult;
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static Object set(Runnable action) {
        action.run();
        return null;
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
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }
}

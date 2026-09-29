package dev.vapee.core.visibility;

import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.friend.FriendRepository;
import dev.vapee.core.friend.FriendRepositoryException;
import dev.vapee.core.friend.FriendRequestPolicy;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.FriendSnapshot;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.SocialService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class VisibilityRelationshipFixture {
    final World lobby = proxy(World.class, (method, arguments) -> DEFAULT);
    final Plugin plugin = proxy(Plugin.class, (method, arguments) -> DEFAULT);
    final MemoryPlayers playerRepository = new MemoryPlayers();
    final PlayerService players = new PlayerService(playerRepository, logger());
    final PlayerSettingsService settings = new PlayerSettingsService(players);
    final SocialService social = new SocialService(players, logger());
    final MemoryFriends friendRepository = new MemoryFriends();
    final FriendService friends = new FriendService(friendRepository, FriendLimits.defaults(),
            FriendRequestPolicy.allowAll(), Clock.systemUTC());
    final List<TestPlayer> online = new ArrayList<>();
    final VisibilityPolicy policy = new VisibilityPolicy(settings, social, friends, (first, second) -> false);
    final VisibilityService visibility = new VisibilityService(plugin,
            () -> online.stream().map(TestPlayer::player).toList(), world -> world == lobby, policy, logger());

    TestPlayer player(String name) {
        UUID id = UUID.nameUUIDFromBytes(("relationship-" + name)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        players.loadPlayer(id, name);
        social.activatePlayer(id);
        TestPlayer result = new TestPlayer(id, name);
        online.add(result);
        return result;
    }

    final class TestPlayer {
        final UUID id;
        final String name;
        final List<String> actions = new ArrayList<>();
        final Player player;

        TestPlayer(UUID id, String name) {
            this.id = id;
            this.name = name;
            player = proxy(Player.class, (method, arguments) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "getWorld" -> lobby;
                case "isOnline" -> true;
                case "hasPermission" -> false;
                case "hidePlayer" -> {
                    actions.add("hide:" + ((Player) arguments[1]).getUniqueId());
                    yield null;
                }
                case "showPlayer" -> {
                    actions.add("show:" + ((Player) arguments[1]).getUniqueId());
                    yield null;
                }
                default -> DEFAULT;
            });
        }

        Player player() { return player; }
        boolean hidden(UUID target) { return actions.contains("hide:" + target); }
        boolean shown(UUID target) { return actions.contains("show:" + target); }
        void clear() { actions.clear(); }
    }

    static final class MemoryPlayers implements PlayerRepository {
        final Map<UUID, CorePlayer> data = new HashMap<>();
        boolean failNext;
        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
        @Override public void save(CorePlayer player) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated player persistence failure");
            }
            data.put(player.getUniqueId(), player);
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
        @Override public Set<UUID> findUniqueIdsByName(String name) { return Set.of(); }
    }

    static final class MemoryFriends implements FriendRepository {
        FriendSnapshot snapshot = FriendSnapshot.empty();
        boolean failNext;
        @Override public FriendSnapshot initialize() { return snapshot; }
        @Override public void save(FriendSnapshot snapshot) {
            if (failNext) {
                failNext = false;
                throw new FriendRepositoryException("simulated friend persistence failure");
            }
            this.snapshot = snapshot;
        }
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static final Object DEFAULT = new Object();

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
                    default -> null;
                };
            }
            Object result = action.invoke(method.getName(), args == null ? new Object[0] : args);
            return result == DEFAULT ? defaultValue(method.getReturnType()) : result;
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (Collection.class.isAssignableFrom(type)) return List.of();
            return null;
        }
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    @FunctionalInterface
    private interface ProxyAction { Object invoke(String method, Object[] arguments); }
}

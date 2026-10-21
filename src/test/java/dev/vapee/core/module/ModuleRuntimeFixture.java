package dev.vapee.core.module;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.logging.Logger;

/** External Paper boundary for real module enable/disable. Dependency module shells expose
 * already-owned services; no production lifecycle or listener cleanup is replaced. */
public final class ModuleRuntimeFixture implements AutoCloseable {
    public final Logger logger = Logger.getAnonymousLogger();
    public final PlayerService players = new PlayerService(new PlayerRepository() {
        public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.empty(); }
        public void save(CorePlayer player) { }
        public boolean exists(UUID id) { return false; }
    }, logger);
    public final PlayerSettingsService settings = new PlayerSettingsService(players);
    public final PlayerModule playerModule = dependency(PlayerModule.class,
            Map.of("playerService", players, "playerSettingsService", settings));
    public final List<Player> online = new ArrayList<>();
    public final List<Listener> registered = new ArrayList<>();
    public final JavaPlugin plugin;
    public final PluginCommand command;
    public final MessageService messages;
    public int failRegistration;
    public boolean missingCommand;
    public boolean failOnlineRead;
    public int sentMessages;
    public BiFunction<String, Object[], Object> serverCalls = (method, args) -> {
        throw new AssertionError("Unexpected server call: " + method);
    };
    private final Server previousServer;

    public ModuleRuntimeFixture() throws Exception {
        this("settings");
    }

    public ModuleRuntimeFixture(String commandName) throws Exception {
        logger.setUseParentHandlers(false);
        plugin = allocate(PluginShell.class);
        var description = new PluginDescriptionFile("VapeeCore", "test", plugin.getClass().getName());
        set(plugin, "description", description);
        set(plugin, "pluginMeta", description);
        set(plugin, "logger", logger);
        set(plugin, "isEnabled", true);
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        command = constructor.newInstance(commandName, plugin);
        PluginManager manager = proxy(PluginManager.class, (method, args) -> {
            if (!method.equals("registerEvents")) throw new AssertionError(method);
            Listener listener = (Listener) args[0];
            registered.add(listener);
            for (var handler : listener.getClass().getMethods()) {
                EventHandler annotation = handler.getAnnotation(EventHandler.class);
                if (annotation == null) continue;
                try {
                    HandlerList list = (HandlerList) handler.getParameterTypes()[0].getMethod("getHandlerList").invoke(null);
                    list.register(new RegisteredListener(listener, (target, event) -> { },
                            annotation.priority(), plugin, annotation.ignoreCancelled()));
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            }
            // Fail after registration, so the real module must release partial side effects.
            if (registered.size() == failRegistration) throw new IllegalStateException("injected registration failure");
            return null;
        });
        Server server = proxy(Server.class, (method, args) -> switch (method) {
            case "getPluginManager" -> manager;
            case "getPluginCommand" -> missingCommand ? null : command;
            case "getPlayer" -> online.stream().filter(p -> p.getUniqueId().equals(args[0])).findFirst().orElse(null);
            case "getOnlinePlayers" -> {
                if (failOnlineRead) throw new IllegalStateException("injected online read failure");
                yield List.copyOf(online);
            }
            case "isPrimaryThread" -> true;
            default -> serverCalls.apply(method, args);
        });
        set(plugin, "server", server);
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = (Server) serverField.get(null);
        serverField.set(null, server);
        var messagesConstructor = MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);
        messagesConstructor.setAccessible(true);
        messages = messagesConstructor.newInstance((java.util.function.Supplier<String>) () -> "");
    }

    public Player player(String name, boolean loaded, boolean isOnline) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (loaded) players.loadPlayer(id, name);
        Player player = proxy(Player.class, (method, args) -> switch (method) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "isOnline" -> isOnline;
            case "sendMessage" -> { sentMessages++; yield null; }
            default -> throw new AssertionError("Unexpected player call: " + method);
        });
        online.add(player);
        return player;
    }

    public int handlerCount() { return HandlerList.getRegisteredListeners(plugin).size(); }

    public static <T> T dependency(Class<T> type, Map<String, ?> services) {
        T result = allocate(type);
        services.forEach((field, service) -> set(result, field, service));
        return result;
    }

    public static Object get(Object target, String name) {
        try { Field field = field(target.getClass(), name); return field.get(target); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    public static void set(Object target, String name, Object value) {
        try { field(target.getClass(), name).set(target, value); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    public static <T> T allocate(Class<T> type) {
        try {
            Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);
            return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type));
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    public static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (target, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(target);
            case "equals" -> target == args[0];
            case "toString" -> type.getSimpleName() + " fixture";
            default -> calls.apply(method.getName(), args);
        }));
    }

    @Override public void close() throws Exception {
        HandlerList.unregisterAll(plugin);
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, previousServer);
    }

    private static final class PluginShell extends JavaPlugin { }

    /** Fails after an external subscription takes effect, including a partial add/remove. */
    public static final class FaultSet<E> extends java.util.LinkedHashSet<E> {
        public int additions;
        public int failAdd;
        public int failRemove;
        public int removals;
        public Runnable afterAdd = () -> { };
        public final RuntimeException startupFailure = new IllegalStateException("injected observer startup failure");
        public final RuntimeException cleanupFailure = new IllegalStateException("injected observer cleanup failure");

        @Override public boolean add(E value) {
            boolean added = super.add(value);
            additions++;
            afterAdd.run();
            if (additions == failAdd) throw startupFailure;
            return added;
        }

        @Override public boolean remove(Object value) {
            boolean removed = super.remove(value);
            if (++removals == failRemove) throw cleanupFailure;
            return removed;
        }
    }
}

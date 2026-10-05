package dev.vapee.core.presentation;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presentation.config.PresentationConfig;
import dev.vapee.core.presentation.scoreboard.ScoreboardService;
import dev.vapee.core.rank.RankService;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.Server;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/** Only external Paper/LP surfaces are replaced; production render/services/config remain real. */
public final class PresentationFixture implements AutoCloseable {
    public final Path directory;
    public final Logger logger = Logger.getAnonymousLogger();
    public final List<TestPlayer> online = new ArrayList<>();
    public final List<Task> tasks = new ArrayList<>();
    public final List<Board> boards = new ArrayList<>();
    public final Board main = new Board();
    public final World world = proxy(World.class, (method, args) -> method.equals("getName") ? "lobby" : null);
    public final JavaPlugin plugin;
    public final ConfigService config;
    public final MessageService messages;
    public final PlayerService players;
    public final PlayerSettingsService settings;
    public final LuckPermsService luckPerms;
    public final RankService ranks;
    public final EconomyService economy;
    public final LobbyService lobby;
    public final PresentationConfig presentationConfig;
    private final Server previousServer;

    public PresentationFixture() throws Exception {
        logger.setUseParentHandlers(false);
        directory = Files.createTempDirectory("vapeecore-presentation-");
        Files.writeString(directory.resolve("presentation.yml"), "scoreboard:\n  lobby-only: false\n");
        ScoreboardManager manager = proxy(ScoreboardManager.class, (method, args) -> switch (method) {
            case "getMainScoreboard" -> main.board;
            case "getNewScoreboard" -> { Board board = new Board(); boards.add(board); yield board.board; }
            default -> throw new AssertionError(method);
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (method, args) -> {
            if (method.equals("runTask") || method.equals("runTaskTimer")) {
                Task task = new Task((Runnable) args[1]); tasks.add(task); return task.task;
            }
            throw new AssertionError(method);
        });
        Server server = proxy(Server.class, (method, args) -> switch (method) {
            case "getScoreboardManager" -> manager;
            case "getScoreboardCriteria" -> proxy(Criteria.class, (operation, values) -> switch (operation) {
                case "getName" -> args[0];
                case "isReadOnly" -> false;
                case "getDefaultRenderType" -> RenderType.INTEGER;
                default -> throw new AssertionError(operation);
            });
            case "getOnlinePlayers" -> online.stream().filter(p -> p.online).map(p -> p.player).toList();
            case "getMaxPlayers" -> 20;
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> proxy(PluginManager.class, (operation, values) -> null);
            default -> null;
        });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true); previousServer = (Server) serverField.get(null); serverField.set(null, server);
        plugin = allocate(PluginShell.class);
        set(plugin, "server", server); set(plugin, "logger", logger);
        set(plugin, "dataFolder", directory.toFile()); set(plugin, "isEnabled", true);
        config = new ConfigService(plugin);
        messages = new MessageService(config);
        players = new PlayerService(new PlayerRepository() {
            public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.empty(); }
            public void save(CorePlayer player) { }
            public boolean exists(UUID id) { return false; }
        }, logger);
        settings = new PlayerSettingsService(players);
        UserManager users = proxy(UserManager.class, (method, args) -> null);
        luckPerms = new LuckPermsService(proxy(LuckPerms.class,
                (method, args) -> method.equals("getUserManager") ? users : null));
        ranks = new RankService(config, luckPerms);
        economy = new EconomyService(players);
        lobby = new LobbyService(plugin, new LobbyConfig(plugin));
        presentationConfig = new PresentationConfig(plugin);
        presentationConfig.initialize();
    }

    public PresentationRenderer renderer(Function<UUID, Optional<String>> tags) {
        return new PresentationRenderer(plugin, config, messages, luckPerms, ranks, economy, presentationConfig, tags);
    }

    public ScoreboardService scoreboards() { return new ScoreboardService(plugin, presentationConfig, settings, lobby); }

    public TestPlayer player(String name) {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), name);
        online.add(player); players.loadPlayer(player.id, name); return player;
    }

    public final class TestPlayer {
        public final UUID id;
        public final Player player;
        public Component name, header, footer;
        public Scoreboard board = main.board;
        public boolean online = true;
        public final Set<UUID> hidden = new HashSet<>();
        public int writes, visibilityWrites;
        public boolean normalizeName, failHeaderWrite;

        public TestPlayer(UUID id, String vanillaName) {
            this.id = id;
            name = Component.text(vanillaName);
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> vanillaName;
                case "displayName" -> Component.text(vanillaName);
                case "getStatistic" -> 0;
                case "getWorld" -> world;
                case "isOnline" -> online;
                case "hasPermission" -> false;
                case "getScoreboard" -> board;
                case "setScoreboard" -> { board = (Scoreboard) args[0]; yield null; }
                case "playerListName" -> {
                    if (args.length == 0) yield name;
                    name = args[0] == null ? Component.text(vanillaName) : (Component) args[0];
                    if (normalizeName) name = name.compact();
                    writes++; yield null;
                }
                case "playerListHeader" -> header;
                case "playerListFooter" -> footer;
                case "sendPlayerListHeader" -> {
                    if (failHeaderWrite) throw new IllegalStateException("injected external header write failure");
                    header = Objects.requireNonNull((Component) args[0]); writes++; yield null;
                }
                case "sendPlayerListFooter" -> { footer = Objects.requireNonNull((Component) args[0]); writes++; yield null; }
                case "sendPlayerListHeaderAndFooter" -> {
                    header = Objects.requireNonNull((Component) args[0]);
                    footer = Objects.requireNonNull((Component) args[1]); writes += 2; yield null;
                }
                case "setPlayerListHeader" -> {
                    if (args[0] != null) throw new AssertionError("No lossy legacy conversion");
                    header = null; writes++; yield null;
                }
                case "setPlayerListFooter" -> {
                    if (args[0] != null) throw new AssertionError("No lossy legacy conversion");
                    footer = null; writes++; yield null;
                }
                case "hidePlayer" -> { hidden.add(((Player) args[1]).getUniqueId()); visibilityWrites++; yield null; }
                case "showPlayer" -> { hidden.remove(((Player) args[1]).getUniqueId()); visibilityWrites++; yield null; }
                case "canSee" -> !hidden.contains(((Player) args[0]).getUniqueId());
                default -> throw new AssertionError("Unexpected player API: " + method);
            });
        }
    }

    public static final class Board {
        public final Map<String, Obj> objectives = new HashMap<>();
        public int writes, teamCalls;
        public final Scoreboard board = proxy(Scoreboard.class, (method, args) -> switch (method) {
            case "registerNewObjective" -> {
                String key = (String) args[0];
                if (objectives.containsKey(key)) throw new IllegalArgumentException("Duplicate objective");
                Obj obj = new Obj(key, (Component) args[2]); objectives.put(key, obj); writes++; yield obj.objective;
            }
            case "getObjective" -> objectives.get(args[0]) == null ? null : objectives.get(args[0]).objective;
            default -> { teamCalls++; throw new AssertionError("Unexpected board API: " + method); }
        });
        public final class Obj {
            public Component title;
            public DisplaySlot slot;
            public boolean removed;
            public final Map<String, Component> lines = new HashMap<>();
            public final Objective objective;
            Obj(String key, Component title) {
                this.title = title;
                objective = proxy(Objective.class, (method, args) -> switch (method) {
                    case "setDisplaySlot" -> { slot = (DisplaySlot) args[0]; writes++; yield null; }
                    case "numberFormat" -> { writes++; yield null; }
                    case "displayName" -> { this.title = (Component) args[0]; writes++; yield null; }
                    case "unregister" -> { removed = true; objectives.remove(key); writes++; yield null; }
                    case "getScore" -> proxy(Score.class, (operation, values) -> {
                        if (operation.equals("customName")) lines.put((String) args[0], (Component) values[0]);
                        else if (!operation.equals("setScore")) throw new AssertionError(operation);
                        writes++; return null;
                    });
                    default -> throw new AssertionError(method);
                });
            }
        }
    }

    public static final class Task {
        public boolean cancelled;
        public final Runnable runnable;
        public final BukkitTask task;
        Task(Runnable runnable) {
            this.runnable = runnable;
            task = proxy(BukkitTask.class, (method, args) -> switch (method) {
                case "isCancelled" -> cancelled;
                case "cancel" -> { cancelled = true; yield null; }
                default -> null;
            });
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, Action action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> type.getSimpleName();
            };
            return action.call(method.getName(), args == null ? new Object[0] : args);
        });
    }
    @FunctionalInterface public interface Action { Object call(String method, Object[] args); }

    public static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true); Object unsafe = field.get(null);
        return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }
    public static void set(Object object, String name, Object value) throws Exception {
        Class<?> type = object.getClass();
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    public static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static final class PluginShell extends JavaPlugin { }
    @Override public void close() throws Exception {
        Field field = Bukkit.class.getDeclaredField("server"); field.setAccessible(true); field.set(null, previousServer);
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}

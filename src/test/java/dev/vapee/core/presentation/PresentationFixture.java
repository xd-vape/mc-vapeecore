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
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.kyori.adventure.text.format.TextColor;
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
    public final Map<UUID, String> prefixes = new HashMap<>();
    public final Map<UUID, String> groups = new HashMap<>();
    public final Map<String, String> groupColors = new HashMap<>();
    public boolean failRankRead;
    public boolean failTaskSchedule;
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
                if (failTaskSchedule) throw new IllegalStateException("injected scheduler failure");
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
        UserManager users = proxy(UserManager.class, (method, args) -> {
            if (failRankRead) throw new IllegalStateException("injected rank provider failure");
            UUID id = (UUID) args[0];
            if (!groups.containsKey(id) && !prefixes.containsKey(id)) return null;
            return proxy(User.class, (operation, values) -> switch (operation) {
                case "getPrimaryGroup" -> groups.get(id);
                case "getCachedData" -> proxy(CachedDataManager.class, (read, ignored) ->
                        proxy(CachedMetaData.class, (meta, keys) -> meta.equals("getPrefix") ? prefixes.get(id) : null));
                default -> throw new AssertionError(operation);
            });
        });
        GroupManager groupManager = proxy(GroupManager.class, (method, args) -> {
            String id = (String) args[0];
            if (!groupColors.containsKey(id)) return null;
            return proxy(Group.class, (operation, values) -> switch (operation) {
                case "getName", "getDisplayName" -> id;
                case "getWeight" -> OptionalInt.empty();
                case "getCachedData" -> proxy(CachedDataManager.class, (read, ignored) ->
                        proxy(CachedMetaData.class, (meta, keys) -> meta.equals("getMetaValue")
                                && keys[0].equals(RankService.COLOR_META_KEY) ? groupColors.get(id) : null));
                default -> throw new AssertionError(operation);
            });
        });
        luckPerms = new LuckPermsService(proxy(LuckPerms.class,
                (method, args) -> method.equals("getUserManager") ? users
                        : method.equals("getGroupManager") ? groupManager : null));
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
        public World currentWorld = world;

        public TestPlayer(UUID id, String vanillaName) {
            this.id = id;
            name = Component.text(vanillaName);
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> vanillaName;
                case "displayName" -> Component.text(vanillaName);
                case "getStatistic" -> 0;
                case "getWorld" -> currentWorld;
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
        public final Map<String, TestTeam> teams = new HashMap<>();
        public int writes, teamCalls;
        public boolean normalizeTeamComponents;
        public final Scoreboard board = proxy(Scoreboard.class, (method, args) -> switch (method) {
            case "registerNewObjective" -> {
                String key = (String) args[0];
                if (objectives.containsKey(key)) throw new IllegalArgumentException("Duplicate objective");
                Obj obj = new Obj(key, (Component) args[2]); objectives.put(key, obj); writes++; yield obj.objective;
            }
            case "getObjective" -> objectives.get(args[0]) == null ? null : objectives.get(args[0]).objective;
            case "getTeam" -> teams.get(args[0]) == null ? null : teams.get(args[0]).team;
            case "getEntryTeam" -> teams.values().stream().filter(team -> team.entries.contains(args[0]))
                    .map(team -> team.team).findFirst().orElse(null);
            case "registerNewTeam" -> {
                String name = (String) args[0];
                if (teams.containsKey(name)) throw new IllegalArgumentException("Duplicate team");
                TestTeam team = new TestTeam(name); teams.put(name, team); teamCalls++; yield team.team;
            }
            default -> { teamCalls++; throw new AssertionError("Unexpected board API: " + method); }
        });
        public final class TestTeam {
            public final String name;
            public Component prefix = Component.empty(), suffix = Component.empty(), display;
            public TextColor color;
            public final Set<String> entries = new HashSet<>();
            public boolean removed;
            public int mutations, optionWrites;
            public final Team team;
            public TestTeam(String name) {
                this.name = name; display = Component.text(name);
                team = proxy(Team.class, (method, args) -> switch (method) {
                    case "getName" -> name;
                    case "getScoreboard" -> board;
                    case "prefix" -> { if (args.length == 0) yield prefix; prefix = normalizeTeamComponents ? Component.empty().append((Component) args[0]) : (Component) args[0]; mutations++; yield null; }
                    case "suffix" -> { if (args.length == 0) yield suffix; suffix = normalizeTeamComponents ? Component.empty().append((Component) args[0]) : (Component) args[0]; mutations++; yield null; }
                    case "displayName" -> { if (args.length == 0) yield display; display = (Component) args[0]; mutations++; yield null; }
                    case "color" -> { if (args.length == 0) yield color; color = (TextColor) args[0]; mutations++; yield null; }
                    case "allowFriendlyFire", "canSeeFriendlyInvisibles" -> true;
                    case "getOption" -> Team.OptionStatus.ALWAYS;
                    case "setOption", "setAllowFriendlyFire", "setCanSeeFriendlyInvisibles" -> { optionWrites++; throw new AssertionError("No gameplay option writes"); }
                    case "getEntries" -> Set.copyOf(entries);
                    case "hasEntry" -> entries.contains(args[0]);
                    case "addEntry" -> {
                        for (TestTeam other : teams.values()) other.entries.remove(args[0]);
                        entries.add((String) args[0]); mutations++; yield null;
                    }
                    case "unregister" -> { removed = true; if (teams.get(name) == this) teams.remove(name); mutations++; yield null; }
                    case "removeEntry" -> { mutations++; yield entries.remove(args[0]); }
                    default -> throw new AssertionError("Unexpected team API: " + method);
                });
            }
        }
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
        public boolean failCancel;
        public final Runnable runnable;
        public final BukkitTask task;
        Task(Runnable runnable) {
            this.runnable = runnable;
            task = proxy(BukkitTask.class, (method, args) -> switch (method) {
                case "isCancelled" -> cancelled;
                case "cancel" -> {
                    if (failCancel) throw new IllegalStateException("injected task cancellation failure");
                    cancelled = true; yield null;
                }
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

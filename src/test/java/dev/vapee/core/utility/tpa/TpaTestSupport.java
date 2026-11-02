package dev.vapee.core.utility.tpa;

import dev.vapee.core.activity.*;
import dev.vapee.core.activity.location.*;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.*;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.player.*;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.rank.RankService;
import dev.vapee.core.social.SocialService;
import dev.vapee.core.utility.UtilityService;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

/** Actual request, Utility, Social, settings, Activity and Lobby state services; controlled Paper I/O and clock. */
public final class TpaTestSupport {
    public static final class Fixture {
        public final Map<UUID, User> online = new LinkedHashMap<>();
        public final Map<UUID, World> worlds = new HashMap<>();
        public final Logger logger = Logger.getAnonymousLogger();
        public final MessageService messages;
        public final PlayerService players;
        public final PlayerSettingsService settings;
        public final SocialService social;
        public final ActivityService activities;
        public final LobbyPlayerStateService states;
        public final UtilityService utility;
        public final Ground ground = new Ground("world"), other = new Ground("other");
        public final TeleportRequests requests;
        public long now;
        public boolean primaryThread = true;
        public String rankColor = "gold";
        public Fixture() {
            logger.setUseParentHandlers(false);
            messages = new MessageService(() -> "OPERATOR PREFIX MUST NOT APPEAR", LocalizationService.bundled(logger));
            players = new PlayerService(new PlayerRepository() {
                public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.empty(); }
                public void save(CorePlayer player) { }
                public boolean exists(UUID id) { return false; }
            }, logger);
            settings = new PlayerSettingsService(players); social = new SocialService(players, logger);
            activities = construct(ActivityService.class,
                    new Class<?>[]{PlayerService.class, Logger.class, BooleanSupplier.class, Predicate.class},
                    players, logger, (BooleanSupplier) () -> true, (Predicate<String>) name -> true);
            states = construct(LobbyPlayerStateService.class,
                    new Class<?>[]{BooleanSupplier.class, Predicate.class, Supplier.class, Consumer.class, Consumer.class, Supplier.class},
                    (BooleanSupplier) () -> true, (Predicate<Player>) p -> true, (Supplier<GameMode>) () -> GameMode.ADVENTURE,
                    (Consumer<Player>) p -> {}, (Consumer<Player>) p -> {}, (Supplier<Collection<Player>>) this::onlinePlayers);
            utility = new UtilityService(() -> primaryThread, this::onlinePlayers, p -> 20);
            worlds.put(ground.id, ground.world); worlds.put(other.id, other.world);
            Class<?> gateway = nested(RankService.class, "Gateway");
            Object ranks = proxy(gateway, (name, args) -> switch (name) {
                case "getPrimaryGroup" -> Optional.of("member");
                case "getGroupInformation" -> Optional.of(new LuckPermsService.GroupInformation("member", Optional.of("Member"),
                        Optional.empty(), Optional.of(rankColor), OptionalInt.of(1)));
                case "getTrackGroups" -> Optional.empty();
                default -> throw new AssertionError(name);
            });
            RankService rankService = construct(RankService.class, new Class<?>[]{Supplier.class, gateway}, (Supplier<String>) () -> "ranks", ranks);
            requests = new TeleportRequests(() -> primaryThread, () -> now,
                    id -> online.containsKey(id) ? online.get(id).player : null, this::onlinePlayers,
                    id -> settings.getSettings(id).isPresent(), activities::isParticipating, states::isBuildMode,
                    social::isIgnoring, worlds::get, utility, messages, new PlayerNameFormatter(() -> rankService), logger);
            activities.addMembershipListener(requests); states.addStateListener(requests); social.addRelationshipListener(requests);
            requests.enable();
        }
        public List<Player> onlinePlayers() { return online.values().stream().map(p -> p.player).toList(); }
        public User user(String name) { return user(UUID.randomUUID(), name); }
        public User user(UUID id, String name) {
            User user = new User(this, id, name); online.put(id, user); players.loadPlayer(id, name); social.activatePlayer(id); return user;
        }
        public void command(User user, String name, String... args) {
            requests.onCommand(user.player, TpaTestSupport.command(name), name, args);
        }
        public List<String> tab(User user, String name, String input) {
            return requests.onTabComplete(user.player, TpaTestSupport.command(name), name, new String[]{input});
        }
        public void seconds(long seconds) { now += java.time.Duration.ofSeconds(seconds).toNanos(); }
        public UUID activity(User user) {
            activities.registerActivityType(new ActivityType() {
                public String getKey() { return "test"; }
                public int getMinParticipants() { return 1; }
                public int getMaxParticipants() { return 8; }
                public ActivitySession createSession(UUID id, ActivityVenue venue) { return new ActivitySession(id, "test", venue) {}; }
            });
            String id = "venue_" + user.id.toString().replace("-", "");
            activities.registerVenue(new ActivityVenue(id, "test", new ActivityArea("world", -100, 0, -100, 100, 200, 100),
                    new ActivityPosition("world", .5, 64, .5, 0, 0)));
            UUID session = activities.createSession("test", id).session().orElseThrow().getSessionId();
            if (activities.joinSession(user.player, session) != ActivityResult.SUCCESS) throw new AssertionError("join");
            return session;
        }
    }
    public static final class User {
        public final Fixture fixture;
        public final UUID id;
        public final String name;
        public final Player player;
        public final Set<String> permissions = new HashSet<>(Set.of(TeleportRequests.PERMISSION));
        public final Set<UUID> hidden = new HashSet<>();
        public final List<Component> received = new ArrayList<>();
        public Location location;
        public Locale locale = Locale.US;
        public GameMode mode = GameMode.ADVENTURE;
        public boolean online = true, dead, vehicle, passengers, teleportSuccess = true, throwTeleport;
        public int teleports;
        public Runnable teleportHook = () -> {};
        User(Fixture fixture, UUID id, String name) {
            this.fixture = fixture; this.id = id; this.name = name; location = new Location(fixture.ground.world, .5, 64, .5);
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> online;
                case "isDead" -> dead;
                case "isInsideVehicle" -> vehicle;
                case "getPassengers" -> passengers ? List.of(player()) : List.of();
                case "hasPermission" -> permissions.contains(args[0]);
                case "canSee" -> !hidden.contains(((Player) args[0]).getUniqueId());
                case "getWorld" -> location.getWorld();
                case "getLocation" -> location.clone();
                case "locale" -> locale;
                case "getLocale" -> locale.toString();
                case "sendMessage" -> { for (Object arg : args) if (arg instanceof Component component) received.add(component); yield null; }
                case "teleport" -> {
                    teleports++; teleportHook.run();
                    if (throwTeleport) throw new IllegalStateException("controlled failure");
                    if (teleportSuccess) location = ((Location) args[0]).clone();
                    yield teleportSuccess;
                }
                case "getGameMode" -> mode;
                case "setGameMode" -> { mode = (GameMode) args[0]; yield null; }
                case "getInventory" -> proxy(PlayerInventory.class, (n, a) -> n.equals("setHeldItemSlot") || n.equals("clear")
                        || n.equals("setArmorContents") || n.equals("setItemInOffHand") ? null : unexpected(n));
                case "closeInventory", "setItemOnCursor", "setFlying", "setAllowFlight" -> null;
                default -> unexpected(method);
            });
        }
        private Player player() { return player; }
        public String last() { return received.isEmpty() ? "" : plain(received.getLast()); }
        public List<ClickEvent> clicks() { return received.stream().flatMap(c -> TpaTestSupport.clicks(c).stream()).toList(); }
        public String accept() { return clicks().stream().filter(c -> c.value().startsWith("/vapeecore:tpaccept ")).reduce((a,b) -> b).orElseThrow().value().split(" ")[1]; }
    }
    public static final class Ground {
        public final UUID id = UUID.randomUUID();
        public final World world;
        public final Map<String, Material> blocks = new HashMap<>();
        public final List<String> blockReads = new ArrayList<>();
        public int min = -64, max = 320, floor = 63, unloadedReads;
        public boolean loaded = true, inside = true;
        public Ground(String name) {
            world = proxy(World.class, (method, args) -> switch (method) {
                case "getUID" -> id;
                case "getName" -> name;
                case "getMinHeight" -> min;
                case "getMaxHeight" -> max;
                case "getWorldBorder" -> proxy(WorldBorder.class, (n, a) -> n.equals("isInside") ? inside : unexpected(n));
                case "isChunkLoaded" -> loaded;
                case "getBlockAt" -> {
                    if (!loaded) { unloadedReads++; throw new AssertionError("Block access may load chunk"); }
                    String key = args[0] + "," + args[1] + "," + args[2]; blockReads.add(key);
                    Material material = blocks.getOrDefault(key, (int) args[1] <= floor ? Material.STONE : Material.AIR);
                    yield proxy(Block.class, (n, a) -> switch (n) {
                        case "getType" -> material;
                        case "isSolid" -> material == Material.STONE || material == Material.MAGMA_BLOCK || material == Material.CACTUS;
                        case "isPassable" -> material != Material.STONE && material != Material.CACTUS;
                        case "isLiquid" -> material == Material.LAVA || material == Material.WATER;
                        default -> unexpected(n);
                    });
                }
                default -> unexpected(method);
            });
        }
    }
    public static Command command(String name) { return new Command(name) { public boolean execute(org.bukkit.command.CommandSender s, String l, String[] a) { return true; } }; }
    public static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    public static List<ClickEvent> clicks(Component component) {
        List<ClickEvent> values = new ArrayList<>(); if (component.clickEvent() != null) values.add(component.clickEvent());
        component.children().forEach(c -> values.addAll(clicks(c))); return values;
    }
    public static <T> T construct(Class<T> type, Class<?>[] signature, Object... args) {
        try { var ctor = type.getDeclaredConstructor(signature); ctor.setAccessible(true); return ctor.newInstance(args); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Class<?> nested(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredClasses()).filter(c -> c.getSimpleName().equals(name)).findFirst().orElseThrow();
    }
    public static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> body) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> switch (method.getName()) {
            case "equals" -> self == args[0]; case "hashCode" -> System.identityHashCode(self); case "toString" -> type.getSimpleName();
            default -> body.apply(method.getName(), args == null ? new Object[0] : args);
        }));
    }
    private static Object unexpected(String method) { throw new AssertionError("Unexpected Paper boundary: " + method); }
}

package dev.vapee.core.friend;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class FriendCommandHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;
    private static Fixture activeFixture;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-friend-command-");
        try {
            Fixture fixture = new Fixture(directory);
            Player alice = fixture.player("Alice", true, true);
            Player bob = fixture.player("Bob", true, true);
            Player charlie = fixture.player("Charlie", false, true);
            Player markup = fixture.player("<red>Safe</red>", false, true);
            fixture.player("Twin", false, true);
            fixture.player("twin", false, true);
            FriendCommand command = fixture.command();
            CommandSender console = fixture.console();
            Player denied = fixture.player("Denied", true, false);

            run(command, console, "help");
            check(fixture.last().contains("Only players"), "console cannot manage friends");
            run(command, denied, "add", "Bob");
            check(fixture.last().contains("permission"), "permission is enforced");
            run(command, alice, "help");
            check(fixture.last().contains("/friend add") && fixture.last().contains("/friend requests"),
                    "help uses common renderer with all operations");
            run(command, alice, "add");
            check(fixture.last().contains("/friend help"), "invalid usage is controlled");
            run(command, alice, "add", "Unknown");
            check(fixture.last().contains("not known"), "unknown name is rejected");
            run(command, alice, "add", UUID.randomUUID().toString());
            check(fixture.last().contains("not known"), "unknown UUID cannot create phantom friendship");
            run(command, alice, "add", "TWIN");
            check(fixture.last().contains("ambiguous"), "collision never chooses a random UUID");
            run(command, alice, "add", "Alice");
            check(fixture.last().contains("cannot add yourself"), "self request is rejected");

            fixture.messages.get(bob.getUniqueId()).clear();
            run(command, alice, "add", "Bob");
            check(fixture.last().contains("Friend request sent to Bob"), "add sends request by known name");
            check(fixture.all(bob.getUniqueId()).contains("Alice sent you a friend request"),
                    "online recipient receives immediate notification");
            run(command, alice, "add", "Bob");
            check(fixture.last().contains("already pending"), "duplicate request is reported");
            run(command, alice, "requests");
            check(fixture.all(alice.getUniqueId()).contains("Outgoing:")
                            && fixture.last().contains("Bob"), "requests separates outgoing list");
            run(command, bob, "requests");
            check(fixture.all(bob.getUniqueId()).contains("Incoming:")
                            && fixture.last().contains("None"), "requests separates incoming list");
            check(fixture.all(bob.getUniqueId()).contains("• Alice"), "incoming request names resolve safely");
            check(command.onTabComplete(alice, null, "friend", new String[]{""}).size() == 8,
                    "root tab exposes eight operations");
            check(command.onTabComplete(console, null, "friend", new String[]{""}).isEmpty()
                            && command.onTabComplete(denied, null, "friend", new String[]{""}).isEmpty(),
                    "console and unpermitted player have no tab completion");
            check(command.onTabComplete(alice, null, "friend", new String[]{"add", "B"}).isEmpty(),
                    "add tab excludes outgoing request");
            check(command.onTabComplete(bob, null, "friend", new String[]{"accept", "a"})
                            .equals(List.of("Alice"))
                            && command.onTabComplete(bob, null, "friend", new String[]{"deny", "a"})
                            .equals(List.of("Alice")), "accept and deny tab only show incoming requests");
            check(command.onTabComplete(alice, null, "friend", new String[]{"cancel", "b"})
                            .equals(List.of("Bob")), "cancel tab only shows outgoing requests");

            run(command, alice, "cancel", bob.getUniqueId().toString());
            check(fixture.last().contains("canceled for Bob"), "cancel works by known UUID");
            run(command, bob, "add", "Alice");
            run(command, alice, "deny", "Bob");
            check(fixture.last().contains("declined from Bob"), "deny removes incoming request");
            run(command, bob, "add", "Alice");
            run(command, alice, "accept", "Bob");
            check(fixture.last().contains("now friends with Bob"), "accept creates friendship");
            run(command, alice, "list");
            check(fixture.all(alice.getUniqueId()).contains("Friends (1/100)")
                            && fixture.last().contains("Bob"), "friend list uses current limits and names");
            check(command.onTabComplete(alice, null, "friend", new String[]{"remove", "b"})
                            .equals(List.of("Bob")), "remove tab includes own friends");
            run(command, alice, "add", "Bob");
            check(fixture.last().contains("already friends"), "already-friends result is mapped");
            run(command, alice, "remove", "Bob");
            check(fixture.last().contains("Removed friend Bob"), "remove deletes friendship");

            run(command, bob, "add", "Alice");
            run(command, alice, "add", "Bob");
            check(fixture.last().contains("now friends with Bob"), "cross request auto-accepts");
            run(command, alice, "remove", "Bob");
            run(command, alice, "add", "Charlie");
            check(fixture.last().contains("Charlie"), "known offline name supports add");
            run(command, charlie, "accept", "Alice");
            check(fixture.last().contains("Alice"), "known offline relation supports accept");
            run(command, alice, "remove", "Charlie");
            run(command, alice, "add", markup.getUniqueId().toString());
            check(fixture.last().contains("<red>Safe</red>")
                            && !hasColor(fixture.lastComponent(), "Safe", NamedTextColor.RED),
                    "user-controlled name is literal Component text");
            run(command, alice, "cancel", markup.getUniqueId().toString());

            fixture.policy = FriendRequestDecision.BLOCKED;
            run(command, alice, "add", "Bob");
            check(fixture.last().contains("could not be processed"), "ignore denial is generic");
            fixture.policy = FriendRequestDecision.REQUESTS_DISABLED;
            run(command, alice, "add", "Bob");
            check(fixture.last().contains("not accepting"), "privacy denial is reported");
            fixture.policy = FriendRequestDecision.ALLOW;
            fixture.repository.failNext = true;
            run(command, alice, "add", "Bob");
            check(fixture.last().contains("could not be updated")
                            && fixture.friends.getRelation(alice.getUniqueId(), bob.getUniqueId())
                            == FriendRelation.NONE, "persistence failure is controlled and not published");
            check(command.onTabComplete(alice, null, "friend", new String[]{"add", "a"}).isEmpty(),
                    "add tab excludes self");
            check(command.onTabComplete(alice, null, "friend", new String[]{"add", "b"})
                            .equals(List.of("Bob")), "add tab is online-only and case-insensitive");
            check(command.onTabComplete(alice, null, "friend", new String[]{"add", "ch"}).isEmpty(),
                    "add tab does not enumerate offline players");

            MemoryRepository friendLimitRepo = new MemoryRepository();
            friendLimitRepo.snapshot = new FriendSnapshot(List.of(
                    new Friendship(alice.getUniqueId(), bob.getUniqueId(), Instant.EPOCH)), List.of());
            FriendCommand friendLimitCommand = fixture.command(new FriendService(friendLimitRepo,
                    new FriendLimits(1, 5, 5), FriendRequestPolicy.allowAll(), Clock.systemUTC()));
            run(friendLimitCommand, alice, "add", "Charlie");
            check(fixture.last().contains("friend limit"), "friend-limit result is mapped");
            check(friendLimitCommand.onTabComplete(alice, null, "friend", new String[]{"remove", "c"})
                            .isEmpty(), "remove tab only includes actual friends");

            MemoryRepository incomingRepo = new MemoryRepository();
            incomingRepo.snapshot = new FriendSnapshot(List.of(), List.of(
                    new FriendRequest(bob.getUniqueId(), charlie.getUniqueId(), Instant.EPOCH)));
            FriendCommand incomingCommand = fixture.command(new FriendService(incomingRepo,
                    new FriendLimits(5, 1, 5), FriendRequestPolicy.allowAll(), Clock.systemUTC()));
            run(incomingCommand, alice, "add", "Charlie");
            check(fixture.last().contains("too many incoming"), "incoming-limit result is mapped");

            MemoryRepository outgoingRepo = new MemoryRepository();
            outgoingRepo.snapshot = new FriendSnapshot(List.of(), List.of(
                    new FriendRequest(alice.getUniqueId(), bob.getUniqueId(), Instant.EPOCH)));
            FriendCommand outgoingCommand = fixture.command(new FriendService(outgoingRepo,
                    new FriendLimits(5, 5, 1), FriendRequestPolicy.allowAll(), Clock.systemUTC()));
            run(outgoingCommand, alice, "add", "Charlie");
            check(fixture.last().contains("too many outgoing"), "outgoing-limit result is mapped");

            MemoryRepository offlineFriendRepo = new MemoryRepository();
            offlineFriendRepo.snapshot = new FriendSnapshot(List.of(
                    new Friendship(alice.getUniqueId(), charlie.getUniqueId(), Instant.EPOCH)), List.of());
            FriendCommand offlineFriendCommand = fixture.command(new FriendService(offlineFriendRepo,
                    FriendLimits.defaults(), FriendRequestPolicy.allowAll(), Clock.systemUTC()));
            check(offlineFriendCommand.onTabComplete(alice, null, "friend", new String[]{"remove", "ch"})
                            .equals(List.of("Charlie")),
                    "remove tab may suggest an offline player from the sender's own friends");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("FriendCommandHarness passed " + checks + " checks.");
    }

    private static void run(FriendCommand command, CommandSender sender, String... args) {
        activeFixture.currentSender = sender instanceof Player player ? player.getUniqueId() : null;
        command.onCommand(sender, null, "friend", args);
    }

    private static boolean hasColor(Component component, String text, NamedTextColor color) {
        if (color.equals(component.color()) && PLAIN.serialize(component).contains(text)) return true;
        return component.children().stream().anyMatch(child -> hasColor(child, text, color));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        private final Path directory;
        private final FilePlayerRepository players;
        private final PlayerIdentityService identities;
        private final MessageService messageService;
        private final Map<UUID, Player> online = new HashMap<>();
        private final Map<UUID, List<Component>> messages = new HashMap<>();
        private final MemoryRepository repository = new MemoryRepository();
        private final FriendService friends;
        private FriendRequestDecision policy = FriendRequestDecision.ALLOW;
        private Component latest;
        private UUID currentSender;

        private Fixture(Path directory) throws Exception {
            activeFixture = this;
            this.directory = directory;
            Path playerDirectory = directory.resolve("players");
            players = new FilePlayerRepository(playerDirectory, logger());
            players.initialize();
            identities = new PlayerIdentityService(new PlayerService(players, logger()));
            var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
            constructor.setAccessible(true);
            messageService = constructor.newInstance((Supplier<String>) () -> "");
            friends = new FriendService(repository, FriendLimits::defaults,
                    (sender, recipient) -> policy, Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC));
        }

        private Player player(String name, boolean isOnline, boolean permitted) throws Exception {
            UUID id = UUID.randomUUID();
            Files.writeString(directory.resolve("players").resolve(id + ".yml"),
                    "name: '" + name + "'\nfirst-join: 0\nlast-join: 0\n");
            // The repository owns its runtime name index, rebuilt here only while arranging the fixture.
            players.initialize();
            messages.put(id, new ArrayList<>());
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> name;
                        case "getUniqueId" -> id;
                        case "isOnline" -> isOnline;
                        case "hasPermission" -> permitted;
                        case "sendMessage" -> {
                            capture(messages.get(id), args);
                            latest = messages.get(id).getLast();
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    });
            if (isOnline) online.put(id, player);
            return player;
        }

        private CommandSender console() {
            return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> "Console";
                        case "hasPermission" -> true;
                        case "sendMessage" -> {
                            List<Component> sent = new ArrayList<>();
                            capture(sent, args);
                            if (!sent.isEmpty()) latest = sent.getLast();
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private FriendCommand command() {
            return command(friends);
        }

        private FriendCommand command(FriendService service) {
            return new FriendCommand(service, identities, messageService,
                    new CommandHelpRenderer(messageService), online::values, online::get, logger());
        }

        private String last() {
            return PLAIN.serialize(lastComponent());
        }

        private Component lastComponent() {
            return currentSender == null ? latest : messages.get(currentSender).getLast();
        }

        private String all(UUID id) {
            return String.join("\n", messages.get(id).stream().map(PLAIN::serialize).toList());
        }
    }

    private static void capture(List<Component> target, Object[] arguments) {
        if (arguments != null) for (Object argument : arguments)
            if (argument instanceof Component component) target.add(component);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("FriendCommandHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static final class MemoryRepository implements FriendRepository {
        private FriendSnapshot snapshot = FriendSnapshot.empty();
        private boolean failNext;

        @Override
        public FriendSnapshot initialize() {
            return snapshot;
        }

        @Override
        public void save(FriendSnapshot snapshot) {
            if (failNext) {
                failNext = false;
                throw new FriendRepositoryException("simulated failure");
            }
            this.snapshot = snapshot;
        }
    }
}

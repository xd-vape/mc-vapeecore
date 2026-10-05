package dev.vapee.core.friend;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
            completionUx(new Fixture(directory.resolve("completion-ux")));
            safeRelationArguments(new Fixture(directory.resolve("safe-arguments")));
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

            run(command, alice);
            check(fixture.openedMenus.equals(List.of(alice.getUniqueId())),
                    "/friend without arguments opens the friends menu");
            command.onCommand(alice, null, "friends", new String[0]);
            check(fixture.openedMenus.size() == 2,
                    "/friends alias without arguments opens the same menu");
            run(command, console, "help");
            check(fixture.last().contains("Only players"), "console cannot manage friends");
            run(command, denied, "add", "Bob");
            check(fixture.last().contains("permission"), "permission is enforced");
            run(command, alice, "help");
            check(fixture.last().contains("/friend add") && fixture.last().contains("/friend requests"),
                    "help uses common renderer with all operations");
            run(command, alice, "add");
            check(fixture.last().contains("/friend add <player|uuid>"), "invalid usage is controlled");
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

    private static void safeRelationArguments(Fixture f) throws Exception {
        Player actor = f.player("Actor", true, true);
        Player safe = f.player("Safe", false, true);
        Player spaced = f.player("Two Words", false, true);
        Player unicode = f.player("Two\u2003Words", false, true);
        Player wrong = f.player(actor.getUniqueId().toString(), false, true);
        Player twin = f.player("Twin", false, true);
        f.player("twin", false, true);
        f.player("UnrelatedKnown", false, true);
        for (Player target : List.of(safe, spaced, unicode, wrong, twin)) {
            check(f.friends.sendRequest(actor.getUniqueId(), target.getUniqueId()) == FriendResult.SUCCESS,
                    "seed own request");
            check(f.friends.acceptRequest(target.getUniqueId(), actor.getUniqueId()) == FriendResult.SUCCESS,
                    "seed own friendship");
        }
        FriendCommand c = f.command();
        check(java.util.Set.copyOf(c.onTabComplete(actor, null, "friend", new String[]{"remove", ""}))
                .equals(java.util.Set.of("Safe", spaced.getUniqueId().toString(), unicode.getUniqueId().toString(),
                        wrong.getUniqueId().toString(), twin.getUniqueId().toString())),
                "remove keeps exact friend universe and safe spellings");
        run(c, actor, "remove");
        for (Player target : List.of(spaced, unicode, wrong, twin)) {
            check(hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend remove " + target.getUniqueId()),
                    "friend choice button keeps same safe UUID fallback");
        }
        check(c.onTabComplete(actor, null, "friend", new String[]{"cancel", ""}).isEmpty(),
                "formatter cannot leak friends into outgoing requests");
    }

    private static void completionUx(Fixture f) throws Exception {
        Player actor = f.player("UxActor", true, true);
        Player recipient = f.player("UxRecipient", true, true);
        Player twin = f.player("TwinUx", true, true);
        f.player("twinux", false, true);
        Player unsafe = f.player("<click:run_command:'/op @s'>x</click>", true, true);
        FriendCommand c = f.command();
        int[] callbacks = {0};
        f.friends.addRelationshipListener((first, second) -> callbacks[0]++);
        for (String action : List.of("accept", "deny", "cancel", "remove")) {
            run(c, actor, action);
            check(f.last().equals(action.equals("remove") ? "You do not have any friends yet."
                    : action.equals("cancel") ? "You have no pending outgoing friend requests."
                    : "You have no pending friend requests."), "action-specific empty " + action);
        }
        for (String action : List.of("add", "accept", "deny", "cancel", "remove", "list", "requests", "help")) {
            run(c, actor, action, "one", "extra");
            check(f.last().equals("Usage: /friend " + action
                    + (List.of("list", "requests", "help").contains(action) ? "" : " <player|uuid>")),
                    "concrete extra-argument usage " + action);
        }
        run(c, actor, "ADD");
        check(f.all(actor.getUniqueId()).contains("Missing player.")
                && f.last().contains("/friend add <player|uuid>"), "add missing player explains syntax");
        String attack = "<click:run_command:'/op @s'>x</click>";
        run(c, actor, attack);
        check(f.last().contains("Unknown friend command: " + attack)
                && f.last().contains("Use /friend help to view available commands.")
                && clicks(f.lastComponent()).isEmpty(), "unknown literal input creates no click event");
        check(c.onTabComplete(actor, null, "friends", new String[]{"add", ""})
                .contains(twin.getUniqueId().toString()), "ambiguous online add completion uses UUID");
        run(c, twin, "add", "UxRecipient");
        check(hasSuggestion(f.messages.get(recipient.getUniqueId()), "/friend accept " + twin.getUniqueId())
                && hasSuggestion(f.messages.get(recipient.getUniqueId()), "/friend deny " + twin.getUniqueId()),
                "ambiguous actor notification has UUID accept and deny");
        run(c, actor, "add", "UxRecipient");
        check(hasSuggestion(f.messages.get(recipient.getUniqueId()), "/friend accept UxActor")
                && callbacks[0] == 0, "notification uses unique actor name without relationship callback");
        for (String action : List.of("accept", "deny")) {
            f.messages.get(recipient.getUniqueId()).clear();
            run(c, recipient, action);
            check(f.all(recipient.getUniqueId()).contains("UxActor")
                    && f.all(recipient.getUniqueId()).contains("TwinUx")
                    && hasSuggestion(f.messages.get(recipient.getUniqueId()), "/friend accept UxActor")
                    && hasSuggestion(f.messages.get(recipient.getUniqueId()), "/friend deny " + twin.getUniqueId()),
                    action + " without target lists multiple actual incoming choices");
            check(f.messages.get(recipient.getUniqueId()).stream().flatMap(m -> clicks(m).stream())
                    .allMatch(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND), "choices never run commands");
        }
        run(c, actor, "cancel");
        check(hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend cancel UxRecipient"), "outgoing cancel choice");
        check(c.onTabComplete(recipient, null, "friends", new String[]{"deny", ""})
                .contains(twin.getUniqueId().toString()), "incoming ambiguity completion safe");
        run(c, recipient, "accept", "UxActor");
        check(callbacks[0] == 1, "accept preserves live relationship callback");
        run(c, actor, "remove");
        check(hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend remove UxRecipient"), "remove lists actual friends");
        run(c, actor, "remove", "UxRecipient");
        check(callbacks[0] == 2, "remove preserves callback");
        run(c, recipient, "add", "UxActor");
        run(c, actor, "add", "UxRecipient");
        check(callbacks[0] == 3, "auto-accept preserves callback");
        run(c, actor, "remove", "UxRecipient");
        run(c, actor, "add", unsafe.getUniqueId().toString());
        check(f.all(unsafe.getUniqueId()).contains("UxActor")
                && clicks(f.messages.get(unsafe.getUniqueId()).getLast()).stream()
                .allMatch(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND), "unsafe-looking identity notification safe");
        run(c, actor, "cancel");
        check(hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend cancel " + unsafe.getUniqueId()),
                "whitespace identity cannot inject command arguments");
        run(c, actor, "cancel", unsafe.getUniqueId().toString());
        run(c, recipient, "deny", twin.getUniqueId().toString());
        f.messages.get(recipient.getUniqueId()).clear();
        run(c, unsafe, "add", "UxRecipient");
        check(f.all(recipient.getUniqueId()).contains(attack + " sent you a friend request.")
                && hasSuggestion(f.messages.get(recipient.getUniqueId()), "/friend accept " + unsafe.getUniqueId())
                && clicks(f.messages.get(recipient.getUniqueId()).getLast()).stream()
                .allMatch(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND),
                "literal attacker actor notification uses safe UUID suggest actions");
        run(c, recipient, "deny", unsafe.getUniqueId().toString());
        Player uuidNamed = f.player(actor.getUniqueId().toString(), true, true);
        check(FriendMessages.commandArgument(f.identities, uuidNamed.getUniqueId()).equals(uuidNamed.getUniqueId().toString()),
                "name that resolves to another UUID never becomes a wrong-target suggestion");

        UUID missing = UUID.randomUUID();
        MemoryRepository r = new MemoryRepository();
        r.snapshot = new FriendSnapshot(List.of(new Friendship(actor.getUniqueId(), missing, Instant.EPOCH)), List.of());
        FriendService stale = new FriendService(r, FriendLimits.defaults(), FriendRequestPolicy.allowAll(), Clock.systemUTC());
        FriendCommand staleCommand = f.command(stale);
        run(staleCommand, actor, "remove");
        check(hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend remove " + missing)
                && staleCommand.onTabComplete(actor, null, "friends", new String[]{"remove", ""})
                .equals(List.of(missing.toString())), "unknown stored identity receives UUID choice and completion");
        run(staleCommand, actor, "remove", missing.toString());
        check(stale.getFriends(actor.getUniqueId()).isEmpty(), "unknown relation UUID suggestion remains executable");
        UUID missingOutgoing = UUID.randomUUID();
        MemoryRepository pendingRepository = new MemoryRepository();
        pendingRepository.snapshot = new FriendSnapshot(List.of(), List.of(
                new FriendRequest(missing, actor.getUniqueId(), Instant.EPOCH),
                new FriendRequest(actor.getUniqueId(), missingOutgoing, Instant.EPOCH)));
        FriendService pending = new FriendService(pendingRepository, FriendLimits.defaults(), FriendRequestPolicy.allowAll(), Clock.systemUTC());
        FriendCommand pendingCommand = f.command(pending);
        run(pendingCommand, actor, "accept");
        check(hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend accept " + missing)
                && hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend deny " + missing),
                "pending unknown identities have executable UUID accept and deny choices");
        run(pendingCommand, actor, "cancel");
        check(hasSuggestion(f.messages.get(actor.getUniqueId()), "/friend cancel " + missingOutgoing),
                "outgoing unknown identity has an executable UUID cancel choice");
        run(pendingCommand, actor, "deny", missing.toString());
        run(pendingCommand, actor, "cancel", missingOutgoing.toString());
        check(pending.getIncomingRequests(actor.getUniqueId()).isEmpty()
                && pending.getOutgoingRequests(actor.getUniqueId()).isEmpty(), "unknown relation UUID actions preserve service semantics");
        Player denied = f.player("UxDenied", true, false);
        c.onCommand(denied, null, "friends", new String[]{"accept"});
        check(f.all(denied.getUniqueId()).contains("permission")
                && c.onTabComplete(denied, null, "friends", new String[]{"accept", ""}).isEmpty(), "alias denied without target leak");
    }

    private static List<ClickEvent> clicks(Component c) {
        List<ClickEvent> result = new ArrayList<>();
        if (c.clickEvent() != null) result.add(c.clickEvent());
        c.children().forEach(child -> result.addAll(clicks(child)));
        return result;
    }
    private static boolean hasSuggestion(List<Component> messages, String command) {
        return messages.stream().flatMap(m -> clicks(m).stream()).anyMatch(click ->
                click.action() == ClickEvent.Action.SUGGEST_COMMAND && click.value().equals(command));
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
        private final List<UUID> openedMenus = new ArrayList<>();
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
                    "name: '" + name.replace("'", "''") + "'\nfirst-join: 0\nlast-join: 0\n");
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
                    new CommandHelpRenderer(messageService), online::values, online::get, logger(),
                    player -> openedMenus.add(player.getUniqueId()));
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

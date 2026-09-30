package dev.vapee.core.privatemessage;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.privatemessage.command.MessageCommand;
import dev.vapee.core.privatemessage.command.ReplyCommand;
import dev.vapee.core.privatemessage.config.PrivateMessageConfig;
import dev.vapee.core.social.IgnoreResult;
import dev.vapee.core.social.SocialService;
import dev.vapee.core.social.command.IgnoreCommand;
import dev.vapee.core.social.command.IgnoreListCommand;
import dev.vapee.core.social.command.UnignoreCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PrivateMessageSocialHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private PrivateMessageSocialHarness() {
    }

    public static void main(String[] args) throws Exception {
        Fixture fixture = new Fixture();
        fixture.testPrivateMessageAndReply();
        fixture.testIgnoreBlocking();
        fixture.testCommandUsageAndCompletion();
        fixture.testMessageCommandBoundaries();
        fixture.testSocialCommandBoundaries();
        System.out.println("PrivateMessageSocialHarness passed " + checks + " checks.");
    }

    private static final class Fixture {

        private final MemoryRepository repository = new MemoryRepository();
        private final PlayerService players = new PlayerService(repository, logger());
        private final SocialService social = new SocialService(players, logger());
        private final PlayerSettingsService settings = new PlayerSettingsService(players);
        private final Map<UUID, PlayerFixture> online = new HashMap<>();
        private final PlayerFixture alice = player("Alice");
        private final PlayerFixture bob = player("Bob");
        private final Server server = server();
        private final PrivateMessageService privateMessages = new PrivateMessageService(
                server,
                settings,
                social,
                MiniMessage.miniMessage()::deserialize,
                logger(),
                Path.of("private-messages.yml"),
                new PrivateMessageConfig.State(
                        true,
                        PrivateMessageConfig.DEFAULT_OUTGOING_FORMAT,
                        PrivateMessageConfig.DEFAULT_INCOMING_FORMAT
                )
        );
        private final MessageService messages = messageService();

        private void testPrivateMessageAndReply() {
            check(privateMessages.send(alice.player(), bob.player(), "<red>Hello</red>")
                            == PrivateMessageResult.SUCCESS,
                    "private message succeeds");
            check(alice.text().contains("<red>Hello</red>") && bob.text().contains("<red>Hello</red>"),
                    "private message body is rendered as safe plain text");
            alice.clear();
            bob.clear();
            check(privateMessages.reply(bob.player(), "Reply") == PrivateMessageResult.SUCCESS,
                    "reply uses the existing conversation");
            check(alice.text().contains("Reply") && bob.text().contains("Reply"),
                    "reply reaches both conversation participants");
        }

        private void testIgnoreBlocking() {
            check(social.ignore(alice.id(), bob.id()) == IgnoreResult.SUCCESS,
                    "social ignore succeeds and persists");
            check(privateMessages.send(alice.player(), bob.player(), "blocked")
                            == PrivateMessageResult.SENDER_IGNORES_RECIPIENT,
                    "sender-side ignore blocks private messages");
            check(social.unignore(alice.id(), bob.id()) == IgnoreResult.SUCCESS,
                    "social unignore succeeds and persists");
            check(social.ignore(bob.id(), alice.id()) == IgnoreResult.SUCCESS,
                    "reverse ignore succeeds");
            check(privateMessages.send(alice.player(), bob.player(), "blocked")
                            == PrivateMessageResult.RECIPIENT_IGNORES_SENDER,
                    "recipient-side ignore blocks private messages");
            check(social.unignore(bob.id(), alice.id()) == IgnoreResult.SUCCESS,
                    "reverse ignore can be removed");
            check(repository.saves >= 6, "social mutations use the existing persistence path");
        }

        private void testCommandUsageAndCompletion() {
            MessageCommand message = new MessageCommand(server, privateMessages, messages, logger());
            ReplyCommand reply = new ReplyCommand(privateMessages, messages, logger());
            IgnoreCommand ignore = new IgnoreCommand(server, social, messages, logger());
            UnignoreCommand unignore = new UnignoreCommand(social, messages, logger());
            IgnoreListCommand ignoreList = new IgnoreListCommand(social, messages);

            alice.clear();
            check(message.onCommand(alice.player(), null, "msg", new String[0]),
                    "missing msg arguments are handled");
            check(alice.text().contains("Invalid usage.")
                            && alice.text().contains("/msg <player> <message>"),
                    "msg reports literal concise syntax");
            check(message.onTabComplete(alice.player(), null, "msg", new String[]{"b"})
                            .equals(List.of("Bob")),
                    "msg completes online players case-insensitively");
            check(!message.onTabComplete(alice.player(), null, "msg", new String[]{""}).contains("Alice"),
                    "msg completion excludes self");

            alice.clear();
            check(reply.onCommand(alice.player(), null, "r", new String[0]),
                    "missing reply arguments are handled");
            check(alice.text().contains("/reply <message>"), "reply reports concise syntax");
            check(reply.onTabComplete(alice.player(), null, "reply", new String[]{""}).isEmpty(),
                    "reply exposes no irrelevant completions");

            alice.clear();
            check(ignore.onCommand(alice.player(), null, "ignore", new String[0]),
                    "missing ignore arguments are handled");
            check(alice.text().contains("/ignore <player>"), "ignore reports concise syntax");

            alice.clear();
            check(unignore.onCommand(alice.player(), null, "unignore", new String[0]),
                    "missing unignore arguments are handled");
            check(alice.text().contains("/unignore <player|uuid>"), "unignore reports concise syntax");

            alice.clear();
            check(ignoreList.onCommand(alice.player(), null, "ignorelist", new String[]{"extra"}),
                    "ignorelist extra arguments are handled");
            check(alice.text().contains("/ignorelist"), "ignorelist reports concise syntax");
            check(ignoreList.onTabComplete(alice.player(), null, "ignorelist", new String[]{""}).isEmpty(),
                    "ignorelist exposes no irrelevant completions");
        }

        private PlayerFixture player(String name) {
            UUID id = UUID.randomUUID();
            players.loadPlayer(id, name);
            PlayerFixture fixture = new PlayerFixture(id, name);
            online.put(id, fixture);
            social.activatePlayer(id);
            return fixture;
        }

        private void testMessageCommandBoundaries() {
            MessageCommand msg = new MessageCommand(server, privateMessages, messages, logger());
            ReplyCommand reply = new ReplyCommand(privateMessages, messages, logger());
            alice.permitted = false;
            assertCommand(msg, alice, "msg", "permission", "Bob", "denied");
            assertCommand(reply, alice, "r", "permission", "denied");
            check(msg.onTabComplete(alice.player(), null, "msg", new String[]{""}).isEmpty(),
                    "denied msg completion reveals no online names");
            alice.permitted = true;
            assertCommand(msg, alice, "msg", "/msg <player> <message>", "Bob");
            assertCommand(msg, alice, "msg", "/msg <player> <message>", "Bob", " ");
            assertCommand(reply, alice, "r", "/reply <message>", " ");
            assertCommand(msg, alice, "msg", "not online", "Bo", "partial");
            assertCommand(msg, alice, "msg", "not online", bob.id().toString(), "uuid");
            assertCommand(msg, alice, "msg", "not online", "@a", "selector");
            assertCommand(msg, alice, "msg", "yourself", "aLiCe", "self");
            String attack = "<click:run_command:'/op @s'>literal</click>";
            bob.clear();
            assertCommand(msg, alice, "msg", attack, "bOb", attack);
            check(bob.text().contains(attack) && bob.received.stream().noneMatch(PrivateMessageSocialHarness::hasClick),
                    "command message bodies remain literal without injected click events");
            assertCommand(reply, bob, "r", "alias reply", "alias", "reply");
            check(alice.text().contains("alias reply"), "r alias delivers to the previous participant");
            privateMessages.clearConversations();
            assertCommand(reply, alice, "reply", "no player to reply", "empty");
            privateMessages.send(alice.player(), bob.player(), "establish");
            online.remove(bob.id());
            assertCommand(reply, alice, "r", "no longer online", "offline");
            assertCommand(msg, alice, "msg", "not online", "Bob", "offline");
            online.put(bob.id(), bob);
            settings.setPrivateMessagesEnabled(bob.id(), false);
            assertCommand(msg, alice, "msg", "not accepting", "Bob", "disabled setting");
            settings.setPrivateMessagesEnabled(bob.id(), true);
            social.ignore(alice.id(), bob.id());
            assertCommand(msg, alice, "msg", "ignoring that player", "Bob", "blocked");
            social.unignore(alice.id(), bob.id());
            social.ignore(bob.id(), alice.id());
            assertCommand(msg, alice, "msg", "not accepting", "Bob", "reverse blocked");
            social.unignore(bob.id(), alice.id());
            PrivateMessageService.RuntimeState original = privateMessages.getState();
            privateMessages.applyState(privateMessages.prepareState(new PrivateMessageConfig.State(false,
                    PrivateMessageConfig.DEFAULT_OUTGOING_FORMAT, PrivateMessageConfig.DEFAULT_INCOMING_FORMAT)));
            assertCommand(msg, alice, "msg", "currently disabled", "Bob", "disabled");
            assertCommand(reply, alice, "r", "currently disabled", "disabled");
            privateMessages.applyState(original);
            players.unloadPlayer(bob.id());
            assertCommand(msg, alice, "msg", "That player's profile is not available", "Bob", "unloaded");
            players.loadPlayer(bob.id(), bob.name());
            players.unloadPlayer(alice.id());
            assertCommand(msg, alice, "msg", "Your player profile is not available", "Bob", "unloaded");
            players.loadPlayer(alice.id(), alice.name());
            List<Component> consoleOutput = new ArrayList<>();
            CommandSender console = console(consoleOutput);
            check(msg.onCommand(console, null, "msg", new String[]{"Bob", "test"})
                    && reply.onCommand(console, null, "r", new String[]{"test"}), "PM console calls are handled");
            check(consoleOutput.size() == 2 && consoleOutput.stream().allMatch(c -> PLAIN.serialize(c).contains("Only players")),
                    "msg and reply are explicitly player-only");
            PlayerFixture collision = player("bOB");
            assertCommand(msg, alice, "msg", "not online", "Bob", "ambiguous");
            online.remove(collision.id());
        }

        private void testSocialCommandBoundaries() {
            IgnoreCommand ignore = new IgnoreCommand(server, social, messages, logger());
            UnignoreCommand unignore = new UnignoreCommand(social, messages, logger());
            IgnoreListCommand list = new IgnoreListCommand(social, messages);
            int[] callbacks = {0};
            social.addRelationshipListener((owner, target) -> callbacks[0]++);
            assertCommand(list, alice, "ignorelist", "not ignoring any players");
            alice.permitted = false;
            for (TabExecutor command : List.of(ignore, unignore, list)) {
                assertCommand(command, alice, "social", "permission", "Bob");
                check(command.onTabComplete(alice.player(), null, "social", new String[]{""}).isEmpty(),
                        "denied social tabs leak no relationship names");
            }
            alice.permitted = true;
            assertCommand(ignore, alice, "ignore", "/ignore <player>", "Bob", "extra");
            assertCommand(unignore, alice, "unignore", "/unignore <player|uuid>", "Bob", "extra");
            assertCommand(ignore, alice, "ignore", "yourself", "ALICE");
            assertCommand(ignore, alice, "ignore", "not online", "Bo");
            assertCommand(ignore, alice, "ignore", "not online", bob.id().toString());
            check(!ignore.onTabComplete(alice.player(), null, "ignore", new String[]{""}).contains("Alice"),
                    "ignore completion excludes self");
            repository.fail = true;
            assertCommand(ignore, alice, "ignore", "could not be saved", "Bob");
            check(!social.isIgnoring(alice.id(), bob.id()) && callbacks[0] == 0,
                    "failed ignore preserves snapshot and emits no callback");
            repository.fail = false;
            assertCommand(ignore, alice, "ignore", "now ignoring Bob", "bOb");
            check(callbacks[0] == 1 && social.isIgnoring(alice.id(), bob.id()), "ignore publishes once after saving");
            assertCommand(ignore, alice, "ignore", "already ignored", "Bob");
            check(!ignore.onTabComplete(alice.player(), null, "ignore", new String[]{""}).contains("Bob"),
                    "ignore completion excludes existing ignored players");
            repository.fail = true;
            assertCommand(unignore, alice, "unignore", "could not be saved", "bOb");
            check(social.isIgnoring(alice.id(), bob.id()) && callbacks[0] == 1,
                    "failed unignore preserves snapshot and emits no callback");
            repository.fail = false;
            PlayerFixture duplicate = player("bOB");
            social.ignore(alice.id(), duplicate.id());
            assertCommand(unignore, alice, "unignore", "ambiguous", "Bob");
            check(social.isIgnoring(alice.id(), bob.id()) && social.isIgnoring(alice.id(), duplicate.id()),
                    "ambiguous ignored names mutate neither relationship");
            List<String> tabs = unignore.onTabComplete(alice.player(), null, "unignore", new String[]{""});
            check(tabs.contains(bob.id().toString()) && tabs.contains(duplicate.id().toString()) && !tabs.contains("Bob"),
                    "ambiguous unignore completions use both UUIDs");
            assertCommand(unignore, alice, "unignore", "no longer ignoring", bob.id().toString());
            online.remove(duplicate.id());
            assertCommand(unignore, alice, "unignore", "no longer ignoring", "BOB");
            UUID unknown = UUID.randomUUID();
            social.ignore(alice.id(), unknown);
            check(unignore.onTabComplete(alice.player(), null, "unignore", new String[]{""}).contains(unknown.toString()),
                    "unknown ignored identity completes as UUID");
            assertCommand(unignore, alice, "unignore", "not ignored", UUID.randomUUID().toString());
            assertCommand(unignore, alice, "unignore", "not ignored", "1-1-1-1-1");
            PlayerFixture zulu = player("Zulu");
            PlayerFixture alpha = player("alpha");
            social.ignore(alice.id(), zulu.id());
            social.ignore(alice.id(), alpha.id());
            assertCommand(list, alice, "ignorelist", "Ignored players (3)");
            String sorted = alice.text();
            check(sorted.indexOf("alpha") < sorted.indexOf("Zulu") && sorted.indexOf("Zulu") < sorted.indexOf(unknown.toString()),
                    "ignorelist orders names case-insensitively before unknown UUIDs");
            assertCommand(unignore, alice, "unignore", "no longer ignoring", unknown.toString());
            assertCommand(unignore, alice, "unignore", "no longer ignoring", "alpha");
            assertCommand(unignore, alice, "unignore", "no longer ignoring", "Zulu");
            String unsafe = "<click:run_command:'/op @s'>name</click>";
            PlayerFixture injection = player(unsafe);
            assertCommand(ignore, alice, "ignore", unsafe, unsafe);
            check(alice.received.stream().noneMatch(PrivateMessageSocialHarness::hasClick), "ignored name cannot inject clicks");
            check(unignore.onTabComplete(alice.player(), null, "unignore", new String[]{""}).contains(injection.id().toString()),
                    "names containing spaces complete as executable UUID arguments");
            assertCommand(unignore, alice, "unignore", "no longer ignoring", injection.id().toString());
            List<Component> consoleOutput = new ArrayList<>();
            CommandSender console = console(consoleOutput);
            for (TabExecutor command : List.of(ignore, unignore, list)) command.onCommand(console, null, "social", new String[0]);
            check(consoleOutput.size() == 3 && consoleOutput.stream().allMatch(c -> PLAIN.serialize(c).contains("Only players")),
                    "all social commands reject console use");
        }

        private Server server() {
            return proxy(Server.class, (method, arguments) -> switch (method.getName()) {
                case "getPlayer" -> Optional.ofNullable(online.get((UUID) arguments[0])).map(PlayerFixture::player).orElse(null);
                case "getPlayerExact" -> online.values().stream()
                        .filter(player -> player.name().equals(arguments[0]))
                        .map(PlayerFixture::player)
                        .findFirst()
                        .orElse(null);
                case "getOnlinePlayers" -> online.values().stream().map(PlayerFixture::player).toList();
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class PlayerFixture {

        private final UUID id;
        private final String name;
        private final List<Component> received = new ArrayList<>();
        private final Player player;
        private boolean permitted = true;

        private PlayerFixture(UUID id, String name) {
            this.id = id;
            this.name = name;
            this.player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "displayName" -> Component.text(name);
                case "isOnline" -> true;
                case "hasPermission" -> permitted;
                case "sendMessage" -> {
                    if (arguments != null) {
                        for (Object argument : arguments) {
                            if (argument instanceof Component component) {
                                received.add(component);
                                break;
                            }
                        }
                    }
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
        }

        private UUID id() {
            return id;
        }

        private String name() {
            return name;
        }

        private Player player() {
            return player;
        }

        private String text() {
            return received.stream().map(PLAIN::serialize).reduce("", (left, right) -> left + "\n" + right);
        }

        private void clear() {
            received.clear();
        }
    }

    private static MessageService messageService() {
        try {
            Constructor<MessageService> constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
            constructor.setAccessible(true);
            return constructor.newInstance((Supplier<String>) () -> "<gray>[VapeeCore]</gray> ");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create message service test seam", exception);
        }
    }

    private static void assertCommand(TabExecutor command, PlayerFixture sender, String label, String expected, String... args) {
        sender.clear();
        check(command.onCommand(sender.player(), null, label, args) && sender.text().contains(expected),
                label + " handles " + List.of(args) + " with " + expected + "; received: " + sender.text());
    }

    private static boolean hasClick(Component component) {
        return component.clickEvent() != null || component.children().stream().anyMatch(PrivateMessageSocialHarness::hasClick);
    }

    private static CommandSender console(List<Component> output) {
        return proxy(CommandSender.class, (method, args) -> switch (method.getName()) {
            case "hasPermission" -> true;
            case "sendMessage" -> { for (Object arg : args) if (arg instanceof Component c) output.add(c); yield null; }
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                };
            }
            return handler.invoke(method, arguments == null ? new Object[0] : arguments);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (Collection.class.isAssignableFrom(type)) {
                return List.of();
            }
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
    private interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }

    private static final class MemoryRepository implements PlayerRepository {

        private final Map<UUID, CorePlayer> stored = new HashMap<>();
        private int saves;
        private boolean fail;

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(stored.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            if (fail) throw new IllegalStateException("Injected save failure");
            stored.put(player.getUniqueId(), player);
            saves++;
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return stored.containsKey(uniqueId);
        }
    }
}

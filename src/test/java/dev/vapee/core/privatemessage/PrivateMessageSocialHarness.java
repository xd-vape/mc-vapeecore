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

        private Server server() {
            return proxy(Server.class, (method, arguments) -> switch (method.getName()) {
                case "getPlayer" -> online.get((UUID) arguments[0]).player();
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

        private PlayerFixture(UUID id, String name) {
            this.id = id;
            this.name = name;
            this.player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "displayName" -> Component.text(name);
                case "isOnline", "hasPermission" -> true;
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

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(stored.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            stored.put(player.getUniqueId(), player);
            saves++;
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return stored.containsKey(uniqueId);
        }
    }
}

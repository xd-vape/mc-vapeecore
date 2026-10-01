package dev.vapee.core.presence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class FriendPresenceNotifierHarness {
    private static int checks;

    public static void main(String[] args) {
        UUID subject = id("subject");
        UUID first = id("first");
        UUID disabled = id("disabled");
        UUID offline = id("offline");
        UUID missingPlayer = id("missing-player");
        UUID missingSetting = id("missing-setting");
        UUID ignored = id("ignored");
        UUID reverseIgnored = id("reverse-ignored");
        UUID last = id("last");
        List<UUID> friends = List.of(first, disabled, offline, missingPlayer,
                missingSetting, ignored, reverseIgnored, last);
        Map<UUID, Player> players = new HashMap<>();
        players.put(first, player(first, true));
        players.put(disabled, player(disabled, true));
        players.put(offline, player(offline, false));
        players.put(missingSetting, player(missingSetting, true));
        players.put(ignored, player(ignored, true));
        players.put(reverseIgnored, player(reverseIgnored, true));
        players.put(last, player(last, true));
        List<UUID> recipients = new ArrayList<>();
        List<Component> messages = new ArrayList<>();
        CapturingHandler logs = new CapturingHandler();
        Logger logger = logger(logs);

        FriendPresenceNotifier notifier = new FriendPresenceNotifier(
                ignoredSubject -> friends,
                players::get,
                recipient -> recipient.equals(missingSetting)
                        ? Optional.empty() : Optional.of(!recipient.equals(disabled)),
                (owner, target) -> owner.equals(ignored) && target.equals(subject)
                        || owner.equals(subject) && target.equals(reverseIgnored),
                (recipient, message) -> {
                    recipients.add(recipient.getUniqueId());
                    messages.add(message);
                },
                logger
        );
        notifier.notifyStatus(subject, "Alice", PresenceStatus.ONLINE);
        check(recipients.equals(List.of(first, last)),
                "only online opted-in non-ignored friends receive notifications in source order");
        check(messages.stream().map(FriendPresenceNotifierHarness::plain).toList()
                        .equals(List.of("Friend Alice is now online.", "Friend Alice is now online.")),
                "online message is exact literal Adventure text");
        check(messages.getFirst().color().equals(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        && messages.getFirst().children().get(0).color()
                        .equals(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        && messages.getFirst().children().get(1).color()
                        .equals(net.kyori.adventure.text.format.NamedTextColor.GREEN),
                "online component uses calm gray, aqua and green colors");

        recipients.clear();
        messages.clear();
        notifier.notifyStatus(subject, "<red>Alice</red>", PresenceStatus.ONLINE);
        check(plain(messages.getFirst()).equals("Friend <red>Alice</red> is now online."),
                "subject name is literal and never parsed as MiniMessage");

        recipients.clear();
        messages.clear();
        notifier.notifyStatus(subject, "Alice", PresenceStatus.OFFLINE);
        check(recipients.equals(List.of(first, last))
                        && messages.stream().allMatch(message -> plain(message).equals("Friend Alice went offline.")),
                "offline message is exact and uses the same filters");

        List<UUID> continued = new ArrayList<>();
        FriendPresenceNotifier isolated = new FriendPresenceNotifier(
                ignoredSubject -> List.of(first, disabled, last),
                players::get,
                recipient -> {
                    if (recipient.equals(disabled)) throw new IllegalStateException("settings failure");
                    return Optional.of(true);
                },
                (owner, target) -> false,
                (recipient, message) -> {
                    if (recipient.getUniqueId().equals(first)) throw new IllegalStateException("send failure");
                    continued.add(recipient.getUniqueId());
                },
                logger
        );
        isolated.notifyStatus(subject, "Alice", PresenceStatus.ONLINE);
        check(continued.equals(List.of(last)), "recipient failures are isolated and later friends continue");
        check(logs.messages.stream().anyMatch(message -> message.contains(subject.toString())
                        && message.contains(first.toString()))
                        && logs.messages.stream().anyMatch(message -> message.contains(disabled.toString())),
                "recipient failures log subject and recipient UUIDs");

        int before = logs.messages.size();
        FriendPresenceNotifier failingLookup = new FriendPresenceNotifier(
                ignoredSubject -> { throw new IllegalStateException("friend lookup failure"); },
                players::get,
                recipient -> Optional.of(true),
                (owner, target) -> false,
                (recipient, message) -> { throw new AssertionError("must not send"); },
                logger
        );
        failingLookup.notifyStatus(subject, "Alice", PresenceStatus.ONLINE);
        check(logs.messages.size() == before + 1
                        && logs.messages.getLast().contains(subject.toString()),
                "friend lookup failure is controlled and logged");
        System.out.println("FriendPresenceNotifierHarness passed " + checks + " checks.");
    }

    private static Player player(UUID id, boolean online) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isOnline" -> online;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "PresencePlayer[" + id + "]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
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

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Logger logger(CapturingHandler handler) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();
        @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
        @Override public void flush() { }
        @Override public void close() { }
    }
}

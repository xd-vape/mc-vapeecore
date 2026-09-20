package dev.vapee.core.rank;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.rank.command.RanksCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class RanksCommandHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private RanksCommandHarness() {
    }

    public static void main(String[] args) {
        Fixture fixture = new Fixture();
        RanksCommand command = new RanksCommand(
                fixture.rankService,
                () -> "Vapee Community",
                fixture.messageService
        );
        Player player = fixture.player("Player", "vip");

        command.onCommand(player, null, "ranks", new String[0]);
        String playerList = fixture.last();
        check(playerList.contains("Vapee Community • Ranks")
                        && playerList.indexOf("1. Member") < playerList.indexOf("2. VIP")
                        && playerList.indexOf("2. VIP") < playerList.indexOf("3. Premium"),
                "/ranks shows the configured track in LuckPerms order");
        check(playerList.contains("Standard community rank.")
                        && playerList.contains("Supporter rank."),
                "/ranks includes available descriptions");
        check(!playerList.contains("Premium\n   "),
                "/ranks omits the description line when meta is missing");
        check(playerList.contains("2. VIP • You"),
                "player sender receives a discreet current-rank marker");

        CommandSender console = fixture.console();
        command.onCommand(console, null, "ranks", new String[0]);
        check(!fixture.last().contains("• You") && fixture.last().contains("2. VIP"),
                "console receives the rank list without a player marker");

        fixture.trackGroups = null;
        command.onCommand(console, null, "ranks", new String[0]);
        check(fixture.last().contains("configured LuckPerms rank track 'ranks' does not exist"),
                "missing track returns a controlled response");
        fixture.trackGroups = List.of();
        command.onCommand(console, null, "ranks", new String[0]);
        check(fixture.last().endsWith("No public ranks are configured yet."),
                "empty track returns a controlled response");
        command.onCommand(console, null, "ranks", new String[]{"extra"});
        check(fixture.last().contains("Use: /ranks"),
                "/ranks rejects arguments with concise usage");

        System.out.println("RanksCommandHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture implements RankService.Gateway {

        private final Map<UUID, String> primaryGroups = new HashMap<>();
        private final List<Component> messages = new ArrayList<>();
        private final RankService rankService = new RankService(() -> "ranks", this);
        private final MessageService messageService = messages();
        private List<String> trackGroups = List.of("default", "vip", "premium");

        private Player player(String name, String groupId) {
            UUID uniqueId = UUID.randomUUID();
            primaryGroups.put(uniqueId, groupId);
            return (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, arguments) -> invokeSender(
                            proxy, method.getName(), method.getReturnType(), arguments, name, uniqueId
                    )
            );
        }

        private CommandSender console() {
            return (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    (proxy, method, arguments) -> invokeSender(
                            proxy, method.getName(), method.getReturnType(), arguments, "Console", null
                    )
            );
        }

        private Object invokeSender(
                Object proxy,
                String name,
                Class<?> returnType,
                Object[] arguments,
                String senderName,
                UUID uniqueId
        ) {
            if (name.equals("getName")) return senderName;
            if (name.equals("getUniqueId")) return uniqueId;
            if (name.equals("hasPermission")) return true;
            if (name.equals("sendMessage") && arguments != null) {
                for (Object argument : arguments) {
                    if (argument instanceof Component component) {
                        messages.add(component);
                        break;
                    }
                }
                return null;
            }
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == arguments[0];
            if (name.equals("toString")) return senderName;
            return defaultValue(returnType);
        }

        private String last() {
            return PLAIN.serialize(messages.getLast());
        }

        @Override
        public Optional<String> getPrimaryGroup(UUID uniqueId) {
            return Optional.ofNullable(primaryGroups.get(uniqueId));
        }

        @Override
        public Optional<LuckPermsService.GroupInformation> getGroupInformation(String groupId) {
            return switch (groupId) {
                case "default" -> Optional.of(group(
                        "default", "Member", "Standard community rank."
                ));
                case "vip" -> Optional.of(group("vip", "VIP", "Supporter rank."));
                case "premium" -> Optional.of(group("premium", "Premium", null));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<List<String>> getTrackGroups(String trackName) {
            return Optional.ofNullable(trackGroups);
        }

        private static LuckPermsService.GroupInformation group(
                String id,
                String displayName,
                String description
        ) {
            return new LuckPermsService.GroupInformation(
                    id,
                    Optional.of(displayName),
                    Optional.ofNullable(description),
                    OptionalInt.empty()
            );
        }
    }

    private static MessageService messages() {
        try {
            var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
            constructor.setAccessible(true);
            return constructor.newInstance((Supplier<String>) () -> "");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
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
}

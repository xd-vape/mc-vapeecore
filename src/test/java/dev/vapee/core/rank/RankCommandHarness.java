package dev.vapee.core.rank;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.rank.command.RankCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class RankCommandHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private RankCommandHarness() {
    }

    public static void main(String[] args) throws Exception {
        Fixture fixture = new Fixture();
        Player self = fixture.player("Self", "default", Set.of(RankCommand.PERMISSION));
        Player other = fixture.player("Other", "vip", Set.of(RankCommand.PERMISSION));
        RankCommand command = new RankCommand(
                fixture.rankService,
                () -> "Vapee Community",
                fixture.messageService,
                fixture.players::get,
                fixture::onlinePlayers
        );

        command.onCommand(self, null, "rank", new String[0]);
        check(fixture.last().contains("Vapee Community • Rank")
                        && fixture.last().contains("Player: Self")
                        && fixture.last().contains("Rank: Member")
                        && fixture.last().contains("Group: default"),
                "/rank shows the player's own rank");
        check(hasColoredText(fixture.lastComponent(), "Member", NamedTextColor.GRAY),
                "/rank renders the friendly self rank in its configured color");

        command.onCommand(self, null, "rank", new String[]{"Other"});
        check(fixture.last().contains("Player: Other")
                        && fixture.last().contains("Rank: VIP")
                        && fixture.last().contains("Description: Supporter rank."),
                "/rank <onlinePlayer> shows the target rank");
        check(hasColoredText(fixture.lastComponent(), "VIP", NamedTextColor.GOLD),
                "/rank renders a target rank in its own configured color");

        command.onCommand(self, null, "rank", new String[]{"NotOnline"});
        check(fixture.last().endsWith("That player is not online."),
                "/rank unknown rejects offline players");
        command.onCommand(self, null, "rank", new String[]{"Other", "extra"});
        check(fixture.last().contains("Use: /rank [player]"),
                "/rank rejects too many arguments with concise usage");

        CommandSender console = fixture.console(Set.of(RankCommand.PERMISSION));
        command.onCommand(console, null, "rank", new String[0]);
        check(fixture.last().contains("Specify an online player:")
                        && fixture.last().contains("/rank <player>"),
                "console /rank asks for an online player");
        command.onCommand(console, null, "rank", new String[]{"Other"});
        check(fixture.last().contains("Player: Other") && fixture.last().contains("Rank: VIP"),
                "console /rank <player> works");

        check(command.onTabComplete(self, null, "rank", new String[]{"O"}).equals(List.of("Other")),
                "rank tab completion returns matching online players");
        check(command.onTabComplete(fixture.console(Set.of()), null, "rank", new String[]{""}).isEmpty(),
                "rank tab completion respects permission");

        System.out.println("RankCommandHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean hasColoredText(Component component, String text, TextColor color) {
        if (color.equals(component.color()) && PLAIN.serialize(component).contains(text)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> hasColoredText(child, text, color));
    }

    private static final class Fixture implements RankService.Gateway {

        private final Map<String, Player> players = new HashMap<>();
        private final Map<UUID, String> primaryGroups = new HashMap<>();
        private final List<Component> messages = new ArrayList<>();
        private final RankService rankService = new RankService(() -> "ranks", this);
        private final MessageService messageService = messages();

        private Player player(String name, String groupId, Set<String> permissions) {
            UUID uniqueId = UUID.randomUUID();
            primaryGroups.put(uniqueId, groupId);
            Player player = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, arguments) -> invokeSender(
                            proxy, method.getName(), method.getReturnType(), arguments,
                            name, uniqueId, permissions
                    )
            );
            players.put(name, player);
            return player;
        }

        private CommandSender console(Set<String> permissions) {
            return (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    (proxy, method, arguments) -> invokeSender(
                            proxy, method.getName(), method.getReturnType(), arguments,
                            "Console", null, permissions
                    )
            );
        }

        private Object invokeSender(
                Object proxy,
                String name,
                Class<?> returnType,
                Object[] arguments,
                String playerName,
                UUID uniqueId,
                Set<String> permissions
        ) {
            if (name.equals("getName")) return playerName;
            if (name.equals("getUniqueId")) return uniqueId;
            if (name.equals("hasPermission") && arguments != null && arguments.length == 1) {
                return permissions.contains(arguments[0]);
            }
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
            if (name.equals("toString")) return playerName;
            return defaultValue(returnType);
        }

        private Collection<? extends Player> onlinePlayers() {
            return players.values();
        }

        private String last() {
            return PLAIN.serialize(lastComponent());
        }

        private Component lastComponent() {
            return messages.getLast();
        }

        @Override
        public Optional<String> getPrimaryGroup(UUID uniqueId) {
            return Optional.ofNullable(primaryGroups.get(uniqueId));
        }

        @Override
        public Optional<LuckPermsService.GroupInformation> getGroupInformation(String groupId) {
            return switch (groupId) {
                case "default" -> Optional.of(group("default", "Member", null, "gray"));
                case "vip" -> Optional.of(group("vip", "VIP", "Supporter rank.", "gold"));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<List<String>> getTrackGroups(String trackName) {
            return Optional.of(List.of("default", "vip"));
        }

        private static LuckPermsService.GroupInformation group(
                String id,
                String displayName,
                String description,
                String color
        ) {
            return new LuckPermsService.GroupInformation(
                    id,
                    Optional.of(displayName),
                    Optional.ofNullable(description),
                    Optional.ofNullable(color),
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

package dev.vapee.core.identity;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.identity.command.ProfileCommand;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.rank.RankInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class ProfileHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-profile-");
        try {
            UUID onlineId = UUID.randomUUID();
            UUID offlineId = UUID.randomUUID();
            UUID collisionId = UUID.randomUUID();
            UUID markupId = UUID.randomUUID();
            Files.writeString(directory.resolve(onlineId + ".yml"), yaml("_ImVentex_", 12500));
            Files.writeString(directory.resolve(offlineId + ".yml"), yaml("Offline", 4200));
            Files.writeString(directory.resolve(collisionId + ".yml"), yaml("offline", 300));
            Files.writeString(directory.resolve(markupId + ".yml"), yaml("<red>Safe</red>", 5));
            FilePlayerRepository repository = new FilePlayerRepository(directory, logger());
            repository.initialize();
            PlayerService players = new PlayerService(repository, logger());
            players.loadPlayer(onlineId, "_ImVentex_");
            PlayerIdentityService identities = new PlayerIdentityService(players);
            EconomyService economy = new EconomyService(players);
            List<Component> messages = new ArrayList<>();
            Player online = player("_ImVentex_", onlineId, true, messages);
            PlayerProfileService profiles = new PlayerProfileService(identities, economy,
                    id -> onlineId.equals(id) ? online : null,
                    id -> Optional.of(new RankInfo("owner", "Owner", Optional.empty(),
                            Optional.of(NamedTextColor.DARK_RED), OptionalInt.empty())));
            ProfileCommand command = new ProfileCommand(profiles, messages(), () -> List.of(online),
                    logger(), ZoneId.of("UTC"));

            PlayerProfile live = profiles.getProfile(onlineId).orElseThrow();
            check(live.online() && live.coins() == 12500L && live.rank().isPresent()
                            && live.playtimeTicks().orElseThrow() == 2L * 24L * 60L * 60L * 20L
                            + 6L * 60L * 60L * 20L,
                    "online profile uses live rank, wallet, and Minecraft statistic");
            PlayerProfile stored = profiles.getProfile(offlineId).orElseThrow();
            check(!stored.online() && stored.coins() == 4200L && stored.rank().isEmpty()
                            && stored.playtimeTicks().isEmpty() && !players.isLoaded(offlineId),
                    "offline profile reads persistence without loading or inventing rank/playtime");
            check(profiles.getProfile(UUID.randomUUID()).isEmpty(), "unknown UUID has no profile");
            check(profiles.resolveProfile("Offline").status() == PlayerLookupStatus.AMBIGUOUS,
                    "ambiguous profile stays ambiguous");

            command.onCommand(online, null, "profile", new String[0]);
            check(all(messages).contains("Player: _ImVentex_") && all(messages).contains("Status: Online")
                            && all(messages).contains("Playtime: 2d 6h"),
                    "player /profile shows own live profile and shared playtime format");
            check(all(messages).contains("First Join: 1970-01-01 00:00")
                            && all(messages).contains("Last Join: "),
                    "first/last join use deterministic injected time zone and correct labels");
            check(messages.stream().anyMatch(component -> colored(component, "Owner", NamedTextColor.DARK_RED)),
                    "rank display retains RankInfo dark-red color");
            CommandSender console = sender("Console", true, messages);
            messages.clear();
            command.onCommand(console, null, "profile", new String[0]);
            check(all(messages).contains("Specify a player"), "console must specify a target");
            messages.clear();
            command.onCommand(console, null, "profile", new String[]{onlineId.toString()});
            check(all(messages).contains("_ImVentex_"), "known UUID input works");
            messages.clear();
            command.onCommand(console, null, "profile", new String[]{"offlineId"});
            check(all(messages).contains("not known"), "unknown name is controlled");
            messages.clear();
            command.onCommand(console, null, "profile", new String[]{"offline"});
            check(all(messages).contains("Multiple stored players"), "collision asks for UUID");
            messages.clear();
            command.onCommand(console, null, "profile", new String[]{offlineId.toString()});
            check(all(messages).contains("Status: Offline")
                            && all(messages).contains("Unavailable while offline"),
                    "console can view known offline UUID");
            messages.clear();
            command.onCommand(console, null, "profile", new String[]{markupId.toString()});
            check(all(messages).contains("Player: <red>Safe</red>")
                            && messages.stream().noneMatch(component -> colored(component, "Safe", NamedTextColor.RED)),
                    "stored player names remain literal components, not MiniMessage markup");
            messages.clear();
            command.onCommand(console, null, "profile", new String[]{"a", "b"});
            check(all(messages).contains("/profile [player|uuid]"), "extra arguments show usage");
            CommandSender denied = sender("Denied", false, messages);
            messages.clear();
            command.onCommand(denied, null, "profile", new String[]{"_ImVentex_"});
            check(all(messages).contains("permission"), "permission is enforced");
            check(command.onTabComplete(online, null, "profile", new String[]{"_im"})
                            .equals(List.of("_ImVentex_")), "tab suggests online names case-insensitively");
            check(command.onTabComplete(online, null, "profile", new String[]{"off"}).isEmpty(),
                    "tab does not enumerate offline names");
            check(command.onTabComplete(denied, null, "profile", new String[]{""}).isEmpty(),
                    "tab respects permission");
            check(dev.vapee.core.format.PlaytimeFormatter.formatTicks(0).equals("0m")
                            && dev.vapee.core.format.PlaytimeFormatter.formatTicks(43 * 1200L).equals("43m")
                            && dev.vapee.core.format.PlaytimeFormatter.formatTicks(6 * 72000L + 43 * 1200L)
                            .equals("6h 43m"), "shared playtime format keeps existing semantics");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        System.out.println("ProfileHarness passed " + checks + " checks.");
    }

    private static String yaml(String name, long coins) {
        return "name: '" + name + "'\nfirst-join: 0\nlast-join: 0\neconomy:\n  coins: " + coins + "\n";
    }

    private static Player player(String name, UUID id, boolean permitted, List<Component> messages) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> id;
                    case "isOnline", "hasPermission" -> permitted;
                    case "getStatistic" -> 2 * 24 * 60 * 60 * 20 + 6 * 60 * 60 * 20;
                    case "sendMessage" -> { capture(messages, args); yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static CommandSender sender(String name, boolean permitted, List<Component> messages) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "hasPermission" -> permitted;
                    case "sendMessage" -> { capture(messages, args); yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static void capture(List<Component> messages, Object[] args) {
        if (args != null) {
            for (Object value : args) {
                if (value instanceof Component component) {
                    messages.add(component);
                }
            }
        }
    }

    private static boolean colored(Component component, String text, NamedTextColor color) {
        if (color.equals(component.color()) && PLAIN.serialize(component).contains(text)) return true;
        return component.children().stream().anyMatch(child -> colored(child, text, color));
    }

    private static String all(List<Component> messages) {
        return String.join("\n", messages.stream().map(PLAIN::serialize).toList());
    }

    private static MessageService messages() throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance((Supplier<String>) () -> "");
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
        Logger logger = Logger.getLogger("ProfileHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

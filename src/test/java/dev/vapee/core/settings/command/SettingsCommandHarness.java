package dev.vapee.core.settings.command;

import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.AddedVisiblePlayerResult;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class SettingsCommandHarness {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    public static void main(String[] args) throws Exception {
        Fixture fixture = new Fixture();
        fixture.menuAndSyntax();
        fixture.addAndRemove();
        fixture.completionAndSecurity();
        new Fixture().safeRelationArguments();
        System.out.println("SettingsCommandHarness passed " + checks + " checks.");
    }

    private static final class Fixture {
        final MemoryRepository repository = new MemoryRepository();
        final PlayerService players = new PlayerService(repository, logger());
        final PlayerSettingsService settings = new PlayerSettingsService(players);
        final PlayerIdentityService identities = new PlayerIdentityService(players);
        final MessageService messages = messages();
        final List<TestSender> candidates = new ArrayList<>();
        final TestSender owner = player("Owner", true, true);
        int mainOpens;
        int visibilityOpens;
        int applyCalls;
        final SettingsCommand command = new SettingsCommand(ignored -> mainOpens++,
                ignored -> visibilityOpens++, settings, identities,
                () -> candidates.stream().filter(candidate -> candidate.online)
                        .map(candidate -> (Player) candidate.sender).toList(),
                ignored -> applyCalls++, messages, logger());

        Fixture() throws Exception { }

        void menuAndSyntax() {
            execute(owner, new String[0]);
            check(mainOpens == 1, "/settings opens existing main menu");
            execute(owner, "visibility");
            check(visibilityOpens == 1, "/settings visibility opens visibility menu");
            owner.clear();
            execute(owner, "visibility", "add");
            check(owner.text().contains("/settings visibility add <player|uuid>"),
                    "missing add target reports concrete usage");
            owner.clear();
            execute(owner, "visibility", "remove", "one", "extra");
            check(owner.text().contains("/settings visibility remove <player|uuid>"),
                    "remove extra arguments report concrete usage");
            owner.clear();
            execute(owner, "other");
            check(owner.text().contains("/settings visibility"), "unknown settings input has focused usage");
        }

        void addAndRemove() {
            TestSender bob = player("Bob", true, true);
            TestSender offline = player("Offline", false, false);
            execute(owner, "visibility", "add", "Bob");
            check(settings.isLobbyAddedVisiblePlayer(owner.id, bob.id).orElseThrow()
                            && owner.text().contains("Added Bob") && applyCalls == 1,
                    "add accepts known online identity and applies immediately");
            owner.clear();
            execute(owner, "visibility", "add", offline.id.toString());
            check(settings.isLobbyAddedVisiblePlayer(owner.id, offline.id).orElseThrow()
                            && owner.text().contains("Added Offline") && applyCalls == 2,
                    "add accepts known offline UUID identity");
            owner.clear();
            execute(owner, "visibility", "add", "Unknown");
            check(owner.text().contains("not known") && applyCalls == 2,
                    "unknown identity is rejected without apply");

            TestSender twinOne = player(UUID.randomUUID(), "Twin", false, false);
            TestSender twinTwo = player(UUID.randomUUID(), "Twin", false, false);
            owner.clear();
            execute(owner, "visibility", "add", "Twin");
            check(owner.text().contains("Multiple known players")
                            && owner.text().contains("UUID") && applyCalls == 2,
                    "ambiguous name requires UUID");
            owner.clear();
            execute(owner, "visibility", "add", "Owner");
            check(owner.text().contains("cannot add yourself"), "self add is controlled");
            owner.clear();
            execute(owner, "visibility", "add", "Bob");
            check(owner.text().contains("already in your visible players list"),
                    "duplicate add is controlled");

            owner.clear();
            execute(owner, "visibility", "remove", "Bob");
            check(!settings.isLobbyAddedVisiblePlayer(owner.id, bob.id).orElseThrow()
                            && owner.text().contains("Removed Bob") && applyCalls == 3,
                    "remove resolves unique known name and applies immediately");
            UUID stale = UUID.randomUUID();
            check(settings.addLobbyVisiblePlayer(owner.id, stale) == AddedVisiblePlayerResult.SUCCESS,
                    "fixture seeds stale UUID");
            owner.clear();
            execute(owner, "visibility", "remove", stale.toString());
            check(!settings.isLobbyAddedVisiblePlayer(owner.id, stale).orElseThrow()
                            && owner.text().contains(stale.toString()) && applyCalls == 4,
                    "stored unknown UUID remains removable");
            owner.clear();
            execute(owner, "visibility", "remove", UUID.randomUUID().toString());
            check(owner.text().contains("not in your visible players list"),
                    "missing remove is controlled");

            TestSender failing = player("Failing", true, true);
            repository.failNext = true;
            owner.clear();
            execute(owner, "visibility", "add", "Failing");
            check(!settings.isLobbyAddedVisiblePlayer(owner.id, failing.id).orElseThrow()
                            && owner.text().contains("could not be saved") && applyCalls == 4,
                    "storage failure rolls back and does not apply");

            TestSender literal = player("<red>Safe</red>", true, true);
            owner.clear();
            execute(owner, "visibility", "add", literal.id.toString());
            check(settings.isLobbyAddedVisiblePlayer(owner.id, literal.id).orElseThrow()
                            && owner.text().contains("<red>Safe</red>"),
                    "player-controlled name remains literal component text");

            check(twinOne.id != null && twinTwo.id != null, "ambiguous fixtures remain distinct");
        }

        void completionAndSecurity() {
            check(complete(owner, "").equals(List.of("visibility")),
                    "first argument completes visibility");
            check(complete(owner, "visibility", "").equals(List.of("add", "remove")),
                    "visibility action completion is focused");
            List<String> add = complete(owner, "visibility", "add", "");
            check(add.contains("Bob") && !add.contains("Owner") && !add.contains("<red>Safe</red>"),
                    "add completion prefers online non-self players and excludes already-added targets");

            UUID duplicateOne = UUID.randomUUID();
            UUID duplicateTwo = UUID.randomUUID();
            player(duplicateOne, "Duplicate", false, false);
            player(duplicateTwo, "Duplicate", false, false);
            settings.addLobbyVisiblePlayer(owner.id, duplicateOne);
            settings.addLobbyVisiblePlayer(owner.id, duplicateTwo);
            List<String> remove = complete(owner, "visibility", "remove", "");
            check(remove.contains(duplicateOne.toString()) && remove.contains(duplicateTwo.toString())
                            && remove.contains("Offline"),
                    "remove completion uses names only when unique and UUIDs when ambiguous");

            TestSender denied = player("Denied", true, true);
            denied.permitted = false;
            execute(denied, new String[0]);
            check(denied.text().contains("do not have permission") && mainOpens == 1,
                    "permission denied cannot open settings");
            check(complete(denied, "").isEmpty(), "permission denied receives no completions");
            TestSender console = console();
            execute(console, "visibility");
            check(console.text().contains("Only players can open/manage player settings"),
                    "console receives player-only feedback");
        }

        void safeRelationArguments() {
            TestSender safe = player("Safe", false, false);
            TestSender spaced = player("Two Words", false, false);
            TestSender wrong = player(owner.id.toString(), false, false);
            TestSender twin = player("Twin", false, false);
            player("twin", false, false);
            player("UnrelatedKnown", false, false);
            UUID unknown = UUID.randomUUID();
            for (UUID id : List.of(safe.id, spaced.id, wrong.id, twin.id, unknown)) {
                check(settings.addLobbyVisiblePlayer(owner.id, id) == AddedVisiblePlayerResult.SUCCESS,
                        "arrange own saved visibility relation");
            }
            List<String> result = complete(owner, "visibility", "remove", "");
            check(Set.copyOf(result).equals(Set.of("Safe", spaced.id.toString(), wrong.id.toString(),
                    twin.id.toString(), unknown.toString())), "remove keeps exact own relation, with safe UUID spellings");
            check(complete(owner, "visibility", "remove", "sA").equals(List.of("Safe")), "remove safe name prefix");
            for (UUID id : List.of(spaced.id, wrong.id, twin.id, unknown)) {
                execute(owner, "visibility", "remove", id.toString());
                check(!settings.isLobbyAddedVisiblePlayer(owner.id, id).orElseThrow(), "fallback remains executable");
            }
        }

        TestSender player(String name, boolean online, boolean loaded) {
            return player(UUID.nameUUIDFromBytes((name + candidates.size())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)), name, online, loaded);
        }

        TestSender player(UUID id, String name, boolean online, boolean loaded) {
            players.loadPlayer(id, name);
            TestSender result = new TestSender(id, name, online, true);
            candidates.add(result);
            if (!loaded) players.unloadPlayer(id);
            return result;
        }

        TestSender console() {
            return new TestSender(null, "Console", false, false);
        }

        void execute(TestSender sender, String... args) {
            command.onCommand(sender.sender, null, "settings", args);
        }

        List<String> complete(TestSender sender, String... args) {
            return command.onTabComplete(sender.sender, null, "settings", args);
        }
    }

    private static final class TestSender {
        final UUID id;
        final String name;
        final List<Component> received = new ArrayList<>();
        final CommandSender sender;
        boolean online;
        boolean permitted;

        TestSender(UUID id, String name, boolean online, boolean permitted) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.permitted = permitted;
            Class<?> type = id == null ? CommandSender.class : Player.class;
            this.sender = proxy(type, (method, arguments) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> this.online;
                case "hasPermission" -> this.permitted;
                case "sendMessage" -> {
                    if (arguments != null) for (Object argument : arguments) {
                        if (argument instanceof Component component) received.add(component);
                    }
                    yield null;
                }
                default -> DEFAULT;
            });
        }

        String text() {
            return received.stream().map(PLAIN::serialize).reduce("", (left, right) -> left + "\n" + right);
        }

        void clear() {
            received.clear();
        }
    }

    private static final class MemoryRepository implements PlayerRepository {
        final Map<UUID, CorePlayer> data = new HashMap<>();
        boolean failNext;
        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
        @Override public void save(CorePlayer player) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated persistence failure");
            }
            data.put(player.getUniqueId(), player);
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
        @Override public Set<UUID> findUniqueIdsByName(String name) {
            return data.values().stream().filter(player -> player.getName().equalsIgnoreCase(name))
                    .map(CorePlayer::getUniqueId).collect(Collectors.toUnmodifiableSet());
        }
    }

    private static MessageService messages() throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance((java.util.function.Supplier<String>) () -> "");
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static final Object DEFAULT = new Object();

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
                    default -> null;
                };
            }
            Object result = action.invoke(method.getName(), args == null ? new Object[0] : args);
            return result == DEFAULT ? defaultValue(method.getReturnType()) : result;
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (Collection.class.isAssignableFrom(type)) return List.of();
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
    private interface ProxyAction { Object invoke(String method, Object[] arguments); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

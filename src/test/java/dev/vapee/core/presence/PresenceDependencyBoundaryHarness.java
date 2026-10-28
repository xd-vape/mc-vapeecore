package dev.vapee.core.presence;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.friend.FileFriendRepository;
import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.friend.FriendRequestPolicy;
import dev.vapee.core.friend.FriendResult;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Real lifecycle and notification behavior with two separately persisted dependency generations. */
public final class PresenceDependencyBoundaryHarness {
    private static int checks;
    private static final UUID SUBJECT = id(1),
            A_FRIEND = id(2),
            B_FRIEND = id(3),
            PREFERENCE = id(4),
            IGNORED = id(5),
            REVERSE_IGNORED = id(6),
            OFFLINE = id(7),
            MISSING_SETTINGS = id(8),
            FAILED_DELIVERY = id(9),
            B_ONLY = id(10),
            UNRELATED = id(11);
    private static final List<String> DEPENDENCIES =
            List.of("PlayerService", "PlayerSettingsService", "SocialService", "FriendService");

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("missing")) providerFailures(true);
        else if (args.length == 1 && args[0].equals("compatibility")) generations(true);
        else {
            generations(false);
            generations(true);
            providerFailures(true);
            providerFailures(false);
            registrationFailures();
            lateLogging();
            constructorValidation();
            architecture();
        }
        System.out.println("PresenceDependencyBoundaryHarness passed " + checks + " checks.");
    }

    private static void generations(boolean compatibility) throws Exception {
        try (var f = new Fixture()) {
            PresenceModule module;
            try {
                module = compatibility ? f.compatibilityModule() : f.typedModule();
            } catch (RuntimeException unexpected) {
                throw new AssertionError(
                        (compatibility ? "compatibility" : "typed")
                                + " construction must not resolve services",
                        unexpected);
            }
            check(
                    Arrays.equals(f.calls, new int[4]),
                    "construction is lazy for all four providers");
            if (compatibility) {
                check(
                        failure(module::enable).getMessage().contains("PlayerModule"),
                        "inactive compatibility dependency fails at enable");
                f.inactive(module, null);
            }
            f.install(f.a);
            module.enable();
            var first = module.getPresenceService();
            var oldNotifier = get(module, "notifier");
            var oldListener = get(module, "listener");
            f.active(module);
            check(
                    f.deliveries.isEmpty()
                            && !first.isOnline(SUBJECT)
                            && !first.isOnline(MISSING_SETTINGS),
                    "seed includes only loaded online players and fabricates no notification");
            f.join(SUBJECT);
            f.expected(f.a, "generation A notification policy recipients", true);
            int delivered = f.deliveries.size();
            f.join(SUBJECT);
            check(f.deliveries.size() == delivered, "duplicate join is not another transition");
            f.clear();
            f.quit(SUBJECT);
            f.expected(f.a, "generation A offline policy recipients", false);
            delivered = f.deliveries.size();
            f.quit(SUBJECT);
            check(
                    f.deliveries.size() == delivered && !first.isOnline(SUBJECT),
                    "duplicate quit does not notify");

            f.install(f.b);
            RuntimeException duplicate = failure(module::enable);
            check(
                    duplicate instanceof IllegalStateException
                            && module.getPresenceService() == first
                            && get(module, "listener") == oldListener,
                    "active runtime is not replaced by another enable");
            f.clear();
            f.join(SUBJECT);
            f.expected(f.a, "active generation retains its captured A services", true);
            f.join(B_ONLY);
            check(
                    !first.isOnline(B_ONLY),
                    "active A listener does not switch to new PlayerService provider target");
            if (!compatibility)
                check(
                        Arrays.equals(f.calls, new int[] {1, 1, 1, 1}),
                        "exactly one service resolution each in first activation");
            module.disable();
            f.inactive(module, first);
            f.unchanged();
            f.clear();
            module.enable();
            PresenceService second = module.getPresenceService();
            f.active(module);
            check(
                    second != first
                            && get(module, "notifier") != oldNotifier
                            && get(module, "listener") != oldListener,
                    "second enable creates fresh owned runtime objects");
            check(f.deliveries.isEmpty(), "second initial seed sends no online notification");
            f.install(f.a); // Changing a provider target is deliberately not live hot-swap.
            f.quit(SUBJECT);
            f.clear();
            f.join(SUBJECT);
            f.expected(f.b, "generation B notification policy recipients", true);
            check(
                    !first.isOnline(SUBJECT),
                    "old registered runtime no longer receives current events");
            f.quit(B_ONLY);
            f.join(B_ONLY);
            check(
                    second.isOnline(B_ONLY),
                    "fresh listener uses B PlayerService even after provider target changes again");
            if (!compatibility)
                check(
                        Arrays.equals(f.calls, new int[] {2, 2, 2, 2}),
                        "exactly four fresh resolutions in second activation");
            module.disable();
            f.inactive(module, second);
            f.unchanged();
            int[] calls = f.calls.clone();
            module.disable();
            check(
                    Arrays.equals(calls, f.calls) && f.runtime.handlerCount() == 0,
                    "repeated disable never resolves or disposes upstream services");
        }
    }

    private static void providerFailures(boolean nullResult) throws Exception {
        for (int dependency = 0; dependency < 4; dependency++) {
            try (var f = new Fixture()) {
                var module = f.typedModule();
                f.install(f.a);
                f.failIndex = dependency;
                f.returnNull = nullResult;
                RuntimeException actual =
                        failureWithMessage(
                                module::enable,
                                "required dependency must fail before publication: "
                                        + DEPENDENCIES.get(dependency));
                if (nullResult)
                    check(
                            actual instanceof NullPointerException
                                    && actual.getMessage().contains(DEPENDENCIES.get(dependency)),
                            "null provider result identifies unavailable service");
                else
                    check(
                            actual == f.providerFailure,
                            "original provider exception preserved for "
                                    + DEPENDENCIES.get(dependency));
                f.inactive(module, null);
                f.unchanged();
                f.failIndex = -1;
                Arrays.fill(f.calls, 0);
                module.enable();
                f.active(module);
                f.join(SUBJECT);
                f.expected(f.a, "valid retry uses actual friend and policy services", true);
                check(
                        Arrays.equals(f.calls, new int[] {1, 1, 1, 1}),
                        "valid retry resolves all services exactly once");
                var service = module.getPresenceService();
                module.disable();
                f.inactive(module, service);
                f.unchanged();
            }
        }
    }

    private static void registrationFailures() throws Exception {
        for (boolean duringSeed : List.of(false, true)) {
            try (var f = new Fixture()) {
                var module = f.typedModule();
                f.install(f.a);
                f.runtime.failRegistration = duringSeed ? 0 : 1;
                f.runtime.failOnlineRead = duringSeed;
                check(
                        failure(module::enable) instanceof IllegalStateException,
                        "real registration or seeding failure observable");
                var created =
                        (PresenceService) get(f.runtime.registered.getLast(), "presenceService");
                f.inactive(module, created);
                f.unchanged();
                f.runtime.failRegistration = 0;
                f.runtime.failOnlineRead = false;
                module.enable();
                f.active(module);
                var active = module.getPresenceService();
                module.disable();
                f.inactive(module, active);
            }
        }
    }

    private static void lateLogging() throws Exception {
        try (var f = new Fixture()) {
            var module = f.typedModule();
            f.install(f.a);
            var original = new IllegalStateException("controlled final Presence log failure");
            var published = new AtomicReference<PresenceService>();
            Handler fault =
                    new Handler() {
                        public void publish(LogRecord record) {
                            if (record.getMessage().equals("Presence module enabled.")) {
                                published.set((PresenceService) get(module, "service"));
                                throw original;
                            }
                        }

                        public void flush() {}

                        public void close() {}
                    };
            f.runtime.logger.addHandler(fault);
            check(
                    failure(module::enable) == original && published.get() != null,
                    "late startup failure preserves original after actual publication");
            f.inactive(module, published.get());
            f.unchanged();
            f.runtime.logger.removeHandler(fault);
            module.enable();
            f.active(module);
            check(
                    module.getPresenceService() != published.get(),
                    "late-failure retry constructs fresh runtime");
            module.disable();
            f.inactive(module, null);
        }
    }

    private static void constructorValidation() throws Exception {
        try (var f = new Fixture()) {
            for (int argument = 0; argument < 6; argument++) {
                int index = argument;
                RuntimeException actual =
                        failure(
                                () ->
                                        new PresenceModule(
                                                index == 0 ? null : f.runtime.plugin,
                                                index == 1 ? null : () -> f.a.players,
                                                index == 2 ? null : () -> f.a.settings,
                                                index == 3 ? null : () -> f.a.social,
                                                index == 4 ? null : () -> f.a.friends,
                                                index == 5 ? null : f.runtime.messages));
                check(
                        actual instanceof NullPointerException,
                        "preferred constructor rejects null argument " + argument);
            }
            f.compatibilityModule();
            for (int argument = 0; argument < 5; argument++) {
                int index = argument;
                RuntimeException actual =
                        failure(
                                () ->
                                        new PresenceModule(
                                                index == 0 ? null : f.runtime.plugin,
                                                index == 1 ? null : f.compatPlayers,
                                                index == 2 ? null : f.compatSocial,
                                                index == 3 ? null : f.compatFriends,
                                                index == 4 ? null : f.runtime.messages));
                check(
                        actual instanceof NullPointerException,
                        "compatibility constructor rejects null argument " + argument);
            }
        }
    }

    private static void architecture() throws Exception {
        check(
                Arrays.stream(PresenceModule.class.getDeclaredFields())
                        .noneMatch(field -> CoreModule.class.isAssignableFrom(field.getType())),
                "Presence stores no foreign module as operational dependency");
        var preferred =
                PresenceModule.class.getConstructor(
                        org.bukkit.plugin.java.JavaPlugin.class,
                        Supplier.class,
                        Supplier.class,
                        Supplier.class,
                        Supplier.class,
                        dev.vapee.core.message.MessageService.class);
        check(
                Arrays.stream(preferred.getGenericParameterTypes())
                                .filter(
                                        type ->
                                                type.getTypeName()
                                                        .startsWith("java.util.function.Supplier<"))
                                .count()
                        == 4,
                "public preferred boundary has four typed service providers");
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        int start = core.indexOf("presenceModule = new PresenceModule(");
        String composition = core.substring(start, core.indexOf(';', start));
        check(
                List.of(
                                "playerModule::getPlayerService",
                                "playerModule::getPlayerSettingsService",
                                "socialModule::getSocialService",
                                "friendModule::getFriendService")
                        .stream()
                        .allMatch(composition::contains),
                "composition root explicitly supplies four current service providers");
    }

    private static final class Fixture implements AutoCloseable {
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture();
        final Path root = Files.createTempDirectory("vapeecore-presence-dependencies-");
        final Map<UUID, Actor> actors = new LinkedHashMap<>();
        final List<Delivery> deliveries = new ArrayList<>();
        final AtomicReference<Generation> current = new AtomicReference<>();
        final int[] calls = new int[4];
        final RuntimeException providerFailure =
                new IllegalStateException("controlled upstream provider failure");
        final Generation a;
        final Generation b;
        final Map<Path, byte[]> durable;
        int failIndex = -1;
        boolean returnNull;
        PlayerModule compatPlayers;
        SocialModule compatSocial;
        FriendModule compatFriends;

        Fixture() throws Exception {
            for (UUID id :
                    List.of(
                            SUBJECT,
                            A_FRIEND,
                            B_FRIEND,
                            PREFERENCE,
                            IGNORED,
                            REVERSE_IGNORED,
                            OFFLINE,
                            MISSING_SETTINGS,
                            FAILED_DELIVERY,
                            B_ONLY,
                            UNRELATED)) {
                Actor actor = new Actor(this, id);
                actors.put(id, actor);
                runtime.online.add(actor.player);
            }
            a = new Generation(this, false);
            b = new Generation(this, true);
            durable = snapshot(root);
        }

        <T> Supplier<T> provider(int index, Function<Generation, T> select) {
            return () -> {
                calls[index]++;
                if (index == failIndex) {
                    if (returnNull) return null;
                    throw providerFailure;
                }
                return select.apply(
                        java.util.Objects.requireNonNull(current.get(), "upstream not enabled"));
            };
        }

        PresenceModule typedModule() {
            return new PresenceModule(
                    runtime.plugin,
                    provider(0, value -> value.players),
                    provider(1, value -> value.settings),
                    provider(2, value -> value.social),
                    provider(3, value -> value.friends),
                    runtime.messages);
        }

        PresenceModule compatibilityModule() {
            compatPlayers = ModuleRuntimeFixture.dependency(PlayerModule.class, Map.of());
            compatSocial = ModuleRuntimeFixture.dependency(SocialModule.class, Map.of());
            compatFriends = ModuleRuntimeFixture.dependency(FriendModule.class, Map.of());
            return new PresenceModule(
                    runtime.plugin, compatPlayers, compatSocial, compatFriends, runtime.messages);
        }

        void install(Generation generation) {
            current.set(generation);
            if (compatPlayers != null) {
                set(compatPlayers, "playerService", generation.players);
                set(compatPlayers, "playerSettingsService", generation.settings);
                set(compatSocial, "socialService", generation.social);
                set(compatFriends, "friendService", generation.friends);
                set(compatFriends, "active", true);
            }
        }

        void active(PresenceModule module) {
            check(
                    module.getPresenceService() != null && runtime.handlerCount() == 2,
                    "one real Presence listener owns exactly two handlers");
            check(
                    get(module, "notifier") instanceof FriendPresenceNotifier
                            && get(module, "listener") instanceof PresenceListener,
                    "real notifier and listener published");
        }

        void inactive(PresenceModule module, PresenceService previous) {
            check(runtime.handlerCount() == 0, "Presence listener cleanup leaves no handler");
            check(get(module, "notifier") == null, "owned notifier reference released");
            check(
                    get(module, "listener") == null && get(module, "service") == null,
                    "owned runtime references released");
            check(
                    failure(module::getPresenceService) instanceof NullPointerException,
                    "inactive getter fails closed");
            if (previous != null)
                check(previous.getOnlinePlayers().isEmpty(), "old Presence-owned state cleared");
        }

        void join(UUID id) {
            var actor = actors.get(id);
            actor.online = true;
            for (var listener : listeners())
                listener.onPlayerJoin(new PlayerJoinEvent(actor.player, Component.empty()));
        }

        void quit(UUID id) {
            var actor = actors.get(id);
            actor.online = false;
            for (var listener : listeners())
                listener.onPlayerQuit(new PlayerQuitEvent(actor.player, Component.empty()));
        }

        List<PresenceListener> listeners() {
            // Fixture event executors are intentionally no-ops. Route only registered real
            // production listeners, never old Java references held by this test.
            return HandlerList.getRegisteredListeners(runtime.plugin).stream()
                    .map(entry -> entry.getListener())
                    .filter(PresenceListener.class::isInstance)
                    .map(PresenceListener.class::cast)
                    .distinct()
                    .toList();
        }

        void expected(Generation generation, String label, boolean online) {
            Set<UUID> expected =
                    generation.second
                            ? Set.of(B_FRIEND, PREFERENCE, IGNORED)
                            : Set.of(A_FRIEND, REVERSE_IGNORED);
            check(
                    deliveries.size() == expected.size()
                            && deliveries.stream()
                                    .map(Delivery::recipient)
                                    .collect(java.util.stream.Collectors.toSet())
                                    .equals(expected),
                    label);
            String text = online ? "[Friends] Friend Subject is now online." : "[Friends] Friend Subject went offline.";
            check(
                    deliveries.stream().allMatch(value -> value.text.equals(text)),
                    "notifications use actual existing localized message flow");
        }

        void clear() {
            deliveries.clear();
        }

        void unchanged() throws Exception {
            var after = snapshot(root);
            check(
                    after.keySet().equals(durable.keySet())
                            && durable.entrySet().stream()
                                    .allMatch(
                                            entry ->
                                                    Arrays.equals(
                                                            entry.getValue(),
                                                            after.get(entry.getKey()))),
                    "Presence never writes or replaces borrowed services' durable files");
            check(
                    a.players.isLoaded(SUBJECT)
                            && b.players.isLoaded(B_ONLY)
                            && !a.settings
                                    .areFriendPresenceNotificationsEnabled(PREFERENCE)
                                    .orElseThrow()
                            && b.settings
                                    .areFriendPresenceNotificationsEnabled(PREFERENCE)
                                    .orElseThrow()
                            && a.social.isKnownIgnoring(IGNORED, SUBJECT)
                            && b.social.isKnownIgnoring(SUBJECT, REVERSE_IGNORED)
                            && a.friends.getFriends(SUBJECT).contains(A_FRIEND)
                            && b.friends.getFriends(SUBJECT).contains(B_FRIEND),
                    "borrowed loaded state, settings, social snapshots and friendships remain intact");
        }

        @Override
        public void close() throws Exception {
            runtime.close();
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private static final class Generation {
        final boolean second;
        final PlayerService players;
        final PlayerSettingsService settings;
        final SocialService social;
        final FriendService friends;

        Generation(Fixture f, boolean second) throws Exception {
            this.second = second;
            Path directory = f.root.resolve(second ? "B" : "A");
            var repository =
                    new FilePlayerRepository(directory.resolve("players"), f.runtime.logger);
            repository.initialize();
            players = new PlayerService(repository, f.runtime.logger);
            settings = new PlayerSettingsService(players);
            social = new SocialService(players, f.runtime.logger);
            friends =
                    new FriendService(
                            new FileFriendRepository(
                                    directory.resolve("friends.yml"), f.runtime.logger),
                            FriendLimits.defaults(),
                            FriendRequestPolicy.allowAll(),
                            Clock.systemUTC());
            for (var entry : f.actors.entrySet()) {
                UUID id = entry.getKey();
                if (id.equals(MISSING_SETTINGS) || !second && id.equals(B_ONLY)) continue;
                players.loadPlayer(id, entry.getValue().name);
                if (!settings.setFriendPresenceNotificationsEnabled(
                        id, !id.equals(PREFERENCE) || second))
                    throw new IllegalStateException("fixture preference setup failed");
            }
            social.ignore(second ? SUBJECT : IGNORED, second ? REVERSE_IGNORED : SUBJECT);
            for (UUID recipient :
                    List.of(
                            second ? B_FRIEND : A_FRIEND,
                            PREFERENCE,
                            IGNORED,
                            REVERSE_IGNORED,
                            OFFLINE,
                            MISSING_SETTINGS,
                            FAILED_DELIVERY)) {
                if (friends.sendRequest(SUBJECT, recipient) != FriendResult.SUCCESS
                        || friends.acceptRequest(recipient, SUBJECT) != FriendResult.SUCCESS)
                    throw new IllegalStateException("fixture friendship setup failed");
            }
            players.saveAll();
        }
    }

    private static final class Actor {
        final String name;
        final Player player;
        boolean online;

        Actor(Fixture f, UUID id) {
            name = id.equals(SUBJECT) ? "Subject" : "Recipient" + id.getLeastSignificantBits();
            online = !Set.of(SUBJECT, OFFLINE, B_ONLY).contains(id);
            player =
                    ModuleRuntimeFixture.proxy(
                            Player.class,
                            (method, args) ->
                                    switch (method) {
                                        case "getUniqueId" -> id;
                                        case "getName" -> name;
                                        case "isOnline" -> online;
                                        case "locale" -> Locale.US;
                                        case "sendMessage" -> {
                                            if (id.equals(FAILED_DELIVERY))
                                                throw new IllegalStateException(
                                                        "controlled recipient delivery failure");
                                            for (Object value : args)
                                                if (value instanceof Component text)
                                                    f.deliveries.add(
                                                            new Delivery(
                                                                    id,
                                                                    PlainTextComponentSerializer
                                                                            .plainText()
                                                                            .serialize(text)));
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected Presence player boundary: "
                                                                + method);
                                    });
        }
    }

    private record Delivery(UUID recipient, String text) {}

    private static Map<Path, byte[]> snapshot(Path root) throws Exception {
        var result = new LinkedHashMap<Path, byte[]>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList())
                result.put(root.relativize(path), Files.readAllBytes(path));
        }
        return result;
    }

    private static RuntimeException failure(Runnable operation) {
        return failureWithMessage(operation, "expected dependency/lifecycle failure");
    }

    private static RuntimeException failureWithMessage(Runnable operation, String missing) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            return exception;
        }
        throw new AssertionError(missing);
    }

    private static UUID id(int value) {
        return new UUID(0, value);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}

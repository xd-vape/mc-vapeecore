package dev.vapee.core.presence;

import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.friend.FriendRepository;
import dev.vapee.core.friend.FriendRequestPolicy;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.FriendSnapshot;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PresenceLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(
                CoreModule.class.isAssignableFrom(PresenceModule.class)
                        && !ReloadParticipant.class.isAssignableFrom(PresenceModule.class),
                "Presence is one CoreModule and not reloadable");
        check(
                List.of(
                                PresenceModule.class
                                        .getConstructor(
                                                JavaPlugin.class,
                                                PlayerModule.class,
                                                SocialModule.class,
                                                FriendModule.class,
                                                MessageService.class)
                                        .getParameterTypes())
                        .equals(
                                List.of(
                                        JavaPlugin.class,
                                        PlayerModule.class,
                                        SocialModule.class,
                                        FriendModule.class,
                                        MessageService.class)),
                "module dependencies are explicit");

        Method join =
                PresenceListener.class.getMethod(
                        "onPlayerJoin", org.bukkit.event.player.PlayerJoinEvent.class);
        Method quit =
                PresenceListener.class.getMethod(
                        "onPlayerQuit", org.bukkit.event.player.PlayerQuitEvent.class);
        check(
                join.getAnnotation(EventHandler.class).priority() == EventPriority.MONITOR,
                "join observes after player load at MONITOR");
        check(
                quit.getAnnotation(EventHandler.class).priority() == EventPriority.LOWEST,
                "quit transitions before player and social cleanup");

        PresenceService service = new PresenceService();
        UUID subject = UUID.randomUUID();
        List<PresenceStatus> statuses = new ArrayList<>();
        PresenceListener listener =
                new PresenceListener(
                        service,
                        id -> id.equals(subject),
                        (id, name, status) -> statuses.add(status),
                        quietLogger());
        listener.handleJoin(UUID.randomUUID(), "Unloaded");
        check(
                service.getOnlinePlayers().isEmpty() && statuses.isEmpty(),
                "unloaded join is ignored without notification");
        listener.handleJoin(subject, "Alice");
        listener.handleJoin(subject, "Alice");
        listener.handleQuit(subject, "Alice");
        listener.handleQuit(subject, "Alice");
        check(
                statuses.equals(List.of(PresenceStatus.ONLINE, PresenceStatus.OFFLINE)),
                "only real transitions notify once");

        PresenceListener failing =
                new PresenceListener(
                        service,
                        id -> true,
                        (id, name, status) -> {
                            throw new IllegalStateException("notification failure");
                        },
                        quietLogger());
        failing.handleJoin(subject, "Alice");
        check(
                service.isOnline(subject),
                "notification failure does not roll back presence transition");

        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(
                core.indexOf("moduleManager.register(friendModule)")
                                < core.indexOf("moduleManager.register(presenceModule)")
                        && core.indexOf("moduleManager.register(presenceModule)")
                                < core.indexOf("moduleManager.register(clanModule)"),
                "module order is Friend then Presence then Clan");
        check(
                core.split("moduleManager.register\\(", -1).length - 1 == 27,
                "exactly twenty-seven modules register");
        check(
                core.contains("presentationModule, dailyQuestModule)")
                        && !core.contains("List.of(configService, presenceModule"),
                "Presence does not alter seven reload participants");

        realModuleLifecycle();
        friendDelivery();
        String listenerSource =
                Files.readString(
                        Path.of("src/main/java/dev/vapee/core/presence/PresenceListener.java"));
        check(
                !listenerSource.contains("setJoinMessage")
                        && !listenerSource.contains("setQuitMessage")
                        && !listenerSource.contains("Respawn")
                        && !listenerSource.contains("Teleport")
                        && !listenerSource.contains("World"),
                "presence listener does not own global messages or unrelated events");
        System.out.println("PresenceLifecycleHarness passed " + checks + " checks.");
    }

    private static void friendDelivery() throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-presence-delivery-");
        try (var f = new ModuleRuntimeFixture()) {
            ModuleRuntimeFixture.set(f.plugin, "dataFolder", directory.toFile());
            Path configPath = directory.resolve("config.yml");
            Files.writeString(configPath, "debug: false\n");
            var ctor =
                    dev.vapee.core.config.ConfigService.class.getDeclaredConstructor(
                            Path.class, Logger.class);
            ctor.setAccessible(true);
            var config = ctor.newInstance(configPath, f.logger);
            config.load();
            PlayerModule owner = new PlayerModule(f.plugin, config, f.messages);
            owner.enable();
            var players = owner.getPlayerService();
            var settings = owner.getPlayerSettingsService();
            var social = new SocialService(players, f.logger);
            var friends =
                    new FriendService(
                            new dev.vapee.core.friend.FileFriendRepository(
                                    directory.resolve("friends.yml"), f.logger),
                            FriendLimits.defaults(),
                            FriendRequestPolicy.allowAll(),
                            java.time.Clock.systemUTC());
            class Actor {
                final UUID id = UUID.randomUUID();
                final List<net.kyori.adventure.text.Component> messages = new ArrayList<>();
                final org.bukkit.entity.Player player;

                Actor(String name) {
                    player =
                            ModuleRuntimeFixture.proxy(
                                    org.bukkit.entity.Player.class,
                                    (method, args) ->
                                            switch (method) {
                                                case "getUniqueId" -> id;
                                                case "getName" -> name;
                                                case "isOnline" -> true;
                                                case "locale" -> java.util.Locale.US;
                                                case "sendMessage" -> {
                                                    for (Object arg : args)
                                                        if (arg
                                                                instanceof
                                                                net.kyori.adventure.text.Component
                                                                                c) messages.add(c);
                                                    yield null;
                                                }
                                                default -> throw new AssertionError(method);
                                            });
                }
            }
            Actor a = new Actor("Alpha"), b = new Actor("Beta"), unrelated = new Actor("Other");
            var playerListener = (dev.vapee.core.player.PlayerListener) f.registered.getFirst();
            var joinA =
                    new org.bukkit.event.player.PlayerJoinEvent(
                            a.player, net.kyori.adventure.text.Component.empty());
            var joinB =
                    new org.bukkit.event.player.PlayerJoinEvent(
                            b.player, net.kyori.adventure.text.Component.empty());
            var quitA =
                    new org.bukkit.event.player.PlayerQuitEvent(
                            a.player, net.kyori.adventure.text.Component.empty());
            playerListener.onPlayerJoin(joinB);
            playerListener.onPlayerJoin(joinA);
            check(
                    settings.areFriendPresenceNotificationsEnabled(b.id).orElseThrow(),
                    "new PlayerModule profile enables friend presence");
            check(
                    friends.sendRequest(a.id, b.id) == dev.vapee.core.friend.FriendResult.SUCCESS
                            && friends.acceptRequest(b.id, a.id)
                                    == dev.vapee.core.friend.FriendResult.SUCCESS,
                    "real friends persisted before presence events");
            playerListener.onPlayerQuit(quitA);
            f.online.add(b.player);
            f.online.add(unrelated.player);
            PresenceModule presence =
                    new PresenceModule(
                            f.plugin,
                            owner::getPlayerService,
                            owner::getPlayerSettingsService,
                            () -> social,
                            () -> friends,
                            f.messages);
            presence.enable();
            var listener = (PresenceListener) f.registered.getLast();
            check(
                    b.messages.isEmpty(),
                    "enable seeds loaded online friend without synthetic join notification");
            listener.onPlayerJoin(joinA);
            check(b.messages.isEmpty(), "unloaded subject cannot emit premature join");
            playerListener.onPlayerJoin(joinA);
            listener.onPlayerJoin(joinA);
            listener.onPlayerJoin(joinA);
            check(
                    b.messages.size() == 1
                            && plain(b.messages.getLast()).contains("Friend Alpha is now online."),
                    "loaded join reaches online friend once through actual notifier and"
                            + " MessageService");
            check(
                    a.messages.size() == 1
                            && plain(a.messages.getFirst())
                                    .equals("Currently, 1 of your 1 friends is online.")
                            && unrelated.messages.isEmpty(),
                    "one prefix-free self summary after duplicate join; no self status or nonfriend notification");
            listener.onPlayerQuit(quitA);
            listener.onPlayerQuit(quitA);
            check(
                    players.isLoaded(a.id)
                            && b.messages.size() == 2
                            && plain(b.messages.getLast()).contains("Friend Alpha went offline."),
                    "LOWEST quit delivers once while original profile remains loaded");
            playerListener.onPlayerQuit(quitA);
            check(!players.isLoaded(a.id), "NORMAL Player quit saves and unloads after presence");
            settings.setFriendPresenceNotificationsEnabled(b.id, false);
            byte[] optedOut =
                    Files.readAllBytes(directory.resolve("players").resolve(b.id + ".yml"));
            playerListener.onPlayerJoin(joinA);
            listener.onPlayerJoin(joinA);
            listener.onPlayerQuit(quitA);
            playerListener.onPlayerQuit(quitA);
            check(b.messages.size() == 2, "persisted disabled preference suppresses join and quit");
            var cold =
                    new dev.vapee.core.player.repository.FilePlayerRepository(
                            directory.resolve("players"), f.logger);
            check(
                    !cold.findByUniqueId(b.id)
                                    .orElseThrow()
                                    .getSettings()
                                    .isFriendPresenceNotificationsEnabled()
                            && java.util.Arrays.equals(
                                    optedOut,
                                    Files.readAllBytes(
                                            directory.resolve("players").resolve(b.id + ".yml"))),
                    "historical explicit false stays false and is not rewritten by delivery or"
                            + " read");
            settings.setFriendPresenceNotificationsEnabled(b.id, true);
            social.ignore(b.id, a.id);
            playerListener.onPlayerJoin(joinA);
            listener.onPlayerJoin(joinA);
            listener.onPlayerQuit(quitA);
            playerListener.onPlayerQuit(quitA);
            check(b.messages.size() == 2, "recipient Ignore suppresses both transitions");
            social.unignore(b.id, a.id);
            playerListener.onPlayerJoin(joinA);
            social.ignore(a.id, b.id);
            listener.onPlayerJoin(joinA);
            listener.onPlayerQuit(quitA);
            check(b.messages.size() == 2, "subject Ignore also suppresses both transitions");
            social.unignore(a.id, b.id);
            playerListener.onPlayerQuit(quitA);
            players.unloadPlayer(b.id);
            playerListener.onPlayerJoin(joinA);
            listener.onPlayerJoin(joinA);
            listener.onPlayerQuit(quitA);
            playerListener.onPlayerQuit(quitA);
            check(
                    b.messages.size() == 2,
                    "online adapter with unloaded recipient cannot bypass loaded preference"
                            + " contract");
            playerListener.onPlayerJoin(joinB);
            check(
                    settings.areFriendPresenceNotificationsEnabled(b.id).orElseThrow(),
                    "explicit true survives profile reload");
            playerListener.onPlayerJoin(joinA);
            listener.onPlayerJoin(joinA);
            listener.onPlayerQuit(quitA);
            playerListener.onPlayerQuit(quitA);
            check(
                    b.messages.size() == 4,
                    "existing Settings toggle re-enables subsequent join and quit");
            check(
                    players.getPlayer(b.id).orElseThrow().getSettings().isSoundsEnabled()
                            && players.getPlayer(b.id)
                                    .orElseThrow()
                                    .getSettings()
                                    .isScoreboardEnabled(),
                    "notification preference leaves unrelated settings unchanged");
            presence.disable();
            check(
                    f.handlerCount() == 2,
                    "Presence releases its exact handlers while Player remains active");
            owner.disable();
            check(
                    f.handlerCount() == 0
                            && new dev.vapee.core.friend.FileFriendRepository(
                                                    directory.resolve("friends.yml"), f.logger)
                                            .initialize()
                                            .friendships()
                                            .size()
                                    == 1,
                    "shutdown preserves friendship and releases Player handlers");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }

    private static PresenceModule module(ModuleRuntimeFixture f) {
        var social =
                ModuleRuntimeFixture.dependency(
                        SocialModule.class,
                        Map.of("socialService", new SocialService(f.players, f.logger)));
        var friend =
                ModuleRuntimeFixture.dependency(
                        FriendModule.class,
                        Map.of(
                                "active",
                                true,
                                "friendService",
                                new FriendService(
                                        new FriendRepository() {
                                            public FriendSnapshot initialize() {
                                                return FriendSnapshot.empty();
                                            }

                                            public void save(FriendSnapshot snapshot) {}
                                        },
                                        FriendLimits.defaults(),
                                        FriendRequestPolicy.allowAll(),
                                        java.time.Clock.systemUTC())));
        return new PresenceModule(
                f.plugin,
                f.playerModule::getPlayerService,
                f.playerModule::getPlayerSettingsService,
                social::getSocialService,
                friend::getFriendService,
                f.messages);
    }

    private static void realModuleLifecycle() throws Exception {
        try (var f = new ModuleRuntimeFixture()) {
            var loaded = f.player("Loaded", true, true);
            f.player("Unloaded", false, true);
            f.player("Offline", true, false);
            var module = module(f);
            module.enable();
            PresenceService state = module.getPresenceService();
            check(
                    state.getOnlinePlayers().equals(java.util.Set.of(loaded.getUniqueId())),
                    "real enable seeds only loaded online players");
            check(f.sentMessages == 0, "enable seeds presence without notifying friends");
            check(
                    f.registered.size() == 1
                            && f.registered.getFirst() instanceof PresenceListener
                            && f.handlerCount() == 2,
                    "real enable registers the presence join/quit handlers");
            module.disable();
            check(
                    state.getOnlinePlayers().isEmpty() && f.handlerCount() == 0,
                    "disable clears owned state and actual handlers");
            for (String name : List.of("service", "notifier", "listener")) {
                check(ModuleRuntimeFixture.get(module, name) == null, "disable releases " + name);
            }
            disabledGetter(module);
            module.disable();
            module.enable();
            check(
                    module.getPresenceService() != state && f.handlerCount() == 2,
                    "re-enable creates fresh state and handlers");
            module.disable();
        }
        for (boolean failAfterRegistration : List.of(false, true)) {
            try (var f = new ModuleRuntimeFixture()) {
                f.failRegistration = failAfterRegistration ? 0 : 1;
                f.failOnlineRead = failAfterRegistration;
                var module = module(f);
                boolean failed = false;
                try {
                    module.enable();
                } catch (IllegalStateException expected) {
                    failed = true;
                }
                check(
                        failed && f.handlerCount() == 0,
                        "failed registration/seeding releases actual handlers");
                disabledGetter(module);
            }
        }
    }

    private static void disabledGetter(PresenceModule module) {
        try {
            module.getPresenceService();
            throw new AssertionError("disabled module exposed its service");
        } catch (NullPointerException expected) {
            check(true, "disabled service getter fails closed");
        }
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

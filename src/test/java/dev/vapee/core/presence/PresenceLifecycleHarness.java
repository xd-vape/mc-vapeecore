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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class PresenceLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(CoreModule.class.isAssignableFrom(PresenceModule.class)
                        && !ReloadParticipant.class.isAssignableFrom(PresenceModule.class),
                "Presence is one CoreModule and not reloadable");
        check(List.of(PresenceModule.class.getConstructor(JavaPlugin.class, PlayerModule.class,
                        SocialModule.class, FriendModule.class, MessageService.class).getParameterTypes())
                        .equals(List.of(JavaPlugin.class, PlayerModule.class,
                                SocialModule.class, FriendModule.class, MessageService.class)),
                "module dependencies are explicit");

        Method join = PresenceListener.class.getMethod("onPlayerJoin",
                org.bukkit.event.player.PlayerJoinEvent.class);
        Method quit = PresenceListener.class.getMethod("onPlayerQuit",
                org.bukkit.event.player.PlayerQuitEvent.class);
        check(join.getAnnotation(EventHandler.class).priority() == EventPriority.MONITOR,
                "join observes after player load at MONITOR");
        check(quit.getAnnotation(EventHandler.class).priority() == EventPriority.LOWEST,
                "quit transitions before player and social cleanup");

        PresenceService service = new PresenceService();
        UUID subject = UUID.randomUUID();
        List<PresenceStatus> statuses = new ArrayList<>();
        PresenceListener listener = new PresenceListener(service, id -> id.equals(subject),
                (id, name, status) -> statuses.add(status), quietLogger());
        listener.handleJoin(UUID.randomUUID(), "Unloaded");
        check(service.getOnlinePlayers().isEmpty() && statuses.isEmpty(),
                "unloaded join is ignored without notification");
        listener.handleJoin(subject, "Alice");
        listener.handleJoin(subject, "Alice");
        listener.handleQuit(subject, "Alice");
        listener.handleQuit(subject, "Alice");
        check(statuses.equals(List.of(PresenceStatus.ONLINE, PresenceStatus.OFFLINE)),
                "only real transitions notify once");

        PresenceListener failing = new PresenceListener(service, id -> true,
                (id, name, status) -> { throw new IllegalStateException("notification failure"); }, quietLogger());
        failing.handleJoin(subject, "Alice");
        check(service.isOnline(subject), "notification failure does not roll back presence transition");

        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(core.indexOf("moduleManager.register(friendModule)")
                        < core.indexOf("moduleManager.register(presenceModule)")
                        && core.indexOf("moduleManager.register(presenceModule)")
                        < core.indexOf("moduleManager.register(clanModule)"),
                "module order is Friend then Presence then Clan");
        check(core.split("moduleManager.register\\(", -1).length - 1 == 28,
                "exactly twenty-eight modules register");
        check(core.contains("presentationModule, dailyQuestModule)")
                        && !core.contains("List.of(configService, presenceModule"),
                "Presence does not alter seven reload participants");

        realModuleLifecycle();
        String listenerSource = Files.readString(Path.of("src/main/java/dev/vapee/core/presence/PresenceListener.java"));
        check(!listenerSource.contains("setJoinMessage") && !listenerSource.contains("setQuitMessage")
                        && !listenerSource.contains("Respawn") && !listenerSource.contains("Teleport")
                        && !listenerSource.contains("World"),
                "presence listener does not own global messages or unrelated events");
        System.out.println("PresenceLifecycleHarness passed " + checks + " checks.");
    }

    private static PresenceModule module(ModuleRuntimeFixture f) {
        var social = ModuleRuntimeFixture.dependency(SocialModule.class,
                Map.of("socialService", new SocialService(f.players, f.logger)));
        var friend = ModuleRuntimeFixture.dependency(FriendModule.class, Map.of("friendService",
                new FriendService(new FriendRepository() {
                    public FriendSnapshot initialize() { return FriendSnapshot.empty(); }
                    public void save(FriendSnapshot snapshot) { }
                }, FriendLimits.defaults(), FriendRequestPolicy.allowAll(),
                        java.time.Clock.systemUTC())));
        return new PresenceModule(f.plugin, f.playerModule, social, friend, f.messages);
    }

    private static void realModuleLifecycle() throws Exception {
        try (var f = new ModuleRuntimeFixture()) {
            var loaded = f.player("Loaded", true, true);
            f.player("Unloaded", false, true);
            f.player("Offline", true, false);
            var module = module(f);
            module.enable();
            PresenceService state = module.getPresenceService();
            check(state.getOnlinePlayers().equals(java.util.Set.of(loaded.getUniqueId())),
                    "real enable seeds only loaded online players");
            check(f.sentMessages == 0, "enable seeds presence without notifying friends");
            check(f.registered.size() == 1 && f.registered.getFirst() instanceof PresenceListener && f.handlerCount() == 2,
                    "real enable registers the presence join/quit handlers");
            module.disable();
            check(state.getOnlinePlayers().isEmpty() && f.handlerCount() == 0, "disable clears owned state and actual handlers");
            for (String name : List.of("service", "notifier", "listener")) {
                check(ModuleRuntimeFixture.get(module, name) == null, "disable releases " + name);
            }
            disabledGetter(module);
            module.disable();
            module.enable();
            check(module.getPresenceService() != state && f.handlerCount() == 2, "re-enable creates fresh state and handlers");
            module.disable();
        }
        for (boolean failAfterRegistration : List.of(false, true)) {
            try (var f = new ModuleRuntimeFixture()) {
                f.failRegistration = failAfterRegistration ? 0 : 1;
                f.failOnlineRead = failAfterRegistration;
                var module = module(f);
                boolean failed = false;
                try { module.enable(); } catch (IllegalStateException expected) { failed = true; }
                check(failed && f.handlerCount() == 0, "failed registration/seeding releases actual handlers");
                disabledGetter(module);
            }
        }
    }

    private static void disabledGetter(PresenceModule module) {
        try { module.getPresenceService(); throw new AssertionError("disabled module exposed its service"); }
        catch (NullPointerException expected) { check(true, "disabled service getter fails closed"); }
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

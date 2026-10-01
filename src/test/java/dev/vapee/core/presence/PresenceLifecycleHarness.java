package dev.vapee.core.presence;

import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.social.SocialModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        check(core.split("moduleManager.register\\(", -1).length - 1 == 27,
                "exactly twenty-seven modules register");
        check(core.contains("presentationModule, dailyQuestModule)")
                        && !core.contains("List.of(configService, presenceModule"),
                "Presence does not alter six reload participants");

        String module = Files.readString(Path.of("src/main/java/dev/vapee/core/presence/PresenceModule.java"));
        check(module.contains("registerEvents(newListener, plugin)")
                        && module.contains("HandlerList.unregisterAll(newListener)")
                        && module.contains("HandlerList.unregisterAll(listener)"),
                "listener registration is rollback-safe and disabled cleanly");
        check(module.contains("playerService.isLoaded(uniqueId)")
                        && module.contains("player.isOnline()")
                        && module.contains("newService.markOnline(uniqueId)"),
                "enable seeds only online loaded players without notifier calls");
        check(module.contains("service.clear()") && module.contains("service = null")
                        && module.contains("requireNonNull(service"),
                "disable clears state and getter is enabled-only");
        String listenerSource = Files.readString(Path.of("src/main/java/dev/vapee/core/presence/PresenceListener.java"));
        check(!listenerSource.contains("setJoinMessage") && !listenerSource.contains("setQuitMessage")
                        && !listenerSource.contains("Respawn") && !listenerSource.contains("Teleport")
                        && !listenerSource.contains("World"),
                "presence listener does not own global messages or unrelated events");
        System.out.println("PresenceLifecycleHarness passed " + checks + " checks.");
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

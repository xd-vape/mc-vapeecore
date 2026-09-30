package dev.vapee.core.moderation;

import static dev.vapee.core.moderation.ModerationTestSupport.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ModerationMuteEnforcementHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Fixture f = new Fixture();
        var tasks = new ArrayList<Runnable>();
        Thread mainThread = Thread.currentThread();
        var listener = new ModerationMuteChatListener(f.projection, task -> {
            check(Thread.currentThread() != mainThread, "schedule from real chat thread"); tasks.add(task);
        }, id -> {
            check(Thread.currentThread() == mainThread, "lookup on main thread");
            return f.online.containsKey(id) ? (Player) f.online.get(id).sender : null;
        }, f.messages, f.logger);
        var handler = ModerationMuteChatListener.class.getMethod("onAsyncChat", AsyncChatEvent.class).getAnnotation(EventHandler.class);
        check(handler.priority() == EventPriority.LOWEST && handler.ignoreCancelled(), "LOWEST ignoreCancelled");
        var chatHandler = dev.vapee.core.chat.ChatListener.class.getMethod("onAsyncChat", AsyncChatEvent.class).getAnnotation(EventHandler.class);
        check(chatHandler.priority() == EventPriority.NORMAL && chatHandler.ignoreCancelled(), "existing formatter skips cancelled events");
        check(!async(listener, f).isCancelled() && tasks.isEmpty(), "unmuted passes unchanged");
        f.service.issueWarning(TARGET, ModerationActor.console(), "warning");
        f.service.recordKick(TARGET, ModerationActor.console(), "kick");
        f.service.issueBan(TARGET, ModerationActor.console(), "ban", Optional.empty());
        check(!async(listener, f).isCancelled(), "non-mutes do not block chat");
        f.repository.fail = true;
        try { f.service.issueMute(TARGET, ModerationActor.console(), ATTACK, Optional.empty()); throw new AssertionError("failed save accepted"); }
        catch (ModerationRepositoryException expected) { checks++; }
        check(!async(listener, f).isCancelled(), "failed mute allows chat");
        f.repository.fail = false;
        f.service.issueMute(TARGET, ModerationActor.console(), ATTACK, Optional.of(NOW.plusSeconds(10)));
        f.failIdentity = true; // An async identity lookup would now fail this test.
        f.target.beforeNotification = () -> check(Thread.currentThread() == mainThread, "messages only main thread");
        var blocked = async(listener, f);
        ChatRenderer originalRenderer = blocked.renderer();
        check(blocked.isCancelled() && tasks.size() == 1 && f.target.output.isEmpty(), "cancel now, feedback deferred");
        int formatterCalls = 0;
        // Paper dispatch rule for the actual NORMAL formatter annotation.
        if (!(chatHandler.ignoreCancelled() && blocked.isCancelled())) formatterCalls++;
        check(formatterCalls == 0, "cancelled event cannot enter normal formatter");
        check(blocked.renderer() == originalRenderer, "mute cancellation never replaces renderer");
        tasks.removeFirst().run();
        check(f.target.text().contains(ATTACK) && f.target.text().contains("UTC")
                && f.target.output.stream().allMatch(ModerationTestSupport::noEvents), "literal timed feedback");
        f.repository.fail = true;
        try { f.service.revokeMute(TARGET, ModerationActor.console(), Optional.empty()); throw new AssertionError("failed revoke accepted"); }
        catch (ModerationRepositoryException expected) { checks++; }
        check(async(listener, f).isCancelled(), "failed unmute continues blocking"); tasks.clear();
        f.repository.fail = false; f.service.revokeMute(TARGET, ModerationActor.console(), Optional.empty());
        check(!async(listener, f).isCancelled(), "committed unmute immediate without reload");
        f.service.issueMute(TARGET, ModerationActor.console(), ATTACK, Optional.of(NOW.plusSeconds(10)));
        f.clock.now = NOW.plusSeconds(10);
        int saves = f.repository.saves;
        check(!async(listener, f).isCancelled() && f.repository.saves == saves, "expiry read-only at exact instant");
        f.clock.now = NOW.plusSeconds(11);
        check(!async(listener, f).isCancelled(), "after expiry allows chat without republish");
        f.service.issueMute(TARGET, ModerationActor.console(), ATTACK, Optional.empty());
        check(async(listener, f).isCancelled(), "permanent blocks");
        tasks.removeFirst().run(); check(f.target.text().contains("Permanent"), "permanent feedback");
        async(listener, f); f.online.remove(TARGET); int outputs = f.target.output.size(); tasks.removeFirst().run();
        check(f.target.output.size() == outputs, "offline before task no queued notification"); f.online.put(TARGET, f.target);
        var failedScheduler = new ModerationMuteChatListener(f.projection, task -> { throw new IllegalStateException("scheduler closed"); },
                id -> (Player) f.target.sender, f.messages, f.logger);
        check(async(failedScheduler, f).isCancelled() && f.severe() == 1, "scheduler failure logged, never uncancels");
        AsyncChatEvent alreadyCancelled = event(f); alreadyCancelled.setCancelled(true); listener.onAsyncChat(alreadyCancelled);
        check(tasks.isEmpty(), "pre-cancelled event untouched");
        async(listener, f); listener.deactivate(); f.projection.clear(); tasks.removeFirst().run();
        check(f.target.output.size() == outputs && !async(listener, f).isCancelled(), "disable suppresses pending feedback and clears enforcement");
        String source = Files.readString(Path.of("src/main/java/dev/vapee/core/moderation/ModerationMuteChatListener.java"));
        for (String forbidden : List.of("ModerationService", "ModerationRepository", "PlayerIdentityService", "PlayerService", "runTaskTimer", "MiniMessage"))
            check(!source.contains(forbidden), "no asynchronous forbidden dependency " + forbidden);
        check(Arrays.stream(ModerationMuteChatListener.class.getDeclaredFields()).noneMatch(field -> field.getType() == ModerationService.class), "no service field");
        check(Arrays.stream(ModerationMuteChatListener.class.getDeclaredConstructors()).noneMatch(c -> List.of(c.getParameterTypes()).contains(ModerationService.class)), "no service constructor");
        System.out.println("ModerationMuteEnforcementHarness passed " + checks + " checks.");
    }
    private static AsyncChatEvent async(ModerationMuteChatListener listener, Fixture fixture) throws Exception {
        var event = event(fixture); var error = new AtomicReference<Throwable>();
        Thread chatThread = new Thread(() -> { try { listener.onAsyncChat(event); } catch (Throwable t) { error.set(t); } }, "paper-chat-test");
        chatThread.start(); chatThread.join(10000);
        check(!chatThread.isAlive() && error.get() == null, "actual async boundary without failure");
        return event;
    }
    private static AsyncChatEvent event(Fixture f) {
        Component message = Component.text("chat");
        return new AsyncChatEvent(true, (Player) f.target.sender, new HashSet<>(), ChatRenderer.defaultRenderer(),
                message, message, SignedMessage.system("chat", message));
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}

package dev.vapee.core.quest;

import dev.vapee.core.chat.ChatService;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.privatemessage.PrivateMessageService;
import dev.vapee.core.utility.tpa.TeleportRequests;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Success observations only. Domain services never depend on quests; all progress runs on main. */
public final class QuestCommunityProducer implements Listener, PrivateMessageService.DeliveryListener,
        FriendService.SuccessListener, TeleportRequests.SuccessListener {
    static final long CHAT_COOLDOWN = java.time.Duration.ofSeconds(3).toNanos();
    static final long DUPLICATE_WINDOW = java.time.Duration.ofSeconds(120).toNanos();
    static final int MAX_FINGERPRINTS = 32, MAX_PENDING = 1024;
    static final QuestProgressKey HELLO = QuestProgressKey.of("social:chat_hello"),
            THANKS = QuestProgressKey.of("social:chat_thanks"), UNIQUE = QuestProgressKey.of("social:chat_unique"),
            PM = QuestProgressKey.of("social:pm_sent"), REPLY = QuestProgressKey.of("social:pm_reply"),
            REQUEST = QuestProgressKey.of("social:friend_request"), FRIEND = QuestProgressKey.of("social:friend_accepted"),
            VISIT = QuestProgressKey.of("social:tpa_visit"), HOST = QuestProgressKey.of("social:tpa_host");
    private final PlayerService players;
    private final QuestDefinitionRegistry definitions;
    private final QuestService quests;
    private final QuestProgressReporter reporter;
    private final Function<UUID, Player> online;
    private final BooleanSupplier primaryThread, authorized;
    private final Consumer<Runnable> schedule;
    private final LongSupplier clock;
    private final Logger logger;
    private final Map<UUID, ChatState> chatStates = new HashMap<>();
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ChatInput> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final List<Runnable> releases = new ArrayList<>();
    private final Consumer<UUID> assignments = this::invalidate;
    private ChatService chat;
    private PrivateMessageService pm;
    private TeleportRequests requests;
    private Predicate<UUID> muted;
    private BooleanSupplier available;
    private volatile boolean open;

    QuestCommunityProducer(PlayerService players, QuestDefinitionRegistry definitions, QuestService quests,
            QuestProgressReporter reporter, Function<UUID, Player> online, BooleanSupplier primaryThread,
            Consumer<Runnable> schedule, BooleanSupplier authorized, LongSupplier clock, Logger logger) {
        this.players = Objects.requireNonNull(players);this.definitions = Objects.requireNonNull(definitions);
        this.quests = Objects.requireNonNull(quests);this.reporter = Objects.requireNonNull(reporter);
        this.online = Objects.requireNonNull(online);this.primaryThread = Objects.requireNonNull(primaryThread);
        this.schedule = Objects.requireNonNull(schedule);this.authorized = Objects.requireNonNull(authorized);
        this.clock = Objects.requireNonNull(clock);this.logger = Objects.requireNonNull(logger);
    }

    void bind(ChatService chat, PrivateMessageService pm, FriendService friends, TeleportRequests requests,
            Predicate<UUID> muted, BooleanSupplier available) {
        requireThread();
        if (open || !releases.isEmpty()) throw new IllegalStateException("Community producers already bound or awaiting cleanup");
        this.chat = Objects.requireNonNull(chat);this.pm = Objects.requireNonNull(pm);
        this.requests = Objects.requireNonNull(requests);this.muted = Objects.requireNonNull(muted);
        this.available = Objects.requireNonNull(available);
        own(() -> quests.addAssignmentListener(assignments), () -> quests.removeAssignmentListener(assignments));
        own(() -> pm.addDeliveryListener(this), () -> pm.removeDeliveryListener(this));
        own(() -> friends.addSuccessListener(this), () -> friends.removeSuccessListener(this));
        own(() -> requests.addSuccessListener(this), () -> requests.removeSuccessListener(this));
        open = true;
    }

    private void own(Runnable acquire, Runnable release) { releases.add(release);acquire.run(); }

    private record Session(int entity) { }
    private record ChatInput(Session session, Object catalog, Object chatConfig, CommunityChatText.Message text) { }
    private record Context(CorePlayer profile, Object assignments, Object catalog) { }
    private static final class ChatState {
        final Session session;
        final Context context;
        final LinkedHashMap<String, Long> fingerprints = new LinkedHashMap<>();
        long last;
        boolean counted;
        ChatState(Session session, Context context) { this.session = session;this.context = context; }
    }

    void seed(Player player) {
        requireThread();
        if (open && online.apply(player.getUniqueId()) == player) {
            invalidate(player.getUniqueId());
            sessions.put(player.getUniqueId(), new Session(player.getEntityId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { seed(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        requireThread();
        UUID id = event.getPlayer().getUniqueId();
        Session session = sessions.get(id);
        if (session != null && session.entity == event.getPlayer().getEntityId() && sessions.remove(id, session)) {
            pending.remove(id);chatStates.remove(id);
        }
    }

    private void invalidate(UUID id) {
        requireThread();
        sessions.computeIfPresent(id, (key, old) -> new Session(old.entity));
        pending.remove(id);chatStates.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!open || event.isCancelled()) return;
        UUID id = event.getPlayer().getUniqueId();
        try {
            Session session = sessions.get(id);
            if (session == null || session.entity != event.getPlayer().getEntityId()
                    || !chat.isEnabled() || muted.test(id) || event.viewers().isEmpty()) return;
            var text = CommunityChatText.parse(PlainTextComponentSerializer.plainText().serialize(event.message()));
            if (text == null) return;
            ChatInput input = new ChatInput(session, definitions.snapshotById(), chat.getState(), text);
            synchronized (pending) {
                if (pending.size() >= MAX_PENDING || pending.putIfAbsent(id, input) != null) return;
            }
            if (!open || sessions.get(id) != session) { pending.remove(id, input);return; }
            if (scheduled.compareAndSet(false, true)) {
                try { schedule.accept(this::drain); }
                catch (RuntimeException failure) { pending.clear();scheduled.set(false);throw failure; }
            }
        } catch (RuntimeException failure) { failed(id, "chat handoff", failure); }
    }

    private void drain() {
        requireThread();
        // Release the one-shot gate before reading the batch so arrivals always get a later drain.
        scheduled.set(false);
        for (var entry : List.copyOf(pending.entrySet())) {
            if (!pending.remove(entry.getKey(), entry.getValue())) continue;
            try { chat(entry.getKey(), entry.getValue()); }
            catch (RuntimeException failure) { chatStates.remove(entry.getKey());failed(entry.getKey(), "chat progress", failure); }
        }
    }

    private void chat(UUID id, ChatInput input) {
        if (!live() || sessions.get(id) != input.session || !chat.isEnabled()
                || chat.getState() != input.chatConfig || definitions.snapshotById() != input.catalog || muted.test(id)) return;
        Player player = online.apply(id);
        Context context = context(player);
        if (context == null || player.getEntityId() != input.session.entity) { chatStates.remove(id);return; }
        var text = input.text;
        boolean hello = text.hello() && wants(context, HELLO), thanks = text.thanks() && wants(context, THANKS),
                unique = text.unique() && wants(context, UNIQUE);
        if (!hello && !thanks && !unique) { chatStates.remove(id);return; }
        ChatState state = chatStates.get(id);
        if (state == null || state.session != input.session || !same(state.context, context)) {
            state = new ChatState(input.session, context);chatStates.put(id, state);
        }
        long now = clock.getAsLong();
        state.fingerprints.values().removeIf(at -> now - at < 0 || now - at >= DUPLICATE_WINDOW);
        if (state.fingerprints.containsKey(text.fingerprint())
                || state.counted && now - state.last >= 0 && now - state.last < CHAT_COOLDOWN) return;
        state.fingerprints.put(text.fingerprint(), now);
        while (state.fingerprints.size() > MAX_FINGERPRINTS) state.fingerprints.remove(state.fingerprints.keySet().iterator().next());
        state.last = now;state.counted = true;
        if (hello) report(player, context, HELLO);
        if (thanks) report(player, context, THANKS);
        if (unique) report(player, context, UNIQUE);
    }

    private boolean live() {
        if (!open || !authorized.getAsBoolean()) return false;
        try { return available.getAsBoolean(); }
        catch (IllegalStateException | NullPointerException ownerUnavailable) { return false; }
    }

    private Context context(Player player) {
        requireThread();
        if (!live() || player == null || !player.isOnline() || online.apply(player.getUniqueId()) != player) return null;
        var loaded = players.getPlayer(player.getUniqueId());
        return loaded.map(profile -> new Context(profile, profile.getQuestState().assignmentToken(), definitions.snapshotById())).orElse(null);
    }

    private static boolean same(Context a, Context b) {
        return a.profile == b.profile && a.assignments == b.assignments && a.catalog == b.catalog;
    }

    private boolean wants(Context context, QuestProgressKey key) {
        for (var progress : context.profile.getQuestState().progressEntries()) {
            var definition = definitions.snapshotById().get(progress.questId());
            if (progress.status() == QuestStatus.ACTIVE && definition != null
                    && definition.progressKey().equals(key) && progress.progress() < definition.target()) return true;
        }
        return false;
    }

    private void report(Player player, Context expected, QuestProgressKey key) {
        try {
            if ((key.equals(HELLO) || key.equals(THANKS) || key.equals(UNIQUE)) && (!chat.isEnabled() || muted.test(player.getUniqueId()))) return;
            if ((key.equals(PM) || key.equals(REPLY)) && (!pm.isEnabled() || muted.test(player.getUniqueId()))) return;
            if ((key.equals(VISIT) || key.equals(HOST)) && !requests.isActive()) return;
            Context current = context(player);
            if (current != null && same(expected, current) && wants(current, key)) reporter.report(player.getUniqueId(), key, 1);
        } catch (RuntimeException failure) { failed(player.getUniqueId(), key.value(), failure); }
    }

    @Override
    public void onDelivered(Player sender, Player recipient, boolean reply) {
        requireThread();
        if (!live() || !pm.isEnabled() || sender.getUniqueId().equals(recipient.getUniqueId())
                || online.apply(recipient.getUniqueId()) != recipient || !recipient.isOnline() || muted.test(sender.getUniqueId())) return;
        Context context = context(sender);
        if (context != null) { report(sender, context, PM);if (reply) report(sender, context, REPLY); }
    }

    @Override
    public void onSuccess(UUID sender, UUID recipient, boolean friendship) {
        requireThread();
        if (!live() || sender.equals(recipient)) return;
        Player first = online.apply(sender), second = online.apply(recipient);
        Context a = context(first), b = context(second);
        if (a != null) report(first, a, friendship ? FRIEND : REQUEST);
        if (friendship && b != null) report(second, b, FRIEND);
    }

    @Override
    public void onTeleport(Player requester, Player recipient, boolean here) {
        requireThread();
        if (!live() || !requests.isActive() || requester.getUniqueId().equals(recipient.getUniqueId())) return;
        Context a = context(requester), b = context(recipient);
        if (a == null || b == null) return;
        if (!here) report(requester, a, VISIT);
        report(recipient, b, HOST);
    }

    void close() {
        requireThread();open = false;pending.clear();sessions.clear();chatStates.clear();
        RuntimeException failure = null;
        for (var release : List.copyOf(releases)) {
            try { release.run();releases.remove(release); }
            catch (RuntimeException next) { if (failure == null) failure = next;else if (next != failure) failure.addSuppressed(next); }
        }
        if (failure != null) throw failure;
    }

    boolean released() { return !open && releases.isEmpty(); }
    int trackedPlayers() { return chatStates.size(); }
    int pendingChats() { return pending.size(); }
    private void requireThread() { if (!primaryThread.getAsBoolean()) throw new IllegalStateException("Community progress requires the main thread"); }
    private void failed(UUID id, String operation, RuntimeException failure) {
        try { logger.log(Level.WARNING, "Community quest " + operation + " failed for " + id
                + "; successful social action is retained, no automatic replay.", failure); }
        catch (RuntimeException diagnostic) { if (diagnostic != failure) failure.addSuppressed(diagnostic); }
    }
}

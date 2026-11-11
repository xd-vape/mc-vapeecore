package dev.vapee.core.quest;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.economy.command.CoinsCommand;
import dev.vapee.core.friend.gui.FriendMenu;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import dev.vapee.core.settings.SettingsMenu;
import dev.vapee.core.settings.visibility.VisibilitySettingsMenu;
import dev.vapee.core.utility.command.PingCommand;
import dev.vapee.core.visibility.VisibilityService;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

import java.util.*;
import java.util.function.*;
import java.util.logging.*;

/** Post-success adapters and a bounded event-driven spatial encounter sampler. Main thread only. */
public final class QuestLobbyProducer implements Listener, LobbyPlayerStateListener, ActivityMembershipListener {
    static final int MAX_SESSIONS = 4096, MAX_PENDING = 1024, SCANS_PER_DRAIN = 32, CANDIDATES_PER_SCAN = 32;
    static final long ENCOUNTER_INTERVAL = 20;
    static final double ENCOUNTER_RADIUS = 6;
    static final QuestProgressKey MEET = QuestProgressKey.of("social:meet_unique");
    private final PlayerService players;
    private final QuestDefinitionRegistry definitions;
    private final QuestService quests;
    private final QuestProgressReporter reporter;
    private final Function<UUID, Player> online;
    private final Predicate<Player> eligible;
    private final BiPredicate<Player, Player> visible;
    private final Function<Player, Collection<Player>> nearby;
    private final Function<Runnable, Runnable> schedule;
    private final BooleanSupplier authorized, primaryThread;
    private final LongSupplier tick;
    private final Logger logger;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final LinkedHashMap<UUID, Context> pending = new LinkedHashMap<>();
    private final List<Runnable> releases = new ArrayList<>();
    private final Consumer<UUID> changed = this::changed;
    private BooleanSupplier available = () -> false;
    private Runnable cancellation;
    private boolean open;

    private static final class Session {
        final int entity;
        boolean sampled;
        long last;
        int offset;
        Session(int entity) { this.entity = entity; }
    }
    private record Context(Session session, CorePlayer profile, Object assignments, Object catalog) { }

    QuestLobbyProducer(PlayerService players, QuestDefinitionRegistry definitions, QuestService quests,
            QuestProgressReporter reporter, Function<UUID, Player> online, Predicate<Player> eligible,
            BiPredicate<Player, Player> visible, Function<Player, Collection<Player>> nearby,
            Function<Runnable, Runnable> schedule, BooleanSupplier authorized, BooleanSupplier primaryThread,
            LongSupplier tick, Logger logger) {
        this.players = Objects.requireNonNull(players); this.definitions = Objects.requireNonNull(definitions);
        this.quests = Objects.requireNonNull(quests); this.reporter = Objects.requireNonNull(reporter);
        this.online = Objects.requireNonNull(online); this.eligible = Objects.requireNonNull(eligible);
        this.visible = Objects.requireNonNull(visible); this.nearby = Objects.requireNonNull(nearby);
        this.schedule = Objects.requireNonNull(schedule); this.authorized = Objects.requireNonNull(authorized);
        this.primaryThread = Objects.requireNonNull(primaryThread); this.tick = Objects.requireNonNull(tick);
        this.logger = Objects.requireNonNull(logger);
    }

    void bind(LobbyService lobby, WarpService warps, NavigatorMenu navigator, DailyQuestMenu daily,
            SettingsMenu settings, FriendMenu friends, VisibilitySettingsMenu visibilityMenu,
            CoinsCommand coins, PingCommand ping,
            VisibilityService visibility, BooleanSupplier available) {
        requireThread();
        if (open || !releases.isEmpty()) throw new IllegalStateException("Lobby producer already bound or awaiting cleanup");
        this.available = Objects.requireNonNull(available);
        Consumer<Player> spawn = p -> action(p, "lobby:spawn_arrived");
        WarpService.SuccessListener warp = (p, id, navigatorSource) -> {
            var context = context(p);
            if (navigatorSource) report(p, context, QuestProgressKey.of("lobby:warp_visit"), null);
            report(p, context, QuestProgressKey.of("lobby:warp_unique"), warpIdentity(id));
        };
        Consumer<Player> nav = p -> action(p, "lobby:navigator_open"),
                setting = p -> action(p, "lobby:settings_open"), friend = p -> action(p, "lobby:friends_open"),
                visibilityOpen = p -> action(p, "lobby:visibility_open"), balance = p -> action(p, "lobby:coins_check");
        BiConsumer<Player, DailyQuestMenu.OpenSource> questMenu = (player, source) -> {
            action(player, "lobby:quests_open");
            if (source == DailyQuestMenu.OpenSource.COMMAND_OR_NPC) {
                action(player, "lobby:rank_check");
                action(player, "lobby:ranks_check");
            }
        };
        BiConsumer<Player, Player> latency = (p, target) -> {
            if (context(target) != null) action(p, p.getUniqueId().equals(target.getUniqueId()) ? "lobby:ping_self" : "lobby:ping_other");
        };
        own(() -> quests.addAssignmentListener(changed), () -> quests.removeAssignmentListener(changed));
        own(() -> lobby.addSuccessListener(spawn), () -> lobby.removeSuccessListener(spawn));
        own(() -> warps.addSuccessListener(warp), () -> warps.removeSuccessListener(warp));
        own(() -> navigator.addSuccessListener(nav), () -> navigator.removeSuccessListener(nav));
        own(() -> daily.addSuccessListener(questMenu), () -> daily.removeSuccessListener(questMenu));
        own(() -> settings.addSuccessListener(setting), () -> settings.removeSuccessListener(setting));
        own(() -> friends.addSuccessListener(friend), () -> friends.removeSuccessListener(friend));
        own(() -> visibilityMenu.addSuccessListener(visibilityOpen), () -> visibilityMenu.removeSuccessListener(visibilityOpen));
        own(() -> coins.addSuccessListener(balance), () -> coins.removeSuccessListener(balance));
        own(() -> ping.addSuccessListener(latency), () -> ping.removeSuccessListener(latency));
        own(() -> visibility.addRefreshListener(changed), () -> visibility.removeRefreshListener(changed));
        open = true;
    }

    void own(Runnable acquire, Runnable release) { releases.add(release); acquire.run(); }

    // Warp IDs have no existing length ceiling; a digest bounds storage without rejecting valid IDs.
    static String warpIdentity(String id) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "warp:" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private boolean live() {
        if (!open || !authorized.getAsBoolean()) return false;
        try { return available.getAsBoolean(); }
        catch (IllegalStateException | NullPointerException unavailable) { return false; }
    }

    void seed(Player player) {
        requireThread();
        if (!open || online.apply(player.getUniqueId()) != player || !player.isOnline()) return;
        UUID id = player.getUniqueId();
        pending.remove(id);
        if (!sessions.containsKey(id) && sessions.size() >= MAX_SESSIONS) return;
        sessions.put(id, new Session(player.getEntityId()));
        queue(player);
    }

    private Context context(Player player) {
        requireThread();
        if (!live() || player == null || !player.isOnline() || online.apply(player.getUniqueId()) != player) return null;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.entity != player.getEntityId()) return null;
        var profile = players.getPlayer(player.getUniqueId()).orElse(null);
        return profile == null ? null : new Context(session, profile, profile.getQuestState().assignmentToken(), definitions.snapshotById());
    }

    private static boolean same(Context first, Context second) {
        return first != null && second != null && first.session == second.session && first.profile == second.profile
                && first.assignments == second.assignments && first.catalog == second.catalog;
    }

    private boolean wants(Context context, QuestProgressKey key) {
        if (context == null) return false;
        return context.profile.getQuestState().progressEntries().stream().anyMatch(progress -> {
            var definition = definitions.findById(progress.questId()).orElse(null);
            return progress.status() == QuestStatus.ACTIVE && definition != null && definition.progressKey().equals(key)
                    && progress.progress() < definition.target();
        });
    }

    void action(Player player, String key) { report(player, context(player), QuestProgressKey.of(key), null); }

    private void report(Player player, Context expected, QuestProgressKey key, String identity) {
        try {
            if (!same(expected, context(player)) || !wants(expected, key)) return;
            if (identity == null) reporter.report(player.getUniqueId(), key, 1);
            else reporter.reportDistinct(player.getUniqueId(), key, identity);
        } catch (RuntimeException failure) { failed(failure); }
    }

    private void queue(Player player) {
        try {
            // The bundled disabled/empty catalog must add no spatial work to ordinary movement.
            if (definitions.snapshotById().values().stream().noneMatch(definition -> definition.progressKey().equals(MEET))) return;
            Context context = context(player);
            if (context == null || !eligible.test(player)) return;
            long now = tick.getAsLong();
            if (context.session.sampled && now - context.session.last >= 0 && now - context.session.last < ENCOUNTER_INTERVAL) return;
            if (!pending.containsKey(player.getUniqueId()) && pending.size() >= MAX_PENDING) return;
            pending.putIfAbsent(player.getUniqueId(), context);
            scheduleDrain();
        } catch (RuntimeException failure) { failed(failure); }
    }

    private void scheduleDrain() {
        if (cancellation == null && !pending.isEmpty()) cancellation = schedule.apply(this::drain);
    }

    void drain() {
        requireThread(); cancellation = null;
        if (!live()) { pending.clear(); return; }
        int scans = 0;
        while (!pending.isEmpty() && scans++ < SCANS_PER_DRAIN) {
            var entry = pending.entrySet().iterator().next();
            UUID id = entry.getKey(); Context expected = entry.getValue(); pending.remove(id);
            try {
                Player player = online.apply(id);
                if (!same(expected, context(player)) || !eligible.test(player)) continue;
                expected.session.sampled = true; expected.session.last = tick.getAsLong();
                var found = nearby.apply(player);
                List<Player> candidates = found instanceof List<Player> list && list instanceof RandomAccess
                        ? list : new ArrayList<>(found);
                int count = candidates.size(), start = count == 0 ? 0 : Math.floorMod(expected.session.offset, count);
                for (int i = 0; i < Math.min(CANDIDATES_PER_SCAN, count); i++)
                    encounter(player, expected, candidates.get((start + i) % count));
                expected.session.offset = (int) ((start + (long) CANDIDATES_PER_SCAN) % Math.max(1, count));
            } catch (RuntimeException failure) { failed(failure); }
        }
        scheduleDrain();
    }

    private void encounter(Player player, Context expected, Player target) {
        Context other = context(target);
        if (other == null || player.getUniqueId().equals(target.getUniqueId()) || !eligible.test(target)
                || !eligible.test(player) || player.getWorld() != target.getWorld()
                || player.getLocation().distanceSquared(target.getLocation()) > ENCOUNTER_RADIUS * ENCOUNTER_RADIUS) return;
        if (visible.test(player, target)) report(player, expected, MEET, "player:" + target.getUniqueId());
        if (same(other, context(target)) && visible.test(target, player)) report(target, other, MEET, "player:" + player.getUniqueId());
    }

    private void changed(UUID id) {
        requireThread(); pending.remove(id);
        Player player = online.apply(id);
        Session session = sessions.get(id);
        if (player != null && session != null && session.entity == player.getEntityId()) {
            // Retain throttling even across repeated visibility/context/assignment changes.
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onJoin(PlayerJoinEvent event) { seed(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST) public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.entity == event.getPlayer().getEntityId()) {
            sessions.remove(event.getPlayer().getUniqueId()); pending.remove(event.getPlayer().getUniqueId());
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) queue(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) { changed(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onWorld(PlayerChangedWorldEvent event) { changed(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onRespawn(PlayerRespawnEvent event) { changed(event.getPlayer().getUniqueId()); }
    @Override public void beforeStateChange(Player player) { pending.remove(player.getUniqueId()); }
    @Override public void afterStateChange(UUID id) { changed(id); }
    @Override public void beforeJoin(Player player) { pending.remove(player.getUniqueId()); }
    @Override public void membershipChanged(UUID id) { changed(id); }

    void close() {
        requireThread(); open = false; pending.clear(); sessions.clear();
        RuntimeException failure = new IllegalStateException("Lobby quest cleanup incomplete; retry with original owners");
        if (cancellation != null) {
            try { cancellation.run(); cancellation = null; }
            catch (RuntimeException exception) { failure.addSuppressed(exception); }
        }
        for (var release : List.copyOf(releases)) {
            try { release.run(); releases.remove(release); }
            catch (RuntimeException exception) { failure.addSuppressed(exception); }
        }
        if (failure.getSuppressed().length != 0) throw failure;
    }
    boolean released() { return !open && releases.isEmpty() && cancellation == null; }
    private void requireThread() { if (!primaryThread.getAsBoolean()) throw new IllegalStateException("Lobby progress requires main thread"); }
    private void failed(RuntimeException failure) {
        try { logger.log(Level.WARNING, "Lobby quest observation failed; domain action is retained.", failure); }
        catch (RuntimeException diagnostic) { if (diagnostic != failure) failure.addSuppressed(diagnostic); }
    }
}

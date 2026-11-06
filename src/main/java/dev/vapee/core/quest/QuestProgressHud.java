package dev.vapee.core.quest;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.i18n.Language;
import dev.vapee.core.i18n.LanguageResolver;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.player.PlayerService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One transient display per UUID. No quest, XP or player persistence; no retained Player references. */
final class QuestProgressHud
        implements QuestProgressListener,
                Listener,
                ActivityMembershipListener,
                LobbyPlayerStateListener {
    static final int UPDATE_TICKS = 5, DISPLAY_TICKS = 60, COMPLETION_HOLD_TICKS = 10;
    private final PlayerService players;
    private final QuestService quests;
    private final QuestDefinitionRegistry definitions;
    private final MessageService messages;
    private final Function<UUID, Player> online;
    private final Predicate<UUID> context;
    private final IntSupplier ticks;
    private final BooleanSupplier authorized;
    private final Logger logger;
    private final Map<UUID, Display> displays = new HashMap<>();
    private boolean closed;

    QuestProgressHud(
            PlayerService players,
            QuestService quests,
            QuestDefinitionRegistry definitions,
            MessageService messages,
            Function<UUID, Player> online,
            Predicate<UUID> context,
            IntSupplier ticks,
            BooleanSupplier authorized,
            Logger logger) {
        this.players = Objects.requireNonNull(players);
        this.quests = Objects.requireNonNull(quests);
        this.definitions = Objects.requireNonNull(definitions);
        this.messages = Objects.requireNonNull(messages);
        this.online = Objects.requireNonNull(online);
        this.context = Objects.requireNonNull(context);
        this.ticks = Objects.requireNonNull(ticks);
        this.authorized = Objects.requireNonNull(authorized);
        this.logger = Objects.requireNonNull(logger);
    }

    private boolean eligible(Player player) {
        return player != null
                && player.isOnline()
                && online.apply(player.getUniqueId()) == player
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR
                && context.test(player.getUniqueId())
                && players.getPlayer(player.getUniqueId()).isPresent();
    }

    private boolean current(UUID id, Display display, Player player) {
        var loaded = players.getPlayer(id);
        return !closed
                && authorized.getAsBoolean()
                && displays.get(id) == display
                && eligible(player)
                && player.getEntityId() == display.entity
                && loaded.isPresent()
                && loaded.get().getQuestState().assignmentToken() == display.assignments
                && definitions.snapshotById() == display.catalog;
    }

    @Override
    public void onProgress(
            UUID id,
            Object assignments,
            Object catalog,
            List<String> increased,
            QuestProgressResult result) {
        if (closed
                || !authorized.getAsBoolean()
                || result.status() != QuestProgressResult.Status.PROCESSED
                || increased.isEmpty()) return;
        Player player = online.apply(id);
        if (!eligible(player)
                || players.getPlayer(id).orElseThrow().getQuestState().assignmentToken()
                        != assignments
                || definitions.snapshotById() != catalog) return;
        int now = ticks.getAsInt();
        Display old = displays.get(id);
        if (old != null && old.entity != player.getEntityId()) {
            displays.remove(id, old);
            old = null;
        }
        if (old != null && old.quest == null) {
            if (now - old.expires < 0) return;
            displays.remove(id, old);
            old = null;
        }
        if (old != null && (old.retiring || !current(id, old, player) || now - old.expires >= 0)) {
            retire(id, old, player);
            if (displays.containsKey(id)) return;
            old = null;
        }
        var changed =
                quests.getActiveQuests(id).orElse(List.of()).stream()
                        .filter(
                                view ->
                                        increased.contains(view.definition().id())
                                                && view.currentProgress() > 0)
                        .sorted(Comparator.comparing(view -> view.definition().id()))
                        .toList();
        if (changed.isEmpty()) return;
        var completion =
                changed.stream()
                        .filter(
                                view ->
                                        result.reachedQuestIds().contains(view.definition().id())
                                                && view.status() == QuestStatus.COMPLETED)
                        .findFirst();
        if (completion.isEmpty() && old != null && now - old.holdUntil < 0) return;
        String prior = old == null ? null : old.quest;
        QuestView choice =
                completion.orElseGet(
                        () ->
                                changed.stream()
                                        .filter(view -> view.definition().id().equals(prior))
                                        .findFirst()
                                        .orElse(changed.getFirst()));
        Display display =
                old == null ? new Display(player.getEntityId(), assignments, catalog, now) : old;
        display.quest = choice.definition().id();
        display.expires = now + DISPLAY_TICKS;
        display.dirty = true;
        display.holdUntil = now + (completion.isPresent() ? COMPLETION_HOLD_TICKS : 0);
        displays.put(id, display);
    }

    void tick() {
        if (closed || !authorized.getAsBoolean()) return;
        int now = ticks.getAsInt();
        for (var entry : List.copyOf(displays.entrySet())) {
            UUID id = entry.getKey();
            Display display = entry.getValue();
            if (displays.get(id) != display) continue;
            try {
                Player player = online.apply(id);
                if (player == null
                        || !player.isOnline()
                        || player.getEntityId() != display.entity) {
                    displays.remove(id, display);
                    continue;
                }
                if (display.quest == null) {
                    if (now - display.expires >= 0) displays.remove(id, display);
                    continue;
                }
                if (display.retiring
                        || now - display.expires >= 0
                        || !current(id, display, player)) {
                    retire(id, display, player);
                    continue;
                }
                var view =
                        quests.getActiveQuests(id).orElse(List.of()).stream()
                                .filter(v -> v.definition().id().equals(display.quest))
                                .findFirst();
                if (view.isEmpty()) {
                    retire(id, display, player);
                    continue;
                }
                if (!display.dirty || now - display.lastSent < UPDATE_TICKS) continue;
                display.lastSent = now;
                display.dirty = false;
                Component text =
                        render(messages, messages.localization().locale(player), view.get());
                if (!current(id, display, player)) continue;
                player.sendActionBar(
                        text); // Owned low-priority output: foreground MessageService output would
                               // yield this display.
                if (!current(id, display, player) || display.retiring) continue;
                float fraction = fraction(view.get());
                int level = player.getLevel();
                if (!display.xpOwned
                        || Float.compare(fraction, display.sentFraction) != 0
                        || level != display.sentLevel) {
                    display.xpOwned =
                            true; // Capture possible after-effect failure before crossing the
                                  // packet boundary.
                    player.sendExperienceChange(fraction, level);
                    display.sentFraction = fraction;
                    display.sentLevel = level;
                }
            } catch (RuntimeException failure) {
                display.retiring = true;
                warn(display, failure);
                try {
                    retire(id, display, online.apply(id));
                } catch (RuntimeException cleanup) {
                    warn(display, cleanup);
                }
            }
        }
    }

    static float fraction(QuestView view) {
        return (float)
                Math.max(0.0, Math.min(1.0, (double) view.currentProgress() / view.target()));
    }

    static Component render(MessageService messages, Locale locale, QuestView view) {
        var language = LanguageResolver.resolve(locale);
        NumberFormat format =
                NumberFormat.getNumberInstance(
                        language == Language.GERMAN ? Locale.GERMAN : Locale.ENGLISH);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        BigDecimal percent =
                BigDecimal.valueOf(view.currentProgress())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(view.target()), 1, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO)
                        .min(BigDecimal.valueOf(100));
        var color =
                view.status() == QuestStatus.COMPLETED
                        ? MessageStyle.SUCCESS
                        : view.status() == QuestStatus.REWARD_PENDING
                                ? MessageStyle.WARNING
                                : MessageStyle.accent();
        Component percentage =
                messages.localization()
                        .component(
                                locale,
                                "quests.hud.percentage",
                                Placeholder.component(
                                        "value", Component.text(format.format(percent))))
                        .color(color);
        return messages.localization()
                .component(
                        locale,
                        "quests.hud.progress",
                        Placeholder.component(
                                "quest",
                                Component.text(
                                        view.definition().nameText().resolve(locale),
                                        MessageStyle.TEXT)),
                        Placeholder.component(
                                "progress",
                                Component.text(
                                        Long.toString(view.currentProgress()),
                                        MessageStyle.accent())),
                        Placeholder.component(
                                "target",
                                Component.text(
                                        Long.toString(view.target()), MessageStyle.accent())),
                        Placeholder.component("percentage", percentage));
    }

    /** Navigator/core foreground output pauses the HUD; never erase somebody else's actionbar. */
    void foreground(Player player) {
        if (closed || !authorized.getAsBoolean() || !eligible(player)) return;
        UUID id = player.getUniqueId();
        Display old = displays.get(id);
        int until = ticks.getAsInt() + DISPLAY_TICKS;
        if (old != null && old.entity == player.getEntityId()) {
            old.pauseUntil = until;
            retire(id, old, player);
            if (displays.containsKey(id)) return;
        }
        Display pause = new Display(player.getEntityId(), null, null, ticks.getAsInt());
        pause.quest = null;
        pause.expires = until;
        displays.put(id, pause);
    }

    private boolean release(Display display, Player player) {
        if (!display.xpOwned) return true;
        if (player == null || !player.isOnline() || player.getEntityId() != display.entity) {
            display.xpOwned = false;
            return true;
        }
        // Read current real XP at release, never the values from when the overlay started.
        player.sendExperienceChange(player.getExp(), player.getLevel());
        display.xpOwned = false;
        return true;
    }

    private void retire(UUID id, Display display, Player player) {
        display.retiring = true;
        if (displays.get(id) != display) return;
        int now = ticks.getAsInt();
        if (display.xpOwned && display.restoreScheduled && now - display.nextRestore < 0) return;
        display.restoreScheduled = true;
        display.nextRestore = now + 20;
        try {
            if (release(display, player)) {
                displays.remove(id, display);
                if (display.pauseUntil != null
                        && ticks.getAsInt() - display.pauseUntil < 0
                        && eligible(player)) {
                    Display pause = new Display(display.entity, null, null, ticks.getAsInt());
                    pause.expires = display.pauseUntil;
                    displays.put(id, pause);
                }
            }
        } catch (RuntimeException failure) {
            warn(display, failure);
        }
    }

    void close() {
        closed = true;
        RuntimeException failure =
                new IllegalStateException(
                        "Quest HUD XP release incomplete; retry cleanup for the original online sessions.");
        for (var entry : List.copyOf(displays.entrySet())) {
            try {
                if (release(entry.getValue(), online.apply(entry.getKey())))
                    displays.remove(entry.getKey(), entry.getValue());
            } catch (RuntimeException exception) {
                failure.addSuppressed(exception);
            }
        }
        if (failure.getSuppressed().length != 0) throw failure;
    }

    boolean released() {
        return displays.isEmpty();
    }

    int trackedPlayers() {
        return displays.size();
    }

    private void warn(Display display, RuntimeException failure) {
        if (display.warned) return;
        display.warned = true;
        try {
            logger.log(
                    Level.WARNING,
                    "Quest HUD output failed; progress/rewards are retained and owned XP release remains retryable.",
                    failure);
        } catch (RuntimeException ignored) {
            /* Presentation diagnostics cannot affect domain processing or other players. */
        }
    }

    private void invalidate(Player player) {
        Display display = displays.get(player.getUniqueId());
        if (display != null && display.entity == player.getEntityId())
            retire(player.getUniqueId(), display, player);
    }

    private void invalidate(UUID id) {
        Player player = online.apply(id);
        Display display = displays.get(id);
        if (display != null) retire(id, display, player);
    }

    @Override
    public void beforeStateChange(Player player) {
        invalidate(player);
    }

    @Override
    public void afterStateChange(UUID id) {
        invalidate(id);
    }

    @Override
    public void beforeJoin(Player player) {
        invalidate(player);
    }

    @Override
    public void membershipChanged(UUID id) {
        invalidate(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Display display = displays.get(event.getPlayer().getUniqueId());
        if (display != null && display.entity == event.getPlayer().getEntityId())
            displays.remove(event.getPlayer().getUniqueId(), display);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (online.apply(event.getPlayer().getUniqueId()) == event.getPlayer())
            displays.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        invalidate(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMode(PlayerGameModeChangeEvent event) {
        invalidate(event.getPlayer());
    }

    // Vanilla may apply XP after the event. Release on the next HUD task using fresh real values.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onExperience(PlayerExpChangeEvent event) {
        if (event.getAmount() != 0) requestRestore(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        if (event.getOldLevel() != event.getNewLevel()) requestRestore(event.getPlayer());
    }

    private void requestRestore(Player player) {
        Display display = displays.get(player.getUniqueId());
        if (display != null && display.entity == player.getEntityId()) display.retiring = true;
    }

    private static final class Display {
        final int entity;
        final Object assignments, catalog;
        String quest;
        int expires, holdUntil, lastSent, nextRestore;
        Integer pauseUntil;
        boolean dirty, xpOwned, retiring, warned, restoreScheduled;
        float sentFraction;
        int sentLevel;

        Display(int entity, Object assignments, Object catalog, int now) {
            this.entity = entity;
            this.assignments = assignments;
            this.catalog = catalog;
            lastSent = now - UPDATE_TICKS;
        }
    }
}

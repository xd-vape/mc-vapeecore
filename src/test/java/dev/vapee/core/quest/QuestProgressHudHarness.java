package dev.vapee.core.quest;

import static dev.vapee.core.quest.QuestHudTestSupport.*;

import dev.vapee.core.i18n.*;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.quest.daily.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.GameMode;
import org.bukkit.event.player.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class QuestProgressHudHarness {
    private static int checks;

    public static void main(String[] args) {
        formatting();
        progressAndThrottle();
        priorities();
        pending();
        assignmentsAndReload();
        lifecycle();
        errors();
        finalMovementKeys();
        System.out.println("QuestProgressHudHarness passed " + checks + " checks.");
    }

    private static void finalMovementKeys() {
        for (String key : List.of("movement:backward", "movement:sneak_toggle"))
            for (Locale locale : List.of(Locale.US, Locale.GERMANY))
                try (var f = new Fixture()) {
                    var a = f.actor();
                    a.locale = locale;
                    f.domain.registry.replaceAll(
                            List.of(
                                    new QuestDefinition(
                                            "final_key",
                                            LocalizedText.parse(
                                                    Map.of(
                                                            "en",
                                                            "Final Movement",
                                                            "de",
                                                            "Letzte Bewegung")),
                                            LocalizedText.literal("Test"),
                                            QuestProgressKey.of(key),
                                            4,
                                            1)));
                    f.assign(a, "final_key");
                    f.report(a, key, 1);
                    f.pump();
                    check(
                            a.bar()
                                            .contains(
                                                    locale.equals(Locale.GERMANY)
                                                            ? "Letzte Bewegung"
                                                            : "Final Movement")
                                    && a.bar().contains("1/4")
                                    && a.bar().contains("25"),
                            "both final movement keys use existing localized numeric/percent HUD");
                    check(
                            a.xp.size() == 1 && a.clientExp == .25F && a.mutations == 0,
                            "one existing HUD update without server XP mutation");
                    f.advance(61);
                    check(
                            a.clientExp == a.realExp
                                    && a.clientLevel == a.realLevel
                                    && a.mutations == 0,
                            "final movement HUD restores actual XP through original owner");
                }
    }

    private static void formatting() {
        try (var f = new Fixture()) {
            f.localized("spin", 200);
            var definition = f.domain.registry.findById("spin").orElseThrow();
            var view = new QuestView(definition, 37, 200, QuestStatus.ACTIVE);
            var en = QuestProgressHud.render(f.messages, Locale.US, view);
            var de = QuestProgressHud.render(f.messages, Locale.GERMANY, view);
            check(
                    plain(en).equals("Spin 200 times · 37/200 · 18.5%"),
                    "exact English HUD with localized name and percent");
            check(
                    plain(de).equals("Drehwurm 200 · 37/200 · 18,5 %"),
                    "exact German HUD with decimal comma and spacing");
            check(
                    span(en, "Spin 200 times", MessageStyle.TEXT)
                            && span(en, "·", MessageStyle.MUTED),
                    "neutral name and muted separators use central palette");
            var old = MessageStyle.accent();
            var accent = TextColor.color(0x123ABC);
            try {
                MessageStyle.applyAccentColor(accent);
                var colored = QuestProgressHud.render(f.messages, Locale.US, view);
                check(
                        span(colored, "37", accent) && span(colored, "18.5%", accent),
                        "configured accent colors progress and active percentage");
            } finally {
                MessageStyle.applyAccentColor(old);
            }
            check(
                    QuestProgressHud.fraction(view) == .185F,
                    "quest XP fraction is bounded division, not percent units");
            for (long target : List.of(1L, 200L, Long.MAX_VALUE)) {
                var d =
                        new QuestDefinition(
                                "limit",
                                "Literal <red> name",
                                "Test",
                                QuestProgressKey.of("future:test"),
                                target,
                                1);
                var full = new QuestView(d, target, target, QuestStatus.COMPLETED);
                var rendered = QuestProgressHud.render(f.messages, Locale.US, full);
                check(
                        plain(rendered).contains(target + "/" + target)
                                && plain(rendered).endsWith("100%")
                                && QuestProgressHud.fraction(full) == 1F,
                        "target 1/large long completes without overflow");
                check(
                        span(rendered, "100%", MessageStyle.SUCCESS)
                                && plain(rendered).contains("Literal <red> name"),
                        "completed color is semantic and quest names stay literal");
                var half = new QuestView(d, target / 2, target, QuestStatus.ACTIVE);
                check(
                        Float.isFinite(QuestProgressHud.fraction(half))
                                && QuestProgressHud.fraction(half) >= 0F
                                && QuestProgressHud.fraction(half) <= 1F,
                        "all fractions finite and within API bounds");
            }
            var pending = new QuestView(definition, 200, 200, QuestStatus.REWARD_PENDING);
            check(
                    span(
                            QuestProgressHud.render(f.messages, Locale.US, pending),
                            "100%",
                            MessageStyle.WARNING),
                    "pending reward never uses completed success color");
            check(
                    plain(QuestProgressHud.render(f.messages, Locale.FRANCE, view))
                            .endsWith("18.5%"),
                    "unsupported locales follow existing English fallback");
        }
    }

    private static void progressAndThrottle() {
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("walk", "movement:walk", 1000, 7);
            f.assign(a, "walk");
            f.domain.quests.addProgress(a.id, QuestProgressKey.of("movement:walk"), 1);
            check(
                    a.bars.isEmpty() && a.xp.isEmpty(),
                    "actual domain progress queues output, never sends per move signal");
            f.pump();
            check(
                    a.bar().contains("1/1000")
                            && a.clientExp == .001F
                            && a.clientLevel == a.realLevel,
                    "direct central QuestService API reaches both HUD outputs");
            for (int n = 0; n < 100; n++) {
                f.report(a, "movement:walk", 1);
                f.pump();
            }
            check(
                    a.bars.size() == 1
                            && a.xp.size() == 1
                            && f.domain
                                            .quests
                                            .getActiveQuests(a.id)
                                            .orElseThrow()
                                            .getFirst()
                                            .currentProgress()
                                    == 101,
                    "100 same-tick signals coalesce without losing authoritative progress");
            f.advance(4);
            check(a.bars.size() == 1, "update budget cannot be reset before five ticks");
            f.advance(1);
            check(
                    a.bars.size() == 2 && a.bar().contains("101/1000"),
                    "shared task emits latest coalesced state");
            f.advance(20);
            check(a.bars.size() == 2, "idle HUD does not refresh packets or chat");
            f.report(a, "movement:walk", 1);
            f.advance(5);
            f.advance(59);
            check(
                    f.hud.trackedPlayers() == 0
                            && a.clientExp == a.realExp
                            && a.clientLevel == a.realLevel,
                    "expiry is three seconds after the final relevant progress, restoring real XP");
            check(
                    a.bars.stream().noneMatch(Component.empty()::equals) && f.chat.isEmpty(),
                    "ordinary progress sends no chat and cleanup never clears actionbar");
            check(
                    a.realExp == .25F
                            && a.realLevel == 12
                            && a.realTotal == 999
                            && a.mutations == 0,
                    "server XP and total experience are untouched");
        }
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("future", "future:anything", 100, 1);
            f.assign(a, "future");
            f.report(a, "future:anything", 1);
            f.pump();
            check(
                    a.bar().contains("1/100"),
                    "future processed keys need no HUD producer registration");
            a.realExp = .8F;
            a.realLevel = 99;
            a.realTotal = 10000;
            f.advance(60);
            check(
                    a.xp.getLast().equals(new Xp(.8F, 99)) && a.mutations == 0,
                    "release reads changed real XP rather than cached snapshots");
            f.report(a, "future:anything", 1);
            f.advance(5);
            f.hud.onExperience(new PlayerExpChangeEvent(a.player, 10));
            a.realExp = .9F;
            a.realLevel = 100;
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0 && a.xp.getLast().equals(new Xp(.9F, 100)),
                    "vanilla XP event retires overlay after event with fresh values");
        }
    }

    private static void priorities() {
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("a", "same:key", 100, 1);
            f.define("z", "same:key", 3, 7);
            f.assign(a, "a", "z");
            f.report(a, "same:key", 1);
            f.pump();
            check(a.bar().startsWith("a"), "first equal-key candidates use stable ID order");
            f.report(a, "same:key", 1);
            f.advance(5);
            check(
                    a.bar().startsWith("a") && a.bar().contains("2/100"),
                    "visible changed quest retains priority without flicker");
            f.report(a, "same:key", 1);
            f.advance(5);
            check(
                    a.bar().startsWith("z") && a.clientExp == 1F,
                    "another newly completed quest interrupts the visible quest");
            int count = a.bars.size();
            f.report(a, "same:key", 1);
            f.advance(5);
            check(a.bars.size() == count, "brief completion hold coalesces continuing progress");
            f.report(a, "same:key", 1);
            f.advance(5);
            check(
                    a.bar().startsWith("a"),
                    "continued active progress replaces an inactive completion after bounded hold");
            f.define("other", "other:key", 100, 1);
            f.assign(a, "a", "z", "other");
            f.report(a, "same:key", 1);
            f.advance(5);
            f.report(a, "other:key", 1);
            f.advance(5);
            check(
                    a.bar().startsWith("other"),
                    "new different-key progress does not stay bound to inactive prior quest");
        }
    }

    private static void pending() {
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("reward", "test:reward", 1, 7);
            f.assign(a, "reward");
            f.domain.economy.setCoins(a.id, Long.MAX_VALUE);
            var result = f.report(a, "test:reward", 1);
            f.pump();
            check(
                    result.rewardPendingQuestIds().equals(List.of("reward"))
                            && span(a.bars.getLast(), "100%", MessageStyle.WARNING),
                    "real overflow pending is rendered without false completion");
            int bars = a.bars.size(), xp = a.xp.size();
            f.domain.economy.setCoins(a.id, 0);
            f.domain.quests.retryPendingRewards(a.id);
            f.advance(10);
            check(
                    a.bars.size() == bars
                            && a.xp.size() == xp
                            && f.domain.economy.getCoins(a.id).orElseThrow() == 7,
                    "pending retry without numeric progress neither sends nor prolongs HUD");
            f.advance(50);
            check(f.hud.trackedPlayers() == 0, "retry does not extend original display expiry");
            f.report(a, "test:reward", 1);
            f.pump();
            check(
                    a.bars.size() == bars && f.domain.economy.getCoins(a.id).orElseThrow() == 7,
                    "completed quests cannot recreate HUD or repeat reward");
        }
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("done", "test:done", 1, 7);
            f.assign(a, "done");
            f.report(a, "test:done", 1);
            f.pump();
            check(
                    f.chat.size() == 1
                            && span(a.bars.getLast(), "100%", MessageStyle.SUCCESS)
                            && f.domain.economy.getCoins(a.id).orElseThrow() == 7,
                    "existing completion chat and original single reward accompany bounded 100-percent HUD");
        }
    }

    private static void assignmentsAndReload() {
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("q", "test:q", 100, 7);
            var config =
                    new AtomicReference<>(
                            new DailyQuestConfig.State(
                                    true,
                                    1,
                                    LocalTime.MIDNIGHT,
                                    "UTC",
                                    ZoneId.of("UTC"),
                                    f.domain.registry.snapshot()));
            var daily =
                    new DailyQuestService(
                            f.domain.players,
                            f.domain.quests,
                            f.domain.registry,
                            config::get,
                            f.domain.logger);
            Instant now = Instant.parse("2026-11-06T12:00:00Z");
            daily.syncPlayer(a.id, now);
            f.report(a, "test:q", 2);
            f.pump();
            var token =
                    f.domain
                            .players
                            .getPlayer(a.id)
                            .orElseThrow()
                            .getQuestState()
                            .assignmentToken();
            var catalog = f.domain.registry.snapshotById();
            check(
                    daily.forcePlayer(a.id, now) == DailyQuestSyncResult.ROTATED,
                    "actual same-cycle force commits reconciliation");
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0 && a.clientExp == a.realExp,
                    "force invalidates even overlapping IDs with retained progress");
            f.hud.onProgress(
                    a.id,
                    token,
                    catalog,
                    List.of("q"),
                    QuestProgressResult.processed(1, List.of(), List.of(), List.of()));
            f.pump();
            check(
                    f.hud.trackedPlayers() == 0,
                    "stale assignment notification cannot queue an old quest again");
            f.report(a, "test:q", 1);
            f.advance(5);
            daily.syncPlayer(a.id, now.plusSeconds(86400));
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0
                            && f.domain
                                            .quests
                                            .getActiveQuests(a.id)
                                            .orElseThrow()
                                            .getFirst()
                                            .currentProgress()
                                    == 0,
                    "daily reset revokes old display before new assignment output");
            f.report(a, "test:q", 1);
            var old = f.domain.registry.snapshot();
            f.domain.registry.replaceAll(old);
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0,
                    "catalog reload invalidates queued pre-reload output");
            f.report(a, "test:q", 1);
            f.advance(5);
            int sent = a.bars.size();
            f.domain.registry.replaceAll(List.of());
            f.advance(5);
            f.domain.registry.replaceAll(old);
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0 && a.bars.size() == sent,
                    "removed definition and subsequent rollback do not resurrect stale display");
            f.assign(a, "q");
            f.report(a, "test:q", 1);
            f.advance(5);
            f.domain.players.unloadPlayer(a.id);
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0,
                    "unloaded profile retires UI without a persistence poll");
        }
    }

    private static void lifecycle() {
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("q", "test:q", 100, 7);
            f.assign(a, "q");
            check(
                    f.report(a, "unknown:key", 1).status()
                                    == QuestProgressResult.Status.NO_MATCHING_QUEST
                            && f.hud.trackedPlayers() == 0,
                    "unknown unmatched key produces no HUD");
            check(
                    f.reporter.report(UUID.randomUUID(), QuestProgressKey.of("test:q"), 1).status()
                            == QuestProgressResult.Status.PLAYER_NOT_LOADED,
                    "unloaded player cannot create HUD");
            try {
                f.report(a, "test:q", 0);
                throw new AssertionError("zero accepted");
            } catch (IllegalArgumentException expected) {
                check(
                        f.hud.trackedPlayers() == 0,
                        "rejected zero progress creates no presentation state");
            }
            f.report(a, "test:q", 1);
            f.pump();
            f.messages.sendActionBar(a.player, Component.text("Navigator priority"));
            int packets = a.bars.size();
            check(
                    a.bar().equals("Navigator priority") && a.clientExp == a.realExp,
                    "foreground core message yields XP and replaces owned actionbar");
            for (int n = 0; n < 10; n++) {
                f.report(a, "test:q", 1);
                f.advance(5);
            }
            check(
                    a.bars.size() == packets && a.bar().equals("Navigator priority"),
                    "HUD cannot overwrite foreground message during its bounded priority window");
            f.advance(10);
            f.report(a, "test:q", 1);
            f.advance(5);
            check(
                    a.bars.size() == packets + 1,
                    "new progress may show again after foreground window");
            a.context = false;
            f.hud.beforeStateChange(a.player);
            f.hud.afterStateChange(a.id);
            f.report(a, "test:q", 1);
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0 && a.clientExp == a.realExp,
                    "forbidden BUILD/Activity context releases without blanking actionbar");
            a.context = true;
            f.report(a, "test:q", 1);
            f.advance(5);
            f.hud.onQuit(new PlayerQuitEvent(a.player, Component.empty()));
            var next = f.actor(a.id);
            f.report(next, "test:q", 1);
            f.advance(5);
            f.hud.onQuit(new PlayerQuitEvent(a.player, Component.empty()));
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 1 && next.bars.size() == 1,
                    "old quit cannot erase a replacement session's display");
            int count = next.bars.size();
            f.active = false;
            f.hud.close();
            f.report(next, "test:q", 1);
            f.advance(5);
            check(
                    f.hud.trackedPlayers() == 0
                            && next.bars.size() == count
                            && next.clientExp == next.realExp,
                    "retired runtime never sends a delayed frame and restores owned XP");
        }
        for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR))
            try (var f = new Fixture()) {
                var a = f.actor();
                a.mode = mode;
                f.define("q", "test:q", 100, 7);
                f.assign(a, "q");
                f.report(a, "test:q", 1);
                f.pump();
                check(
                        a.bars.isEmpty() && a.xp.isEmpty(),
                        "HUD stays out of modes without normal XP presentation");
            }
    }

    private static void errors() {
        for (boolean after : List.of(false, true))
            try (var f = new Fixture()) {
                var a = f.actor();
                f.define("q", "test:q", 1, 7);
                f.assign(a, "q");
                a.xpFailures = 2;
                a.xpAfter = after;
                var result = f.report(a, "test:q", 1);
                f.pump();
                check(
                        result.completedQuestIds().equals(List.of("q"))
                                && f.domain.economy.getCoins(a.id).orElseThrow() == 7,
                        "XP packet failure cannot roll back progress or repeat rewards");
                check(
                        f.hud.trackedPlayers() == 1,
                        "unconfirmed XP cleanup remains owned after before/after-effect send failure");
                int packets = a.xp.size();
                f.report(a, "test:q", 1);
                f.advance(5);
                check(
                        a.xp.size() == packets,
                        "failed cleanup retries are throttled, not triggered by every progress call");
                a.realExp = .7F;
                a.realLevel = 50;
                f.hud.close();
                check(
                        f.hud.released()
                                && a.xp.getLast().equals(new Xp(.7F, 50))
                                && a.mutations == 0,
                        "explicit close retries original owned XP release using current values");
            }
        try (var f = new Fixture()) {
            var a = f.actor();
            f.define("q", "test:q", 100, 7);
            f.assign(a, "q");
            a.actionFailures = 1;
            f.report(a, "test:q", 1);
            f.pump();
            check(
                    a.xp.isEmpty()
                            && f.hud.trackedPlayers() == 0
                            && f.domain
                                            .quests
                                            .getActiveQuests(a.id)
                                            .orElseThrow()
                                            .getFirst()
                                            .currentProgress()
                                    == 1,
                    "failed actionbar before XP acquisition leaves no invented ownership or domain rollback");
            f.domain.quests.addProgressListener(
                    (id, assignments, catalog, increased, result) -> {
                        throw new IllegalStateException("observer failure");
                    });
            check(
                    f.report(a, "test:q", 1).status() == QuestProgressResult.Status.PROCESSED,
                    "faulty presentation observer never escapes successful domain processing");
        }
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}

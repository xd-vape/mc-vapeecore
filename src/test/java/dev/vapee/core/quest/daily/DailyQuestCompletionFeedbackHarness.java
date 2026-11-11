package dev.vapee.core.quest.daily;

import static dev.vapee.core.quest.QuestCompletionFixture.DEFAULT;
import static dev.vapee.core.quest.QuestCompletionFixture.definition;
import static dev.vapee.core.quest.QuestCompletionFixture.proxy;

import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.quest.QuestProgressKey;
import dev.vapee.core.quest.daily.menu.DailyQuestMenuListener;
import dev.vapee.core.quest.daily.menu.QuestMenuFixture;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class DailyQuestCompletionFeedbackHarness {
    private static final QuestProgressKey PROGRESS = QuestProgressKey.of("test:finish");
    private static final QuestProgressKey MENU = QuestProgressKey.of("lobby:quests_open");
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (Fixture f = new Fixture(DailyQuestConfig.Feedback.DEFAULT)) {
            var batch = f.complete();
            check(
                    batch.completedQuestIds().size() == 2 && f.coins() == 12,
                    "two rewards delivered");
            check(
                    f.viewer.sounds.size() == 1
                            && f.viewer
                                    .sounds
                                    .getFirst()
                                    .name()
                                    .asString()
                                    .equals("minecraft:ui.toast.challenge_complete"),
                    "default once per batch sound");
            check(f.tasks.isEmpty() && f.viewer.opens == 0, "auto-open default is disabled");
            f.complete();
            f.menus.domain.quests.retryPendingRewards(f.viewer.id);
            check(
                    f.viewer.sounds.size() == 1 && f.coins() == 12,
                    "repeat cannot double pay or sound");
        }
        var open =
                new DailyQuestConfig.Feedback(
                        true, "minecraft:ui.toast.challenge_complete", true, 3);
        try (Fixture f = new Fixture(open)) {
            f.menus.domain.economy.setCoins(f.viewer.id, Long.MAX_VALUE);
            check(
                    f.complete().rewardPendingQuestIds().size() == 2,
                    "both overflow rewards remain pending");
            f.menus.domain.quests.retryPendingRewards(f.viewer.id);
            check(
                    f.tasks.isEmpty() && f.viewer.sounds.isEmpty(),
                    "failed retries produce no completion feedback");
            f.menus.domain.economy.setCoins(f.viewer.id, 0);
            check(
                    f.menus
                                    .domain
                                    .quests
                                    .retryPendingRewards(f.viewer.id)
                                    .completedQuestIds()
                                    .size()
                            == 2,
                    "one retry batch delivers both rewards");
            check(
                    f.tasks.size() == 1
                            && f.tasks.getFirst().delay == 3
                            && f.viewer.sounds.size() == 1,
                    "one delayed opening and one sound for retry batch");
            f.menus.domain.quests.retryPendingRewards(f.viewer.id);
            f.tasks.getFirst().callback.run();
            f.tasks.getFirst().callback.run();
            check(
                    f.viewer.opens == 1 && f.viewer.sounds.size() == 1 && f.coins() == 12,
                    "one real menu open and exact payout after retries");
            check(f.menuProgress() == 0, "automatic open does not report lobby:quests_open");
            f.viewer.player.closeInventory();
            f.menus.menu.open(f.viewer.player, 0);
            check(f.menuProgress() == 1, "interactive opening still reports lobby:quests_open");
        }
        for (String guard :
                List.of(
                        "foreign-inventory",
                        "offline",
                        "quit",
                        "session",
                        "permission",
                        "reload",
                        "disable")) {
            try (Fixture f = new Fixture(open)) {
                f.complete();
                var task = f.tasks.getFirst();
                switch (guard) {
                    case "foreign-inventory" ->
                            f.viewer.open = QuestMenuFixture.inventory(null, 27);
                    case "offline" -> f.viewer.online = false;
                    case "quit" ->
                            f.listener.onPlayerQuit(
                                    new PlayerQuitEvent(
                                            f.viewer.player,
                                            net.kyori.adventure.text.Component.empty()));
                    case "session" -> {
                        f.runtime.online.clear();
                        f.runtime.online.add(
                                proxy(
                                        Player.class,
                                        (method, parameters) ->
                                                method.equals("getUniqueId")
                                                        ? f.viewer.id
                                                        : DEFAULT));
                    }
                    case "permission" -> f.viewer.permission = false;
                    case "reload" -> f.feedback.invalidate();
                    case "disable" -> {
                        f.active = false;
                        f.feedback.invalidate();
                    }
                    default -> throw new AssertionError(guard);
                }
                var inventory = f.viewer.open;
                task.callback.run(); // Even a dispatched, stale callback must be inert.
                check(
                        f.viewer.open == inventory && f.viewer.opens == 0 && f.menuProgress() == 0,
                        guard + " preserves current view and progress");
                if (List.of("quit", "reload", "disable").contains(guard))
                    check(task.cancelled, guard + " cancels owned callback");
            }
        }
        try (Fixture f =
                new Fixture(new DailyQuestConfig.Feedback(false, "minecraft:custom", true, 3))) {
            f.complete();
            check(
                    f.viewer.sounds.isEmpty() && f.tasks.size() == 1,
                    "sound can be disabled independently");
        }
        try (Fixture f = new Fixture(open)) {
            f.complete();
            var foreign = QuestMenuFixture.inventory(null, 27);
            f.menus.duringSync = () -> f.viewer.open = foreign;
            f.tasks.getFirst().callback.run();
            check(
                    f.viewer.open == foreign && f.viewer.opens == 0,
                    "container opened during preparation is preserved");
        }
        config();
        System.out.println("DailyQuestCompletionFeedbackHarness passed " + checks + " checks.");
    }

    private static void config() throws Exception {
        var file = Files.createTempFile("vapeecore-quest-feedback-", ".yml");
        try {
            var config = new DailyQuestConfig(file, java.util.logging.Logger.getAnonymousLogger());
            Files.writeString(
                    file,
                    "feedback:\n  sound-enabled: false\n  sound: test:done\n  open-menu: true\n  open-delay-ticks: 5\n");
            check(
                    config.prepareReloadState()
                            .feedback()
                            .equals(new DailyQuestConfig.Feedback(false, "test:done", true, 5)),
                    "feedback values are parsed");
            Files.writeString(file, "feedback:\n  open-delay-ticks: 0\n");
            try {
                config.prepareReloadState();
                throw new AssertionError("zero delay must fail preparation");
            } catch (IllegalArgumentException expected) {
                check(
                        config.getState().feedback().equals(DailyQuestConfig.Feedback.DEFAULT),
                        "invalid config leaves active state alone");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final QuestMenuFixture menus = new QuestMenuFixture();
        final QuestMenuFixture.TestPlayer viewer = menus.player();
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture("quests");
        final List<Task> tasks = new ArrayList<>();
        final DailyQuestCompletionFeedback feedback;
        final DailyQuestMenuListener listener;
        boolean active = true;

        Fixture(DailyQuestConfig.Feedback settings) throws Exception {
            menus.domain.define(
                    definition("first", PROGRESS.value(), 1, 5),
                    definition("second", PROGRESS.value(), 1, 7),
                    definition("open", MENU.value(), 5, 1));
            menus.daily.syncPlayer(viewer.id, menus.now);
            menus.menu.addSuccessListener(
                    player -> menus.domain.quests.addProgress(player.getUniqueId(), MENU, 1));
            runtime.online.add(viewer.player);
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (method, args) -> {
                                if (!method.equals("runTaskLater"))
                                    throw new AssertionError(method);
                                var task =
                                        new Task(
                                                runtime.plugin, (Runnable) args[1], (long) args[2]);
                                tasks.add(task);
                                return task;
                            });
            runtime.serverCalls =
                    (method, args) -> {
                        if (method.equals("getScheduler")) return scheduler;
                        throw new AssertionError(method);
                    };
            var config = new DailyQuestConfig(java.nio.file.Path.of("unused.yml"), runtime.logger);
            config.applyState(
                    new DailyQuestConfig.State(
                            true,
                            3,
                            LocalTime.MIDNIGHT,
                            "UTC",
                            menus.zone,
                            menus.domain.registry.snapshot(),
                            DailyQuestSelection.unrestricted(),
                            settings));
            feedback =
                    new DailyQuestCompletionFeedback(
                            runtime.plugin, menus.domain.players, config, menus.menu, () -> active);
            menus.domain.quests.addCompletionListener(feedback);
            listener = new DailyQuestMenuListener(menus.menu, feedback::forget);
        }

        dev.vapee.core.quest.QuestProgressResult complete() {
            return menus.domain.quests.addProgress(viewer.id, PROGRESS, 1);
        }

        long coins() {
            return menus.domain.players.getPlayer(viewer.id).orElseThrow().getWallet().getCoins();
        }

        long menuProgress() {
            return menus.domain
                    .players
                    .getPlayer(viewer.id)
                    .orElseThrow()
                    .getQuestState()
                    .snapshot()
                    .get("open")
                    .progress();
        }

        @Override
        public void close() throws Exception {
            active = false;
            menus.domain.quests.removeCompletionListener(feedback);
            feedback.invalidate();
            runtime.close();
        }
    }

    private static final class Task implements BukkitTask {
        final Plugin owner;
        final Runnable callback;
        final long delay;
        boolean cancelled;

        Task(Plugin owner, Runnable callback, long delay) {
            this.owner = owner;
            this.callback = callback;
            this.delay = delay;
        }

        @Override
        public int getTaskId() {
            return 1;
        }

        @Override
        public Plugin getOwner() {
            return owner;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

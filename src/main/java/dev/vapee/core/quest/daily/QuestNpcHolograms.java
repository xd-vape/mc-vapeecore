package dev.vapee.core.quest.daily;

import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.quest.QuestProgressResult;
import dev.vapee.core.quest.QuestService;
import dev.vapee.core.quest.QuestStatus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** One private view per tracked session. All entity changes run outside tracking events. */
final class QuestNpcHolograms {
    private final JavaPlugin plugin;
    private final QuestService quests;
    private final LocalizationService localization;
    private final BooleanSupplier authorized;
    private final BooleanSupplier dailyEnabled;
    private final Supplier<Mannequin> anchor;
    private final Supplier<QuestNpcConfig.Hologram> settings;
    private final Map<UUID, View> views = new LinkedHashMap<>();
    private final Consumer<UUID> assignments = this::viewerChanged;
    private final BiConsumer<UUID, QuestProgressResult> completions =
            (id, result) -> viewerChanged(id);
    private Object generation = new Object();
    private Object ticket;
    private BukkitTask task;
    private boolean retired;

    QuestNpcHolograms(
            JavaPlugin plugin,
            QuestService quests,
            LocalizationService localization,
            BooleanSupplier authorized,
            BooleanSupplier dailyEnabled,
            Supplier<Mannequin> anchor,
            Supplier<QuestNpcConfig.Hologram> settings) {
        this.plugin = plugin;
        this.quests = quests;
        this.localization = localization;
        this.authorized = authorized;
        this.dailyEnabled = dailyEnabled;
        this.anchor = anchor;
        this.settings = settings;
        quests.addAssignmentListener(assignments);
        quests.addCompletionListener(completions);
    }

    void viewerChanged(UUID id) {
        if (!available()) return;
        try {
            Mannequin npc = anchor.get();
            if (views.containsKey(id)
                    || npc != null
                            && npc.isValid()
                            && npc.getTrackedBy().stream()
                                    .anyMatch(viewer -> viewer.getUniqueId().equals(id))) request();
        } catch (RuntimeException failure) {
            warn("Could not inspect quest NPC viewers", failure);
        }
    }

    void refreshIfTracked() {
        if (!available()) return;
        try {
            Mannequin npc = anchor.get();
            if (!views.isEmpty() || npc != null && npc.isValid() && !npc.getTrackedBy().isEmpty())
                request();
        } catch (RuntimeException failure) {
            warn("Could not inspect quest NPC tracking", failure);
        }
    }

    void request() {
        if (!available() || task != null || (!settings.get().enabled() && views.isEmpty())) return;
        Object requested = new Object();
        ticket = requested;
        try {
            task =
                    plugin.getServer()
                            .getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        if (ticket != requested) return;
                                        ticket = null;
                                        task = null;
                                        if (!available()) return;
                                        try {
                                            reconcile();
                                        } catch (RuntimeException failure) {
                                            warn(
                                                    "Could not reconcile quest NPC holograms",
                                                    failure);
                                        }
                                    });
        } catch (RuntimeException failure) {
            ticket = null;
            warn("Could not schedule quest NPC holograms", failure);
        }
    }

    private boolean available() {
        return !retired && authorized.getAsBoolean();
    }

    private void reconcile() {
        Mannequin npc = anchor.get();
        QuestNpcConfig.Hologram appearance = settings.get();
        Object epoch = generation;
        Map<UUID, Player> tracked = new LinkedHashMap<>();
        if (npc != null && npc.isValid() && appearance.enabled() && dailyEnabled.getAsBoolean()) {
            for (Player viewer : npc.getTrackedBy()) {
                if (current(viewer, npc)) tracked.put(viewer.getUniqueId(), viewer);
            }
        }
        for (var entry : List.copyOf(views.entrySet())) {
            View view = entry.getValue();
            if (view.retired
                    || view.npc != npc
                    || tracked.get(entry.getKey()) != view.viewer
                    || view.display == null
                    || !view.display.isValid()) removeSafely(entry.getKey(), view);
        }
        for (var entry : tracked.entrySet()) {
            if (epoch != generation || !available()) return;
            UUID id = entry.getKey();
            Player viewer = entry.getValue();
            View view = views.get(id);
            if (view != null && view.retired) continue;
            try {
                var assigned = quests.getActiveQuests(id).orElse(List.of());
                if (assigned.isEmpty()) {
                    if (view != null) removeSafely(id, view);
                    continue;
                }
                long completed =
                        assigned.stream()
                                .filter(quest -> quest.status() == QuestStatus.COMPLETED)
                                .count();
                Component text = render(viewer, appearance, completed, assigned.size());
                if (view == null) spawn(npc, viewer, appearance, text, epoch);
                else if (!text.equals(view.display.text())) view.display.text(text);
            } catch (RuntimeException failure) {
                View failedView = views.get(id);
                if (failedView != null && failedView.viewer == viewer) removeSafely(id, failedView);
                warn("Could not update quest NPC hologram for " + id, failure);
            }
        }
    }

    private boolean current(Player player, Mannequin npc) {
        return player.isOnline()
                && plugin.getServer().getPlayer(player.getUniqueId()) == player
                && player.getWorld() == npc.getWorld();
    }

    private Component render(
            Player viewer, QuestNpcConfig.Hologram appearance, long completed, int total) {
        String done = Long.toString(completed);
        String all = Integer.toString(total);
        String title =
                appearance.title() == null
                        ? line(localization.component(viewer, "daily-quests.hologram.title"))
                        : appearance.title();
        String progress =
                appearance.progressFormat() == null
                        ? line(
                                localization.component(
                                        viewer,
                                        "daily-quests.hologram.progress",
                                        Placeholder.unparsed("completed", done),
                                        Placeholder.unparsed("total", all)))
                        : appearance
                                .progressFormat()
                                .replace("<completed>", done)
                                .replace("<total>", all);
        return Component.text(title, appearance.titleColor())
                .append(Component.newline())
                .append(Component.text(progress, appearance.progressColor()));
    }

    private static String line(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).replaceAll("\\R", " ");
    }

    private void spawn(
            Mannequin npc,
            Player viewer,
            QuestNpcConfig.Hologram appearance,
            Component text,
            Object epoch) {
        UUID id = viewer.getUniqueId();
        View view = new View(npc, viewer);
        views.put(id, view);
        var location = npc.getLocation().add(0, appearance.heightOffset(), 0);
        TextDisplay display =
                location.getWorld()
                        .spawn(
                                location,
                                TextDisplay.class,
                                candidate -> {
                                    view.display = candidate;
                                    candidate.setVisibleByDefault(false);
                                    candidate.setPersistent(false);
                                    candidate.setGravity(false);
                                    candidate.setInvulnerable(true);
                                    candidate.setBillboard(Display.Billboard.CENTER);
                                    candidate.setAlignment(TextDisplay.TextAlignment.CENTER);
                                    candidate.setDefaultBackground(false);
                                    candidate.setBackgroundColor(Color.fromARGB(0));
                                    candidate.setShadowed(true);
                                    candidate.setLineWidth(Integer.MAX_VALUE);
                                    candidate.text(text);
                                });
        if (epoch != generation
                || !available()
                || views.get(id) != view
                || anchor.get() != npc
                || !npc.isValid()
                || !current(viewer, npc)
                || !display.isValid()) {
            removeSafely(id, view);
            return;
        }
        viewer.showEntity(plugin, display);
    }

    private void removeSafely(UUID id, View view) {
        try {
            remove(id, view);
        } catch (RuntimeException failure) {
            warn("Could not remove quest NPC hologram for " + id, failure);
        }
    }

    private void remove(UUID id, View view) {
        view.retired = true;
        if (view.display != null) {
            try {
                view.viewer.hideEntity(plugin, view.display);
            } catch (RuntimeException failure) {
                warn("Could not hide quest NPC hologram", failure);
            }
            view.display.remove();
            if (view.display.isValid())
                throw new IllegalStateException("Quest NPC hologram removal not confirmed");
        }
        views.remove(id, view);
    }

    void reset() {
        try {
            clear();
        } catch (RuntimeException failure) {
            warn("Could not reset quest NPC holograms", failure);
        }
    }

    private void clear() {
        generation = new Object();
        ticket = null;
        BukkitTask pending = task;
        task = null;
        var failure = new IllegalStateException("Quest NPC hologram cleanup incomplete");
        if (pending != null) {
            try {
                pending.cancel();
                if (!pending.isCancelled())
                    throw new IllegalStateException("Hologram cancellation not confirmed");
            } catch (RuntimeException cancellation) {
                failure.addSuppressed(cancellation);
            }
        }
        for (var entry : List.copyOf(views.entrySet())) {
            try {
                remove(entry.getKey(), entry.getValue());
            } catch (RuntimeException removal) {
                failure.addSuppressed(removal);
            }
        }
        if (failure.getSuppressed().length > 0) throw failure;
    }

    void shutdown() {
        retired = true;
        quests.removeAssignmentListener(assignments);
        quests.removeCompletionListener(completions);
        clear();
    }

    private void warn(String message, RuntimeException failure) {
        try {
            plugin.getLogger().log(Level.WARNING, message, failure);
        } catch (RuntimeException ignored) {
            /* Presentation diagnostics do not affect quest state. */
        }
    }

    private static final class View {
        final Mannequin npc;
        final Player viewer;
        TextDisplay display;
        boolean retired;

        View(Mannequin npc, Player viewer) {
            this.npc = npc;
            this.viewer = viewer;
        }
    }
}

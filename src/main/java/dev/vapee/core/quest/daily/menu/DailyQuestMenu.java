package dev.vapee.core.quest.daily.menu;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.ui.Pagination;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.quest.QuestService;
import dev.vapee.core.quest.QuestStatus;
import dev.vapee.core.quest.QuestView;
import dev.vapee.core.quest.daily.DailyQuestService;
import dev.vapee.core.quest.daily.DailyQuestSyncResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Read-only quest view with a scoped presentation-only reset clock. */
public final class DailyQuestMenu {
    private final dev.vapee.core.quest.QuestChangeListener committedChanges = this::refreshActive;
    private boolean observingChanges;

    private Inventory rerenderInventory;

    public static final String PERMISSION = "vapeecore.quest.use";
    public enum Action { PREVIOUS, PAGE_INFO, CLOSE, REFRESH, NEXT, RESET_INFO }
    public static final Layout DEFAULT_LAYOUT = Layout.adaptive(
            MenuLayout.builder(36, Action.class)
                    .emptyState(22)
                    .button(Action.PREVIOUS, 27)
                    .button(Action.PAGE_INFO, 31)
                    .button(Action.CLOSE, 35)
                    .button(Action.RESET_INFO, 34)
                    .button(Action.NEXT, 33)
                    .build(),
            Map.of(1, List.of(13),
                    2, List.of(11, 15),
                    3, List.of(11, 13, 15),
                    4, List.of(10, 12, 14, 16),
                    5, List.of(9, 11, 13, 15, 17)));

    /** Feature-local arrangements; different row counts are exclusive render states. */
    public record Layout(MenuLayout<Action> menu, Map<Integer, List<Integer>> arrangements) {
        public Layout {
            Objects.requireNonNull(menu); menu.requirePaged();
            var copy = new TreeMap<Integer, List<Integer>>();
            arrangements.forEach((count, slots) -> copy.put(count, List.copyOf(slots)));
            int maximum = copy.isEmpty() ? 0 : copy.lastKey();
            if (maximum < 1 || maximum > 5 || copy.size() != maximum)
                throw new IllegalArgumentException("DailyQuest needs complete arrangements for 1..capacity (at most 5)");
            for (int count = 1; count <= maximum; count++) {
                var slots = copy.get(count);
                if (slots == null || slots.size() != count || new HashSet<>(slots).size() != count)
                    throw new IllegalArgumentException("Invalid DailyQuest arrangement for " + count);
                menu.validateTargets(slots);
            }
            arrangements = Collections.unmodifiableMap(copy);
        }
        public int pageSize() { return arrangements.size(); }
        public List<Integer> slots(int visibleCount) {
            if (visibleCount == 0) return List.of();
            var slots = arrangements.get(visibleCount);
            if (slots == null) throw new IllegalArgumentException("Invalid visible quest count: " + visibleCount);
            return slots;
        }
        public static Layout adaptive(MenuLayout<Action> controls, Map<Integer, List<Integer>> rows) {
            var union = rows.values().stream().flatMap(List::stream).distinct().sorted().toList();
            return new Layout(new MenuLayout<>(controls.size(), union, controls.emptySlot(), controls.actions()), rows);
        }
        public static Layout uniform(MenuLayout<Action> menu) {
            var rows = new LinkedHashMap<Integer, List<Integer>>();
            for (int count = 1; count <= Math.min(5, menu.pageSize()); count++)
                rows.put(count, menu.contentSlots().subList(0, count));
            return new Layout(menu, rows);
        }
    }
    private final Layout definition;
    public int pageSize() { return definition.pageSize(); }
    public List<Integer> visibleContentSlots(int count) { return definition.slots(count); }

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final QuestService quests;
    private final PlayerService players;
    private final Function<UUID, DailyQuestSyncResult> sync;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventories;
    private final ItemRenderer items;
    private Player renderingFor;
    private final java.util.function.Consumer<Player> localeChanges = viewer -> refreshActive(viewer.getUniqueId());
    private final Logger logger;
    private final Map<UUID, Inventory> active = new HashMap<>();
    private final Function<Instant, Instant> nextReset;
    private final Supplier<Instant> now;
    private final CountdownScheduler countdownScheduler;
    private Runnable cancelCountdown;

    /** One task per menu, never one per viewer. The returned handle cancels that task. */
    @FunctionalInterface interface CountdownScheduler { Runnable repeat(Runnable update); }

    public DailyQuestMenu(JavaPlugin plugin, DailyQuestService daily, QuestService quests,
                          PlayerService players, MessageService messages) {
        this(id -> daily.syncPlayer(id, Instant.now()), quests, players, messages, plugin.getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, plugin.getLogger(), DEFAULT_LAYOUT, daily::nextReset, Instant::now,
                update -> {
                    var task = plugin.getServer().getScheduler().runTaskTimer(plugin, update, 20L, 20L);
                    return task::cancel;
                });
    }

    DailyQuestMenu(Function<UUID, DailyQuestSyncResult> sync, QuestService quests, PlayerService players,
                   MessageService messages, Function<UUID, Player> onlinePlayer,
                   InventoryFactory inventories, ItemRenderer items, Logger logger, Layout menuLayout,
                   Function<Instant, Instant> nextReset, Supplier<Instant> now, CountdownScheduler countdownScheduler) {
        this.definition = Objects.requireNonNull(menuLayout, "layout");
        this.layout = menuLayout.menu().validateActions(java.util.Set.of());
        this.layout.requirePaged();
        this.sync = Objects.requireNonNull(sync, "sync");
        this.quests = Objects.requireNonNull(quests, "quests");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventories = Objects.requireNonNull(inventories, "inventories");
        Objects.requireNonNull(items, "items");
        this.items = spec -> items.render(UiItems.localize(spec, this.messages, renderingFor));
        this.logger = Objects.requireNonNull(logger, "logger");
        this.nextReset = Objects.requireNonNull(nextReset, "nextReset");
        this.now = Objects.requireNonNull(now, "now");
        this.countdownScheduler = Objects.requireNonNull(countdownScheduler, "countdownScheduler");
    }

    public void open(Player player, int requestedPage) {
        Objects.requireNonNull(player, "player");
        if (!player.hasPermission(PERMISSION)) {
            deny(player, tr("daily-quests.denied"));
            return;
        }
        if (!player.isOnline() || players.getPlayer(player.getUniqueId()).isEmpty()) {
            deny(player, tr("common.profile-unavailable"));
            return;
        }
        DailyQuestSyncResult result;
        try {
            result = rerenderInventory == null ? sync.apply(player.getUniqueId()) : DailyQuestSyncResult.CURRENT;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Daily quest preparation failed for " + player.getUniqueId(), exception);
            deny(player, tr("daily-quests.prepare-failed"));
            return;
        }
        switch (result) {
            case DISABLED -> { deny(player, tr("daily-quests.disabled")); return; }
            case NO_DEFINITIONS -> { deny(player, tr("daily-quests.no-definitions")); return; }
            case PLAYER_NOT_LOADED -> { deny(player, tr("common.profile-unavailable")); return; }
            case ASSIGNMENT_FAILED -> {
                // DailyQuestService records the specific assignment failure and its player UUID.
                logger.warning("Daily quest menu preparation returned ASSIGNMENT_FAILED for " + player.getUniqueId());
                deny(player, tr("daily-quests.prepare-failed")); return;
            }
            case BLOCKED_PENDING_REWARD -> messages.send(player,
                    tr("daily-quests.previous-pending").color(NamedTextColor.YELLOW));
            case CURRENT, INITIALIZED, ROTATED -> { }
        }
        if (!canUse(player)) return;
        Player previousViewer = renderingFor; renderingFor = player;
        try {
        List<QuestView> views = quests.getActiveQuests(player.getUniqueId()).orElse(List.of());
        Pagination pagination = Pagination.of(views.size(), requestedPage, pageSize());
        int page = pagination.page();
        int pages = pagination.pageCount();
        List<Integer> contentSlots = visibleContentSlots(pagination.toIndex() - pagination.fromIndex());
        Map<Integer, String> targets = new LinkedHashMap<>();
        for (int i = pagination.fromIndex(); i < pagination.toIndex(); i++)
            targets.put(contentSlots.get(i - pagination.fromIndex()), views.get(i).definition().id());
        DailyQuestInventoryHolder holder = new DailyQuestInventoryHolder(player.getUniqueId(), page, targets, layout);
        Inventory inventory;
        if (rerenderInventory != null && rerenderInventory.getHolder() instanceof DailyQuestInventoryHolder existing) {
            inventory = rerenderInventory;
            existing.beginRender(holder); holder = existing;
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        } else {
            inventory = inventories.create(holder, layout.size(), messages.localize(player, UiItems.text(tr("daily-quests.title"), NamedTextColor.DARK_GRAY)));
            holder.bind(inventory);
        }
        int start = pagination.fromIndex();
        for (int slot = 0; slot < pagination.toIndex() - start; slot++) {
            inventory.setItem(contentSlots.get(slot), questItem(views.get(start + slot)));
        }
        if (views.isEmpty() && layout.emptySlot() >= 0) inventory.setItem(layout.emptySlot(), item(Material.PAPER, tr("daily-quests.empty"), List.of()));
        if (pagination.hasPrevious()) place(inventory, Action.PREVIOUS, item(Material.ARROW, tr("common.previous-page"), List.of()));
        if (pagination.hasMultiplePages()) place(inventory, Action.PAGE_INFO, item(Material.BOOK, tr("common.page", Map.of("current", Component.text(page + 1), "total", Component.text(pages))),
                List.of(tr("daily-quests.auto-rewards"))));
        place(inventory, Action.CLOSE, item(Material.BARRIER, tr("common.close"), List.of()));
        place(inventory, Action.REFRESH, item(Material.CLOCK, tr("common.refresh"), List.of(tr("daily-quests.refresh-hint"))));
        if (pagination.hasNext()) place(inventory, Action.NEXT, item(Material.ARROW, tr("common.next-page"), List.of()));
        renderResetClock(player, inventory, now.get());
        DailyQuestInventoryHolder renderedHolder = holder;
        layout.actionSlot(Action.RESET_INFO).ifPresent(slot -> renderedHolder.recordAction(slot, Action.RESET_INFO));
        holder.finishRender();
        var opened = inventory == rerenderInventory ? player.getOpenInventory() : player.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory
                && player.getOpenInventory().getTopInventory() == inventory && canUse(player)) {
            active.put(player.getUniqueId(), inventory);
            ensureCountdownTask();
        }
        } finally { renderingFor = previousViewer; }
    }

    public boolean isActive(Player player, Inventory inventory, DailyQuestInventoryHolder holder) {
        return player != null && inventory != null && holder != null
                && holder.owner().equals(player.getUniqueId()) && inventory.getHolder() == holder
                && holder.isBoundTo(inventory) && active.get(player.getUniqueId()) == inventory
                && player.getOpenInventory().getTopInventory() == inventory;
    }

    boolean canUse(Player player) {
        return player.isOnline() && player.hasPermission(PERMISSION)
                && players.getPlayer(player.getUniqueId()).isPresent();
    }

    void forgetIfActive(UUID owner, Inventory inventory) { active.remove(owner, inventory); stopCountdownIfEmpty(); }
    void forget(UUID owner) { active.remove(owner); stopCountdownIfEmpty(); }
    int activeCount() { return active.size(); }

    public void closeOpenInventories() {
        try {
            for (var entry : List.copyOf(active.entrySet())) {
                try {
                    Player player = onlinePlayer.apply(entry.getKey());
                    if (player != null && player.isOnline()
                            && player.getOpenInventory().getTopInventory() == entry.getValue()) player.closeInventory();
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not close daily quest menu for " + entry.getKey(), exception);
                }
            }
        } finally { active.clear(); stopCountdownIfEmpty(); }
    }

    private void deny(Player player, Component reason) {
        Inventory previous = active.remove(player.getUniqueId());
        stopCountdownIfEmpty();
        if (previous != null && player.getOpenInventory().getTopInventory() == previous) player.closeInventory();
        messages.send(player, reason.color(NamedTextColor.RED));
    }

    private ItemStack questItem(QuestView view) {
        boolean completed = view.status() == QuestStatus.COMPLETED;
        boolean pending = view.status() == QuestStatus.REWARD_PENDING;
        NamedTextColor color = completed ? NamedTextColor.GREEN : pending ? NamedTextColor.YELLOW : NamedTextColor.AQUA;
        List<Component> lore = new ArrayList<>();
        lore.add(UiItems.text(view.definition().descriptionText().resolve(messages.localization().locale(renderingFor)), NamedTextColor.GRAY));
        lore.add(UiItems.text(tr("daily-quests.progress", Map.of("current", Component.text(view.currentProgress()), "target", Component.text(view.target()))), NamedTextColor.WHITE));
        lore.add(UiItems.text(tr("daily-quests.reward", Map.of("coins", Component.text(view.definition().rewardCoins()))), NamedTextColor.GOLD));
        lore.add(UiItems.text(completed ? tr("daily-quests.status.completed") : pending ? tr("daily-quests.status.pending") : tr("daily-quests.status.active"), color));
        if (completed) lore.add(UiItems.text(tr("daily-quests.reward-delivered"), NamedTextColor.GRAY));
        else if (pending) lore.add(UiItems.text(tr("daily-quests.reward-retry"), NamedTextColor.YELLOW));
        ItemStack item = items.render(new UiItemSpec(completed ? Material.LIME_DYE : pending ? Material.GOLD_INGOT : Material.PAPER,
                UiItems.text(view.definition().nameText().resolve(messages.localization().locale(renderingFor)), color), List.copyOf(lore)));
        if (completed) {
            var meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        return items.render(UiItems.literal(material, name, NamedTextColor.AQUA, lore));
    }
    @FunctionalInterface interface InventoryFactory {
        Inventory create(DailyQuestInventoryHolder holder, int size, Component title);
    }
    @FunctionalInterface interface ItemRenderer { ItemStack render(UiItemSpec spec); }
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> { inventory.setItem(slot, item); ((DailyQuestInventoryHolder) inventory.getHolder()).recordAction(slot, action); });
    }

    public void refreshActive(UUID owner) {
        Inventory inventory = active.get(owner);
        if (inventory == null || rerenderInventory != null) return;
        Player player = onlinePlayer.apply(owner);
        if (player == null || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || !(inventory.getHolder() instanceof DailyQuestInventoryHolder holder) || !isActive(player, inventory, holder)) {
            forgetIfActive(owner, inventory); return;
        }
        if (!canUse(player)) { forgetIfActive(owner, inventory); player.closeInventory(); return; }
        try { rerenderInventory = inventory; open(player, holder.page()); }
        catch (RuntimeException exception) {
            forgetIfActive(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not update DailyQuestMenu for " + owner, exception);
        } finally { rerenderInventory = null; }
    }

    public void startUpdates() {
        messages.localization().addLocaleListener(localeChanges);
        if (!observingChanges) { quests.addChangeListener(committedChanges); observingChanges = true; }
        ensureCountdownTask();
    }
    public void stopUpdates() {
        cancelCountdown();
        messages.localization().removeLocaleListener(localeChanges);
        if (observingChanges) { quests.removeChangeListener(committedChanges); observingChanges = false; }
    }
    public void shutdown() { stopUpdates(); closeOpenInventories(); }

    private void renderResetClock(Player player, Inventory inventory, Instant instant) {
        layout.actionSlot(Action.RESET_INFO).ifPresent(slot -> {
            String time = ResetCountdownFormatter.format(Duration.between(instant, nextReset.apply(instant)));
            UiItemSpec spec = UiItems.localize(UiItems.literal(Material.CLOCK, tr("daily-quests.reset-title"),
                    NamedTextColor.GRAY, List.of(tr("daily-quests.reset-countdown", Map.of("time", Component.text(time))),
                            Component.empty(), tr("daily-quests.reset-description"))), messages, player);
            var holder = (DailyQuestInventoryHolder) inventory.getHolder();
            if (!spec.equals(holder.resetClock) || inventory.getItem(slot) == null) {
                inventory.setItem(slot, items.render(spec));
                holder.resetClock = spec;
            }
        });
    }

    private void ensureCountdownTask() {
        if (observingChanges && cancelCountdown == null && !active.isEmpty()
                && layout.actionSlot(Action.RESET_INFO).isPresent())
            cancelCountdown = countdownScheduler.repeat(this::updateCountdowns);
    }

    private void stopCountdownIfEmpty() { if (active.isEmpty()) cancelCountdown(); }
    private void cancelCountdown() {
        if (cancelCountdown != null) { Runnable cancel = cancelCountdown; cancelCountdown = null; cancel.run(); }
    }

    /** Iterates only registered open views; never syncs quests or persists player state. */
    private void updateCountdowns() {
        if (!observingChanges) return;
        Instant instant = now.get();
        for (var entry : List.copyOf(active.entrySet())) {
            UUID owner = entry.getKey(); Inventory inventory = entry.getValue();
            try {
                Player player = onlinePlayer.apply(owner);
                if (player == null || !canUse(player)
                        || !(inventory.getHolder() instanceof DailyQuestInventoryHolder holder)
                        || !isActive(player, inventory, holder)
                        || layout.actionSlot(Action.RESET_INFO).isEmpty()
                        || holder.actionAt(layout.slot(Action.RESET_INFO)).orElse(null) != Action.RESET_INFO) {
                    forgetIfActive(owner, inventory); continue;
                }
                renderResetClock(player, inventory, instant);
            } catch (RuntimeException exception) {
                forgetIfActive(owner, inventory);
                logger.log(Level.WARNING, "Could not update daily reset clock for " + owner, exception);
            }
        }
        stopCountdownIfEmpty();
    }

}

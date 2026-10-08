package dev.vapee.core.quest.daily.menu;

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
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Focused read-only daily quest view. Controls always synchronize and rebuild from current state. */
public final class DailyQuestMenu {
    public static final String PERMISSION = "vapeecore.quest.use";
    public enum Action { PREVIOUS, PAGE_INFO, CLOSE, REFRESH, NEXT }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(54, Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(Action.PREVIOUS, 45)
            .button(Action.PAGE_INFO, 49)
            .button(Action.CLOSE, 50)
            .button(Action.REFRESH, 52)
            .button(Action.NEXT, 53)
            .build();

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final QuestService quests;
    private final PlayerService players;
    private final Function<UUID, DailyQuestSyncResult> sync;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventories;
    private final ItemRenderer items;
    private final Logger logger;
    private final Map<UUID, Inventory> active = new HashMap<>();

    public DailyQuestMenu(JavaPlugin plugin, DailyQuestService daily, QuestService quests,
                          PlayerService players, MessageService messages) {
        this(daily, quests, players, messages, plugin.getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, Instant::now, plugin.getLogger());
    }

    DailyQuestMenu(DailyQuestService daily, QuestService quests, PlayerService players, MessageService messages,
                   Function<UUID, Player> onlinePlayer, InventoryFactory inventories, ItemRenderer items,
                   Supplier<Instant> now, Logger logger) {
        this(id -> daily.syncPlayer(id, now.get()), quests, players, messages, onlinePlayer, inventories, items, logger);
        Objects.requireNonNull(daily, "daily");
        Objects.requireNonNull(now, "now");
    }

    DailyQuestMenu(Function<UUID, DailyQuestSyncResult> sync, QuestService quests, PlayerService players,
                   MessageService messages, Function<UUID, Player> onlinePlayer,
                   InventoryFactory inventories, ItemRenderer items, Logger logger) {
        this(sync, quests, players, messages, onlinePlayer, inventories, items, logger, DEFAULT_LAYOUT);
    }

    DailyQuestMenu(Function<UUID, DailyQuestSyncResult> sync, QuestService quests, PlayerService players,
                   MessageService messages, Function<UUID, Player> onlinePlayer,
                   InventoryFactory inventories, ItemRenderer items, Logger logger, MenuLayout<Action> menuLayout) {
        this.layout = Objects.requireNonNull(menuLayout, "layout").validateActions(java.util.Set.of());
        this.layout.requirePaged();
        this.sync = Objects.requireNonNull(sync, "sync");
        this.quests = Objects.requireNonNull(quests, "quests");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventories = Objects.requireNonNull(inventories, "inventories");
        this.items = Objects.requireNonNull(items, "items");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player, int requestedPage) {
        Objects.requireNonNull(player, "player");
        if (!player.hasPermission(PERMISSION)) {
            deny(player, "You do not have permission to view quests.");
            return;
        }
        if (!player.isOnline() || players.getPlayer(player.getUniqueId()).isEmpty()) {
            deny(player, "Your player profile is not available.");
            return;
        }
        DailyQuestSyncResult result;
        try {
            result = sync.apply(player.getUniqueId());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Daily quest preparation failed for " + player.getUniqueId(), exception);
            deny(player, "Your daily quests could not be prepared. Please try again later.");
            return;
        }
        switch (result) {
            case DISABLED -> { deny(player, "Daily quests are currently disabled."); return; }
            case NO_DEFINITIONS -> { deny(player, "No daily quests are currently configured."); return; }
            case PLAYER_NOT_LOADED -> { deny(player, "Your player profile is not available."); return; }
            case ASSIGNMENT_FAILED -> {
                // DailyQuestService records the specific assignment failure and its player UUID.
                logger.warning("Daily quest menu preparation returned ASSIGNMENT_FAILED for " + player.getUniqueId());
                deny(player, "Your daily quests could not be prepared. Please try again later."); return;
            }
            case BLOCKED_PENDING_REWARD -> messages.send(player,
                    Component.text("A previous quest reward is still pending.", NamedTextColor.YELLOW));
            case CURRENT, INITIALIZED, ROTATED -> { }
        }
        if (!canUse(player)) return;
        List<QuestView> views = quests.getActiveQuests(player.getUniqueId()).orElse(List.of());
        Pagination pagination = Pagination.of(views.size(), requestedPage, layout.pageSize());
        int page = pagination.page();
        int pages = pagination.pageCount();
        Map<Integer, String> targets = new LinkedHashMap<>();
        for (int i = pagination.fromIndex(); i < pagination.toIndex(); i++)
            targets.put(layout.contentSlot(i - pagination.fromIndex()), views.get(i).definition().id());
        DailyQuestInventoryHolder holder = new DailyQuestInventoryHolder(player.getUniqueId(), page, targets, layout);
        Inventory inventory = inventories.create(holder, layout.size(), UiItems.text("Daily Quests", NamedTextColor.DARK_GRAY));
        holder.bind(inventory);
        int start = pagination.fromIndex();
        for (int slot = 0; slot < pagination.toIndex() - start; slot++) {
            inventory.setItem(layout.contentSlot(slot), questItem(views.get(start + slot)));
        }
        if (pagination.hasPrevious()) place(inventory, Action.PREVIOUS, item(Material.ARROW, "Previous page", List.of()));
        place(inventory, Action.PAGE_INFO, item(Material.BOOK, "Page " + (page + 1) + " / " + pages,
                List.of("Rewards are delivered automatically.")));
        place(inventory, Action.CLOSE, item(Material.BARRIER, "Close", List.of()));
        place(inventory, Action.REFRESH, item(Material.CLOCK, "Refresh", List.of("Show your current daily quests.")));
        if (pagination.hasNext()) place(inventory, Action.NEXT, item(Material.ARROW, "Next page", List.of()));
        var opened = player.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory
                && player.getOpenInventory().getTopInventory() == inventory && canUse(player)) {
            active.put(player.getUniqueId(), inventory);
        }
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

    void forgetIfActive(UUID owner, Inventory inventory) { active.remove(owner, inventory); }
    void forget(UUID owner) { active.remove(owner); }
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
        } finally { active.clear(); }
    }

    private void deny(Player player, String reason) {
        Inventory previous = active.remove(player.getUniqueId());
        if (previous != null && player.getOpenInventory().getTopInventory() == previous) player.closeInventory();
        messages.send(player, Component.text(reason, NamedTextColor.RED));
    }

    private ItemStack questItem(QuestView view) {
        boolean completed = view.status() == QuestStatus.COMPLETED;
        boolean pending = view.status() == QuestStatus.REWARD_PENDING;
        NamedTextColor color = completed ? NamedTextColor.GREEN : pending ? NamedTextColor.YELLOW : NamedTextColor.AQUA;
        List<Component> lore = new ArrayList<>();
        lore.add(UiItems.text(view.definition().description(), NamedTextColor.GRAY));
        lore.add(UiItems.text("Progress: " + view.currentProgress() + " / " + view.target(), NamedTextColor.WHITE));
        lore.add(UiItems.text("Reward: " + view.definition().rewardCoins() + " Coins", NamedTextColor.GOLD));
        lore.add(UiItems.text(completed ? "Completed" : pending ? "Reward Pending" : "In Progress", color));
        if (completed) lore.add(UiItems.text("Your reward has already been delivered.", NamedTextColor.GRAY));
        else if (pending) lore.add(UiItems.text("Your reward will be retried automatically.", NamedTextColor.YELLOW));
        return items.render(new UiItemSpec(completed ? Material.LIME_DYE : pending ? Material.GOLD_INGOT : Material.PAPER,
                UiItems.text(view.definition().name(), color), List.copyOf(lore)));
    }

    private ItemStack item(Material material, String name, List<String> lore) {
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
        layout.actionSlot(action, position).ifPresent(slot -> inventory.setItem(slot, item));
    }

}

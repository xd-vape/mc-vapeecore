package dev.vapee.core.quest.daily.menu;

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
    public static final int SIZE = 54;
    public static final int CONTENT_SIZE = 45;
    public static final int PREVIOUS = 45;
    public static final int PAGE_INFO = 49;
    public static final int CLOSE = 50;
    public static final int REFRESH = 52;
    public static final int NEXT = 53;
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
        Pagination pagination = Pagination.of(views.size(), requestedPage, CONTENT_SIZE);
        int page = pagination.page();
        int pages = pagination.pageCount();
        DailyQuestInventoryHolder holder = new DailyQuestInventoryHolder(player.getUniqueId(), page);
        Inventory inventory = inventories.create(holder, SIZE, UiItems.text("Daily Quests", NamedTextColor.DARK_GRAY));
        holder.bind(inventory);
        int start = pagination.fromIndex();
        for (int slot = 0; slot < pagination.toIndex() - start; slot++) {
            inventory.setItem(slot, questItem(views.get(start + slot)));
        }
        if (pagination.hasPrevious()) inventory.setItem(PREVIOUS, item(Material.ARROW, "Previous page", List.of()));
        inventory.setItem(PAGE_INFO, item(Material.BOOK, "Page " + (page + 1) + " / " + pages,
                List.of("Rewards are delivered automatically.")));
        inventory.setItem(CLOSE, item(Material.BARRIER, "Close", List.of()));
        inventory.setItem(REFRESH, item(Material.CLOCK, "Refresh", List.of("Show your current daily quests.")));
        if (pagination.hasNext()) inventory.setItem(NEXT, item(Material.ARROW, "Next page", List.of()));
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
}

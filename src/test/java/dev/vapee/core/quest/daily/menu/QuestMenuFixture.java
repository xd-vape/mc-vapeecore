package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.quest.*;
import dev.vapee.core.quest.daily.*;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.time.*;
import java.util.*;
import static dev.vapee.core.quest.QuestCompletionFixture.*;

public final class QuestMenuFixture {
    public final QuestCompletionFixture domain = new QuestCompletionFixture();
    public final MessageService messages = messages();
    public final Map<UUID, TestPlayer> online = new HashMap<>();
    public final DailyQuestMenu menu;
    public final DailyQuestMenuListener listener;
    public DailyQuestSyncResult forcedResult;
    public RuntimeException syncFailure;
    public int syncCalls;
    public int presentationCalls;
    public Instant now = Instant.parse("2026-10-03T12:00:00Z");
    public boolean enabled = true;
    public int perDay = 100;
    public final DailyQuestService daily = new DailyQuestService(domain.players, domain.quests, domain.registry,
            () -> new DailyQuestConfig.State(enabled, perDay, LocalTime.MIDNIGHT, "UTC", ZoneId.of("UTC"), domain.registry.snapshot()),
            domain.logger);

    public QuestMenuFixture() throws Exception {
        menu = new DailyQuestMenu(id -> {
            syncCalls++;
            if (syncFailure != null) throw syncFailure;
            return forcedResult != null ? forcedResult : daily.syncPlayer(id, now);
        }, domain.quests, domain.players, messages, id -> {
            TestPlayer player = online.get(id);
            return player != null && player.online ? player.player : null;
        }, (holder, size, title) -> inventory(holder, size), SpecItem::new, domain.logger);
        listener = new DailyQuestMenuListener(menu);
    }

    public TestPlayer player() {
        TestPlayer value = new TestPlayer(domain.load());
        online.put(value.id, value);
        return value;
    }
    public InventoryClickEvent click(TestPlayer player, Inventory top, int slot, ClickType click) {
        InventoryClickEvent event = new InventoryClickEvent(view(player, top), InventoryType.SlotType.CONTAINER,
                slot, click, click.isShiftClick() ? InventoryAction.MOVE_TO_OTHER_INVENTORY : InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(event);
        return event;
    }
    public static Inventory inventory(InventoryHolder holder, int size) {
        ItemStack[] items = new ItemStack[size];
        return proxy(Inventory.class, (method, args) -> switch (method) {
            case "getHolder" -> holder;
            case "getSize" -> size;
            case "getItem" -> items[(int) args[0]];
            case "setItem" -> { items[(int) args[0]] = (ItemStack) args[1]; yield null; }
            case "getType" -> InventoryType.CHEST;
            default -> DEFAULT;
        });
    }
    public static InventoryView view(TestPlayer player, Inventory top) {
        return proxy(InventoryView.class, (method, args) -> switch (method) {
            case "getTopInventory" -> top;
            case "getBottomInventory" -> player.bottom;
            case "getPlayer" -> player.player;
            case "getType" -> InventoryType.CHEST;
            case "getInventory" -> (int) args[0] < 0 ? null : (int) args[0] < top.getSize() ? top : player.bottom;
            case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
            case "getSlotType" -> InventoryType.SlotType.CONTAINER;
            case "countSlots" -> top.getSize() + player.bottom.getSize();
            default -> DEFAULT;
        });
    }
    public static UiItemSpec spec(Inventory inventory, int slot) { return ((SpecItem) inventory.getItem(slot)).spec; }
    public static String text(Inventory inventory, int slot) { return plain(spec(inventory, slot).name()); }
    public static String lore(Inventory inventory, int slot) {
        return spec(inventory, slot).lore().stream().map(QuestCompletionFixture::plain).reduce("", (a, b) -> a + "\n" + b);
    }
    public final class TestPlayer {
        public final UUID id;
        public final Inventory bottom = inventory(null, 36);
        public final List<Component> output = new ArrayList<>();
        public final Player player;
        public Inventory open = bottom;
        public int opens;
        public int closes;
        public boolean permission = true;
        public boolean online = true;
        public boolean cancelNextOpen;
        public boolean failClose;
        TestPlayer(UUID id) {
            this.id = id;
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "isOnline" -> online;
                case "hasPermission" -> permission;
                case "openInventory" -> {
                    if (cancelNextOpen) { cancelNextOpen = false; yield null; }
                    listener.onInventoryClose(new InventoryCloseEvent(view(this, open)));
                    open = (Inventory) args[0]; opens++;
                    yield view(this, open);
                }
                case "getOpenInventory" -> view(this, open);
                case "closeInventory" -> {
                    if (failClose) throw new IllegalStateException("synthetic close failure");
                    Inventory old = open; open = bottom; closes++;
                    listener.onInventoryClose(new InventoryCloseEvent(view(this, old)));
                    yield null;
                }
                case "sendMessage" -> { for (Object arg : args) if (arg instanceof Component c) output.add(c); yield null; }
                case "playSound", "showTitle", "sendActionBar", "showBossBar" -> { presentationCalls++; yield null; }
                default -> DEFAULT;
            });
        }
        public String received() { return output.stream().map(QuestCompletionFixture::plain).reduce("", (a, b) -> a + "\n" + b); }
    }
    private static final class SpecItem extends ItemStack {
        private final UiItemSpec spec;
        private SpecItem(UiItemSpec spec) { super(); this.spec = spec; }
    }
}

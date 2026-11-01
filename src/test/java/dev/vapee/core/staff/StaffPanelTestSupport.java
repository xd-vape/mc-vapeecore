package dev.vapee.core.staff;

import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.staff.StaffHierarchyConfig;
import dev.vapee.core.rank.staff.StaffHierarchyService;
import dev.vapee.core.ui.HeadTestSupport;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Real feature/policy/profile code, controlled Paper I/O only. Also used by the module lifecycle tests. */
public final class StaffPanelTestSupport {
    public static final class Fixture {
        public final Logger logger = Logger.getAnonymousLogger();
        public final MessageService messages;
        public final Map<UUID, Probe> players = new LinkedHashMap<>();
        public final Map<UUID, String> groups = new HashMap<>();
        public final Map<UUID, RankInfo> ranks = new HashMap<>();
        public final AtomicReference<StaffHierarchyConfig> config = new AtomicReference<>(StaffHierarchyConfig.defaults());
        public final StaffHierarchyService hierarchy = new StaffHierarchyService(config::get,
                id -> Optional.ofNullable(groups.get(id)), id -> { throw new AssertionError("Panel must never load LP users"); });
        public final HeadTestSupport.Profiles profiles = new HeadTestSupport.Profiles();
        public final Queue<Runnable> queued = new ArrayDeque<>();
        public final List<StaffPanel.CommandRequest> executed = new ArrayList<>();
        public BiConsumer<Player, StaffPanel.CommandRequest> execute = (p, r) -> executed.add(r);
        public boolean runtime = true;
        public StaffPanel panel;
        public Fixture() {
            logger.setUseParentHandlers(false);
            messages = new MessageService(() -> "", LocalizationService.bundled(logger));
            panel = createPanel(() -> runtime);
        }
        public StaffPanel createPanel(BooleanSupplier enabled) {
            panel = new StaffPanel(hierarchy, id -> Optional.ofNullable(ranks.get(id)), messages,
                    () -> players.values().stream().map(p -> p.player).toList(),
                    id -> players.containsKey(id) ? players.get(id).player : null,
                    (holder, size, title) -> new Stock(holder, size, title).inventory,
                    HeadTestSupport::item, profiles.heads::apply, queued::add, enabled,
                    (p, request) -> execute.accept(p, request), logger);
            return panel;
        }
        public Probe player(String name, String group, String... permissions) { return player(UUID.randomUUID(), name, group, permissions); }
        public Probe player(UUID id, String name, String group, String... permissions) {
            Probe probe = new Probe(this, id, name); players.put(id, probe); groups.put(id, group);
            probe.permissions.addAll(List.of(permissions));
            ranks.put(id, new RankInfo(group == null ? "default" : group, group == null ? "Default" : group,
                    Optional.empty(), Optional.of(NamedTextColor.GOLD), OptionalInt.of(1000)));
            profiles.online.put(id, profiles.profile(id, HeadTestSupport.skin(1)));
            return probe;
        }
        public Probe admin() { return player("Admin", "admin", StaffPanel.PERMISSION, "vapeecore.utility.teleport",
                "vapeecore.moderation.history", "vapeecore.moderation.warn", "vapeecore.moderation.mute", "vapeecore.moderation.kick"); }
        public void open(Probe p) { panel.onCommand(p.player, null, "staff", new String[0]); }
        public void list(Probe p) { p.messages.clear(); panel.onCommand(p.player, null, "staff", new String[]{"list"}); }
        public InventoryClickEvent click(Probe p, int slot) { return click(p, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL); }
        public InventoryClickEvent click(Probe p, int slot, ClickType type, InventoryAction action) {
            var event = new InventoryClickEvent(p.view, InventoryType.SlotType.CONTAINER, slot, type, action, -1);
            panel.onClick(event); return event;
        }
        public void select(Probe viewer, Probe target) {
            StaffPanelHolder holder = viewer.holder();
            if (!holder.content().targets().containsValue(target.player)) {
                click(viewer, StaffPanel.ROSTER_LAYOUT.slot(StaffPanel.Action.MODE)); drain(); holder = viewer.holder();
            }
            int slot = holder.content().targets().entrySet().stream().filter(e -> e.getValue() == target.player).findFirst().orElseThrow().getKey();
            click(viewer, slot); drain();
        }
        public void drain() {
            int count = 0; while (!queued.isEmpty()) { if (++count > 200) throw new AssertionError("Unbounded fixture tasks"); queued.remove().run(); }
        }
    }
    public static final class Probe {
        final Fixture f;
        public final UUID id;
        public final String name;
        public final Player player;
        public final Set<String> permissions = new HashSet<>();
        public final Set<UUID> invisible = new HashSet<>();
        public final List<Component> messages = new ArrayList<>();
        public Locale locale = Locale.US;
        public boolean online = true, permissionContext = true, op, cancelOpen;
        public int opens, closes, opReads;
        public InventoryView view;
        public Runnable afterOpen = () -> { }, afterClose = () -> { };
        final PlayerInventory bottom = proxy(PlayerInventory.class, (m, a) -> m.equals("getSize") ? 36 : null);
        Probe(Fixture f, UUID id, String name) {
            this.f = f; this.id = id; this.name = name;
            player = proxy(Player.class, (m, a) -> switch (m) {
                case "getUniqueId" -> id; case "getName" -> name; case "isOnline" -> online;
                case "hasPermission" -> permissionContext && permissions.contains(a[0]);
                case "isOp" -> { opReads++; yield op; }
                case "canSee" -> !invisible.contains(((Player) a[0]).getUniqueId());
                case "locale", "getLocale" -> m.equals("locale") ? locale : locale.toString();
                case "getOpenInventory" -> view; case "getInventory" -> bottom; case "getGameMode" -> GameMode.ADVENTURE;
                case "openInventory" -> {
                    opens++; if (cancelOpen) yield null;
                    if (f.panel != null) f.panel.onClose(new InventoryCloseEvent(view));
                    view = view((Inventory) a[0]); afterOpen.run(); yield view;
                }
                case "closeInventory" -> {
                    closes++; if (f.panel != null) f.panel.onClose(new InventoryCloseEvent(view));
                    view = view(new Stock(null, 9, Component.empty()).inventory); afterClose.run(); yield null;
                }
                case "sendMessage" -> { for (Object v : a) if (v instanceof Component c) messages.add(c); yield null; }
                case "setGameMode", "setAllowFlight", "hidePlayer", "showPlayer", "setOp" -> throw new AssertionError("Roster presentation changed player authority/presence");
                default -> null;
            });
            view = view(new Stock(null, 9, Component.empty()).inventory);
        }
        public StaffPanelHolder holder() { return (StaffPanelHolder) view.getTopInventory().getHolder(); }
        public String text() { return messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("", (a, b) -> a + "\n" + b); }
        public void replace(Inventory inventory) { view = view(inventory); }
        public InventoryView view(Inventory top) {
            return proxy(InventoryView.class, (m, a) -> switch (m) {
                case "getPlayer" -> player; case "getTopInventory" -> top; case "getBottomInventory" -> bottom;
                case "getInventory" -> (int) a[0] < 0 ? null : (int) a[0] < top.getSize() ? top : bottom;
                case "convertSlot" -> (int) a[0] < top.getSize() ? a[0] : (int) a[0] - top.getSize();
                case "getType" -> InventoryType.CHEST;
                default -> null;
            });
        }
    }
    public static final class Stock {
        public final Inventory inventory;
        public ItemStack[] contents;
        public final Component title;
        public Stock(InventoryHolder holder, int size, Component title) {
            this.title = title; contents = new ItemStack[size];
            inventory = proxy(Inventory.class, (m, a) -> switch (m) {
                case "getHolder" -> holder; case "getSize" -> size; case "getType" -> InventoryType.CHEST;
                case "getItem" -> contents[(int) a[0]];
                case "setContents" -> { if (((ItemStack[]) a[0]).length != size) throw new AssertionError("Wrong size"); contents = ((ItemStack[]) a[0]).clone(); yield null; }
                case "getContents" -> contents.clone();
                case "setItem" -> { contents[(int) a[0]] = (ItemStack) a[1]; yield null; }
                default -> null;
            });
        }
    }
    public static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return switch (m.getName()) {
                case "hashCode" -> System.identityHashCode(p); case "equals" -> p == a[0]; default -> type.getSimpleName();
            };
            Object value = handler.apply(m.getName(), a == null ? new Object[0] : a);
            if (value != null || !m.getReturnType().isPrimitive()) return value;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == double.class) return 0D;
            if (m.getReturnType() == float.class) return 0F;
            if (m.getReturnType() == long.class) return 0L;
            return 0;
        }));
    }
}

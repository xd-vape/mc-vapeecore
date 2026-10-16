package dev.vapee.core.lobby.experience.player;

import static dev.vapee.core.quest.QuestCompletionFixture.*;
import dev.vapee.core.i18n.*;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.*;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.*;
import dev.vapee.core.ui.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Real menu, holder and native event handlers; only Paper inventory/player objects are substitutes. */
public final class PlayerMenuHarness {
    static int checks;
    public static void main(String[] args) throws Exception {
        var f = new Fixture(PlayerMenu.DEFAULT_LAYOUT);
        var owner = f.player("Owner"); var foreign = f.player("Foreign");
        check(f.menu.open(owner.player), "root opens for a loaded eligible player");
        Inventory top = owner.open; var holder = (PlayerMenuHolder) top.getHolder();
        check(top.getSize() == f.menu.layout().size() && f.menu.isActive(owner.player, top, holder), "exact native owner/binding registered");
        for (var action : PlayerMenu.Action.values()) check(top.getItem(f.menu.layout().slot(action)) != null, "supported destination rendered " + action);
        for (int slot = 0; slot < top.getSize(); slot++) check((top.getItem(slot) != null) == f.menu.layout().actionAt(slot).isPresent(), "no filler/dead entry " + slot);
        check(f.menu.layout().actions().size() == 4, "only three supported features and Close; no chat-only profile entry");
        for (var action : List.of(PlayerMenu.Action.VISIBILITY, PlayerMenu.Action.FRIENDS, PlayerMenu.Action.SETTINGS)) {
            f.menu.open(owner.player); top = owner.open;
            check(f.click(owner, top, f.menu.layout().slot(action), ClickType.LEFT).isCancelled(), "navigation consumes click");
            check(f.calls.getOrDefault(action, 0) == 1 && f.menu.activeCount() == 0, "existing destination dispatched once and old root unbound " + action);
        }
        f.menu.open(owner.player); top = owner.open; holder = (PlayerMenuHolder) top.getHolder();
        int calls = f.callCount(); int slot = f.menu.layout().slot(PlayerMenu.Action.FRIENDS);
        f.click(foreign, top, slot, ClickType.LEFT);
        f.click(owner, top, slot, ClickType.SHIFT_LEFT);
        f.click(owner, top, top.getSize() + 1, ClickType.LEFT);
        check(f.callCount() == calls && f.menu.isActive(owner.player, top, holder), "foreign/shift/bottom clicks inert");
        Inventory stolenBinding = inventory(holder, top.getSize());
        owner.open = stolenBinding; f.click(owner, stolenBinding, slot, ClickType.LEFT);
        check(f.callCount() == calls, "copied exact holder cannot authorize forged inventory");
        var forgedHolder = new PlayerMenuHolder(owner.id); var forged = inventory(forgedHolder, top.getSize()); forgedHolder.bind(forged);
        owner.open = forged; f.click(owner, forged, slot, ClickType.RIGHT);
        check(f.callCount() == calls, "unregistered bound inventory is inert");
        owner.open = top; f.menu.open(owner.player); var fresh = owner.open;
        f.click(owner, top, slot, ClickType.LEFT);
        check(f.callCount() == calls && owner.open == fresh, "stale previous inventory inert");
        owner.denied.add("vapeecore.friend.use"); f.click(owner, fresh, slot, ClickType.LEFT);
        check(f.callCount() == calls, "permission loss revalidated after rendering");
        f.menu.open(owner.player); check(owner.open.getItem(slot) == null, "unavailable destination omitted");
        f.click(owner, owner.open, slot, ClickType.LEFT); check(f.callCount() == calls, "hidden destination has no invisible action");
        owner.denied.clear(); f.menu.open(owner.player); top = owner.open;
        f.eligible = false; f.click(owner, top, slot, ClickType.LEFT);
        check(f.callCount() == calls, "BUILD/activity/non-lobby ownership gate revalidated"); f.eligible = true;
        var drag = new InventoryDragEvent(view(owner, top), new SpecItem(new UiItemSpec(org.bukkit.Material.STONE, Component.text("cursor"), List.of())), null, false, Map.of(1, new SpecItem(new UiItemSpec(org.bukkit.Material.STONE, Component.text("one"), List.of()))));
        f.listener.onDrag(drag); check(drag.isCancelled(), "all top inventory drag cancelled");
        f.menu.startUpdates(); f.messages.localization().localeChanged(owner.player, Locale.GERMANY);
        check(owner.open == top && plain(((SpecItem) top.getItem(f.menu.layout().slot(PlayerMenu.Action.SETTINGS))).spec.name()).equals("Einstellungen"), "locale event rerenders same inventory with actual localized specs");
        owner.locale = Locale.GERMANY; f.menu.open(owner.player);
        check(plain(f.titles.get(owner.open)).equals("Spielermenü"), "German native title on open");
        foreign.locale = Locale.US; f.menu.open(foreign.player);
        check(plain(f.titles.get(foreign.open)).equals("Player Menu"), "parallel English native title");
        f.listener.onQuit(new PlayerQuitEvent(owner.player, Component.empty())); check(f.menu.activeCount() == 1, "quit removes exact owner's active root");
        f.menu.shutdown(); check(f.menu.activeCount() == 0 && foreign.open == foreign.bottom, "disable closes owned active inventory and clears registry");
        f.menu.open(owner.player); f.click(owner, owner.open, f.menu.layout().slot(PlayerMenu.Action.CLOSE), ClickType.RIGHT);
        check(owner.open == owner.bottom && f.menu.activeCount() == 0, "Close follows native close cleanup");
        f.menu.open(owner.player);
        f.listener.onWorldChange(new org.bukkit.event.player.PlayerChangedWorldEvent(owner.player,
                proxy(org.bukkit.World.class, (name,values) -> DEFAULT)));
        check(owner.open == owner.bottom && f.menu.activeCount() == 0, "world change closes and forgets only owned active root");
        f.eligible = false; check(!f.menu.open(owner.player), "ineligible open denied"); f.eligible = true;
        f.players.unloadPlayer(owner.id); check(!f.menu.open(owner.player), "profile unavailable open denied");
        var minimal = new Fixture(MenuLayout.builder(9, PlayerMenu.Action.class).button(PlayerMenu.Action.SETTINGS, 3).build());
        var user = minimal.player("Minimal"); minimal.menu.open(user.player);
        check(user.open.getItem(3) != null && minimal.menu.layout().actionSlot(PlayerMenu.Action.VISIBILITY).isEmpty(), "one local layout controls size and omitted composition");
        minimal.click(user, user.open, 3, ClickType.RIGHT); check(minimal.calls.get(PlayerMenu.Action.SETTINGS) == 1, "relocated supported action works");
        System.out.println("PlayerMenuHarness passed " + checks + " checks.");
    }
    static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    static Inventory inventory(InventoryHolder holder, int size) {
        var items = new ItemStack[size];
        return proxy(Inventory.class, (name,args) -> switch(name) {
            case "getHolder" -> holder; case "getSize" -> size; case "getItem" -> items[(int)args[0]];
            case "setItem" -> { items[(int)args[0]] = (ItemStack)args[1]; yield null; }
            case "clear" -> { Arrays.fill(items,null); yield null; } case "getType" -> InventoryType.CHEST;
            default -> DEFAULT;
        });
    }
    static InventoryView view(TestPlayer player, Inventory top) {
        return proxy(InventoryView.class, (name,args) -> switch(name) {
            case "getTopInventory" -> top; case "getBottomInventory" -> player.bottom; case "getPlayer" -> player.player;
            case "getInventory" -> (int)args[0] < 0 ? null : (int)args[0] < top.getSize() ? top : player.bottom;
            case "convertSlot" -> (int)args[0] < top.getSize() ? args[0] : (int)args[0]-top.getSize();
            case "getType" -> InventoryType.CHEST; case "getSlotType" -> InventoryType.SlotType.CONTAINER;
            default -> DEFAULT;
        });
    }
    static final class SpecItem extends ItemStack { final UiItemSpec spec; SpecItem(UiItemSpec spec) { super(); this.spec = spec; } }
    static final class Fixture {
        final Map<UUID,TestPlayer> online = new HashMap<>();
        final Map<Inventory,Component> titles = new IdentityHashMap<>();
        final Map<PlayerMenu.Action,Integer> calls = new EnumMap<>(PlayerMenu.Action.class);
        final PlayerService players = new PlayerService(new PlayerRepository() {
            public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.empty(); }
            public void save(CorePlayer player) { }
            public boolean exists(UUID id) { return false; }
        }, Logger.getAnonymousLogger());
        final MessageService messages = new MessageService(() -> "", LocalizationService.bundled(Logger.getAnonymousLogger()));
        final PlayerMenu menu; final PlayerMenuListener listener; boolean eligible = true;
        Fixture(MenuLayout<PlayerMenu.Action> layout) {
            Map<PlayerMenu.Action,Consumer<Player>> destinations = new EnumMap<>(PlayerMenu.Action.class);
            for (var action : PlayerMenu.Action.values()) if (action != PlayerMenu.Action.CLOSE) destinations.put(action, ignored -> calls.merge(action,1,Integer::sum));
            menu = new PlayerMenu(new PlayerSettingsService(players),messages,p -> eligible,destinations,
                    id -> online.containsKey(id) ? online.get(id).player : null,
                    (holder,size,title) -> { var inventory = inventory(holder,size); titles.put(inventory,title); return inventory; }, SpecItem::new, layout);
            listener = new PlayerMenuListener(menu);
        }
        TestPlayer player(String name) { var p = new TestPlayer(this); online.put(p.id,p); players.loadPlayer(p.id,name); return p; }
        int callCount() { return calls.values().stream().mapToInt(Integer::intValue).sum(); }
        InventoryClickEvent click(TestPlayer p, Inventory top, int slot, ClickType click) {
            var event = new InventoryClickEvent(view(p,top), InventoryType.SlotType.CONTAINER,slot,click,InventoryAction.PICKUP_ALL);
            listener.onClick(event); return event;
        }
    }
    static final class TestPlayer {
        final UUID id = UUID.randomUUID(); final Inventory bottom = inventory(null,36); Inventory open = bottom;
        Locale locale = Locale.US; final Set<String> denied = new HashSet<>(); final Player player;
        TestPlayer(Fixture f) { player = proxy(Player.class,(name,args) -> switch(name) {
            case "getUniqueId" -> id; case "isOnline" -> true; case "locale" -> locale; case "hasPermission" -> !denied.contains((String)args[0]);
            case "getOpenInventory" -> view(this,open);
            case "openInventory" -> { f.listener.onClose(new InventoryCloseEvent(view(this,open))); open = (Inventory)args[0]; yield view(this,open); }
            case "closeInventory" -> { var old = open; open = bottom; f.listener.onClose(new InventoryCloseEvent(view(this,old))); yield null; }
            default -> DEFAULT;
        }); }
    }
}

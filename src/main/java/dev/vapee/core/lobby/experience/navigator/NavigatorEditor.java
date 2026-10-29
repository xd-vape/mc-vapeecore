package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.i18n.LocalizedText;
import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.ui.UiItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** A deliberately scoped Navigator draft editor. Main-thread GUI and explicit text commands only. */
public final class NavigatorEditor implements TabExecutor, Listener {
    public static final String PERMISSION = "vapeecore.navigator.admin";
    private static final List<Material> ICONS = List.of(Material.COMPASS, Material.GRASS_BLOCK, Material.OAK_SAPLING,
            Material.FEATHER, Material.ENDER_PEARL, Material.DIAMOND_PICKAXE, Material.NETHER_STAR, Material.BOOK,
            Material.CHEST, Material.CRAFTING_TABLE, Material.MAP, Material.BEACON, Material.CLOCK, Material.FIREWORK_ROCKET,
            Material.IRON_SWORD, Material.GOLD_INGOT, Material.EMERALD, Material.BRICKS);
    private final WarpService warps;
    private final NavigatorMenu navigator;
    private final NavigatorStore store;
    private final MessageService messages;
    private final InventoryFactory inventories;
    private final Function<dev.vapee.core.ui.UiItemSpec, org.bukkit.inventory.ItemStack> renderer;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Inventory> active = new HashMap<>();
    private final Function<UUID, Player> online;
    private boolean running = true;
    private PluginCommand command;
    private org.bukkit.command.CommandExecutor previousExecutor;
    private org.bukkit.command.TabCompleter previousCompleter;

    static final class Session {
        final UUID owner;
        final NavigatorStore.Snapshot base;
        NavigatorConfig.State draft;
        String menu;
        String selected;
        int page;
        int bank;
        int slot = -1;
        long revision;
        Location first;
        Session(UUID owner, NavigatorStore.Snapshot base) {
            this.owner = owner; this.base = base;
            draft = new NavigatorConfig.State(true, base.state().root(), base.state().menus()); menu = draft.root();
        }
    }
    static final class Holder implements InventoryHolder {
        final UUID owner;
        final Session session;
        final long revision;
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        Inventory inventory;
        Holder(Session session) { this.session = session; owner = session.owner; revision = session.revision; }
        @Override public @NotNull Inventory getInventory() { return java.util.Objects.requireNonNull(inventory); }
    }
    @FunctionalInterface interface InventoryFactory { Inventory create(Holder holder, int size, Component title); }

    public NavigatorEditor(JavaPlugin plugin, WarpService warps, NavigatorMenu navigator,
                           NavigatorConfig config, MessageService messages) {
        this(warps, navigator, new NavigatorStore(config, plugin.getLogger(), state -> navigator.closeOpenInventories()),
                messages, (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                NavigatorMenu::renderIcon, plugin.getServer()::getPlayer);
    }
    NavigatorEditor(WarpService warps, NavigatorMenu navigator, NavigatorStore store, MessageService messages,
                    InventoryFactory inventories, Function<dev.vapee.core.ui.UiItemSpec, org.bukkit.inventory.ItemStack> renderer,
                    Function<UUID, Player> online) {
        this.warps = warps; this.navigator = navigator; this.store = store; this.messages = messages;
        this.inventories = inventories; this.renderer = renderer; this.online = online;
        navigator.previewAccess(player -> authorized(player) && sessions.containsKey(player.getUniqueId()));
    }
    public void register(PluginCommand owned) {
        command = java.util.Objects.requireNonNull(owned, "Missing /navigator descriptor");
        previousExecutor = command.getExecutor(); previousCompleter = command.getTabCompleter();
        command.setExecutor(this); command.setTabCompleter(this);
    }
    private boolean authorized(Player player) { return running && player.isOnline() && player.hasPermission(PERMISSION); }
    private String text(Player player, String en, String de) {
        return messages.localization().locale(player).getLanguage().equals("de") ? de : en;
    }
    private void say(Player player, String en, String de) { player.sendMessage(Component.text(text(player, en, de), NamedTextColor.GRAY)); }
    private Session session(Player player) {
        if (!authorized(player)) { release(player); throw new IllegalArgumentException("Permission required: " + PERMISSION); }
        var session = sessions.get(player.getUniqueId());
        if (session == null) throw new IllegalArgumentException("/navigator edit");
        return session;
    }
    @Override public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This editor requires a player."); return true; }
        if (!authorized(player)) { release(player); say(player, "No Navigator editor permission.", "Keine Navigator-Editor-Berechtigung."); return true; }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(player); return true; }
            if (args[0].equalsIgnoreCase("edit")) {
                if (!sessions.containsKey(player.getUniqueId())) sessions.put(player.getUniqueId(), new Session(player.getUniqueId(), store.begin()));
                navigator.endPreview(player); showMenu(player, session(player)); return true;
            }
            var session = session(player);
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "save" -> { save(player, session); return true; }
                case "discard" -> { release(player); say(player, "Draft discarded.", "Entwurf verworfen."); return true; }
                case "preview" -> { preview(player, session); return true; }
                case "page" -> { session.page = integer(arg(args, 1)); session.bank = 0; select(session, null); }
                case "select" -> {
                    var entry = menu(session).entries().get(arg(args, 1));
                    if (entry == null) throw new IllegalArgumentException("Unknown entry");
                    session.page = entry.page(); select(session, entry.id());
                }
                case "menu" -> menuCommand(player, session, args);
                case "add" -> add(session, arg(args, 1), arg(args, 2), args.length > 3 ? integer(args[3]) : suggestedSlot(menu(session), session.page));
                case "entry" -> entryCommand(session, args);
                case "region" -> region(player, session, arg(args, 1));
                default -> { help(player); return true; }
            }
            changed(player, session); showMenu(player, session);
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            say(player, "Editor: " + failure.getMessage(), "Editor: " + failure.getMessage());
        }
        return true;
    }
    private void help(Player player) {
        say(player, "/navigator edit | preview | save | discard", "/navigator edit | preview | save | discard");
        say(player, "/navigator menu create <id> <name...> | select <id> | rename [en|de|all] <name...> | icon <material> | size <27|36|45|54> | back <-1|slot> | delete <id>",
                "/navigator menu create <id> <Name...> | select <id> | rename [en|de|all] <Name...> | icon <Material> | size <27|36|45|54> | back <-1|Slot> | delete <id>");
        say(player, "/navigator add <warp|menu|unavailable> <id> [slot] | page <0..999> | select <entry-id>",
                "/navigator add <warp|menu|unavailable> <id> [Slot] | page <0..999> | select <Eintrags-ID>");
        say(player, "/navigator entry icon <material> | name [en|de|all] <text...> | lore [en|de|all] <line | line...> | permission <node|none> | move <menu> <slot> [page] | remove | stack <true|false>",
                "/navigator entry icon <Material> | name [en|de|all] <Text...> | lore [en|de|all] <Zeile | Zeile...> | permission <Node|none> | move <Menü> <Slot> [Seite] | remove | stack <true|false>");
        say(player, "/navigator region pos1 | pos2 | clear | info — selected Warp entry; optional counter, no teleport changes.",
                "/navigator region pos1 | pos2 | clear | info — ausgewählter Warp-Eintrag; optionaler Zähler, kein Teleportziel.");
    }
    private void menuCommand(Player player, Session s, String[] args) {
        var current = menu(s);
        switch (arg(args, 1)) {
            case "create" -> {
                String id = arg(args, 2); String name = joined(args, 3);
                if (s.draft.menus().containsKey(id)) throw new IllegalArgumentException("Menu ID already exists");
                var menus = new LinkedHashMap<>(s.draft.menus());
                menus.put(id, new NavigatorConfig.Menu(id, LocalizedText.literal(name), Map.of(), 27, Material.COMPASS, -1));
                int slot = s.slot >= 0 ? s.slot : suggestedSlot(current, s.page);
                var entries = new LinkedHashMap<>(current.entries());
                String entryId = unique(entries, id);
                entries.put(entryId, new NavigatorConfig.Entry(entryId, NavigatorConfig.Type.MENU, s.page, slot,
                        Material.COMPASS, LocalizedText.literal(name), List.of(), id, "", null, false));
                menus.put(current.id(), withEntries(current, entries));
                accept(s, menus); select(s, entryId);
                say(player, "Submenu created and placed. /navigator entry icon <material>; click its entry, then Edit submenu.",
                        "Untermenü erstellt und platziert. /navigator entry icon <Material>; Eintrag anklicken und Untermenü bearbeiten wählen.");
            }
            case "select" -> {
                if (!s.draft.menus().containsKey(arg(args, 2))) throw new IllegalArgumentException("Unknown menu");
                s.menu = args[2]; s.page = 0; s.bank = 0; select(s, null);
            }
            case "rename" -> replaceMenu(s, new NavigatorConfig.Menu(current.id(), localized(current.title(), args, 2), current.entries(), current.size(), current.icon(), current.backSlot()));
            case "icon" -> replaceMenu(s, new NavigatorConfig.Menu(current.id(), current.title(), current.entries(), current.size(), material(arg(args, 2)), current.backSlot()));
            case "size" -> {
                int size = integer(arg(args, 2)); if (!List.of(27, 36, 45, 54).contains(size)) throw new IllegalArgumentException("Expected 27, 36, 45 or 54");
                replaceMenu(s, new NavigatorConfig.Menu(current.id(), current.title(), current.entries(), size, current.icon(), current.backSlot()));
            }
            case "back" -> replaceMenu(s, new NavigatorConfig.Menu(current.id(), current.title(), current.entries(), current.size(), current.icon(), Integer.parseInt(arg(args, 2))));
            case "delete" -> {
                String id = arg(args, 2);
                if (id.equals(s.draft.root()) || !s.draft.menus().containsKey(id)) throw new IllegalArgumentException("Cannot delete root or unknown menu");
                var menus = new LinkedHashMap<>(s.draft.menus()); menus.remove(id);
                // Deleting a menu explicitly removes all incoming links, never leaves dangling references.
                menus.replaceAll((key, menu) -> {
                    var entries = new LinkedHashMap<>(menu.entries());
                    entries.values().removeIf(entry -> entry.type() == NavigatorConfig.Type.MENU && entry.target().equals(id));
                    return withEntries(menu, entries);
                });
                accept(s, menus); s.menu = s.draft.root(); s.page = 0; s.bank = 0; select(s, null);
            }
            default -> throw new IllegalArgumentException("/navigator menu create|select|rename|icon|size|back|delete");
        }
    }
    private void entryCommand(Session s, String[] args) {
        var e = selected(s);
        switch (arg(args, 1)) {
            case "remove" -> { var entries = new LinkedHashMap<>(menu(s).entries()); entries.remove(e.id()); replaceMenu(s, withEntries(menu(s), entries)); select(s, null); }
            case "icon" -> update(s, old -> copy(old, material(arg(args, 2)), old.name(), old.lore(), old.permission(), old.region(), old.stackAmount()));
            case "name" -> update(s, old -> copy(old, old.icon(), localized(old.name(), args, 2), old.lore(), old.permission(), old.region(), old.stackAmount()));
            case "lore" -> {
                int start = language(arg(args, 2)) ? 3 : 2; String code = start == 3 ? args[2] : "all";
                String value = joined(args, start);
                List<LocalizedText> lore = new ArrayList<>();
                if (!value.equals("none")) {
                    String[] lines = value.split("\\s*\\|\\s*", -1);
                    for (int i = 0; i < lines.length; i++) {
                        var previous = i < e.lore().size() ? e.lore().get(i) : LocalizedText.literal(lines[i]);
                        lore.add(translated(previous, code, lines[i]));
                    }
                    // Locale-specific edits retain other language lines beyond the edited locale's input.
                    if (!code.equals("all")) for (int i = lines.length; i < e.lore().size(); i++)
                        lore.add(translated(e.lore().get(i), code, ""));
                }
                update(s, old -> copy(old, old.icon(), old.name(), lore, old.permission(), old.region(), old.stackAmount()));
            }
            case "permission" -> {
                if (args.length != 3) throw new IllegalArgumentException("Expected one permission node or none");
                update(s, old -> copy(old, old.icon(), old.name(), old.lore(), arg(args, 2).equals("none") ? "" : args[2], old.region(), old.stackAmount()));
            }
            case "stack" -> {
                if (!List.of("true", "false").contains(arg(args, 2))) throw new IllegalArgumentException("Expected true or false");
                if (e.region() == null) throw new IllegalArgumentException("Set a region first");
                update(s, old -> copy(old, old.icon(), old.name(), old.lore(), old.permission(), old.region(), Boolean.parseBoolean(args[2])));
            }
            case "move" -> {
                String target = arg(args, 2); int slot = integer(arg(args, 3)); int page = args.length > 4 ? integer(args[4]) : s.page;
                var destination = s.draft.menus().get(target);
                if (destination == null) throw new IllegalArgumentException("Unknown target menu");
                if (!target.equals(s.menu) && destination.entries().containsKey(e.id())) throw new IllegalArgumentException("Target already has this entry ID");
                var menus = new LinkedHashMap<>(s.draft.menus());
                var source = new LinkedHashMap<>(menu(s).entries()); source.remove(e.id()); menus.put(s.menu, withEntries(menu(s), source));
                destination = menus.get(target); var entries = new LinkedHashMap<>(destination.entries());
                entries.put(e.id(), new NavigatorConfig.Entry(e.id(), e.type(), page, slot, e.icon(), e.name(), e.lore(), e.target(), e.permission(), e.region(), e.stackAmount()));
                menus.put(target, withEntries(destination, entries)); accept(s, menus);
                s.menu = target; s.page = page; s.bank = slot / 45; select(s, e.id());
            }
            default -> throw new IllegalArgumentException("/navigator entry icon|name|lore|permission|move|remove|stack");
        }
    }
    private void region(Player player, Session s, String action) {
        var entry = selected(s);
        if (entry.type() != NavigatorConfig.Type.WARP) throw new IllegalArgumentException("Select a Warp entry first");
        switch (action) {
            case "info" -> say(player, regionInfo(s), regionInfo(s));
            case "clear" -> { update(s, old -> copy(old, old.icon(), old.name(), old.lore(), old.permission(), null, false)); s.first = null; }
            case "pos1", "pos2" -> {
                Location at = player.getLocation().clone();
                if (at.getWorld() == null || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ()))
                    throw new IllegalArgumentException("Invalid location");
                if (action.equals("pos1")) { s.first = at; say(player, "Position 1 selected; use pos2 in the same world. Save is blocked until complete or clear.", "Position 1 gesetzt; pos2 in derselben Welt setzen. Speichern erst nach pos2 oder clear."); }
                else {
                    if (s.first == null || !s.first.getWorld().getUID().equals(at.getWorld().getUID())) throw new IllegalArgumentException("Select pos1 and pos2 in the same world");
                    var bounds = new NavigatorRegion(at.getWorld().getName(), s.first.getX(), s.first.getY(), s.first.getZ(), at.getX(), at.getY(), at.getZ());
                    update(s, old -> copy(old, old.icon(), old.name(), old.lore(), old.permission(), bounds, old.stackAmount())); s.first = null;
                }
            }
            default -> throw new IllegalArgumentException("/navigator region pos1|pos2|clear|info");
        }
    }
    private String regionInfo(Session s) {
        var entry = selected(s); var r = entry.region();
        if (s.first != null) return "Pending pos1: " + s.first.getWorld().getName() + " " + s.first.getX() + "," + s.first.getY() + "," + s.first.getZ();
        return r == null ? "Optional count: OFF (no region)" : "Count: ON; " + r.world() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] - [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]; stack=" + entry.stackAmount();
    }
    private void add(Session s, String kind, String target, int slot) {
        var type = switch (kind) { case "warp" -> NavigatorConfig.Type.WARP; case "menu" -> NavigatorConfig.Type.MENU; case "unavailable" -> NavigatorConfig.Type.UNAVAILABLE; default -> throw new IllegalArgumentException("Expected warp, menu or unavailable"); };
        Material icon = Material.GRAY_DYE; LocalizedText name = LocalizedText.literal(target);
        if (type == NavigatorConfig.Type.WARP) { var warp = warps.getWarp(target).orElseThrow(() -> new IllegalArgumentException("Unknown Warp")); icon = warp.icon(); name = LocalizedText.literal(warp.displayName()); }
        if (type == NavigatorConfig.Type.MENU) { var menu = s.draft.menus().get(target); if (menu == null) throw new IllegalArgumentException("Unknown menu"); icon = menu.icon(); name = menu.title(); }
        var entries = new LinkedHashMap<>(menu(s).entries()); String id = unique(entries, target);
        entries.put(id, new NavigatorConfig.Entry(id, type, s.page, slot, icon, name, List.of(), type == NavigatorConfig.Type.UNAVAILABLE ? "" : target, "", null, false));
        replaceMenu(s, withEntries(menu(s), entries)); select(s, id);
    }
    private static String unique(Map<String, ?> map, String base) {
        String id = base; for (int i = 2; map.containsKey(id); i++) id = base.substring(0, Math.min(base.length(), 57)) + "_" + i;
        return id;
    }
    static int suggestedSlot(NavigatorConfig.Menu menu, int page) {
        int center = menu.inventorySize() / 2;
        var order = new ArrayList<Integer>(List.of(center - 2, center, center + 2));
        for (int i = 0; i < menu.inventorySize(); i++) if (!order.contains(i)) order.add(i);
        for (int slot : order) {
            if (slot == menu.backSlot() || slot == menu.inventorySize() - 9 || slot == menu.inventorySize() - 1) continue;
            if (menu.entries().values().stream().noneMatch(e -> e.page() == page && e.slot() == slot)) return slot;
        }
        throw new IllegalArgumentException("Page is full; choose another /navigator page");
    }
    private static NavigatorConfig.Menu menu(Session s) { return s.draft.menus().get(s.menu); }
    private static NavigatorConfig.Entry selected(Session s) {
        var entry = menu(s).entries().get(s.selected);
        if (entry == null) throw new IllegalArgumentException("Select an entry first"); return entry;
    }
    private static void select(Session s, String id) { if (!java.util.Objects.equals(s.selected, id)) s.first = null; s.selected = id; s.slot = -1; }
    private static NavigatorConfig.Menu withEntries(NavigatorConfig.Menu m, Map<String, NavigatorConfig.Entry> entries) {
        return new NavigatorConfig.Menu(m.id(), m.title(), entries, m.size(), m.icon(), m.backSlot());
    }
    private void replaceMenu(Session s, NavigatorConfig.Menu menu) { var menus = new LinkedHashMap<>(s.draft.menus()); menus.put(menu.id(), menu); accept(s, menus); }
    private static void accept(Session s, Map<String, NavigatorConfig.Menu> menus) {
        var candidate = new NavigatorConfig.State(true, s.draft.root(), menus);
        // Round-trip the whole document through the strict parser before publishing a draft mutation.
        s.draft = NavigatorConfig.parse(NavigatorDocument.write(candidate));
    }
    private void update(Session s, UnaryOperator<NavigatorConfig.Entry> change) {
        var entry = selected(s); var entries = new LinkedHashMap<>(menu(s).entries()); entries.put(entry.id(), change.apply(entry));
        replaceMenu(s, withEntries(menu(s), entries));
    }
    private static NavigatorConfig.Entry copy(NavigatorConfig.Entry e, Material icon, LocalizedText name, List<LocalizedText> lore, String permission, NavigatorRegion region, boolean stack) {
        return new NavigatorConfig.Entry(e.id(), e.type(), e.page(), e.slot(), icon, name, lore, e.target(), permission, region, stack);
    }
    private static boolean language(String code) { return List.of("en", "de", "all").contains(code); }
    private static LocalizedText localized(LocalizedText previous, String[] args, int start) {
        String code = language(arg(args, start)) ? args[start++] : "all";
        return translated(previous, code, joined(args, start));
    }
    private static LocalizedText translated(LocalizedText old, String code, String text) {
        if (code.equals("all")) return LocalizedText.literal(text);
        var values = new LinkedHashMap<>(old.values()); values.put(code, text); return new LocalizedText(values);
    }
    private static Material material(String name) {
        var material = Material.matchMaterial(name); if (!WarpPoint.isDisplayableItem(material)) throw new IllegalArgumentException("Invalid item material"); return material;
    }
    private static String arg(String[] args, int index) { if (index >= args.length) throw new IllegalArgumentException("Missing argument; /navigator help"); return args[index]; }
    private static String joined(String[] args, int start) { arg(args, start); return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length)); }
    private static int integer(String value) { int n = Integer.parseInt(value); if (n < 0 || n > 999) throw new IllegalArgumentException("Expected 0..999"); return n; }
    private void changed(Player player, Session s) { s.revision++; navigator.endPreview(player); active.remove(player.getUniqueId()); }
    private void save(Player player, Session s) throws IOException {
        if (s.first != null) throw new IllegalArgumentException("Region selection incomplete; pos2 or clear first");
        var backup = store.save(s.base, s.draft);
        release(player); say(player, "Navigator saved and V2 activated. Backup: " + backup.getFileName(), "Navigator gespeichert und V2 aktiviert. Backup: " + backup.getFileName());
    }
    private void preview(Player player, Session s) { active.remove(player.getUniqueId()); navigator.preview(player, s.draft); say(player, "Draft preview: Warp clicks do not teleport. /navigator edit returns; /navigator save commits.", "Entwurfsvorschau: Warp-Klicks teleportieren nicht. /navigator edit zurück; /navigator save übernimmt."); }

    private Holder view(Player player, Session s, String en, String de, int size) {
        var holder = new Holder(s); holder.inventory = inventories.create(holder, size, Component.text(text(player, en, de), NamedTextColor.DARK_GRAY)); return holder;
    }
    private void button(Holder h, int slot, Material icon, String title, List<String> lore, Consumer<Player> action) {
        h.inventory.setItem(slot, renderer.apply(UiItems.literal(icon, Component.text(title), NamedTextColor.AQUA,
                lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).map(Component.class::cast).toList())));
        if (action != null) h.actions.put(slot, action);
    }
    private void button(Holder h, int slot, Material icon, String title, Consumer<Player> action) { button(h, slot, icon, title, List.of(), action); }
    private void open(Player player, Holder holder) {
        active.remove(player.getUniqueId());
        var opened = player.openInventory(holder.inventory);
        if (opened != null && opened.getTopInventory() == holder.inventory && player.getOpenInventory().getTopInventory() == holder.inventory
                && authorized(player) && sessions.get(player.getUniqueId()) == holder.session && holder.revision == holder.session.revision)
            active.put(player.getUniqueId(), holder.inventory);
        else if (!authorized(player)) release(player);
    }
    private void showMenu(Player player, Session s) {
        var menu = menu(s); var h = view(player, s, "Editor: " + menu.id(), "Editor: " + menu.id(), 54);
        for (int display = 0; display < 45; display++) {
            int slot = display + s.bank * 45;
            if (slot >= menu.inventorySize()) continue;
            var entry = menu.entries().values().stream().filter(e -> e.page() == s.page && e.slot() == slot).findFirst().orElse(null);
            if (entry == null) h.actions.put(display, p -> { select(s, null); s.slot = slot; showAdd(p, s); });
            else button(h, display, entry.icon(), entry.name().resolve(messages.localization().locale(player)),
                    List.of(entry.id() + "; slot " + slot + "; page " + s.page, "permission: " + (entry.permission().isEmpty() ? "none" : entry.permission()),
                            text(player, "Click: edit. /navigator select " + entry.id(), "Klick: bearbeiten. /navigator select " + entry.id())),
                    p -> { select(s, entry.id()); showEntry(p, s); });
        }
        button(h, 45, Material.BOOK, text(player, "Select menu", "Menü auswählen"), p -> showMenus(p, s, false, 0));
        button(h, 46, menu.icon(), text(player, "Menu settings / Create submenu", "Menü bearbeiten / Untermenü erstellen"),
                List.of("/navigator menu create <id> <name...>", "/navigator menu rename [en|de|all] <name...>", "/navigator menu size <27|36|45|54>", "/navigator menu icon <material>", "/navigator menu delete <id>"), p -> { help(p); showIcons(p, s, true); });
        button(h, 47, Material.PAPER, "Page " + s.page + " / slots " + (s.bank * 45) + ".." + Math.min(menu.inventorySize() - 1, s.bank * 45 + 44), List.of("/navigator page <0..999>"), null);
        button(h, 48, Material.ENDER_EYE, text(player, "Preview", "Vorschau"), p -> preview(p, s));
        button(h, 49, Material.LIME_DYE, text(player, "Save and activate V2", "Speichern und V2 aktivieren"), p -> { try { save(p, s); } catch (IOException e) { throw new IllegalStateException(e.getMessage(), e); } });
        button(h, 50, Material.GRAY_DYE, text(player, "Discard", "Verwerfen"), p -> release(p));
        if (s.page > 0) button(h, 51, Material.ARROW, text(player, "Previous page", "Vorherige Seite"), p -> { s.page--; select(s, null); changed(p, s); showMenu(p, s); });
        if (s.page < 999) button(h, 52, Material.ARROW, text(player, "Next page", "Nächste Seite"), p -> { s.page++; select(s, null); changed(p, s); showMenu(p, s); });
        if (menu.inventorySize() > 45) button(h, 53, Material.MAP, text(player, "Other slots", "Weitere Plätze"), p -> { s.bank = 1 - s.bank; showMenu(p, s); });
        open(player, h);
    }
    private void showAdd(Player player, Session s) {
        var h = view(player, s, "Add at slot " + s.slot, "Hinzufügen auf Platz " + s.slot, 27);
        button(h, 11, Material.COMPASS, text(player, "Add Warp", "Warp hinzufügen"), p -> showWarps(p, s, 0));
        button(h, 13, Material.BOOK, text(player, "Link existing submenu", "Vorhandenes Untermenü verknüpfen"), p -> showMenus(p, s, true, 0));
        button(h, 15, Material.GRAY_DYE, text(player, "Unavailable placeholder", "Nicht verfügbarer Platzhalter"), p -> { add(s, "unavailable", "unavailable", s.slot); changed(p, s); showEntry(p, s); });
        button(h, 22, Material.WRITABLE_BOOK, text(player, "Create submenu", "Untermenü erstellen"), List.of("/navigator menu create <id> <name...>", text(player, "Uses this selected empty slot.", "Verwendet diesen ausgewählten leeren Platz.")), p -> say(p, "/navigator menu create <id> <name...>", "/navigator menu create <id> <Name...>"));
        button(h, 18, Material.ARROW, text(player, "Back", "Zurück"), p -> showMenu(p, s)); open(player, h);
    }
    private void showWarps(Player player, Session s, int page) {
        var list = warps.getWarps(); var h = view(player, s, "Choose existing Warp", "Vorhandenen Warp wählen", 54);
        for (int i = page * 45; i < Math.min(list.size(), (page + 1) * 45); i++) {
            var warp = list.get(i);
            button(h, i % 45, warp.icon(), warp.id() + " — " + warp.displayName(),
                    List.of(warp.navigation().visible() ? "Navigable" : text(player, "Globally hidden: /warp show required", "Global versteckt: /warp show erforderlich")),
                    p -> { add(s, "warp", warp.id(), s.slot); changed(p, s); showEntry(p, s); });
        }
        pickerButtons(h, s, page, list.size(), p -> showWarps(p, s, page - 1), p -> showWarps(p, s, page + 1)); open(player, h);
    }
    private void showMenus(Player player, Session s, boolean link, int page) {
        var list = List.copyOf(s.draft.menus().values()); var h = view(player, s, "Choose menu", "Menü auswählen", 54);
        for (int i = page * 45; i < Math.min(list.size(), (page + 1) * 45); i++) {
            var menu = list.get(i);
            button(h, i % 45, menu.icon(), menu.id() + " — " + menu.title().resolve(messages.localization().locale(player)), p -> {
                if (link) { add(s, "menu", menu.id(), s.slot); changed(p, s); showEntry(p, s); }
                else { s.menu = menu.id(); s.page = 0; s.bank = 0; select(s, null); changed(p, s); showMenu(p, s); }
            });
        }
        pickerButtons(h, s, page, list.size(), p -> showMenus(p, s, link, page - 1), p -> showMenus(p, s, link, page + 1)); open(player, h);
    }
    private void pickerButtons(Holder h, Session s, int page, int total, Consumer<Player> previous, Consumer<Player> next) {
        if (page > 0) button(h, 45, Material.ARROW, "←", previous);
        button(h, 49, Material.ARROW, "↩", p -> showMenu(p, s));
        if ((page + 1) * 45 < total) button(h, 53, Material.ARROW, "→", next);
    }
    private void showIcons(Player player, Session s, boolean menuIcon) {
        var h = view(player, s, "Choose icon", "Icon auswählen", 27);
        for (int i = 0; i < ICONS.size(); i++) { Material icon = ICONS.get(i);
            button(h, i, icon, icon.name(), p -> {
                if (menuIcon) { var menu = menu(s); replaceMenu(s, new NavigatorConfig.Menu(menu.id(), menu.title(), menu.entries(), menu.size(), icon, menu.backSlot())); }
                else update(s, e -> copy(e, icon, e.name(), e.lore(), e.permission(), e.region(), e.stackAmount()));
                changed(p, s); showMenu(p, s);
            });
        }
        button(h, 22, Material.ARROW, "↩", List.of("/navigator " + (menuIcon ? "menu" : "entry") + " icon <any-item-material>"), p -> showMenu(p, s)); open(player, h);
    }
    private void showEntry(Player player, Session s) {
        var entry = selected(s); var h = view(player, s, "Entry: " + entry.id(), "Eintrag: " + entry.id(), 27);
        button(h, 4, entry.icon(), entry.name().resolve(messages.localization().locale(player)), List.of("Type: " + entry.type(), "Target: " + entry.target(), "Permission: " + entry.permission()), null);
        button(h, 10, Material.NAME_TAG, text(player, "Name and lore", "Name und Beschreibung"), List.of("/navigator entry name [en|de|all] <text...>", "/navigator entry lore [en|de|all] <line | line...>", "/navigator entry lore none"), p -> help(p));
        button(h, 11, entry.icon(), text(player, "Icon", "Icon"), p -> showIcons(p, s, false));
        button(h, 12, Material.IRON_DOOR, text(player, "Permission", "Berechtigung"), List.of("/navigator entry permission <node|none>"), p -> help(p));
        button(h, 13, Material.PISTON, text(player, "Move", "Verschieben"), List.of("/navigator entry move <menu-id> <slot> [page]"), p -> help(p));
        button(h, 14, Material.RED_DYE, text(player, "Remove entry", "Eintrag entfernen"), p -> { entryCommand(s, new String[]{"entry", "remove"}); changed(p, s); showMenu(p, s); });
        if (entry.type() == NavigatorConfig.Type.MENU) button(h, 15, Material.BOOK, text(player, "Edit submenu", "Untermenü bearbeiten"), p -> { s.menu = entry.target(); s.page = 0; s.bank = 0; select(s, null); changed(p, s); showMenu(p, s); });
        if (entry.type() == NavigatorConfig.Type.WARP) {
            button(h, 18, Material.MAP, text(player, "Optional player counter", "Optionaler Spielerzähler"), List.of(regionInfo(s), "/navigator region info"), p -> region(p, s, "info"));
            button(h, 19, Material.WOODEN_AXE, "Position 1", p -> { region(p, s, "pos1"); changed(p, s); showEntry(p, s); });
            button(h, 20, Material.STONE_AXE, "Position 2", p -> { region(p, s, "pos2"); changed(p, s); showEntry(p, s); });
            button(h, 21, Material.SPONGE, text(player, "Clear region", "Region entfernen"), p -> { region(p, s, "clear"); changed(p, s); showEntry(p, s); });
            button(h, 22, Material.SLIME_BALL, "Stack: " + entry.stackAmount(), p -> { entryCommand(s, new String[]{"entry", "stack", String.valueOf(!entry.stackAmount())}); changed(p, s); showEntry(p, s); });
        }
        button(h, 26, Material.ARROW, "↩", p -> showMenu(p, s)); open(player, h);
    }
    private boolean valid(Player p, Inventory top, Holder holder) {
        return authorized(p) && holder.owner.equals(p.getUniqueId()) && sessions.get(p.getUniqueId()) == holder.session
                && holder.revision == holder.session.revision && top == holder.inventory && top.getHolder() == holder
                && active.get(p.getUniqueId()) == top && p.getOpenInventory().getTopInventory() == top;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        var top = event.getView().getTopInventory(); if (!(top.getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!authorized(p)) { release(p); return; }
        if (!valid(p, top, holder) || event.getClickedInventory() != top
                || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        var action = holder.actions.get(event.getRawSlot());
        try {
            if (action != null) action.accept(p);
        } catch (IllegalArgumentException | IllegalStateException failure) { say(p, "Editor: " + failure.getMessage(), "Editor: " + failure.getMessage()); }
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        var top = event.getView().getTopInventory(); if (!(top.getHolder() instanceof Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player p) || !valid(p, top, holder)
                || event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < top.getSize())) event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p && !authorized(p)) release(p);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) active.remove(holder.owner, event.getInventory());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { release(event.getPlayer()); }
    private void release(Player player) {
        sessions.remove(player.getUniqueId()); var inventory = active.remove(player.getUniqueId());
        try { navigator.endPreview(player); }
        finally { if (inventory != null && player.getOpenInventory().getTopInventory() == inventory) player.closeInventory(); }
    }
    public void shutdown() {
        running = false;
        try { for (UUID owner : List.copyOf(sessions.keySet())) { var player = online.apply(owner); if (player != null) { try { release(player); } catch (RuntimeException ignored) { /* authority is cleared below even if transport fails */ } } } }
        finally {
            sessions.clear(); active.clear(); navigator.previewAccess(player -> false);
            if (command != null) {
                if (command.getExecutor() == this) command.setExecutor(previousExecutor);
                if (command.getTabCompleter() == this) command.setTabCompleter(previousCompleter);
                command = null;
            }
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !authorized(player)) return List.of();
        List<String> values = List.of();
        var s = sessions.get(player.getUniqueId());
        if (args.length == 1) values = List.of("edit", "menu", "add", "entry", "select", "page", "region", "preview", "save", "discard", "help");
        else if (args.length == 2) values = switch (args[0]) {
            case "menu" -> List.of("create", "select", "rename", "icon", "size", "back", "delete");
            case "entry" -> List.of("icon", "name", "lore", "permission", "move", "remove", "stack");
            case "region" -> List.of("pos1", "pos2", "clear", "info");
            case "add" -> List.of("warp", "menu", "unavailable");
            case "select" -> s == null ? List.of() : List.copyOf(menu(s).entries().keySet());
            default -> List.of();
        };
        else if (args.length == 3 && s != null) {
            if (args[0].equals("add") && args[1].equals("warp")) values = warps.getWarps().stream().map(WarpPoint::id).toList();
            else if (args[0].equals("menu") && List.of("select", "delete").contains(args[1]) || args[0].equals("add") && args[1].equals("menu") || args[0].equals("entry") && args[1].equals("move")) values = List.copyOf(s.draft.menus().keySet());
            else if (args[1].equals("icon")) values = java.util.Arrays.stream(Material.values()).filter(WarpPoint::isDisplayableItem).map(Enum::name).toList();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}

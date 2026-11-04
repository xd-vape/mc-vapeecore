package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.i18n.LocalizedText;
import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.ui.UiItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    private final Function<String, World> worlds;
    private boolean running = true;
    private PluginCommand command;
    private org.bukkit.command.CommandExecutor previousExecutor;
    private org.bukkit.command.TabCompleter previousCompleter;
    private NavigatorRegionTool regionTool;

    static final class Session {
        final UUID owner;
        final UUID token = UUID.randomUUID();
        final NavigatorStore.Snapshot base;
        NavigatorConfig.State draft;
        String menu;
        String selected;
        int page;
        int bank;
        int slot = -1;
        long revision;
        PendingRegion first;
        boolean pendingNoticeShown;
        final Map<RegionTarget, RegionHeight> heights = new HashMap<>();
        Session(UUID owner, NavigatorStore.Snapshot base) {
            this.owner = owner; this.base = base;
            draft = new NavigatorConfig.State(true, base.state().root(), base.state().menus()); menu = draft.root();
        }
    }
    private record RegionTarget(String menu, String entry) { }
    /** Draft-only editing choice; the existing document still stores only concrete cuboid coordinates. */
    private record RegionHeight(boolean full, double manualMin, double manualMax) { }
    /** One unfinished selection, bound to the real menu/entry and a snapshot of the Bukkit world UUID. */
    record PendingRegion(String menu, String entry, UUID worldId, String world, double x, double y, double z) {
        boolean owns(Session s) { return menu.equals(s.menu) && entry.equals(s.selected); }
        PendingRegion moved(String targetMenu) { return new PendingRegion(targetMenu, entry, worldId, world, x, y, z); }
    }
    private static final class EditorProblem extends IllegalArgumentException {
        final String german;
        EditorProblem(String english, String german) { super(english); this.german = german; }
    }
    private static final class MissingArgument extends IllegalArgumentException { }
    private static EditorProblem problem(String english, String german) { return new EditorProblem(english, german); }
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
        this(plugin, warps, navigator, config, messages, java.util.Set::of);
    }
    public NavigatorEditor(JavaPlugin plugin, WarpService warps, NavigatorMenu navigator,
                           NavigatorConfig config, MessageService messages, java.util.function.Supplier<java.util.Set<Integer>> reserved) {
        this(warps, navigator, new NavigatorStore(config, plugin.getLogger(), state -> navigator.closeOpenInventories()),
                messages, (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                NavigatorMenu::renderIcon, plugin.getServer()::getPlayer, plugin.getServer()::getWorld);
        regionTool = new NavigatorRegionTool(plugin, this, reserved);
    }
    public NavigatorRegionTool regionTool() { return regionTool; }
    void regionTool(NavigatorRegionTool tool) { regionTool = tool; }
    NavigatorEditor(WarpService warps, NavigatorMenu navigator, NavigatorStore store, MessageService messages,
                    InventoryFactory inventories, Function<dev.vapee.core.ui.UiItemSpec, org.bukkit.inventory.ItemStack> renderer,
                    Function<UUID, Player> online, Function<String, World> worlds) {
        this.warps = warps; this.navigator = navigator; this.store = store; this.messages = messages;
        this.inventories = inventories; this.renderer = renderer; this.online = online; this.worlds = worlds;
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
        if (session == null) throw problem("No editor session. Open /navigator edit.", "Keine Editor-Sitzung. Öffne /navigator edit.");
        if (!session.owner.equals(player.getUniqueId())) throw problem("Invalid editor state. Discard and reopen /navigator edit.", "Ungültiger Editor-Zustand. Entwurf verwerfen und /navigator edit erneut öffnen.");
        return session;
    }
    @Override public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This editor requires a player."); return true; }
        if (!authorized(player)) { release(player); messages.sendRaw(player, messages.commandDenied(player)); return true; }
        try {
            if (args.length == 0) { say(player, "Use /navigator edit or /navigator help.", "Nutze /navigator edit oder /navigator help."); return true; }
            if (args[0].equalsIgnoreCase("help")) { help(player); return true; }
            if (args[0].equalsIgnoreCase("edit")) {
                cancelTool(player, false);
                if (!sessions.containsKey(player.getUniqueId())) sessions.put(player.getUniqueId(), new Session(player.getUniqueId(), store.begin()));
                navigator.endPreview(player); showMenu(player, session(player)); return true;
            }
            if (!List.of("save", "discard", "preview", "page", "select", "menu", "add", "entry", "region").contains(args[0].toLowerCase(Locale.ROOT))) {
                syntax(player, args); return true;
            }
            var session = session(player);
            var before = session.draft;
            if (args[0].equalsIgnoreCase("region")) {
                String action = arg(args, 1);
                if (!List.of("info", "mark", "cancel").contains(action)) cancelTool(player, false);
                region(player, session, action);
                if (!action.equals("info")) commandChanged(player, session);
                return true;
            }
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
                default -> { syntax(player, args); return true; }
            }
            commandChanged(player, session); explainPendingSelection(player, session);
            if (before != session.draft) say(player, "Draft updated.", "Entwurf aktualisiert.");
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            if (failure instanceof MissingArgument || failure instanceof NumberFormatException) syntax(player, args);
            else feedback(player, failure);
        }
        return true;
    }
    private void feedback(Player player, Exception failure) {
        if (failure instanceof EditorProblem known) say(player, known.getMessage(), known.german);
        else if (failure instanceof IOException) say(player, "Save/read failed: " + failure.getMessage(), "Speichern/Lesen fehlgeschlagen: " + failure.getMessage());
        else say(player, "Editor: " + failure.getMessage(), "Editor: " + failure.getMessage());
    }
    private void syntax(Player player, String[] args) {
        String family = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        Component usage = switch (family) {
            case "menu" -> switch (action) {
                case "create" -> MessageStyle.usage("/navigator menu create", "<id> <name...>");
                case "select", "delete" -> MessageStyle.usage("/navigator menu " + action, "<id>");
                case "rename" -> MessageStyle.usage("/navigator menu rename", "[en|de|all] <name...>");
                case "icon" -> MessageStyle.usage("/navigator menu icon", "<material>");
                case "size" -> MessageStyle.usage("/navigator menu size", "<27|36|45|54>");
                case "back" -> MessageStyle.usage("/navigator menu back", "<-1|slot>");
                default -> MessageStyle.usage("/navigator menu", "<create|select|rename|icon|size|back|delete>");
            };
            case "entry" -> switch (action) {
                case "icon" -> MessageStyle.usage("/navigator entry icon", "<material>");
                case "name", "lore" -> MessageStyle.usage("/navigator entry " + action, "[en|de|all] <text...>");
                case "permission" -> MessageStyle.usage("/navigator entry permission", "<node|none>");
                case "move" -> MessageStyle.usage("/navigator entry move", "<menu> <slot> [page]");
                case "stack" -> MessageStyle.usage("/navigator entry stack", "<true|false>");
                case "remove" -> MessageStyle.usage("/navigator entry remove", "");
                default -> MessageStyle.usage("/navigator entry", "<icon|name|lore|permission|move|remove|stack>");
            };
            case "region" -> MessageStyle.usage("/navigator region", "<mark|cancel|pos1|pos2|clear|info>");
            case "add" -> MessageStyle.usage("/navigator add", "<warp|menu|unavailable> <id> [slot]");
            case "select" -> MessageStyle.usage("/navigator select", "<entry-id>");
            case "page" -> MessageStyle.usage("/navigator page", "<0..999>");
            default -> MessageStyle.usage("/navigator", "edit — /navigator help");
        };
        messages.sendRaw(player, usage);
    }
    private void help(Player player) {
        say(player, "/navigator edit | preview | save | discard", "/navigator edit | preview | save | discard");
        say(player, "/navigator menu create <id> <name...> | select <id> | rename [en|de|all] <name...> | icon <material> | size <27|36|45|54> | back <-1|slot> | delete <id>",
                "/navigator menu create <id> <Name...> | select <id> | rename [en|de|all] <Name...> | icon <Material> | size <27|36|45|54> | back <-1|Slot> | delete <id>");
        say(player, "/navigator add <warp|menu|unavailable> <id> [slot] | page <0..999> | select <entry-id>",
                "/navigator add <warp|menu|unavailable> <id> [Slot] | page <0..999> | select <Eintrags-ID>");
        say(player, "/navigator entry icon <material> | name [en|de|all] <text...> | lore [en|de|all] <line | line...> | permission <node|none> | move <menu> <slot> [page] | remove | stack <true|false>",
                "/navigator entry icon <Material> | name [en|de|all] <Text...> | lore [en|de|all] <Zeile | Zeile...> | permission <Node|none> | move <Menü> <Slot> [Seite] | remove | stack <true|false>");
        say(player, "/navigator region mark | cancel | pos1 | pos2 | clear | info — selected Warp entry; draft only.",
                "/navigator region mark | cancel | pos1 | pos2 | clear | info — ausgewählter Warp-Eintrag; nur Entwurf.");
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
                if (s.first != null && s.first.menu().equals(id)) throw unfinished(s);
                var menus = new LinkedHashMap<>(s.draft.menus()); menus.remove(id);
                // Deleting a menu explicitly removes all incoming links, never leaves dangling references.
                menus.replaceAll((key, menu) -> {
                    var entries = new LinkedHashMap<>(menu.entries());
                    entries.values().removeIf(entry -> entry.type() == NavigatorConfig.Type.MENU && entry.target().equals(id));
                    return withEntries(menu, entries);
                });
                accept(s, menus); s.menu = s.draft.root(); s.page = 0; s.bank = 0; select(s, null);
                s.heights.keySet().removeIf(target -> target.menu().equals(id));
            }
            default -> throw new MissingArgument();
        }
    }
    private void entryCommand(Session s, String[] args) {
        if (!List.of("remove", "icon", "name", "lore", "permission", "stack", "move").contains(arg(args, 1))) throw new MissingArgument();
        var e = selected(s);
        switch (arg(args, 1)) {
            case "remove" -> {
                if (s.first != null && s.first.owns(s)) throw unfinished(s);
                var entries = new LinkedHashMap<>(menu(s).entries()); entries.remove(e.id()); replaceMenu(s, withEntries(menu(s), entries)); select(s, null);
                s.heights.remove(new RegionTarget(s.menu, e.id()));
            }
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
                if (args.length != 3) throw new MissingArgument();
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
                if (s.first != null && s.first.owns(s)) s.first = s.first.moved(target);
                var height = s.heights.remove(new RegionTarget(s.menu, e.id()));
                if (height != null) s.heights.put(new RegionTarget(target, e.id()), height);
                s.menu = target; s.page = page; s.bank = slot / 45; select(s, e.id());
            }
            default -> throw new MissingArgument();
        }
    }
    private void region(Player player, Session s, String action) {
        if (!List.of("info", "clear", "pos1", "pos2", "mark", "cancel").contains(action)) throw new MissingArgument();
        selectedWarp(s);
        switch (action) {
            case "mark" -> {
                if (regionTool == null) throw problem("Region tool is unavailable.", "Das Regionswerkzeug ist nicht verfügbar.");
                regionTool.start(player);
            }
            case "cancel" -> cancelTool(player, true);
            case "info" -> say(player, regionInfo(player, s), regionInfo(player, s));
            case "clear" -> {
                requirePendingTarget(s);
                update(s, old -> copy(old, old.icon(), old.name(), old.lore(), old.permission(), null, false)); s.first = null; s.pendingNoticeShown = false;
                s.heights.remove(new RegionTarget(s.menu, s.selected));
                say(player, "Region cleared in draft.", "Region im Entwurf entfernt.");
            }
            case "pos1", "pos2" -> position(player, s, action, player.getLocation(), false);
            default -> throw new MissingArgument();
        }
    }
    private void position(Player player, Session s, String action, Location at, boolean tool) {
        if (action.equals("pos2") && s.first == null)
            throw problem("Position 1 is missing. Use /navigator region pos1 first.", "Position 1 fehlt. Nutze zuerst /navigator region pos1.");
        requirePendingTarget(s);
        if (at == null || at.getWorld() == null || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ()))
            throw problem("Invalid coordinates or missing world. No position was changed.", "Ungültige Koordinaten oder fehlende Welt. Keine Position wurde geändert.");
        if (action.equals("pos1")) {
            s.first = new PendingRegion(s.menu, s.selected, at.getWorld().getUID(), at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
            s.pendingNoticeShown = false;
            if (tool) toolFeedback(player, "Position 1 selected.", "Position 1 gewählt.");
            else say(player, "Position 1 saved. Move to the second corner and use /navigator region pos2.", "Position 1 gesetzt. Gehe zur zweiten Ecke und nutze /navigator region pos2.");
        } else {
            if (!s.first.worldId().equals(at.getWorld().getUID()))
                throw problem("Both positions must be in the same world. Pos1: " + s.first.world() + "; Pos2: " + at.getWorld().getName() + ".",
                        "Beide Positionen müssen in derselben Welt liegen. Pos1: " + s.first.world() + "; Pos2: " + at.getWorld().getName() + ".");
            var height = height(s);
            double manualMin = Math.min(s.first.y(), at.getBlockY()), manualMax = Math.max(s.first.y(), at.getBlockY());
            var manual = new NavigatorRegion(at.getWorld().getName(), s.first.x(), manualMin, s.first.z(), at.getBlockX(), manualMax, at.getBlockZ());
            var bounds = height.full() ? fullHeight(manual, at.getWorld()) : manual;
            update(s, old -> copy(old, old.icon(), old.name(), old.lore(), old.permission(), bounds, old.region() == null || old.stackAmount())); s.first = null; s.pendingNoticeShown = false;
            s.heights.put(new RegionTarget(s.menu, s.selected), new RegionHeight(height.full(), manualMin, manualMax));
            if (tool) toolFeedback(player, "Position 2 selected. Region selected; Save to apply.", "Position 2 gewählt. Region gewählt; zum Übernehmen speichern.");
            else say(player, "Region selected. Save to apply.", "Region gewählt. Zum Übernehmen speichern.");
        }
    }
    private RegionHeight height(Session s) {
        var entry = selectedWarp(s);
        return s.heights.computeIfAbsent(new RegionTarget(s.menu, s.selected), ignored -> {
            var r = entry.region();
            if (r == null) return new RegionHeight(true, 0, 0);
            var world = worlds.apply(r.world());
            // Infer a saved full-height region without rewriting it or adding a persistent mode flag.
            boolean full = world != null && r.minY() == world.getMinHeight() && r.maxY() == world.getMaxHeight() - 1;
            return new RegionHeight(full, r.minY(), r.maxY());
        });
    }
    private static NavigatorRegion fullHeight(NavigatorRegion r, World world) {
        if (world == null || !r.world().equals(world.getName()))
            throw problem("Region world is not loaded. No height was changed.", "Die Regionswelt ist nicht geladen. Keine Höhe wurde geändert.");
        if (world.getMinHeight() >= world.getMaxHeight())
            throw problem("Invalid world height. No height was changed.", "Ungültige Welthöhe. Keine Höhe wurde geändert.");
        return new NavigatorRegion(r.world(), r.minX(), world.getMinHeight(), r.minZ(), r.maxX(), world.getMaxHeight() - 1, r.maxZ());
    }
    private void toggleHeight(Session s) {
        selectedWarp(s); requirePendingTarget(s);
        var height = height(s); var r = selected(s).region();
        if (r != null) {
            var bounds = height.full()
                    ? new NavigatorRegion(r.world(), r.minX(), height.manualMin(), r.minZ(), r.maxX(), height.manualMax(), r.maxZ())
                    : fullHeight(r, worlds.apply(r.world()));
            update(s, old -> copy(old, old.icon(), old.name(), old.lore(), old.permission(), bounds, old.stackAmount()));
        }
        s.heights.put(new RegionTarget(s.menu, s.selected), new RegionHeight(!height.full(), height.manualMin(), height.manualMax()));
    }
    private String heightName(Player player, Session s) {
        return height(s).full() ? text(player, "Full world height", "Gesamte Welthöhe") : text(player, "Manual height", "Manuelle Höhe");
    }
    NavigatorRegionTool.Target toolTarget(Player player) {
        var s = session(player); selectedWarp(s); requirePendingTarget(s);
        return new NavigatorRegionTool.Target(s.owner, s.token, s.menu, s.selected);
    }
    boolean toolValid(Player player, NavigatorRegionTool.Target target) {
        var s = sessions.get(player.getUniqueId());
        if (!authorized(player) || s == null || !target.owner().equals(player.getUniqueId()) || !target.session().equals(s.token)
                || !target.menu().equals(s.menu) || !target.entry().equals(s.selected)) return false;
        var menu = s.draft.menus().get(target.menu());
        return menu != null && menu.entries().containsKey(target.entry()) && menu.entries().get(target.entry()).type() == NavigatorConfig.Type.WARP;
    }
    void toolPosition(Player player, NavigatorRegionTool.Target target, Location at, boolean first) {
        if (!toolValid(player, target)) throw problem("This selection expired.", "Diese Auswahl ist abgelaufen.");
        var s = session(player); position(player, s, first ? "pos1" : "pos2", at, true); changed(player, s);
    }
    void toolFeedback(Player player, String english, String german) { player.sendActionBar(Component.text(text(player, english, german), NamedTextColor.GRAY)); }
    void toolFailure(Player player, RuntimeException failure) { toolFeedback(player, failure.getMessage(), failure instanceof EditorProblem known ? known.german : failure.getMessage()); }
    boolean toolAuthorized(Player player) { return authorized(player); }
    void toolPermissionLost(Player player) { release(player); }
    Player toolPlayer(UUID owner) { return online.apply(owner); }
    void closeForTool(Player player) { commandChanged(player, session(player)); }
    private void cancelTool(Player player, boolean notify) { if (regionTool != null) regionTool.cancel(player, notify); }
    private static void requirePendingTarget(Session s) {
        if (s.first != null && !s.first.owns(s)) throw problem("Position 1 belongs to " + s.first.menu() + "/" + s.first.entry() + ". Select that Warp entry again, then use pos2 or clear.",
                "Position 1 gehört zu " + s.first.menu() + "/" + s.first.entry() + ". Wähle diesen Warp-Eintrag erneut und nutze pos2 oder clear.");
    }
    private void explainPendingSelection(Player player, Session s) {
        if (s.first != null && !s.first.owns(s) && !s.pendingNoticeShown) {
            s.pendingNoticeShown = true;
            say(player, "Position 1 retained for " + s.first.menu() + "/" + s.first.entry() + ". Select it again to continue.",
                    "Position 1 bleibt für " + s.first.menu() + "/" + s.first.entry() + " erhalten. Wähle den Eintrag zum Fortsetzen erneut.");
        }
    }
    private String regionInfo(Player player, Session s) {
        var entry = selected(s); var r = entry.region();
        String info = r == null ? text(player, "Optional count: OFF (no region)", "Optionaler Zähler: AUS (keine Region)")
                : text(player, "Count: ON; ", "Zählung: AN; ") + r.world() + " [" + r.minX() + "," + r.minY() + "," + r.minZ() + "] - [" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]; stack=" + entry.stackAmount();
        info += text(player, "; height: ", "; Höhe: ") + heightName(player, s);
        if (s.first != null) info += text(player, "; pending Pos1: ", "; offene Pos1: ") + s.first.menu() + "/" + s.first.entry() + " @ " + s.first.world() + " [" + s.first.x() + "," + s.first.y() + "," + s.first.z() + "]";
        return info;
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
    private static NavigatorConfig.Menu menu(Session s) {
        var menu = s.draft == null ? null : s.draft.menus().get(s.menu);
        if (menu == null) throw problem("Invalid editor state. Discard and reopen /navigator edit.", "Ungültiger Editor-Zustand. Entwurf verwerfen und /navigator edit erneut öffnen.");
        return menu;
    }
    private static NavigatorConfig.Entry selected(Session s) {
        if (s.selected == null) throw problem("Select an entry first. Open /navigator edit and click the entry.", "Wähle zuerst einen Eintrag. Öffne /navigator edit und klicke ihn an.");
        var entry = menu(s).entries().get(s.selected);
        if (entry == null) throw problem("Selected entry is stale or missing. Reopen /navigator edit and select an existing entry.", "Der ausgewählte Eintrag ist veraltet oder fehlt. Öffne /navigator edit und wähle einen vorhandenen Eintrag."); return entry;
    }
    private static NavigatorConfig.Entry selectedWarp(Session s) {
        if (s.selected == null) throw problem("Select a Warp entry first. Open /navigator edit and click the Warp.", "Wähle zuerst einen Warp-Eintrag. Öffne /navigator edit und klicke den Warp an.");
        var entry = selected(s);
        if (entry.type() != NavigatorConfig.Type.WARP) throw problem("Select a Warp entry first. Open /navigator edit and click the Warp.", "Wähle zuerst einen Warp-Eintrag. Öffne /navigator edit und klicke den Warp an.");
        return entry;
    }
    private static void select(Session s, String id) { s.selected = id; s.slot = -1; }
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
    private static String arg(String[] args, int index) { if (index >= args.length) throw new MissingArgument(); return args[index]; }
    private static String joined(String[] args, int start) { arg(args, start); return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length)); }
    private static int integer(String value) { int n = Integer.parseInt(value); if (n < 0 || n > 999) throw new IllegalArgumentException("Expected 0..999"); return n; }
    private void changed(Player player, Session s) { s.revision++; navigator.endPreview(player); active.remove(player.getUniqueId()); }
    private void commandChanged(Player player, Session s) {
        var previous = active.get(player.getUniqueId());
        changed(player, s);
        if (previous != null && player.getOpenInventory().getTopInventory() == previous) player.closeInventory();
        if (regionTool != null) regionTool.reconcile(player);
    }
    private static EditorProblem unfinished(Session s) {
        return problem("Incomplete region for " + s.first.menu() + "/" + s.first.entry() + ". Select that entry, then pos2 or clear before saving/removing.",
                "Unvollständige Region für " + s.first.menu() + "/" + s.first.entry() + ". Wähle den Eintrag und nutze pos2 oder clear vor dem Speichern/Entfernen.");
    }
    private static EditorProblem saveProblem(Session s) {
        if (s.first != null) return unfinished(s);
        try {
            if (s.draft == null || !NavigatorConfig.parse(NavigatorDocument.write(s.draft)).enabled())
                return problem("Invalid Navigator draft. Discard and reopen /navigator edit.", "Ungültiger Navigator-Entwurf. Verwerfen und /navigator edit erneut öffnen.");
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return problem("Cannot save: " + invalid.getMessage(), "Speichern gesperrt: " + invalid.getMessage());
        }
        return null;
    }
    private void save(Player player, Session s) throws IOException {
        cancelTool(player, false);
        var blocked = saveProblem(s); if (blocked != null) throw blocked;
        store.save(s.base, s.draft);
        release(player); say(player, "Navigator saved.", "Navigator gespeichert.");
    }
    private void preview(Player player, Session s) { cancelTool(player, false); active.remove(player.getUniqueId()); navigator.preview(player, s.draft); say(player, "Preview without teleport. Return: /navigator edit.", "Vorschau ohne Teleport. Zurück: /navigator edit."); }

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
        button(h, 46, menu.icon(), text(player, "Menu actions", "Menü-Aktionen"), p -> showMenuActions(p, s));
        button(h, 47, Material.PAPER, "Page " + s.page + " / slots " + (s.bank * 45) + ".." + Math.min(menu.inventorySize() - 1, s.bank * 45 + 44), List.of("/navigator page <0..999>"), null);
        button(h, 48, Material.ENDER_EYE, text(player, "Preview", "Vorschau"), p -> preview(p, s));
        var blocked = saveProblem(s);
        String saveLabel = store.isActive() ? text(player, "Save Changes", "Änderungen speichern")
                : text(player, "Save & Activate Navigator", "Speichern & Navigator aktivieren");
        button(h, 49, blocked == null ? Material.LIME_DYE : Material.GRAY_DYE,
                blocked == null ? saveLabel : text(player, "Cannot save", "Speichern gesperrt"),
                blocked == null ? List.of() : List.of(text(player, blocked.getMessage(), blocked.german)),
                p -> { try { save(p, s); } catch (IOException e) { feedback(p, e); } });
        button(h, 50, Material.GRAY_DYE, text(player, "Discard", "Verwerfen"), p -> release(p));
        if (s.page > 0) button(h, 51, Material.ARROW, text(player, "Previous page", "Vorherige Seite"), p -> { s.page--; select(s, null); changed(p, s); showMenu(p, s); });
        if (s.page < 999) button(h, 52, Material.ARROW, text(player, "Next page", "Nächste Seite"), p -> { s.page++; select(s, null); changed(p, s); showMenu(p, s); });
        if (menu.inventorySize() > 45) button(h, 53, Material.MAP, text(player, "Other slots", "Weitere Plätze"), p -> { s.bank = 1 - s.bank; showMenu(p, s); });
        open(player, h);
    }
    private void prompt(Player player, Session s, String english, String german, String suggestion) {
        var inventory = active.remove(player.getUniqueId());
        if (inventory != null && player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
        player.sendMessage(Component.text(text(player, english, german), NamedTextColor.GRAY)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(suggestion))
                .hoverEvent(Component.text(text(player, "Click to prepare the command.", "Klicken, um den Befehl vorzubereiten."))));
    }
    private void menuAction(Player player, Session s, String action, String value) {
        menuCommand(player, s, new String[]{"menu", action, value});
        changed(player, s); explainPendingSelection(player, s);
        say(player, "Draft updated.", "Entwurf aktualisiert."); showMenu(player, s);
    }
    private void showMenuActions(Player player, Session s) {
        var h = view(player, s, "Menu actions", "Menü-Aktionen", 27);
        button(h, 10, Material.WRITABLE_BOOK, text(player, "Create submenu", "Untermenü erstellen"),
                p -> prompt(p, s, "/navigator menu create <id> <name...>", "/navigator menu create <id> <Name...>", "/navigator menu create "));
        button(h, 11, Material.BOOK, text(player, "Select existing menu", "Vorhandenes Menü auswählen"), p -> showMenus(p, s, false, 0));
        button(h, 12, Material.NAME_TAG, text(player, "Rename current menu", "Aktuelles Menü umbenennen"),
                p -> prompt(p, s, "/navigator menu rename [en|de|all] <name...>", "/navigator menu rename [en|de|all] <Name...>", "/navigator menu rename all "));
        button(h, 14, menu(s).icon(), text(player, "Change menu icon", "Menü-Icon ändern"), p -> showIcons(p, s, true));
        button(h, 15, Material.CHEST, text(player, "Change menu size", "Menügröße ändern"), p -> showSizes(p, s));
        button(h, 16, Material.ARROW, text(player, "Optional Back icon", "Optionales Zurück-Icon"), p -> showBackOptions(p, s));
        button(h, 22, Material.ARROW, "↩", p -> showMenu(p, s)); open(player, h);
    }
    private void showSizes(Player player, Session s) {
        var h = view(player, s, "Menu size", "Menügröße", 27);
        int[] sizes = {27, 36, 45, 54};
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i]; button(h, 10 + i * 2, Material.CHEST, String.valueOf(size), p -> menuAction(p, s, "size", String.valueOf(size)));
        }
        button(h, 22, Material.ARROW, "↩", p -> showMenuActions(p, s)); open(player, h);
    }
    private void showBackOptions(Player player, Session s) {
        var h = view(player, s, "Optional Back icon", "Optionales Zurück-Icon", 27);
        int slot = menu(s).backSlot();
        button(h, 4, Material.PAPER, slot < 0 ? text(player, "Disabled", "Deaktiviert") : text(player, "Slot ", "Platz ") + slot, null);
        button(h, 11, Material.GRAY_DYE, text(player, "Disable Back icon", "Zurück-Icon deaktivieren"), p -> menuAction(p, s, "back", "-1"));
        button(h, 15, Material.ARROW, text(player, "Choose Back slot", "Zurück-Platz wählen"),
                p -> prompt(p, s, "/navigator menu back <free-slot|-1> (optional)", "/navigator menu back <freier-Platz|-1> (optional)", "/navigator menu back "));
        button(h, 22, Material.ARROW, "↩", p -> showMenuActions(p, s)); open(player, h);
    }
    private void showAdd(Player player, Session s) {
        var h = view(player, s, "Add at slot " + s.slot, "Hinzufügen auf Platz " + s.slot, 27);
        button(h, 11, Material.COMPASS, text(player, "Add Warp", "Warp hinzufügen"), p -> showWarps(p, s, 0));
        button(h, 13, Material.BOOK, text(player, "Link existing submenu", "Vorhandenes Untermenü verknüpfen"), p -> showMenus(p, s, true, 0));
        button(h, 15, Material.GRAY_DYE, text(player, "Unavailable placeholder", "Nicht verfügbarer Platzhalter"), p -> { add(s, "unavailable", "unavailable", s.slot); changed(p, s); showEntry(p, s); });
        button(h, 22, Material.WRITABLE_BOOK, text(player, "Create submenu", "Untermenü erstellen"), List.of(text(player, "Uses this selected empty slot.", "Verwendet diesen ausgewählten leeren Platz.")),
                p -> prompt(p, s, "/navigator menu create <id> <name...>", "/navigator menu create <id> <Name...>", "/navigator menu create "));
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
        button(h, 10, Material.NAME_TAG, text(player, "Name and lore", "Name und Beschreibung"), p -> showTextActions(p, s));
        button(h, 11, entry.icon(), text(player, "Icon", "Icon"), p -> showIcons(p, s, false));
        button(h, 12, Material.IRON_DOOR, text(player, "Permission", "Berechtigung"),
                p -> prompt(p, s, "/navigator entry permission <node|none>", "/navigator entry permission <Node|none>", "/navigator entry permission "));
        button(h, 13, Material.PISTON, text(player, "Move", "Verschieben"),
                p -> prompt(p, s, "/navigator entry move <menu-id> <slot> [page]", "/navigator entry move <Menü-ID> <Platz> [Seite]", "/navigator entry move "));
        button(h, 14, Material.RED_DYE, text(player, "Remove entry", "Eintrag entfernen"), p -> { entryCommand(s, new String[]{"entry", "remove"}); changed(p, s); showMenu(p, s); });
        if (entry.type() == NavigatorConfig.Type.MENU) button(h, 15, Material.BOOK, text(player, "Edit submenu", "Untermenü bearbeiten"), p -> { s.menu = entry.target(); s.page = 0; s.bank = 0; select(s, null); changed(p, s); showMenu(p, s); });
        if (entry.type() == NavigatorConfig.Type.WARP) {
            button(h, 18, Material.MAP, text(player, "Player-count region", "Spielerzähler-Region"), List.of(regionInfo(player, s)), p -> showRegion(p, s));
        }
        button(h, 26, Material.ARROW, "↩", p -> showMenu(p, s)); open(player, h);
    }
    private void showTextActions(Player player, Session s) {
        selected(s); var h = view(player, s, "Name and lore", "Name und Beschreibung", 27);
        button(h, 11, Material.NAME_TAG, text(player, "Edit name", "Name ändern"),
                p -> prompt(p, s, "/navigator entry name [en|de|all] <text...>", "/navigator entry name [en|de|all] <Text...>", "/navigator entry name all "));
        button(h, 15, Material.WRITABLE_BOOK, text(player, "Edit lore", "Beschreibung ändern"),
                p -> prompt(p, s, "/navigator entry lore [en|de|all] <line | line...> (none clears)", "/navigator entry lore [en|de|all] <Zeile | Zeile...> (none entfernt)", "/navigator entry lore all "));
        button(h, 22, Material.ARROW, "↩", p -> showEntry(p, s)); open(player, h);
    }
    private void showRegion(Player player, Session s) {
        var entry = selectedWarp(s); var h = view(player, s, "Player-count region", "Spielerzähler-Region", 27);
        button(h, 4, Material.MAP, text(player, "Optional player counter", "Optionaler Spielerzähler"), List.of(regionInfo(player, s)), p -> region(p, s, "info"));
        boolean full = height(s).full();
        button(h, 6, Material.SCAFFOLDING, text(player, "Height: ", "Höhe: ") + heightName(player, s), List.of(
                full ? text(player, "Y: world minimum to world maximum.", "Y: Weltminimum bis Weltmaximum.")
                        : text(player, "Y: the two marked blocks (inclusive).", "Y: die zwei markierten Blöcke (inklusive)."),
                full ? text(player, "Click: Manual height", "Klicken: Manuelle Höhe")
                        : text(player, "Click: Full world height", "Klicken: Gesamte Welthöhe"),
                text(player, "Draft only; Save to apply. Reselect to change manual Y.", "Nur Entwurf; mit Save übernehmen. Für neue manuelle Y-Werte erneut markieren.")),
                p -> { toggleHeight(s); changed(p, s); showRegion(p, s); });
        button(h, 10, Material.WOODEN_AXE, "Position 1", p -> { region(p, s, "pos1"); changed(p, s); showRegion(p, s); });
        button(h, 12, Material.STONE_AXE, "Position 2", p -> { region(p, s, "pos2"); changed(p, s); showRegion(p, s); });
        button(h, 14, Material.SPONGE, text(player, "Clear region", "Region entfernen"), p -> { region(p, s, "clear"); changed(p, s); showRegion(p, s); });
        button(h, 16, Material.SLIME_BALL, "Stack: " + entry.stackAmount(), p -> { entryCommand(s, new String[]{"entry", "stack", String.valueOf(!entry.stackAmount())}); changed(p, s); showRegion(p, s); });
        button(h, 20, Material.BLAZE_ROD, text(player, "Mark Region", "Region markieren"), p -> region(p, s, "mark"));
        button(h, 24, Material.GRAY_DYE, text(player, "Cancel marking", "Markierung abbrechen"), p -> region(p, s, "cancel"));
        button(h, 22, Material.ARROW, "↩", p -> showEntry(p, s)); open(player, h);
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
        if (!valid(p, top, holder)) {
            if (holder.owner.equals(p.getUniqueId()) && holder.inventory == top && active.remove(p.getUniqueId(), top))
                say(p, "This editor view expired. Reopen /navigator edit.", "Diese Editor-Ansicht ist veraltet. Öffne /navigator edit erneut.");
            return;
        }
        if (event.getClickedInventory() != top || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        var action = holder.actions.get(event.getRawSlot());
        try {
            if (action != null) { action.accept(p); if (sessions.get(p.getUniqueId()) == holder.session) explainPendingSelection(p, holder.session); }
        } catch (IllegalArgumentException | IllegalStateException failure) { feedback(p, failure); }
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
        cancelTool(player, false);
        sessions.remove(player.getUniqueId()); var inventory = active.remove(player.getUniqueId());
        try { navigator.endPreview(player); }
        finally { if (inventory != null && player.getOpenInventory().getTopInventory() == inventory) player.closeInventory(); }
    }
    public void shutdown() {
        running = false;
        if (regionTool != null) regionTool.shutdown();
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
            case "region" -> List.of("mark", "cancel", "pos1", "pos2", "clear", "info");
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

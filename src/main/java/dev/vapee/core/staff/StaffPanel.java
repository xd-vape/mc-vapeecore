package dev.vapee.core.staff;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.SocialChannel;
import dev.vapee.core.moderation.command.HistoryCommand;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
import dev.vapee.core.rank.staff.StaffHierarchyService;
import dev.vapee.core.rank.staff.StaffTargetDecision;
import dev.vapee.core.ui.CachedPlayerHeads;
import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.Pagination;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.utility.command.TeleportCommand;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Small roster/action presentation owned by ModerationModule; all moderation rules stay in their executors. */
public final class StaffPanel implements TabExecutor, Listener {
    public static final String PERMISSION = "vapeecore.staff.panel";
    public enum Action { PREVIOUS, MODE, REFRESH, STATUS, CLOSE, PAGE, NEXT, TARGET, BACK, TELEPORT, HISTORY, WARN, MUTE, KICK }
    public static final MenuLayout<Action> ROSTER_LAYOUT = MenuLayout.builder(27, Action.class)
            .content(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)).emptyState(13)
            .button(Action.PREVIOUS, 18).button(Action.MODE, 19).button(Action.REFRESH, 20)
            .setting(Action.STATUS, 22).button(Action.CLOSE, 24).button(Action.PAGE, 25).button(Action.NEXT, 26).build();
    public static final MenuLayout<Action> TARGET_LAYOUT = MenuLayout.builder(27, Action.class)
            .button(Action.TARGET, 4).button(Action.TELEPORT, 10).button(Action.HISTORY, 11)
            .button(Action.WARN, 12).button(Action.MUTE, 14).button(Action.KICK, 15)
            .button(Action.BACK, 18).setting(Action.STATUS, 22).button(Action.CLOSE, 26).build();

    private final StaffHierarchyService hierarchy;
    private final Function<UUID, Optional<RankInfo>> ranks;
    private final MessageService messages;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventories;
    private final Function<UiItemSpec, ItemStack> items;
    private final BiFunction<UUID, ItemStack, ItemStack> heads;
    private final Consumer<Runnable> nextTick;
    private final BooleanSupplier runtime;
    private final BiConsumer<Player, CommandRequest> commands;
    private final Logger logger;
    private final Map<UUID, Binding> views = new HashMap<>();
    private final Map<UUID, Player> hidden = new HashMap<>();
    private final Consumer<Player> localeChanges = player -> { if (current(player)) refresh(player.getUniqueId()); };
    private boolean active = true;

    public StaffPanel(JavaPlugin plugin, RankService ranks, StaffHierarchyService hierarchy,
                      MessageService messages, BooleanSupplier runtime) {
        this(hierarchy, ranks::getPrimaryRank, messages, plugin.getServer()::getOnlinePlayers,
                plugin.getServer()::getPlayer, plugin.getServer()::createInventory,
                UiItems::render, new CachedPlayerHeads(plugin.getServer())::apply,
                task -> plugin.getServer().getScheduler().runTask(plugin, task), runtime,
                (viewer, request) -> {
                    PluginCommand command = plugin.getCommand(request.action() == Action.TELEPORT ? "tp" : "history");
                    // Use the owning plugin's actual executor, never an alias, console sender or replacement executor.
                    if (command == null || !(request.action() == Action.TELEPORT
                            ? command.getExecutor() instanceof TeleportCommand : command.getExecutor() instanceof HistoryCommand)) {
                        SocialChannel.STAFF.send(messages, viewer, tr("staff.action-unavailable")); return;
                    }
                    command.execute(viewer, command.getName(), new String[]{request.action() == Action.TELEPORT
                            ? request.target().getName() : request.target().getUniqueId().toString()});
                }, plugin.getLogger());
    }

    // Local Paper boundaries, shared by the executable GUI and module lifecycle tests.
    StaffPanel(StaffHierarchyService hierarchy, Function<UUID, Optional<RankInfo>> ranks, MessageService messages,
               Supplier<? extends Collection<? extends Player>> onlinePlayers, Function<UUID, Player> onlinePlayer,
               InventoryFactory inventories, Function<UiItemSpec, ItemStack> items,
               BiFunction<UUID, ItemStack, ItemStack> heads, Consumer<Runnable> nextTick,
               BooleanSupplier runtime, BiConsumer<Player, CommandRequest> commands, Logger logger) {
        this.hierarchy = Objects.requireNonNull(hierarchy); this.ranks = Objects.requireNonNull(ranks);
        this.messages = Objects.requireNonNull(messages); this.onlinePlayers = Objects.requireNonNull(onlinePlayers);
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer); this.inventories = Objects.requireNonNull(inventories);
        this.items = Objects.requireNonNull(items); this.heads = Objects.requireNonNull(heads);
        this.nextTick = Objects.requireNonNull(nextTick); this.runtime = Objects.requireNonNull(runtime);
        this.commands = Objects.requireNonNull(commands); this.logger = Objects.requireNonNull(logger);
    }

    public void startUpdates() { messages.localization().addLocaleListener(localeChanges); }

    public boolean canOpen(Player player) {
        return active && runtime.getAsBoolean() && current(player) && player.hasPermission(PERMISSION) && staff(player);
    }

    private boolean staff(Player player) {
        try { return hierarchy.isStaff(player.getUniqueId()); }
        catch (RuntimeException unavailable) { return false; }
    }

    private boolean current(Player player) {
        return player != null && player.isOnline() && onlinePlayer.apply(player.getUniqueId()) == player;
    }

    /** Public roster eligibility only; never an appearance, rank, nametag or tablist disguise. */
    public boolean isPublic(UUID id) { return !hidden.containsKey(id) || hidden.get(id) != onlinePlayer.apply(id); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!active || !runtime.getAsBoolean()) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) { publicRoster(sender); return true; }
        if (args.length != 0) { SocialChannel.STAFF.send(messages, sender, tr("staff.usage")); return true; }
        if (!(sender instanceof Player player)) { SocialChannel.STAFF.send(messages, sender, tr("common.inventory-player-only")); return true; }
        open(player, StaffPanelHolder.View.STAFF, 0, null);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return active && runtime.getAsBoolean() && args.length == 1 && "list".startsWith(args[0].toLowerCase(Locale.ROOT))
                ? List.of("list") : List.of();
    }

    private void publicRoster(CommandSender viewer) {
        List<Component> names = onlinePlayers.get().stream().filter(this::current).filter(this::staff)
                .filter(p -> isPublic(p.getUniqueId())).filter(p -> !(viewer instanceof Player v) || v.canSee(p))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(p -> p.getUniqueId().toString()))
                .map(p -> rank(p).map(r -> r.displayComponent().append(Component.space())).orElse(Component.empty())
                        .append(playerName(p))).toList();
        Component list = names.isEmpty() ? tr("staff.roster-empty")
                : Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), names);
        SocialChannel.STAFF.send(messages, viewer, tr("staff.roster", Map.of("players", list)));
    }

    private boolean open(Player viewer, StaffPanelHolder.View view, int page, Player selected) {
        if (!canOpen(viewer)) {
            SocialChannel.STAFF.send(messages, viewer, tr("staff.denied"));
            Binding existing = views.get(viewer.getUniqueId());
            if (existing != null && existing.viewer == viewer) closeOwned(viewer.getUniqueId());
            return false;
        }
        if (selected != null && !visible(viewer, selected)) { SocialChannel.STAFF.send(messages, viewer, tr("staff.target-unavailable")); return false; }
        StaffPanelHolder holder = new StaffPanelHolder(viewer.getUniqueId());
        Inventory inventory = inventories.create(holder, 27, messages.localize(viewer, tr(selected == null ? "staff.title" : "staff.target-title")));
        holder.bind(inventory);
        Binding binding = new Binding(viewer, inventory, holder);
        render(binding, view, page, selected);
        InventoryView opened = viewer.openInventory(inventory);
        if (opened == null || opened.getTopInventory() != inventory || !owned(binding) || !canOpen(viewer)) {
            if (owned(binding)) viewer.closeInventory();
            return false;
        }
        views.put(viewer.getUniqueId(), binding);
        return true;
    }

    private Optional<RankInfo> rank(Player player) {
        try { return ranks.apply(player.getUniqueId()); }
        catch (RuntimeException cosmeticFailure) { return Optional.empty(); }
    }
    private Component playerName(Player player) {
        return Component.text(player.getName(), rank(player).map(RankInfo::effectiveColor).orElse(NamedTextColor.WHITE));
    }
    private boolean visible(Player viewer, Player target) { return current(target) && (viewer == target || viewer.canSee(target)); }

    private boolean allowed(Player viewer, Player target, Action action) {
        if (!canOpen(viewer) || !visible(viewer, target) || !viewer.hasPermission(permission(action))) return false;
        if (viewer == target) return action == Action.HISTORY;
        // Existing /tp <player> changes the actor, not the destination player's protected state.
        // Its executor checks the self source and activity policy; moderation protects the target.
        if (action == Action.TELEPORT) return true;
        try { return hierarchy.decideLoaded(viewer.getUniqueId(), target.getUniqueId()) == StaffTargetDecision.ALLOW; }
        catch (RuntimeException unavailable) { return false; }
    }

    private static String permission(Action action) {
        return switch (action) {
            case TELEPORT -> TeleportCommand.PERMISSION;
            case HISTORY -> "vapeecore.moderation.history";
            case KICK -> "vapeecore.moderation.kick";
            case MUTE -> "vapeecore.moderation.mute";
            case WARN -> "vapeecore.moderation.warn";
            default -> throw new IllegalArgumentException("Not a staff capability: " + action);
        };
    }
    private static final List<Action> CAPABILITIES = List.of(Action.TELEPORT, Action.HISTORY, Action.WARN, Action.MUTE, Action.KICK);

    private static Component actionName(Action action) {
        return switch (action) {
            case TELEPORT -> tr("staff.action-teleport"); case HISTORY -> tr("staff.action-history");
            case WARN -> tr("staff.action-warn"); case MUTE -> tr("staff.action-mute"); case KICK -> tr("staff.action-kick");
            default -> throw new IllegalArgumentException("Not a staff action: " + action);
        };
    }

    private void render(Binding binding, StaffPanelHolder.View view, int requestedPage, Player selected) {
        Player viewer = binding.viewer;
        ItemStack[] contents = new ItemStack[27];
        Map<Integer, Player> targets = new LinkedHashMap<>();
        Map<Integer, Action> actions = new LinkedHashMap<>();
        MenuLayout<Action> layout = selected == null ? ROSTER_LAYOUT : TARGET_LAYOUT;
        int page = requestedPage;
        if (selected == null) {
            List<Player> entries = onlinePlayers.get().stream().filter(p -> visible(viewer, p))
                    .filter(p -> view == StaffPanelHolder.View.STAFF ? staff(p) : CAPABILITIES.stream().anyMatch(a -> allowed(viewer, p, a)))
                    .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(p -> p.getUniqueId().toString()))
                    .map(p -> (Player) p).toList();
            Pagination pagination = Pagination.of(entries.size(), requestedPage, layout.pageSize()); page = pagination.page();
            int index = 0;
            for (Player target : entries.subList(pagination.fromIndex(), pagination.toIndex())) {
                int slot = layout.contentSlot(index++); targets.put(slot, target); contents[slot] = head(viewer, target);
            }
            if (entries.isEmpty()) contents[layout.emptySlot()] = item(viewer, Material.GRAY_DYE, tr("staff.empty"), List.of());
            if (pagination.hasPrevious()) place(viewer, contents, actions, layout, Action.PREVIOUS, Material.ARROW, tr("common.previous-page"));
            if (pagination.hasNext()) place(viewer, contents, actions, layout, Action.NEXT, Material.ARROW, tr("common.next-page"));
            place(viewer, contents, actions, layout, Action.MODE, Material.SPYGLASS,
                    tr(view == StaffPanelHolder.View.STAFF ? "staff.show-players" : "staff.show-staff"));
            place(viewer, contents, actions, layout, Action.REFRESH, Material.CLOCK, tr("common.refresh"));
            place(viewer, contents, actions, layout, Action.PAGE, Material.PAPER, tr("common.page", Map.of(
                    "current", Component.text(page + 1), "total", Component.text(pagination.pageCount()))));
        } else {
            contents[layout.slot(Action.TARGET)] = head(viewer, selected);
            for (Action action : CAPABILITIES) if (allowed(viewer, selected, action)) {
                Material icon = switch (action) {
                    case TELEPORT -> Material.ENDER_PEARL; case HISTORY -> Material.BOOK;
                    case WARN -> Material.PAPER; case MUTE -> Material.NOTE_BLOCK; case KICK -> Material.IRON_DOOR;
                    default -> throw new IllegalStateException();
                };
                contents[layout.slot(action)] = item(viewer, icon, actionName(action),
                        List.of(tr(action == Action.TELEPORT || action == Action.HISTORY ? "staff.action-execute" : "staff.action-input")));
                actions.put(layout.slot(action), action);
            }
            place(viewer, contents, actions, layout, Action.BACK, Material.ARROW, tr("common.back"));
        }
        int statusSlot = layout.slot(Action.STATUS);
        contents[statusSlot] = heads.apply(viewer.getUniqueId(), item(viewer, Material.PLAYER_HEAD,
                tr(isPublic(viewer.getUniqueId()) ? "staff.status-public" : "staff.status-hidden"), List.of(
                        tr("staff.status-scope"), tr(isPublic(viewer.getUniqueId()) ? "staff.hide-hint" : "staff.public-hint"))));
        actions.put(statusSlot, Action.STATUS);
        place(viewer, contents, actions, layout, Action.CLOSE, Material.BARRIER, tr("common.close"));
        binding.inventory.setContents(contents);
        binding.holder.content(new StaffPanelHolder.Content(view, page, selected, targets, actions));
    }

    private ItemStack head(Player viewer, Player target) {
        List<Component> lore = new ArrayList<>();
        lore.add(tr("staff.rank", Map.of("rank", rank(target).map(RankInfo::displayComponent).orElse(tr("staff.rank-unavailable")))));
        if (staff(target)) lore.add(tr(isPublic(target.getUniqueId()) ? "staff.member-public" : "staff.member-hidden"));
        lore.add(tr(CAPABILITIES.stream().anyMatch(a -> allowed(viewer, target, a)) ? "staff.select" : "staff.no-actions"));
        return heads.apply(target.getUniqueId(), item(viewer, Material.PLAYER_HEAD, playerName(target), lore));
    }
    private ItemStack item(Player viewer, Material material, Component name, List<Component> lore) {
        return items.apply(UiItems.localize(UiItems.literal(material, name, NamedTextColor.AQUA, lore), messages, viewer));
    }
    private void place(Player viewer, ItemStack[] contents, Map<Integer, Action> actions, MenuLayout<Action> layout,
                       Action action, Material material, Component name) {
        int slot = layout.slot(action); contents[slot] = item(viewer, material, name, List.of()); actions.put(slot, action);
    }

    private boolean owned(Binding binding) {
        return current(binding.viewer) && binding.holder.viewerId().equals(binding.viewer.getUniqueId())
                && binding.inventory.getHolder() == binding.holder && binding.holder.boundTo(binding.inventory)
                && binding.viewer.getOpenInventory().getTopInventory() == binding.inventory;
    }
    private Binding binding(Player player, Inventory top) {
        Binding bound = views.get(player.getUniqueId());
        return bound != null && bound.viewer == player && bound.inventory == top && owned(bound) ? bound : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StaffPanelHolder)) return;
        boolean cancelled = event.isCancelled(); event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        Binding binding = binding(viewer, top);
        if (binding == null) return;
        if (!canOpen(viewer)) { binding.blocked = true; defer(binding, () -> { }); return; }
        if (cancelled || event.getClick() != ClickType.LEFT || event.getRawSlot() < 0 || event.getRawSlot() >= 27
                || (event.getAction() != InventoryAction.PICKUP_ALL && event.getAction() != InventoryAction.NOTHING)) return;
        var content = binding.holder.content();
        Player target = content.targets().get(event.getRawSlot());
        Action action = content.actions().get(event.getRawSlot());
        if (target != null) defer(binding, () -> {
            if (!visible(viewer, target) || (content.view() == StaffPanelHolder.View.STAFF ? !staff(target)
                    : CAPABILITIES.stream().noneMatch(a -> allowed(viewer, target, a)))) {
                SocialChannel.STAFF.send(messages, viewer, tr("staff.target-unavailable")); refresh(viewer.getUniqueId()); return;
            }
            open(viewer, content.view(), content.page(), target);
        });
        else if (action != null) defer(binding, () -> handle(binding, content, action));
    }

    private void handle(Binding binding, StaffPanelHolder.Content content, Action action) {
        Player viewer = binding.viewer;
        switch (action) {
            case CLOSE -> closeOwned(viewer.getUniqueId());
            case STATUS -> {
                if (!hidden.remove(viewer.getUniqueId(), viewer)) hidden.put(viewer.getUniqueId(), viewer);
                refreshAll();
            }
            case MODE -> open(viewer, content.view() == StaffPanelHolder.View.STAFF ? StaffPanelHolder.View.PLAYERS : StaffPanelHolder.View.STAFF, 0, null);
            case PREVIOUS -> open(viewer, content.view(), Math.max(0, content.page() - 1), null);
            case NEXT -> open(viewer, content.view(), content.page() + 1, null);
            case BACK -> open(viewer, content.view(), content.page(), null);
            case REFRESH -> refresh(viewer.getUniqueId());
            case TELEPORT, HISTORY, WARN, MUTE, KICK -> {
                Player target = content.selected();
                if (target == null || !allowed(viewer, target, action)) {
                    SocialChannel.STAFF.send(messages, viewer, tr("staff.action-denied")); refresh(viewer.getUniqueId()); return;
                }
                closeOwned(viewer.getUniqueId());
                // A close hook may itself change authority or replace the player session.
                if (!allowed(viewer, target, action)) return;
                if (action == Action.TELEPORT || action == Action.HISTORY) commands.accept(viewer, new CommandRequest(action, target));
                else SocialChannel.STAFF.send(messages, viewer, tr("staff.prepare", Map.of("action", actionName(action).color(dev.vapee.core.message.MessageStyle.HIGHLIGHT)))
                        .clickEvent(ClickEvent.suggestCommand("/" + action.name().toLowerCase(Locale.ROOT) + " " + target.getUniqueId() + " ")));
            }
            default -> { }
        }
    }

    private void defer(Binding binding, Runnable action) {
        if (binding.pending) return;
        binding.pending = true;
        var content = binding.holder.content();
        nextTick.accept(() -> {
            binding.pending = false;
            if (!active || !runtime.getAsBoolean() || views.get(binding.viewer.getUniqueId()) != binding) return;
            if (!owned(binding)) { views.remove(binding.viewer.getUniqueId(), binding); return; }
            if (binding.blocked || !canOpen(binding.viewer)) { closeOwned(binding.viewer.getUniqueId()); return; }
            if (binding.holder.content() != content) return; // Render/page changed since this click.
            action.run();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StaffPanelHolder)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player viewer) {
            Binding binding = binding(viewer, top);
            if (binding != null && !canOpen(viewer)) { binding.blocked = true; defer(binding, () -> { }); }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof StaffPanelHolder) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof StaffPanelHolder) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        Binding binding = views.get(event.getPlayer().getUniqueId());
        if (binding != null && event.getPlayer() == binding.viewer && event.getView().getTopInventory() == binding.inventory)
            views.remove(binding.viewer.getUniqueId(), binding);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        Binding bound = views.get(leaving.getUniqueId());
        if (bound != null && bound.viewer == leaving) views.remove(leaving.getUniqueId(), bound);
        hidden.remove(leaving.getUniqueId(), leaving);
        nextTick.accept(() -> { if (active && runtime.getAsBoolean()) refreshAll(); });
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (!current(event.getPlayer())) return;
        hidden.remove(event.getPlayer().getUniqueId());
        nextTick.accept(() -> { if (active && runtime.getAsBoolean()) refreshAll(); });
    }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) {
        nextTick.accept(() -> { if (active && runtime.getAsBoolean() && current(event.getPlayer())) refresh(event.getPlayer().getUniqueId()); });
    }

    private void refreshAll() { for (UUID id : List.copyOf(views.keySet())) refresh(id); }
    private void refresh(UUID id) {
        Binding binding = views.get(id);
        if (binding == null) return;
        if (!owned(binding)) { views.remove(id, binding); return; }
        if (!canOpen(binding.viewer) || binding.blocked) { binding.blocked = true; defer(binding, () -> { }); return; }
        var content = binding.holder.content();
        if (content.selected() != null && !visible(binding.viewer, content.selected())) { binding.blocked = true; defer(binding, () -> { }); return; }
        try { render(binding, content.view(), content.page(), content.selected()); }
        catch (RuntimeException exception) {
            binding.blocked = true; defer(binding, () -> { }); logger.log(Level.WARNING, "Staff panel refresh failed for " + id, exception);
        }
    }
    private void closeOwned(UUID id) {
        Binding binding = views.remove(id);
        if (binding != null && owned(binding)) binding.viewer.closeInventory();
    }

    public void disable() {
        active = false;
        RuntimeException failure = null;
        try { messages.localization().removeLocaleListener(localeChanges); }
        catch (RuntimeException exception) { failure = exception; }
        for (UUID id : List.copyOf(views.keySet())) {
            try { closeOwned(id); }
            catch (RuntimeException exception) { if (failure == null) failure = exception; else if (failure != exception) failure.addSuppressed(exception); }
        }
        views.clear(); hidden.clear();
        if (failure != null) throw failure;
    }

    public record CommandRequest(Action action, Player target) { }
    @FunctionalInterface interface InventoryFactory { Inventory create(InventoryHolder holder, int size, Component title); }
    private static final class Binding {
        final Player viewer; final Inventory inventory; final StaffPanelHolder holder;
        boolean pending, blocked;
        Binding(Player viewer, Inventory inventory, StaffPanelHolder holder) { this.viewer = viewer; this.inventory = inventory; this.holder = holder; }
    }
}

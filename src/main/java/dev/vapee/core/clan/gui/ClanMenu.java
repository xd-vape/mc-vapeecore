package dev.vapee.core.clan.gui;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.ui.Pagination;
import dev.vapee.core.clan.*;
import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Holder-bound clan views; no cached domain data or scheduler. */
public final class ClanMenu {
    private UUID presenceTarget;
    private boolean presenceOnline;

    private final dev.vapee.core.clan.ClanChangeListener committedChanges = affected -> affected.forEach(this::refreshActive);
    private boolean observingChanges;

    private Inventory rerenderInventory;

    public enum Action { PREVIOUS, OVERVIEW, MEMBERS, INVITES, CLOSE, ACTION, PAGE_INFO, REFRESH, NEXT }
    public static final Layout DEFAULT_LAYOUT = new Layout(
            MenuLayout.builder(54, Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(Action.PREVIOUS, 45)
            .button(Action.OVERVIEW, 46)
            .button(Action.MEMBERS, 47)
            .button(Action.INVITES, 48)
            .button(Action.CLOSE, 49)
            .button(Action.ACTION, 50)

            .button(Action.PAGE_INFO, 52)
            .button(Action.NEXT, 53)
            .build(), 20, 22, 24);
    /** Overview displays and paged content are mutually exclusive views. */
    public record Layout(MenuLayout<Action> menu, int clanNameSlot, int memberCountSlot, int roleSlot) {
        public Layout {
            Objects.requireNonNull(menu, "menu");
            var allDisplays = List.of(clanNameSlot, memberCountSlot, roleSlot);
            if (allDisplays.stream().anyMatch(slot -> slot < -1))
                throw new IllegalArgumentException("Invalid overview display slot");
            var displays = allDisplays.stream().filter(slot -> slot >= 0).toList();
            if (new HashSet<>(displays).size() != displays.size())
                throw new IllegalArgumentException("Duplicate clan overview display slot");
            for (int slot : displays) {
                MenuLayout.validateSlot(menu.size(), slot);
                if (menu.actionAt(slot).isPresent())
                    throw new IllegalArgumentException("Overview display collides with control: " + slot);
            }
        }
    }

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }
    private final Layout clanLayout;
    public Layout clanLayout() { return clanLayout; }

    private final ClanService clans;
    private final PlayerIdentityService identities;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private Player renderingFor;
    private final java.util.function.Consumer<Player> localeChanges = viewer -> refreshActive(viewer.getUniqueId());
    private final Logger logger;
    private final Map<UUID, Inventory> active = new HashMap<>();

    public ClanMenu(JavaPlugin plugin, ClanService clans, PlayerIdentityService identities, MessageService messages) {
        this(clans, identities, messages, plugin.getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title), UiItems::render, plugin.getLogger());
    }

    public ClanMenu(ClanService clans, PlayerIdentityService identities, MessageService messages,
                    Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger) {
        this(clans, identities, messages, onlinePlayer, inventoryFactory, itemRenderer, logger, DEFAULT_LAYOUT);
    }

    public ClanMenu(ClanService clans, PlayerIdentityService identities, MessageService messages,
                    Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger, Layout menuLayout) {
        this.clanLayout = Objects.requireNonNull(menuLayout, "layout");
        this.layout = menuLayout.menu().validateActions(java.util.Set.of());
        this.layout.requirePaged();
        this.clans = Objects.requireNonNull(clans, "clans");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.itemRenderer = spec -> itemRenderer.render(UiItems.localize(spec, this.messages, renderingFor));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player) { open(player, ClanMenuView.OVERVIEW, 0); }

    public void open(Player player, ClanMenuView requestedView, int requestedPage) {
        Player previousViewer = renderingFor; renderingFor = player;
        try {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestedView, "requestedView");
        UUID viewer = player.getUniqueId();
        Clan clan = clans.getClanOf(viewer).orElse(null);
        ClanMenuView view = clan == null && requestedView == ClanMenuView.MEMBERS
                ? ClanMenuView.OVERVIEW : requestedView;
        List<Entry> entries = entries(viewer, clan, view);
        Pagination pagination = Pagination.of(entries.size(), requestedPage, layout.pageSize());
        int page = pagination.page();
        int first = pagination.fromIndex();
        int end = pagination.toIndex();
        Map<Integer, UUID> targets = new LinkedHashMap<>();
        for (int i = first; i < end; i++) targets.put(layout.contentSlot(i - first), entries.get(i).id());
        ClanMenuHolder holder = new ClanMenuHolder(viewer, view, page, clan == null ? null : clan.id(), targets, layout);
        Inventory inventory;
        if (rerenderInventory != null && rerenderInventory.getHolder() instanceof ClanMenuHolder existing && existing.view() == holder.view()) {
            inventory = rerenderInventory;
            existing.beginRender(holder); holder = existing;
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        } else {
            inventory = inventoryFactory.create(holder, layout.size(), messages.localize(player, title(view)));
            holder.bind(inventory);
        }
        if (view == ClanMenuView.OVERVIEW) renderOverview(inventory, viewer, clan);
        else if (entries.isEmpty()) placeDisplay(inventory, layout.emptySlot(), item(Material.PAPER,
                view == ClanMenuView.MEMBERS ? tr("clan.empty-members") : tr("clan.empty-invites"), List.of()));
        for (int i = first; i < end; i++) inventory.setItem(layout.contentSlot(i - first), item(entries.get(i)));
        if (pagination.hasMultiplePages()) place(inventory, Action.PAGE_INFO, item(Material.PAPER, tr("common.page", java.util.Map.of("current", Component.text(pagination.page()+1), "total", Component.text(pagination.pageCount()))), List.of()));
        if (pagination.hasPrevious()) place(inventory, Action.PREVIOUS, item(Material.ARROW, tr("common.previous-page"), List.of()));
        place(inventory, Action.OVERVIEW, tab(tr("clan.overview-tab"), view == ClanMenuView.OVERVIEW));
        if (clan != null) place(inventory, Action.MEMBERS, tab(tr("clan.members-tab"), view == ClanMenuView.MEMBERS));
        place(inventory, Action.INVITES, tab(tr("clan.invites-tab"), view == ClanMenuView.INVITES));
        place(inventory, Action.CLOSE, item(Material.BARRIER, tr("common.close"), List.of()));
        if (clan == null) place(inventory, Action.ACTION, item(Material.LIME_DYE, tr("clan.create-name"),
                List.of(tr("clan.create-hint"))));
        else if (clan.ownerId().equals(viewer)) place(inventory, Action.ACTION,
                item(Material.RED_DYE, tr("clan.disband-name"), List.of(tr("clan.disband-hint"))));
        place(inventory, Action.REFRESH, item(Material.CLOCK, tr("common.refresh"), List.of(tr("clan.refresh-hint"))));
        if (pagination.hasNext()) place(inventory, Action.NEXT, item(Material.ARROW, tr("common.next-page"), List.of()));
        holder.finishRender();
        var opened = inventory == rerenderInventory ? player.getOpenInventory() : player.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) active.put(viewer, inventory);

        } finally { renderingFor = previousViewer; }
    }

    private void renderOverview(Inventory inventory, UUID viewer, Clan clan) {
        if (clan == null) {
            placeDisplay(inventory, layout.emptySlot(), item(Material.PAPER, tr("clan.no-clan"),
                    List.of(tr("clan.no-clan-hint"))));
            return;
        }
        placeDisplay(inventory, clanLayout.clanNameSlot(), item(Material.NAME_TAG, Component.text(clan.name()), List.of(tr("clan.tag", java.util.Map.of("tag", Component.text(clan.tag()))))));
        placeDisplay(inventory, clanLayout.memberCountSlot(), item(Material.PLAYER_HEAD, tr("clan.member-capacity", java.util.Map.of("count", Component.text(clan.members().size()), "maximum", Component.text(clans.getLimits().maxMembers()))), List.of(tr("clan.owner", java.util.Map.of("player", Component.text(displayName(clan.ownerId())))))));
        placeDisplay(inventory, clanLayout.roleSlot(), item(Material.PAPER, tr("clan.role", java.util.Map.of("role", tr(clan.ownerId().equals(viewer) ? "clan.role.owner" : "clan.role.member"))), List.of()));
    }

    private List<Entry> entries(UUID viewer, Clan clan, ClanMenuView view) {
        if (view == ClanMenuView.OVERVIEW || (view == ClanMenuView.MEMBERS && clan == null)) return List.of();
        List<Entry> entries = new ArrayList<>();
        if (view == ClanMenuView.MEMBERS) {
            for (ClanMember member : clan.members()) {
                UUID id = member.playerId();
                String name = displayName(id);
                boolean online = isOnline(id);
                List<Component> lore = new ArrayList<>(List.of(tr(member.role() == ClanRole.OWNER ? "clan.role.owner" : "clan.role.member"), online ? tr("common.online") : tr("common.offline")));
                if (clan.ownerId().equals(viewer) && !id.equals(viewer)) {
                    lore.add(tr("clan.kick-hint"));
                    lore.add(tr("clan.transfer-hint"));
                }
                entries.add(new Entry(id, name, Material.PLAYER_HEAD, lore, member.role() == ClanRole.OWNER ? 0 : 1));
            }
            entries.sort(Comparator.comparingInt(Entry::order)
                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.id().toString()));
        } else if (clan == null) {
            for (ClanInvite invite : clans.getIncomingInvites(viewer)) {
                Clan source = clans.getClan(invite.clanId()).orElse(null);
                if (source == null) continue;
                entries.add(new Entry(source.id(), source.name() + " [" + source.tag() + "]",
                        Material.PAPER, List.of(tr("clan.member-count", java.util.Map.of("count", Component.text(source.members().size()))),
                        tr("clan.accept-hint"), tr("clan.deny-hint")), 0));
            }
            entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.id().toString()));
        } else if (clan.ownerId().equals(viewer)) {
            for (ClanInvite invite : clans.getOutgoingInvites(clan.id())) {
                UUID id = invite.recipient();
                entries.add(new Entry(id, displayName(id), Material.PLAYER_HEAD,
                        List.of(isOnline(id) ? tr("common.online") : tr("common.offline"), tr("clan.cancel-hint")), 0));
            }
            entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.id().toString()));
        }
        return entries;
    }

    public boolean isActive(Player viewer, Inventory inventory, ClanMenuHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.owner().equals(viewer.getUniqueId()) && inventory.getHolder() == holder
                && holder.boundTo(inventory) && active.get(viewer.getUniqueId()) == inventory
                && viewer.getOpenInventory().getTopInventory() == inventory;
    }

    void forgetIfActive(UUID viewer, Inventory inventory) { active.remove(viewer, inventory); }

    public void closeOpenInventories() {
        try {
            for (Map.Entry<UUID, Inventory> entry : List.copyOf(active.entrySet())) {
                try {
                    Player player = onlinePlayer.apply(entry.getKey());
                    if (player != null && player.isOnline()
                            && player.getOpenInventory().getTopInventory() == entry.getValue()) {
                        player.closeInventory();
                    }
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not close ClanMenu for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            active.clear();
        }
    }

    int activeCount() { return active.size(); }

    public boolean hasNext(UUID viewer, ClanMenuView view, int page) {
        return page >= 0 && Pagination.of(entries(viewer, clans.getClanOf(viewer).orElse(null), view).size(), page, layout.pageSize()).hasNext();
    }

    public String displayName(UUID id) {
        return identities.findById(id).map(PlayerIdentity::name).orElse(id.toString());
    }

    public void promptCreate(Player player) {
        player.closeInventory();
        messages.send(player, tr("clan.create-prompt").color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/clan create TAG Clan Name"))
                .hoverEvent(HoverEvent.showText(tr("clan.create-hover"))));
    }

    public void promptDisband(Player player) {
        player.closeInventory();
        messages.send(player, tr("clan.disband-prompt").color(NamedTextColor.RED)
                .clickEvent(ClickEvent.suggestCommand("/clan disband confirm"))
                .hoverEvent(HoverEvent.showText(tr("clan.disband-hover"))));
    }

    private boolean isOnline(UUID id) {
        Player player = onlinePlayer.apply(id);
        return (id.equals(presenceTarget) ? presenceOnline : player != null && player.isOnline());
    }

    private ItemStack tab(Component label, boolean selected) {
        return item(selected ? Material.LIME_DYE : Material.GRAY_DYE, label,
                List.of(selected ? tr("common.selected") : tr("common.click-open-short")));
    }

    private ItemStack item(Entry entry) { return item(entry.material(), Component.text(entry.name()), entry.lore()); }
    private ItemStack item(Material material, Component name, List<Component> lore) {
        return itemRenderer.render(UiItems.literal(material, name, NamedTextColor.AQUA, lore));
    }
    private static Component title(ClanMenuView view) {
        return UiItems.text(switch (view) {
            case OVERVIEW -> tr("clan.title");
            case MEMBERS -> tr("clan.members-title");
            case INVITES -> tr("clan.invites-title");
        }, NamedTextColor.DARK_GRAY);
    }

    private record Entry(UUID id, String name, Material material, List<Component> lore, int order) { }
    @FunctionalInterface public interface InventoryFactory {
        Inventory create(ClanMenuHolder holder, int size, Component title);
    }
    @FunctionalInterface public interface ItemRenderer { ItemStack render(UiItemSpec spec); }
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> { inventory.setItem(slot, item); ((ClanMenuHolder) inventory.getHolder()).recordAction(slot, action); });
    }

    private void placeDisplay(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0) inventory.setItem(slot, item);
    }

    public void refreshActive(UUID owner) {
        Inventory inventory = active.get(owner);
        if (inventory == null || rerenderInventory != null) return;
        Player player = onlinePlayer.apply(owner);
        if (player == null || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || !(inventory.getHolder() instanceof ClanMenuHolder holder) || !isActive(player, inventory, holder)) {
            active.remove(owner, inventory); return;
        }
        if (!player.hasPermission(dev.vapee.core.clan.command.ClanCommand.PERMISSION)) { active.remove(owner, inventory); player.closeInventory(); return; }
        try { rerenderInventory = inventory; open(player, holder.view(), holder.page()); }
        catch (RuntimeException exception) {
            active.remove(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not update ClanMenu for " + owner, exception);
        } finally { rerenderInventory = null; }
    }

    public void startUpdates() {
        messages.localization().addLocaleListener(localeChanges);
        if (!observingChanges) { clans.addChangeListener(committedChanges); observingChanges = true; }
    }
    public void stopUpdates() {
        messages.localization().removeLocaleListener(localeChanges);
        if (observingChanges) { clans.removeChangeListener(committedChanges); observingChanges = false; }
    }
    public void shutdown() { stopUpdates(); closeOpenInventories(); }

    public void targetPresenceChanged(UUID target, boolean online) {
        presenceTarget = target; presenceOnline = online;
        try { for (UUID owner : java.util.List.copyOf(active.keySet())) {
            if (!owner.equals(target) && (clans.getClanOf(owner).map(clan -> clan.members().stream().anyMatch(member -> member.playerId().equals(target))).orElse(false))) refreshActive(owner);
        } } finally { presenceTarget = null; }
    }

}

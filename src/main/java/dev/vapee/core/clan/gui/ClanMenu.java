package dev.vapee.core.clan.gui;

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
    public enum Action { PREVIOUS, OVERVIEW, MEMBERS, INVITES, CLOSE, ACTION, REFRESH, NEXT }
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
            .button(Action.REFRESH, 52)
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
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player) { open(player, ClanMenuView.OVERVIEW, 0); }

    public void open(Player player, ClanMenuView requestedView, int requestedPage) {
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
        Inventory inventory = inventoryFactory.create(holder, layout.size(), title(view));
        holder.bind(inventory);
        if (view == ClanMenuView.OVERVIEW) renderOverview(inventory, viewer, clan);
        else if (entries.isEmpty()) placeDisplay(inventory, layout.emptySlot(), item(Material.PAPER,
                view == ClanMenuView.MEMBERS ? "No members" : "No invites", List.of()));
        for (int i = first; i < end; i++) inventory.setItem(layout.contentSlot(i - first), item(entries.get(i)));
        if (pagination.hasPrevious()) place(inventory, Action.PREVIOUS, item(Material.ARROW, "Previous Page", List.of()));
        place(inventory, Action.OVERVIEW, tab("Overview", view == ClanMenuView.OVERVIEW));
        if (clan != null) place(inventory, Action.MEMBERS, tab("Members", view == ClanMenuView.MEMBERS));
        place(inventory, Action.INVITES, tab("Invites", view == ClanMenuView.INVITES));
        place(inventory, Action.CLOSE, item(Material.BARRIER, "Close", List.of()));
        if (clan == null) place(inventory, Action.ACTION, item(Material.LIME_DYE, "Create Clan",
                List.of("Click for a command prompt.")));
        else if (clan.ownerId().equals(viewer)) place(inventory, Action.ACTION,
                item(Material.RED_DYE, "Disband Clan", List.of("Shift + Right-click for confirmation command.")));
        place(inventory, Action.REFRESH, item(Material.CLOCK, "Refresh", List.of("Read current clan state.")));
        if (pagination.hasNext()) place(inventory, Action.NEXT, item(Material.ARROW, "Next Page", List.of()));
        var opened = player.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) active.put(viewer, inventory);
    }

    private void renderOverview(Inventory inventory, UUID viewer, Clan clan) {
        if (clan == null) {
            placeDisplay(inventory, layout.emptySlot(), item(Material.PAPER, "You are not in a clan",
                    List.of("Create one or review incoming invites.")));
            return;
        }
        placeDisplay(inventory, clanLayout.clanNameSlot(), item(Material.NAME_TAG, clan.name(), List.of("Tag: " + clan.tag())));
        placeDisplay(inventory, clanLayout.memberCountSlot(), item(Material.PLAYER_HEAD, "Members: " + clan.members().size()
                + "/" + clans.getLimits().maxMembers(), List.of("Owner: " + displayName(clan.ownerId()))));
        placeDisplay(inventory, clanLayout.roleSlot(), item(Material.PAPER, "Your role: " +
                (clan.ownerId().equals(viewer) ? "OWNER" : "MEMBER"), List.of("Clan ID: " + clan.id())));
    }

    private List<Entry> entries(UUID viewer, Clan clan, ClanMenuView view) {
        if (view == ClanMenuView.OVERVIEW || (view == ClanMenuView.MEMBERS && clan == null)) return List.of();
        List<Entry> entries = new ArrayList<>();
        if (view == ClanMenuView.MEMBERS) {
            for (ClanMember member : clan.members()) {
                UUID id = member.playerId();
                String name = displayName(id);
                boolean online = isOnline(id);
                List<String> lore = new ArrayList<>(List.of(member.role().name(), online ? "Online" : "Offline"));
                if (clan.ownerId().equals(viewer) && !id.equals(viewer)) {
                    lore.add("Shift + Right-click to kick");
                    lore.add("Shift + Left-click to transfer ownership");
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
                        Material.PAPER, List.of("Members: " + source.members().size(),
                        "Left-click to accept", "Right-click to deny"), 0));
            }
            entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.id().toString()));
        } else if (clan.ownerId().equals(viewer)) {
            for (ClanInvite invite : clans.getOutgoingInvites(clan.id())) {
                UUID id = invite.recipient();
                entries.add(new Entry(id, displayName(id), Material.PLAYER_HEAD,
                        List.of(isOnline(id) ? "Online" : "Offline", "Right-click to cancel"), 0));
            }
            entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.id().toString()));
        }
        return entries;
    }

    public boolean isActive(Player viewer, Inventory inventory, ClanMenuHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.owner().equals(viewer.getUniqueId()) && inventory.getHolder() == holder
                && holder.boundTo(inventory) && active.get(viewer.getUniqueId()) == inventory;
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
        messages.send(player, Component.text("Click here to enter /clan create ", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/clan create TAG Clan Name"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to edit the clan tag and name."))));
    }

    public void promptDisband(Player player) {
        player.closeInventory();
        messages.send(player, Component.text("Click to enter /clan disband confirm", NamedTextColor.RED)
                .clickEvent(ClickEvent.suggestCommand("/clan disband confirm"))
                .hoverEvent(HoverEvent.showText(Component.text("This permanently deletes the clan."))));
    }

    private boolean isOnline(UUID id) {
        Player player = onlinePlayer.apply(id);
        return player != null && player.isOnline();
    }

    private ItemStack tab(String label, boolean selected) {
        return item(selected ? Material.LIME_DYE : Material.GRAY_DYE, label,
                List.of(selected ? "Selected" : "Click to open"));
    }

    private ItemStack item(Entry entry) { return item(entry.material(), entry.name(), entry.lore()); }
    private ItemStack item(Material material, String name, List<String> lore) {
        return itemRenderer.render(UiItems.literal(material, name, NamedTextColor.AQUA, lore));
    }
    private static Component title(ClanMenuView view) {
        return UiItems.text(switch (view) {
            case OVERVIEW -> "Clan";
            case MEMBERS -> "Clan • Members";
            case INVITES -> "Clan • Invites";
        }, NamedTextColor.DARK_GRAY);
    }

    private record Entry(UUID id, String name, Material material, List<String> lore, int order) { }
    @FunctionalInterface public interface InventoryFactory {
        Inventory create(ClanMenuHolder holder, int size, Component title);
    }
    @FunctionalInterface public interface ItemRenderer { ItemStack render(UiItemSpec spec); }
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> inventory.setItem(slot, item));
    }

    private void placeDisplay(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0) inventory.setItem(slot, item);
    }

}

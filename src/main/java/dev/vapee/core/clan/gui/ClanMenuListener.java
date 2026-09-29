package dev.vapee.core.clan.gui;

import dev.vapee.core.clan.*;
import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Only explicit click types and slot UUIDs may reach domain mutations. */
public final class ClanMenuListener implements Listener {
    private final ClanMenu menu;
    private final ClanService clans;
    private final ClanMessages feedback;
    private final MessageService messages;
    private final Logger logger;

    public ClanMenuListener(ClanMenu menu, ClanService clans, ClanMessages feedback,
                            MessageService messages, Logger logger) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.clans = Objects.requireNonNull(clans, "clans");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ClanMenuHolder holder)) return;
        event.setCancelled(true); // Includes forged menus, bottom inventory and every unsupported click type.
        if (!(event.getWhoClicked() instanceof Player player) || !menu.isActive(player, top, holder)
                || event.getClickedInventory() != top) return;
        if (!player.hasPermission(ClanCommand.PERMISSION)) {
            player.closeInventory();
            messages.send(player, Component.text("You do not have permission to use clans.", NamedTextColor.RED));
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= ClanMenu.INVENTORY_SIZE) return;
        try {
            ClickType click = event.getClick();
            UUID currentClanId = clans.getClanOf(player.getUniqueId()).map(Clan::id).orElse(null);
            if (!Objects.equals(holder.clanId().orElse(null), currentClanId)) {
                messages.send(player, Component.text("Your clan state changed. The menu was refreshed.", NamedTextColor.YELLOW));
                menu.open(player, holder.view(), 0);
                return;
            }
            if (navigate(player, holder, slot, click)) return;
            if (slot >= ClanMenu.CONTENT_SIZE) return;
            UUID target = holder.target(slot).orElse(null);
            if (target == null) return;
            mutate(player, holder, target, click);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not process clan menu action for player " + player.getUniqueId(), exception);
            player.closeInventory();
            messages.send(player, Component.text("The clan data could not be updated. Please try again.", NamedTextColor.RED));
        }
    }

    private void mutate(Player player, ClanMenuHolder holder, UUID target, ClickType click) {
        UUID self = player.getUniqueId();
        Clan clan = clans.getClanOf(self).orElse(null); // Never trust the rendered view as authority.
        ClanResult result;
        String action;
        String display;
        if (holder.view() == ClanMenuView.MEMBERS) {
            if (click != ClickType.SHIFT_RIGHT && click != ClickType.SHIFT_LEFT) return;
            action = click == ClickType.SHIFT_RIGHT ? "kick" : "transfer";
            display = menu.displayName(target);
            result = click == ClickType.SHIFT_RIGHT ? clans.kickMember(self, target)
                    : clans.transferOwnership(self, target);
            feedback.report(player, action, display, result);
            if (result == ClanResult.SUCCESS) {
                if (action.equals("kick")) feedback.notifyKick(target, clan.tag());
                else feedback.notifyTransfer(target, clan.tag());
            }
        } else if (holder.view() == ClanMenuView.INVITES && clan == null) {
            if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
            Clan source = clans.getClan(target).orElse(null);
            action = click == ClickType.LEFT ? "accept" : "deny";
            display = source == null ? target.toString() : source.tag();
            result = click == ClickType.LEFT ? clans.acceptInvite(self, target) : clans.denyInvite(self, target);
            feedback.report(player, action, display, result);
            if (result == ClanResult.SUCCESS && action.equals("accept") && source != null)
                feedback.notifyAccept(source.ownerId(), player.getName());
        } else if (holder.view() == ClanMenuView.INVITES && click == ClickType.RIGHT) {
            action = "cancel";
            display = menu.displayName(target);
            result = clans.cancelInvite(self, target);
            feedback.report(player, action, display, result);
        } else return;
        menu.open(player, holder.view(), holder.page());
    }

    private boolean navigate(Player player, ClanMenuHolder holder, int slot, ClickType click) {
        if (slot == ClanMenu.ACTION_SLOT && click == ClickType.SHIFT_RIGHT) {
            Clan clan = clans.getClanOf(player.getUniqueId()).orElse(null);
            if (clan != null && clan.ownerId().equals(player.getUniqueId())) menu.promptDisband(player);
            return true;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return false;
        switch (slot) {
            case ClanMenu.PREVIOUS_SLOT -> {
                if (holder.page() > 0) menu.open(player, holder.view(), holder.page() - 1);
                return true;
            }
            case ClanMenu.OVERVIEW_SLOT -> { menu.open(player, ClanMenuView.OVERVIEW, 0); return true; }
            case ClanMenu.MEMBERS_SLOT -> {
                if (clans.getClanOf(player.getUniqueId()).isPresent()) menu.open(player, ClanMenuView.MEMBERS, 0);
                return true;
            }
            case ClanMenu.INVITES_SLOT -> { menu.open(player, ClanMenuView.INVITES, 0); return true; }
            case ClanMenu.CLOSE_SLOT -> { player.closeInventory(); return true; }
            case ClanMenu.ACTION_SLOT -> {
                if (clans.getClanOf(player.getUniqueId()).isEmpty()) menu.promptCreate(player);
                return true;
            }
            case ClanMenu.REFRESH_SLOT -> { menu.open(player, holder.view(), holder.page()); return true; }
            case ClanMenu.NEXT_SLOT -> {
                if (menu.hasNext(player.getUniqueId(), holder.view(), holder.page()))
                    menu.open(player, holder.view(), holder.page() + 1);
                return true;
            }
            default -> { return false; }
        }
    }

    @EventHandler public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClanMenuHolder) event.setCancelled(true);
    }
    @EventHandler public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ClanMenuHolder holder) menu.forgetIfActive(holder.owner(), top);
    }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof ClanMenuHolder) menu.forgetIfActive(player.getUniqueId(), top);
    }
}

package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.quest.QuestCompletionFixture;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import java.util.*;
import static dev.vapee.core.quest.daily.menu.QuestMenuFixture.*;

public final class QuestMenuSecurityHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        QuestMenuFixture f = new QuestMenuFixture();
        f.domain.define(QuestCompletionFixture.definition("one", "playtime:minute", 1, 1));
        var owner = f.player(); var other = f.player();
        f.menu.open(owner.player, 0);
        Inventory active = owner.open;
        var holder = (DailyQuestInventoryHolder) active.getHolder();
        check(holder.owner().equals(owner.id) && holder.getInventory() == active && holder.isBoundTo(active)
                && f.menu.isActive(owner.player, active, holder), "owner, holder, bound inventory, registry and current top all match");
        int sync = f.syncCalls; int saves = f.domain.repository.saves;
        for (ClickType click : ClickType.values()) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) continue;
            check(f.click(owner, active, 52, click).isCancelled() && f.syncCalls == sync,
                    "non-left/right refresh control is inert: " + click);
        }
        for (int slot : new int[]{0, 44, 46, 48, 49, 51, 54, 61, -999}) {
            check(f.click(owner, active, slot, ClickType.LEFT).isCancelled() && f.syncCalls == sync,
                    "content, filler, info, bottom and outside cannot act: " + slot);
        }
        check(f.click(owner, active, 54, ClickType.SHIFT_LEFT).isCancelled(), "bottom shift-transfer blocked");
        check(f.click(other, active, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync, "foreign viewer cannot refresh");
        var unbound = new DailyQuestInventoryHolder(owner.id, 0);
        Inventory unboundInventory = inventory(unbound, 54);
        check(f.click(owner, unboundInventory, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync,
                "unbound holder safely cancelled");
        var forged = new DailyQuestInventoryHolder(owner.id, 0);
        Inventory forgedInventory = inventory(forged, 54); forged.bind(forgedInventory);
        owner.open = forgedInventory;
        check(f.click(owner, forgedInventory, 52, ClickType.RIGHT).isCancelled() && f.syncCalls == sync,
                "opened forged inventory lacks exact active registry binding");
        Inventory mismatched = inventory(holder, 54); owner.open = mismatched;
        check(f.click(owner, mismatched, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync,
                "real holder on mismatched inventory cannot act");
        owner.open = owner.bottom;
        check(f.click(owner, active, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync,
                "active registry requires exact current top inventory");
        owner.open = active;
        for (Inventory top : new Inventory[]{active, forgedInventory, unboundInventory}) {
            var drag = new InventoryDragEvent(view(owner, top), null, null, false, Map.of());
            f.listener.onInventoryDrag(drag);
            check(drag.isCancelled() && f.syncCalls == sync, "recognized drags cancel even invalid holders");
        }
        Inventory foreign = inventory(null, 54);
        var foreignClick = f.click(owner, foreign, 52, ClickType.LEFT);
        check(!foreignClick.isCancelled() && f.syncCalls == sync, "unrelated inventory is untouched");
        owner.permission = false;
        check(f.click(owner, active, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync, "revoked permission makes existing controls inert");
        owner.permission = true; owner.online = false;
        check(f.click(owner, active, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync, "offline player cannot act");
        owner.online = true;
        f.domain.players.unloadPlayer(owner.id);
        check(f.click(owner, active, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync, "unloaded profile cannot act");
        f.domain.players.loadPlayer(owner.id, "Tester");
        int afterReloadSaves = f.domain.repository.saves;
        f.menu.open(owner.player, 0); Inventory replacement = owner.open; sync = f.syncCalls;
        check(f.click(owner, active, 52, ClickType.LEFT).isCancelled() && f.syncCalls == sync, "old page is stale after replacement");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(owner, active)));
        check(f.menu.isActive(owner.player, replacement, (DailyQuestInventoryHolder) replacement.getHolder()),
                "late old close cannot forget replacement");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(owner, replacement)));
        check(f.menu.activeCount() == 0, "exact active close invalidates registry");
        owner.cancelNextOpen = true; f.menu.open(owner.player, 0);
        check(f.menu.activeCount() == 0, "cancelled inventory open publishes no binding");
        f.menu.open(owner.player, 0);
        f.listener.onPlayerQuit(new PlayerQuitEvent(owner.player, net.kyori.adventure.text.Component.empty(),
                PlayerQuitEvent.QuitReason.DISCONNECTED));
        check(f.menu.activeCount() == 0, "quit clears binding");
        f.menu.open(owner.player, 0); f.menu.open(other.player, 0);
        owner.failClose = true;
        f.menu.closeOpenInventories();
        check(f.menu.activeCount() == 0 && other.open == other.bottom, "one failed close cannot retain bindings or prevent other closures");
        check(f.domain.logs.stream().anyMatch(log -> log.getMessage().contains(owner.id.toString()) && log.getThrown() != null),
                "close failure logs UUID and cause");
        owner.failClose = false; f.menu.open(owner.player, 0); owner.open = foreign;
        int closes = owner.closes; f.menu.closeOpenInventories();
        check(owner.closes == closes && owner.open == foreign, "disable preserves unrelated current inventory");
        check(f.domain.economy.getCoins(owner.id).orElseThrow() == 0
                && f.domain.players.getPlayer(owner.id).orElseThrow().getQuestState().snapshot().get("one").progress() == 0,
                "security attacks leave progress and rewards untouched");
        check(f.domain.repository.saves == afterReloadSaves && afterReloadSaves > saves && f.presentationCalls == 0,
                "only explicit profile unload/reload persists; invalid UI actions produce no presentation effects");
        System.out.println("QuestMenuSecurityHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String text) { checks++; if (!condition) throw new AssertionError(text); }
}

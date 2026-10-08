package dev.vapee.core.settings;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.logging.Level;

import static dev.vapee.core.settings.SettingsMenuFixture.*;
import static dev.vapee.core.settings.SettingsMenu.*;

public final class SettingsMenuHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        design();
        functions();
        failures();
        System.out.println("SettingsMenuHarness passed " + checks + " checks.");
    }

    private static void design() throws Exception {
        var f = new SettingsMenuFixture();
        var p = f.player("Owner");
        f.menu.open(p.player);
        Inventory top = p.open;
        check(top.getSize() == 54, "root uses 54 slots");
        Map<Integer, Material> features = Map.of(SCOREBOARD_SLOT, Material.MAP, SOUNDS_SLOT, Material.NOTE_BLOCK,
                PRIVATE_MESSAGES_SLOT, Material.WRITABLE_BOOK, FRIEND_REQUESTS_SLOT, Material.PLAYER_HEAD,
                VISIBILITY_SLOT, Material.SPYGLASS, FRIEND_PRESENCE_SLOT, Material.BELL);
        Map<Integer, Integer> statuses = Map.of(SCOREBOARD_SLOT, SCOREBOARD_STATUS_SLOT,
                SOUNDS_SLOT, SOUNDS_STATUS_SLOT, PRIVATE_MESSAGES_SLOT, PRIVATE_MESSAGES_STATUS_SLOT,
                FRIEND_REQUESTS_SLOT, FRIEND_REQUESTS_STATUS_SLOT, VISIBILITY_SLOT, VISIBILITY_STATUS_SLOT,
                FRIEND_PRESENCE_SLOT, FRIEND_PRESENCE_STATUS_SLOT);
        for (var entry : features.entrySet()) {
            int slot = entry.getKey();
            check(spec(top, slot).material() == entry.getValue(), "correct feature icon at " + slot);
            boolean presence = slot == FRIEND_PRESENCE_SLOT;
            int statusSlot = statuses.get(slot);
            check(spec(top, statusSlot).material() == (presence
                            ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE)
                            && spec(top, statusSlot).name().color().equals(presence
                            ? NamedTextColor.RED : NamedTextColor.GREEN),
                    "correct default status at named feature status slot " + slot);
        }
        check(text(top, SCOREBOARD_SLOT).equals("Scoreboard") && text(top, SOUNDS_SLOT).equals("Sounds")
                        && text(top, PRIVATE_MESSAGES_SLOT).equals("Private Messages") && text(top, FRIEND_REQUESTS_SLOT).equals("Friend Requests")
                        && text(top, FRIEND_PRESENCE_SLOT).equals("Friend Presence"),
                "all five real settings retain clear labels");
        check(text(top, VISIBILITY_SLOT).equals("Player Visibility") && text(top, VISIBILITY_STATUS_SLOT).equals("All Players"),
                "visibility card has dedicated current-mode status");
        check(spec(top, CLOSE_SLOT).material() == Material.BARRIER && text(top, CLOSE_SLOT).equals("Close")
                        && spec(top, REFRESH_SLOT).material() == Material.CLOCK && text(top, REFRESH_SLOT).equals("Refresh"),
                "consistent close and refresh controls");
        int populated = 0;
        for (int slot = 0; slot < 54; slot++) if (top.getItem(slot) != null) populated++;
        check(populated == 14 && top.getItem(4) == null, "only functional items; no filler or fake settings");
        check(text(top, FRIEND_PRESENCE_STATUS_SLOT).equals("Disabled") && spec(top, FRIEND_PRESENCE_STATUS_SLOT).material() == Material.RED_STAINED_GLASS_PANE,
                "friend presence is visibly opt-in by default");
        f.settings.setLobbyPlayersVisible(p.id, false);
        f.settings.setScoreboardEnabled(p.id, false);
        f.menu.refresh(p.player, top);
        check(text(top, VISIBILITY_STATUS_SLOT).equals("Filtered") && spec(top, VISIBILITY_STATUS_SLOT).material() == Material.YELLOW_STAINED_GLASS_PANE
                        && spec(top, VISIBILITY_STATUS_SLOT).name().color().equals(NamedTextColor.YELLOW),
                "filtered mode is yellow rather than disabled");
        check(text(top, SCOREBOARD_STATUS_SLOT).equals("Disabled") && spec(top, SCOREBOARD_STATUS_SLOT).material() == Material.RED_STAINED_GLASS_PANE
                        && spec(top, SCOREBOARD_STATUS_SLOT).name().color().equals(NamedTextColor.RED),
                "disabled setting is red");
    }

    private static void functions() throws Exception {
        var f = new SettingsMenuFixture();
        var p = f.player("Owner");
        f.menu.open(p.player);
        Inventory top = p.open;
        int baseline = f.repository.saves;
        int[] icons = {SCOREBOARD_SLOT, SOUNDS_SLOT, PRIVATE_MESSAGES_SLOT, FRIEND_REQUESTS_SLOT, FRIEND_PRESENCE_SLOT};
        int[] panes = {SCOREBOARD_STATUS_SLOT, SOUNDS_STATUS_SLOT, PRIVATE_MESSAGES_STATUS_SLOT, FRIEND_REQUESTS_STATUS_SLOT, FRIEND_PRESENCE_STATUS_SLOT};
        for (int i = 0; i < icons.length; i++) {
            check(f.click(p, top, icons[i], ClickType.LEFT).isCancelled(), "left click is protected");
            check(text(top, panes[i]).equals(icons[i] == FRIEND_PRESENCE_SLOT ? "Enabled" : "Disabled"),
                    "icon toggles and refreshes status");
            check(f.click(p, top, panes[i], ClickType.RIGHT).isCancelled(), "right click is protected");
            check(text(top, panes[i]).equals(icons[i] == FRIEND_PRESENCE_SLOT ? "Disabled" : "Enabled"),
                    "status toggles same setting");
        }
        check(f.repository.saves == baseline + 10, "all toggles persist immediately");
        check(f.presentationCalls == 2, "only successful scoreboard toggles update presentation");
        check(f.soundCalls == 9, "sound disable is silent, sound enable gives feedback");
        check(p.open == top && p.opens == 1, "toggles rerender existing inventory without reopen");
        f.settings.setLobbyPlayersVisible(p.id, false);
        baseline = f.repository.saves;
        f.click(p, top, REFRESH_SLOT, ClickType.LEFT);
        check(p.open == top && text(top, VISIBILITY_STATUS_SLOT).equals("Filtered") && f.repository.saves == baseline
                        && f.soundCalls == 9 && f.presentationCalls == 2,
                "refresh reads live state without mutation, presentation action or sound");
        f.click(p, top, VISIBILITY_SLOT, ClickType.LEFT);
        f.click(p, top, VISIBILITY_STATUS_SLOT, ClickType.RIGHT);
        check(f.visibilityCalls == 2 && !f.settings.areLobbyPlayersVisible(p.id).orElseThrow()
                        && f.repository.saves == baseline, "both visibility items only open category");
        f.click(p, top, CLOSE_SLOT, ClickType.RIGHT);
        check(p.open == p.bottom && p.closes == 1 && f.visibilityCalls == 2 && f.soundCalls == 9,
                "close only closes and is silent");
    }

    private static void failures() throws Exception {
        var f = new SettingsMenuFixture();
        var p = f.player("Owner");
        f.menu.open(p.player);
        Inventory top = p.open;
        int[] icons = {SCOREBOARD_SLOT, SOUNDS_SLOT, PRIVATE_MESSAGES_SLOT, FRIEND_REQUESTS_SLOT, FRIEND_PRESENCE_SLOT};
        int[] panes = {SCOREBOARD_STATUS_SLOT, SOUNDS_STATUS_SLOT, PRIVATE_MESSAGES_STATUS_SLOT, FRIEND_REQUESTS_STATUS_SLOT, FRIEND_PRESENCE_STATUS_SLOT};
        for (int i = 0; i < icons.length; i++) {
            int slot = icons[i];
            int statusSlot = panes[i];
            int baseline = f.repository.saves;
            f.repository.failNext = true;
            f.click(p, top, slot, ClickType.LEFT);
            check(text(top, statusSlot).equals(slot == FRIEND_PRESENCE_SLOT ? "Disabled" : "Enabled")
                            && f.repository.saves == baseline,
                    "failed toggle restores actual runtime state at " + slot);
            check(f.presentationCalls == 0 && f.soundCalls == 0 && p.open == top,
                    "failed save does not apply, sound or reopen");
            check(plain(p.received.getLast()).contains("could not be saved")
                            && f.logs.getLast().getLevel() == Level.SEVERE
                            && f.logs.getLast().getMessage().contains(p.id.toString()),
                    "failure produces UUID-tagged severe log and controlled message");
        }
        f.players.unloadPlayer(p.id);
        f.click(p, top, SCOREBOARD_STATUS_SLOT, ClickType.LEFT);
        check(p.open == p.bottom && plain(p.received.getLast()).contains("profile is not available"),
                "unavailable profile closes menu without exception");
        check(!f.menu.isActive(p.player, top, (SettingsInventoryHolder) top.getHolder()),
                "unavailable profile invalidates active inventory");
        f.menu.open(p.player);
        check(p.open == p.bottom && plain(p.received.getLast()).contains("profile is not available"),
                "unavailable open remains controlled");
        var refresh = f.player("RefreshMissing");
        f.menu.open(refresh.player);
        f.players.unloadPlayer(refresh.id);
        f.click(refresh, refresh.open, REFRESH_SLOT, ClickType.LEFT);
        check(refresh.open == refresh.bottom, "refresh closes if profile disappeared");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

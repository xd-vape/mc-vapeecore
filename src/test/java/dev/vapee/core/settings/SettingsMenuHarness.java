package dev.vapee.core.settings;

import dev.vapee.core.ui.MenuTestLayouts;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.logging.Level;

import static dev.vapee.core.settings.SettingsMenuFixture.*;

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
        check(top.getSize() == MenuTestLayouts.SETTINGS.size(), "root uses defined inventory size");
        Map<Integer, Material> features = Map.of(MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD), Material.MAP, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS), Material.NOTE_BLOCK,
                MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES), Material.WRITABLE_BOOK, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS), Material.PLAYER_HEAD,
                MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY), Material.SPYGLASS, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE), Material.BELL);
        Map<Integer, Integer> statuses = Map.of(MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD, 1),
                MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES, 1),
                MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1),
                MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE, 1));
        for (var entry : features.entrySet()) {
            int slot = entry.getKey();
            check(spec(top, slot).material() == entry.getValue(), "correct feature icon at " + slot);
            int statusSlot = statuses.get(slot);
            check(spec(top, statusSlot).material() == Material.LIME_STAINED_GLASS_PANE
                            && spec(top, statusSlot).name().color().equals(NamedTextColor.GREEN),
                    "correct default status at named feature status slot " + slot);
        }
        check(text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD)).equals("Scoreboard") && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS)).equals("Sounds")
                        && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES)).equals("Private Messages") && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS)).equals("Friend Requests")
                        && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE)).equals("Friend Presence"),
                "all five real settings retain clear labels");
        check(text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY)).equals("Player Visibility") && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1)).equals("All Players"),
                "visibility card has dedicated current-mode status");
        check(spec(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.CLOSE)).material() == Material.BARRIER && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.CLOSE)).equals("Close")
                        && spec(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.REFRESH)).material() == Material.CLOCK && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.REFRESH)).equals("Refresh"),
                "consistent close and refresh controls");
        int populated = 0;
        for (int slot = 0; slot < top.getSize(); slot++) if (top.getItem(slot) != null) populated++;
        check(populated == 14 && java.util.stream.IntStream.range(0, top.getSize()).allMatch(slot -> top.getItem(slot) == null || f.menu.entryAt(slot).isPresent() || f.menu.layout().actionAt(slot).isPresent()), "only functional items; no filler or fake settings");
        check(text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE, 1)).equals("Enabled") && spec(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE, 1)).material() == Material.LIME_STAINED_GLASS_PANE,
                "friend presence is visibly enabled by default");
        f.settings.setLobbyPlayersVisible(p.id, false);
        f.settings.setScoreboardEnabled(p.id, false);
        f.menu.refresh(p.player, top);
        check(text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1)).equals("Filtered") && spec(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1)).material() == Material.YELLOW_STAINED_GLASS_PANE
                        && spec(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1)).name().color().equals(NamedTextColor.YELLOW),
                "filtered mode is yellow rather than disabled");
        check(text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD, 1)).equals("Disabled") && spec(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD, 1)).material() == Material.RED_STAINED_GLASS_PANE
                        && spec(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD, 1)).name().color().equals(NamedTextColor.RED),
                "disabled setting is red");
    }

    private static void functions() throws Exception {
        var f = new SettingsMenuFixture();
        var p = f.player("Owner");
        f.menu.open(p.player);
        Inventory top = p.open;
        int baseline = f.repository.saves;
        int[] icons = {MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE)};
        int[] panes = {MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE, 1)};
        for (int i = 0; i < icons.length; i++) {
            check(f.click(p, top, icons[i], ClickType.LEFT).isCancelled(), "left click is protected");
            check(text(top, panes[i]).equals("Disabled"),
                    "icon toggles and refreshes status");
            check(f.click(p, top, panes[i], ClickType.RIGHT).isCancelled(), "right click is protected");
            check(text(top, panes[i]).equals("Enabled"),
                    "status toggles same setting");
        }
        check(f.repository.saves == baseline + 10, "all toggles persist immediately");
        check(f.presentationCalls == 2, "only successful scoreboard toggles update presentation");
        check(f.soundCalls == 9, "sound disable is silent, sound enable gives feedback");
        check(p.open == top && p.opens == 1, "toggles rerender existing inventory without reopen");
        f.settings.setLobbyPlayersVisible(p.id, false);
        baseline = f.repository.saves;
        f.click(p, top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.REFRESH), ClickType.LEFT);
        check(p.open == top && text(top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1)).equals("Filtered") && f.repository.saves == baseline
                        && f.soundCalls == 9 && f.presentationCalls == 2,
                "refresh reads live state without mutation, presentation action or sound");
        f.click(p, top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY), ClickType.LEFT);
        f.click(p, top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1), ClickType.RIGHT);
        check(f.visibilityCalls == 2 && !f.settings.areLobbyPlayersVisible(p.id).orElseThrow()
                        && f.repository.saves == baseline, "both visibility items only open category");
        f.click(p, top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.CLOSE), ClickType.RIGHT);
        check(p.open == p.bottom && p.closes == 1 && f.visibilityCalls == 2 && f.soundCalls == 9,
                "close only closes and is silent");
    }

    private static void failures() throws Exception {
        var f = new SettingsMenuFixture();
        var p = f.player("Owner");
        f.menu.open(p.player);
        Inventory top = p.open;
        int[] icons = {MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE)};
        int[] panes = {MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SOUNDS, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.PRIVATE_MESSAGES, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_REQUESTS, 1), MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.FRIEND_PRESENCE, 1)};
        for (int i = 0; i < icons.length; i++) {
            int slot = icons[i];
            int statusSlot = panes[i];
            int baseline = f.repository.saves;
            f.repository.failNext = true;
            f.click(p, top, slot, ClickType.LEFT);
            check(text(top, statusSlot).equals("Enabled")
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
        f.click(p, top, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD, 1), ClickType.LEFT);
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
        f.click(refresh, refresh.open, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.REFRESH), ClickType.LEFT);
        check(refresh.open == refresh.bottom, "refresh closes if profile disappeared");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

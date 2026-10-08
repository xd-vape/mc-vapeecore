package dev.vapee.core.settings;

import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** One list of real Settings tiles, including their feature-owned state mutations. */
public final class SettingsMenuEntries {
    private SettingsMenuEntries() { }

    public static List<SettingsMenuEntry> defaults() { return defaults(SettingsMenu.DEFAULT_LAYOUT); }

    public static List<SettingsMenuEntry> defaults(MenuLayout<SettingsMenu.Action> layout) {
        return List.of(
                toggle(layout.slot(SettingsMenu.Action.SCOREBOARD), layout.slot(SettingsMenu.Action.SCOREBOARD, 1), Material.MAP,
                        "Scoreboard", List.of("Show or hide the lobby scoreboard."), PlayerSettings::isScoreboardEnabled,
                        PlayerSettingsService::setScoreboardEnabled, true),
                toggle(layout.slot(SettingsMenu.Action.SOUNDS), layout.slot(SettingsMenu.Action.SOUNDS, 1), Material.NOTE_BLOCK,
                        "Sounds", List.of("Enable or disable VapeeCore", "interface feedback sounds."), PlayerSettings::isSoundsEnabled,
                        PlayerSettingsService::setSoundsEnabled, false),
                toggle(layout.slot(SettingsMenu.Action.PRIVATE_MESSAGES), layout.slot(SettingsMenu.Action.PRIVATE_MESSAGES, 1), Material.WRITABLE_BOOK,
                        "Private Messages", List.of("Choose whether other players can send", "private messages to you."),
                        PlayerSettings::isPrivateMessagesEnabled, PlayerSettingsService::setPrivateMessagesEnabled, false),
                toggle(layout.slot(SettingsMenu.Action.FRIEND_REQUESTS), layout.slot(SettingsMenu.Action.FRIEND_REQUESTS, 1), Material.PLAYER_HEAD,
                        "Friend Requests", List.of("Choose whether other players can send", "friend requests to you."),
                        PlayerSettings::isFriendRequestsEnabled, PlayerSettingsService::setFriendRequestsEnabled, false),
                toggle(layout.slot(SettingsMenu.Action.FRIEND_PRESENCE), layout.slot(SettingsMenu.Action.FRIEND_PRESENCE, 1), Material.BELL,
                        "Friend Presence", List.of("Notify you when friends join or leave."),
                        PlayerSettings::isFriendPresenceNotificationsEnabled, PlayerSettingsService::setFriendPresenceNotificationsEnabled, false),
                new SettingsMenuEntry(layout.slot(SettingsMenu.Action.VISIBILITY), layout.slot(SettingsMenu.Action.VISIBILITY, 1),
                        current -> new UiItemSpec(Material.SPYGLASS, UiItems.text("Player Visibility", NamedTextColor.AQUA),
                                List.of(UiItems.text("Manage which lobby players you can see.", NamedTextColor.GRAY),
                                        Component.empty(), UiItems.text("Click to open.", NamedTextColor.YELLOW))),
                        current -> new UiItemSpec(current.isLobbyPlayersVisible() ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE,
                                UiItems.text(current.isLobbyPlayersVisible() ? "All Players" : "Filtered",
                                        current.isLobbyPlayersVisible() ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                                List.of(UiItems.text("Click to open visibility settings.", NamedTextColor.GRAY))),
                        (player, current, context) -> {
                            context.visibilityMenuOpener().accept(player);
                            return SettingsMenuEntry.Result.OPENED;
                        }));
    }

    private static SettingsMenuEntry toggle(int slot, int statusSlot, Material material, String name, List<String> description,
                                             Predicate<PlayerSettings> enabled, ToggleSetter setter, boolean refreshPresentation) {
        return new SettingsMenuEntry(slot, statusSlot,
                current -> {
                    List<Component> lore = new ArrayList<>();
                    description.forEach(line -> lore.add(UiItems.text(line, NamedTextColor.GRAY)));
                    lore.add(Component.empty());
                    lore.add(UiItems.text(enabled.test(current) ? "Click to disable." : "Click to enable.", NamedTextColor.YELLOW));
                    return new UiItemSpec(material, UiItems.text(name, NamedTextColor.AQUA), lore);
                }, current -> {
                    boolean on = enabled.test(current);
                    return new UiItemSpec(on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                            UiItems.text(on ? "Enabled" : "Disabled", on ? NamedTextColor.GREEN : NamedTextColor.RED),
                            List.of(UiItems.text(on ? "Click to disable." : "Click to enable.", NamedTextColor.GRAY)));
                }, (player, current, context) -> {
                    if (!setter.save(context.settings(), player.getUniqueId(), !enabled.test(current))) return SettingsMenuEntry.Result.UNAVAILABLE;
                    if (refreshPresentation) context.presentationRefresh().accept(player);
                    return SettingsMenuEntry.Result.SAVED;
                });
    }
    @FunctionalInterface private interface ToggleSetter {
        boolean save(PlayerSettingsService settings, UUID owner, boolean value);
    }
}

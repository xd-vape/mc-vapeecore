package dev.vapee.core.lobby.item;

import dev.vapee.core.lobby.experience.player.PlayerMenu;
import dev.vapee.core.friend.gui.FriendMenu;
import dev.vapee.core.friend.gui.FriendsLobbyItemAction;
import dev.vapee.core.lobby.experience.navigator.NavigatorLobbyItemAction;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.settings.SettingsLobbyItemAction;
import dev.vapee.core.settings.SettingsMenu;
import dev.vapee.core.visibility.VisibilityLobbyItemAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

import java.util.List;
import java.util.function.Supplier;

/** The single list of production lobby features. Add a feature adapter and one registration here;
 * parser, builder, service and listener never need another ID-specific branch. */
public final class LobbyItemRegistrations {
    private LobbyItemRegistrations() { }

    public static void register(LobbyItemRegistry registry, Supplier<NavigatorMenu> navigator,
                                Supplier<SettingsMenu> settings, Supplier<FriendMenu> friends,
                                Supplier<VisibilityLobbyItemAction> visibility) {
        register(registry, navigator, settings, friends, visibility, () -> null);
    }

    public static void register(LobbyItemRegistry registry, Supplier<NavigatorMenu> navigator,
                                Supplier<SettingsMenu> settings, Supplier<FriendMenu> friends,
                                Supplier<VisibilityLobbyItemAction> visibility,
                                Supplier<PlayerMenu> playerMenu) {
        var nav = appearance(Material.COMPASS, "<i18n:lobby.navigator.name>",
                List.of("<i18n:lobby.navigator.lore>"), false);
        var visible = appearance(Material.LIME_DYE, "<i18n:lobby.visibility.name>",
                List.of("<i18n:lobby.visibility.lore>"), false);
        var filtered = appearance(Material.GRAY_DYE, "<i18n:lobby.visibility.filtered-name>",
                List.of("<i18n:lobby.visibility.filtered-lore>"), false);
        var hidden = appearance(Material.RED_DYE, "<i18n:lobby.visibility.hidden-name>",
                List.of("<i18n:lobby.visibility.hidden-lore>"), false);
        var playerHead = appearance(Material.PLAYER_HEAD, "<i18n:lobby.player-menu.name>",
                List.of("<i18n:lobby.player-menu.open>", "<i18n:lobby.player-menu.select>"), true);
        var preferences = appearance(Material.COMPARATOR, "<i18n:lobby.settings.name>",
                List.of("<i18n:lobby.settings.lore>"), false);
        var friend = appearance(Material.PLAYER_HEAD, "<i18n:lobby.friends.name>",
                List.of("<i18n:lobby.friends.lore>", "", "<i18n:lobby.friends.open>"), true);

        registry.register("player-menu", new LobbyItemDefinition(true, 0, playerHead, playerHead), (viewer, click) -> {
            var menu = playerMenu.get();
            return (click == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || click == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)
                    && menu != null && menu.open(viewer);
        });
        registry.register("navigator", new LobbyItemDefinition(true, 8, nav, nav), new NavigatorLobbyItemAction(navigator));
        registry.register("visibility", new LobbyItemDefinition(true, 1, visible, filtered, java.util.Map.of("hidden", hidden)),
                new LobbyItemAction() {
                    @Override public boolean supports(org.bukkit.event.block.Action click) {
                        var action = visibility.get();
                        return action != null && action.supports(click);
                    }
                    @Override public boolean handleClick(org.bukkit.entity.Player player, org.bukkit.event.block.Action click) {
                        var action = visibility.get();
                        return action != null && action.handleClick(player, click);
                    }
                }, (player, definition) -> {
                    var action = visibility.get();
                    return action == null ? definition.appearance() : action.appearance(player, definition);
                });
        registry.register("settings", new LobbyItemDefinition(false, 7, preferences, preferences), new SettingsLobbyItemAction(settings));
        registry.register("friends", new LobbyItemDefinition(false, 6, friend, friend), new FriendsLobbyItemAction(friends));
    }

    /** Standalone config loaders have the same registrations but no enabled feature runtime. */
    public static LobbyItemRegistry inactive() {
        var registry = new LobbyItemRegistry();
        register(registry, () -> null, () -> null, () -> null, () -> null);
        return registry;
    }

    private static LobbyItemDefinition.Appearance appearance(Material material, String name, List<String> lore, boolean self) {
        return new LobbyItemDefinition.Appearance(material, text(name), lore.stream().map(LobbyItemRegistrations::text).toList(), self);
    }
    private static Component text(String value) {
        return MiniMessage.miniMessage().deserialize(value, dev.vapee.core.i18n.Translations.tagResolver()).decoration(TextDecoration.ITALIC, false);
    }
}

package dev.vapee.core.lobby.item;

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
        var nav = appearance(Material.COMPASS, "<i18n:lobby.navigator.name>",
                List.of("<i18n:lobby.navigator.lore>"), false);
        var visible = appearance(Material.LIME_DYE, "<i18n:lobby.visibility.name>",
                List.of("<i18n:lobby.visibility.lore>"), false);
        var filtered = appearance(Material.GRAY_DYE, "<i18n:lobby.visibility.filtered-name>",
                List.of("<i18n:lobby.visibility.filtered-lore>"), false);
        var preferences = appearance(Material.COMPARATOR, "<i18n:lobby.settings.name>",
                List.of("<i18n:lobby.settings.lore>"), false);
        var friend = appearance(Material.PLAYER_HEAD, "<i18n:lobby.friends.name>",
                List.of("<i18n:lobby.friends.lore>", "", "<i18n:lobby.friends.open>"), true);

        registry.register("navigator", new LobbyItemDefinition(true, 0, nav, nav), new NavigatorLobbyItemAction(navigator));
        registry.register("visibility", new LobbyItemDefinition(true, 4, visible, filtered),
                (player, click) -> {
                    var action = visibility.get();
                    return action != null && action.handleClick(player, click);
                }, (player, definition) -> {
                    var action = visibility.get();
                    return action == null ? definition.appearance() : action.appearance(player, definition);
                });
        registry.register("settings", new LobbyItemDefinition(true, 8, preferences, preferences), new SettingsLobbyItemAction(settings));
        registry.register("friends", new LobbyItemDefinition(true, 1, friend, friend), new FriendsLobbyItemAction(friends));
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

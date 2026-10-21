package dev.vapee.core.settings;

import static dev.vapee.core.i18n.Translations.tr;

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
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;
import java.util.function.Predicate;

/** Supported feature definitions. The menu layout alone selects visible icons and optional status. */
public final class SettingsMenuEntries {
    private SettingsMenuEntries() { }

    public static List<SettingsMenuEntry> defaults() { return defaults(SettingsMenu.DEFAULT_LAYOUT); }

    public static List<SettingsMenuEntry> defaults(MenuLayout<SettingsMenu.Action> layout) {
        Map<SettingsMenu.Action, Definition> definitions = Map.ofEntries(
                Map.entry(SettingsMenu.Action.SCOREBOARD, toggle(Material.MAP,
                        tr("settings.scoreboard.name"), List.of(tr("settings.scoreboard.hint")), PlayerSettings::isScoreboardEnabled,
                        PlayerSettingsService::setScoreboardEnabled, true)),
                Map.entry(SettingsMenu.Action.SOUNDS, toggle(Material.NOTE_BLOCK,
                        tr("settings.sounds.name"), List.of(tr("settings.sounds.hint-one"), tr("settings.sounds.hint-two")), PlayerSettings::isSoundsEnabled,
                        PlayerSettingsService::setSoundsEnabled, false)),
                Map.entry(SettingsMenu.Action.PRIVATE_MESSAGES, toggle(Material.WRITABLE_BOOK,
                        tr("settings.private-messages.name"), List.of(tr("settings.friend-requests.hint-one"), tr("settings.private-messages.hint-two")),
                        PlayerSettings::isPrivateMessagesEnabled, PlayerSettingsService::setPrivateMessagesEnabled, false)),
                Map.entry(SettingsMenu.Action.FRIEND_REQUESTS, toggle(Material.PLAYER_HEAD,
                        tr("settings.friend-requests.name"), List.of(tr("settings.friend-requests.hint-one"), tr("settings.friend-requests.hint-two")),
                        PlayerSettings::isFriendRequestsEnabled, PlayerSettingsService::setFriendRequestsEnabled, false)),
                Map.entry(SettingsMenu.Action.FRIEND_PRESENCE, toggle(Material.BELL,
                        tr("settings.friend-presence.name"), List.of(tr("settings.friend-presence.hint")),
                        PlayerSettings::isFriendPresenceNotificationsEnabled, PlayerSettingsService::setFriendPresenceNotificationsEnabled, false)),
                Map.entry(SettingsMenu.Action.VISIBILITY, new Definition(
                        current -> new UiItemSpec(Material.SPYGLASS, UiItems.text(tr("settings.visibility.name"), NamedTextColor.AQUA),
                                List.of(UiItems.text(tr("settings.visibility.hint"), NamedTextColor.GRAY),
                                        UiItems.text(tr("common.current", Map.of("state", tr("visibility.mode." + current.getVisibility().getMode().name()))), NamedTextColor.GRAY),
                                        Component.empty(), UiItems.text(tr("common.click-open"), NamedTextColor.GRAY))),
                        current -> new UiItemSpec(switch (current.getVisibility().getMode()) {
                                    case ALL -> Material.LIME_STAINED_GLASS_PANE;
                                    case FILTERED -> Material.YELLOW_STAINED_GLASS_PANE;
                                    case HIDDEN -> Material.RED_STAINED_GLASS_PANE;
                                },
                                UiItems.text(tr("visibility.mode." + current.getVisibility().getMode().name()),
                                        switch (current.getVisibility().getMode()) {
                                            case ALL -> NamedTextColor.GREEN;
                                            case FILTERED -> NamedTextColor.YELLOW;
                                            case HIDDEN -> NamedTextColor.RED;
                                        }),
                                List.of(UiItems.text(tr("settings.visibility.open-hint"), NamedTextColor.GRAY))),
                        (player, current, context) -> {
                            context.visibilityMenuOpener().accept(player);
                            return SettingsMenuEntry.Result.OPENED;
                        })));
        List<SettingsMenuEntry> entries = new ArrayList<>();
        // Enum order is stable; Map.ofEntries has no iteration-order contract.
        for (SettingsMenu.Action action : SettingsMenu.Action.values()) {
            if (!SettingsMenu.FEATURE_ACTIONS.contains(action) || layout.placement(action).isEmpty()) continue;
            Definition feature = definitions.get(action);
            if (feature == null) throw new IllegalArgumentException("Missing Settings definition for " + action);
            entries.add(new SettingsMenuEntry(layout.slot(action), layout.actionSlot(action, 1).orElse(-1),
                    feature.icon(), feature.status(), feature.action()));
        }
        return List.copyOf(entries);
    }

    private static Definition toggle(Material material, Component name, List<Component> description,
                                             Predicate<PlayerSettings> enabled, ToggleSetter setter, boolean refreshPresentation) {
        return new Definition(
                current -> {
                    List<Component> lore = new ArrayList<>();
                    description.forEach(line -> lore.add(UiItems.text(line, NamedTextColor.GRAY)));
                    lore.add(UiItems.text(tr("common.current", Map.of("state", enabled.test(current)
                            ? tr("common.enabled") : tr("common.disabled"))), NamedTextColor.GRAY));
                    lore.add(Component.empty());
                    lore.add(UiItems.text(enabled.test(current) ? tr("common.click-disable") : tr("common.click-enable"), NamedTextColor.YELLOW));
                    return new UiItemSpec(material, UiItems.text(name, NamedTextColor.AQUA), lore);
                }, current -> {
                    boolean on = enabled.test(current);
                    return new UiItemSpec(on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                            UiItems.text(on ? tr("common.enabled") : tr("common.disabled"), on ? NamedTextColor.GREEN : NamedTextColor.RED),
                            List.of(UiItems.text(on ? tr("common.click-disable") : tr("common.click-enable"), NamedTextColor.GRAY)));
                }, (player, current, context) -> {
                    if (!setter.save(context.settings(), player.getUniqueId(), !enabled.test(current))) return SettingsMenuEntry.Result.UNAVAILABLE;
                    if (refreshPresentation) context.presentationRefresh().accept(player);
                    return SettingsMenuEntry.Result.SAVED;
                });
    }
    private record Definition(Function<PlayerSettings, UiItemSpec> icon, Function<PlayerSettings, UiItemSpec> status,
                              SettingsMenuEntry.Action action) { }

    @FunctionalInterface private interface ToggleSetter {
        boolean save(PlayerSettingsService settings, UUID owner, boolean value);
    }
}

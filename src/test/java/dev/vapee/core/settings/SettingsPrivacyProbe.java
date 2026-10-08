package dev.vapee.core.settings;

import dev.vapee.core.player.settings.PlayerSettingsService;
import org.bukkit.event.inventory.ClickType;
import java.util.UUID;

/** Exercises actual rendering/listener actions against the caller's persistence-backed settings. */
public final class SettingsPrivacyProbe {
    private SettingsPrivacyProbe() { }
    public record Result(boolean rendered, boolean iconSaved, boolean statusSaved) { }
    public static Result exercise(PlayerSettingsService settings, UUID owner) throws Exception {
        var fixture = new SettingsMenuFixture();
        var player = fixture.new TestPlayer(owner);
        var menu = SettingsMenuFixture.createMenu(settings, fixture.messages,
                id -> id.equals(owner) ? player.player : null, SettingsMenuFixture::inventory);
        var listener = SettingsMenuFixture.createListener(menu, settings, ignored -> { }, ignored -> { },
                ignored -> { }, fixture.messages, fixture.logger);
        boolean initial = settings.areFriendRequestsEnabled(owner).orElseThrow();
        menu.open(player.player);
        var inventory = player.open;
        int feature = menu.layout().slot(SettingsMenu.Action.FRIEND_REQUESTS);
        int status = menu.layout().slot(SettingsMenu.Action.FRIEND_REQUESTS, 1);
        boolean rendered = SettingsMenuFixture.text(inventory, feature).equals("Friend Requests")
                && SettingsMenuFixture.text(inventory, status).equals(initial ? "Enabled" : "Disabled");
        listener.onInventoryClick(new org.bukkit.event.inventory.InventoryClickEvent(
                SettingsMenuFixture.view(player, inventory), org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                feature, ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        boolean iconSaved = settings.areKnownFriendRequestsEnabled(owner).orElseThrow() != initial
                && SettingsMenuFixture.text(inventory, status).equals(initial ? "Disabled" : "Enabled");
        listener.onInventoryClick(new org.bukkit.event.inventory.InventoryClickEvent(
                SettingsMenuFixture.view(player, inventory), org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                status, ClickType.RIGHT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        boolean statusSaved = settings.areKnownFriendRequestsEnabled(owner).orElseThrow() == initial
                && SettingsMenuFixture.text(inventory, status).equals(initial ? "Enabled" : "Disabled");
        menu.closeOpenInventories();
        return new Result(rendered, iconSaved, statusSaved);
    }
}

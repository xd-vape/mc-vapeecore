package dev.vapee.core.settings.visibility;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public final class VisibilitySettingsMenuHarness {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    public static void main(String[] args) throws Exception {
        VisibilityMenuFixture fixture = new VisibilityMenuFixture();
        var owner = fixture.player("Owner", true);
        var added = fixture.player("Added", false);
        check(fixture.settings.addLobbyVisiblePlayer(owner.id, added.id)
                        == dev.vapee.core.player.settings.AddedVisiblePlayerResult.SUCCESS,
                "fixture adds one visible player");

        fixture.visibilityMenu.open(owner.player);
        Inventory inventory = owner.open;
        VisibilitySettingsHolder holder = VisibilityMenuFixture.visibilityHolder(inventory);
        check(inventory.getSize() == 54 && holder.getOwnerUniqueId().equals(owner.id)
                        && holder.getInventory() == inventory,
                "menu binds exact owner and inventory");
        check(fixture.visibilityMenu.isActive(owner.player, inventory, holder), "opened menu is active");
        check(text(inventory, VisibilitySettingsMenu.MASTER_SLOT).equals("All Players")
                        && lore(inventory, VisibilitySettingsMenu.MASTER_SLOT).contains("Current: ON"),
                "master feature renders enabled state");
        check(material(inventory, VisibilitySettingsMenu.MASTER_STATUS_SLOT)
                        == Material.LIME_STAINED_GLASS_PANE
                        && text(inventory, VisibilitySettingsMenu.MASTER_STATUS_SLOT).equals("Enabled"),
                "master status pane is enabled");
        check(text(inventory, VisibilitySettingsMenu.FRIENDS_SLOT).equals("Friends")
                        && text(inventory, VisibilitySettingsMenu.STAFF_SLOT).equals("Staff Members")
                        && text(inventory, VisibilitySettingsMenu.ADDED_USERS_SLOT).equals("Added Users"),
                "friends, staff and added-user features render");
        check(lore(inventory, VisibilitySettingsMenu.ADDED_USERS_SLOT).contains("Configured users: 1")
                        && lore(inventory, VisibilitySettingsMenu.FRIENDS_SLOT)
                        .contains("Used while All Players is filtered."),
                "filter lore explains count and filtered-mode behavior");
        check(material(inventory, VisibilitySettingsMenu.FRIENDS_STATUS_SLOT)
                        == Material.RED_STAINED_GLASS_PANE,
                "disabled filter uses red status pane");
        check(text(inventory, VisibilitySettingsMenu.GAME_PARTICIPANTS_STATUS_SLOT).equals("Unavailable")
                        && material(inventory, VisibilitySettingsMenu.GAME_PARTICIPANTS_STATUS_SLOT)
                        == Material.GRAY_STAINED_GLASS_PANE,
                "game participants are visibly unavailable");
        check(text(inventory, VisibilitySettingsMenu.MANAGE_PLAYERS_SLOT).equals("Manage Visible Players"),
                "manage visible players button renders");

        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.MASTER_SLOT, ClickType.LEFT);
        check(!fixture.settings.areLobbyPlayersVisible(owner.id).orElseThrow()
                        && fixture.applyCalls == 1 && fixture.hotbarCalls == 1,
                "master toggle saves, applies visibility and refreshes hotbar");
        check(lore(owner.open, VisibilitySettingsMenu.MASTER_SLOT).contains("Current: FILTERED")
                        && text(owner.open, VisibilitySettingsMenu.MASTER_STATUS_SLOT).equals("Filtered"),
                "master filtered state renders accurately");
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.FRIENDS_STATUS_SLOT, ClickType.RIGHT);
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.STAFF_SLOT, ClickType.LEFT);
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.ADDED_USERS_SLOT, ClickType.LEFT);
        check(fixture.settings.areLobbyFriendsVisible(owner.id).orElseThrow()
                        && fixture.settings.areLobbyStaffVisible(owner.id).orElseThrow()
                        && fixture.settings.areLobbyAddedUsersVisible(owner.id).orElseThrow(),
                "friends, staff and added-user toggles save independently");
        check(fixture.applyCalls == 4 && fixture.hotbarCalls == 1,
                "every toggle applies visibility while only master refreshes hotbar");
        int soundsBeforeUnavailable = owner.soundCalls;
        boolean gameBefore = fixture.settings.areLobbyGameParticipantsVisible(owner.id).orElseThrow();
        fixture.visibilityClick(owner, owner.open,
                VisibilitySettingsMenu.GAME_PARTICIPANTS_STATUS_SLOT, ClickType.LEFT);
        check(fixture.settings.areLobbyGameParticipantsVisible(owner.id).orElseThrow() == gameBefore
                        && owner.soundCalls == soundsBeforeUnavailable
                        && plain(owner.received.getLast()).contains("game integration"),
                "unavailable game option does not mutate or play success sound");

        Inventory beforeRefresh = owner.open;
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.REFRESH_SLOT, ClickType.LEFT);
        check(owner.open != beforeRefresh && fixture.applyCalls == 4, "refresh rebuilds without mutation");
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.MANAGE_PLAYERS_SLOT, ClickType.LEFT);
        check(owner.open.getHolder() instanceof VisiblePlayersHolder, "manage button opens visible players menu");
        fixture.visibilityMenu.open(owner.player);
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.BACK_SLOT, ClickType.LEFT);
        check(fixture.settingsBackCalls == 1, "back returns to player settings");
        fixture.visibilityMenu.open(owner.player);
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.CLOSE_SLOT, ClickType.LEFT);
        check(owner.closeCalls == 1, "close button closes menu");

        fixture.visibilityMenu.open(owner.player);
        boolean previous = fixture.settings.areLobbyPlayersVisible(owner.id).orElseThrow();
        int appliesBeforeFailure = fixture.applyCalls;
        int soundsBeforeFailure = owner.soundCalls;
        fixture.repository.failNext = true;
        fixture.visibilityClick(owner, owner.open, VisibilitySettingsMenu.MASTER_SLOT, ClickType.LEFT);
        check(fixture.settings.areLobbyPlayersVisible(owner.id).orElseThrow() == previous
                        && fixture.applyCalls == appliesBeforeFailure
                        && owner.soundCalls == soundsBeforeFailure,
                "save failure rolls back without runtime apply or success sound");
        check(plain(owner.received.getLast()).contains("could not be saved"),
                "save failure gives controlled feedback");
        System.out.println("VisibilitySettingsMenuHarness passed " + checks + " checks.");
    }

    private static Material material(Inventory inventory, int slot) {
        return VisibilityMenuFixture.visibilitySpec(inventory, slot).material();
    }

    private static String text(Inventory inventory, int slot) {
        return plain(VisibilityMenuFixture.visibilitySpec(inventory, slot).name());
    }

    private static String lore(Inventory inventory, int slot) {
        return String.join("\n", VisibilityMenuFixture.visibilitySpec(inventory, slot).lore().stream()
                .map(PLAIN::serialize).toList());
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PLAIN.serialize(component);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

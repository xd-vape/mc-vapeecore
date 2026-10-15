package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.MenuTestLayouts;
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
        check(inventory.getSize() == MenuTestLayouts.VISIBILITY.size() && holder.getOwnerUniqueId().equals(owner.id)
                        && holder.getInventory() == inventory,
                "menu binds exact owner and inventory");
        check(fixture.visibilityMenu.isActive(owner.player, inventory, holder), "opened menu is active");
        check(text(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER)).equals("Player Visibility")
                        && lore(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER)).contains("Current: All Players"),
                "master feature renders enabled state");
        check(material(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1))
                        == Material.LIME_STAINED_GLASS_PANE
                        && text(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1)).equals("All Players")
                        && VisibilityMenuFixture.visibilitySpec(inventory,
                        MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1)).name().color()
                        .equals(net.kyori.adventure.text.format.NamedTextColor.GREEN),
                "master status pane is enabled");
        check(text(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS)).equals("Friends")
                        && text(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF)).equals("Staff Members")
                        && text(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS)).equals("Added Users"),
                "friends, staff and added-user features render");
        check(lore(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS)).contains("Configured users: 1")
                        && lore(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS))
                        .contains("Used while All Players is filtered."),
                "filter lore explains count and filtered-mode behavior");
        check(material(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS, 1))
                        == Material.RED_STAINED_GLASS_PANE,
                "disabled filter uses red status pane");
        check(text(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.GAME_PARTICIPANTS, 1)).equals("Unavailable")
                        && material(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.GAME_PARTICIPANTS, 1))
                        == Material.GRAY_STAINED_GLASS_PANE,
                "game participants are visibly unavailable");
        check(text(inventory, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MANAGE_PLAYERS)).equals("Manage Visible Players"),
                "manage visible players button renders");

        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), ClickType.LEFT);
        check(!fixture.settings.areLobbyPlayersVisible(owner.id).orElseThrow()
                        && fixture.applyCalls == 1 && fixture.hotbarCalls == 1,
                "master toggle saves, applies visibility and refreshes hotbar");
        check(lore(owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER)).contains("Current: Filtered")
                        && text(owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1)).equals("Filtered")
                        && material(owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1))
                        == Material.YELLOW_STAINED_GLASS_PANE
                        && VisibilityMenuFixture.visibilitySpec(owner.open,
                        MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1)).name().color()
                        .equals(net.kyori.adventure.text.format.NamedTextColor.YELLOW),
                "master filtered state renders accurately");
        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS, 1), ClickType.RIGHT);
        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF), ClickType.LEFT);
        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS), ClickType.LEFT);
        check(fixture.settings.areLobbyFriendsVisible(owner.id).orElseThrow()
                        && fixture.settings.areLobbyStaffVisible(owner.id).orElseThrow()
                        && fixture.settings.areLobbyAddedUsersVisible(owner.id).orElseThrow(),
                "friends, staff and added-user toggles save independently");
        check(fixture.applyCalls == 4 && fixture.hotbarCalls == 1,
                "every toggle applies visibility while only master refreshes hotbar");
        for (int feature : new int[]{MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.GAME_PARTICIPANTS)}) {
            check(owner.open.getItem(feature) != null && owner.open.getItem(MenuTestLayouts.VISIBILITY.slot(MenuTestLayouts.VISIBILITY.actionAt(feature).orElseThrow(), 1)) != null,
                    "feature has its semantic status at " + feature);
        }
        for (int status : new int[]{MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS, 1)}) {
            check(material(owner.open, status) == Material.LIME_STAINED_GLASS_PANE
                            && text(owner.open, status).equals("Enabled"), "enabled filter is green at " + status);
        }
        int soundsBeforeUnavailable = owner.soundCalls;
        boolean gameBefore = fixture.settings.areLobbyGameParticipantsVisible(owner.id).orElseThrow();
        fixture.visibilityClick(owner, owner.open,
                MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.GAME_PARTICIPANTS, 1), ClickType.LEFT);
        check(fixture.settings.areLobbyGameParticipantsVisible(owner.id).orElseThrow() == gameBefore
                        && owner.soundCalls == soundsBeforeUnavailable
                        && plain(owner.received.getLast()).contains("game integration"),
                "unavailable game option does not mutate or play success sound");

        Inventory beforeRefresh = owner.open;
        fixture.visibilityMenu.refreshActive(owner.id);
        check(owner.open == beforeRefresh && fixture.applyCalls == 4, "internal rerender preserves inventory without mutation");
        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MANAGE_PLAYERS), ClickType.LEFT);
        check(owner.open.getHolder() instanceof VisiblePlayersHolder, "manage button opens visible players menu");
        fixture.visibilityMenu.open(owner.player);
        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.BACK), ClickType.LEFT);
        check(fixture.settingsBackCalls == 1, "back returns to player settings");
        fixture.visibilityMenu.open(owner.player);
        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.CLOSE), ClickType.LEFT);
        check(owner.closeCalls == 1, "close button closes menu");

        fixture.visibilityMenu.open(owner.player);
        boolean previous = fixture.settings.areLobbyPlayersVisible(owner.id).orElseThrow();
        int appliesBeforeFailure = fixture.applyCalls;
        int soundsBeforeFailure = owner.soundCalls;
        fixture.repository.failNext = true;
        fixture.visibilityClick(owner, owner.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), ClickType.LEFT);
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

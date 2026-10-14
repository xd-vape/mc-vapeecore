package dev.vapee.core.friend.gui;

import dev.vapee.core.i18n.I18nTestSupport;
import java.util.Locale;
import org.bukkit.inventory.Inventory;

public final class FriendLocalizationHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var f = new FriendMenuFixture(FriendMenu.DEFAULT_LAYOUT);
        var german = f.player("Deutsch", true);
        var english = f.player("English", true);
        german.locale = Locale.GERMANY;
        f.menu.startUpdates();
        f.menu.open(german.player);
        f.menu.open(english.player);
        Inventory deInventory = german.open;
        Inventory enInventory = english.open;
        java.util.function.BiFunction<Inventory, Integer, String> text = (inventory, slot) -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(FriendMenuFixture.spec(inventory, slot).spec.name());
        int close = f.menu.layout().slot(FriendMenu.Action.CLOSE);
        check(text.apply(deInventory, close).equals("Schließen"), "German viewer's own close item");
        check(text.apply(enInventory, close).equals("Close"), "simultaneous English viewer");
        check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(f.titles.get(deInventory)).equals(I18nTestSupport.text(german.player, dev.vapee.core.i18n.Translations.tr("friends.title"))), "German inventory title resolves for owner");
        check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(f.titles.get(enInventory)).equals(I18nTestSupport.english("friends.title")), "English inventory title resolves for owner");
        Object holder = deInventory.getHolder();
        f.messages.localization().localeChanged(german.player, Locale.US);
        check(german.open == deInventory && deInventory.getHolder() == holder, "locale event preserves exact inventory and holder binding");
        check(text.apply(deInventory, close).equals("Close"), "locale event immediately rerenders active items");
        check(text.apply(enInventory, close).equals("Close"), "other viewer is unaffected by locale event");
        f.menu.stopUpdates();
        f.messages.localization().localeChanged(german.player, Locale.GERMANY);
        check(text.apply(deInventory, close).equals("Close"), "disabled observer never updates old views");
        var first = f.player(java.util.UUID.randomUUID(), "Identical", true, true);
        var second = f.player(java.util.UUID.randomUUID(), "Identical", true, true);
        check(f.friends.sendRequest(german.id, first.id) == dev.vapee.core.friend.FriendResult.SUCCESS
                && f.friends.acceptRequest(first.id, german.id) == dev.vapee.core.friend.FriendResult.SUCCESS
                && f.friends.sendRequest(german.id, second.id) == dev.vapee.core.friend.FriendResult.SUCCESS
                && f.friends.acceptRequest(second.id, german.id) == dev.vapee.core.friend.FriendResult.SUCCESS, "duplicate-name relationship fixture");
        f.menu.open(german.player);
        Inventory owned = german.open;
        FriendMenuHolder identities = FriendMenuFixture.holder(owned);
        int targetSlot = f.menu.layout().contentSlots().stream().filter(slot -> identities.getTarget(slot).filter(first.id::equals).isPresent()).findFirst().orElseThrow();
        var original = FriendMenuFixture.spec(owned, targetSlot).spec;
        owned.setItem(targetSlot, new FriendMenuFixture.SpecItem(new dev.vapee.core.ui.UiItemSpec(original.material(),
                net.kyori.adventure.text.Component.text("Schließen"), original.lore())));
        f.click(german, owned, targetSlot, org.bukkit.event.inventory.ClickType.SHIFT_RIGHT);
        check(f.friends.getRelation(german.id, first.id) == dev.vapee.core.friend.FriendRelation.NONE
                && f.friends.getRelation(german.id, second.id) == dev.vapee.core.friend.FriendRelation.FRIENDS,
                "UUID binding controls mutation despite duplicated identities and translated display collision");
        System.out.println("FriendLocalizationHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}

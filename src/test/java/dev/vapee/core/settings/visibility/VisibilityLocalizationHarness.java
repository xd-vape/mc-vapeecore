package dev.vapee.core.settings.visibility;

import dev.vapee.core.i18n.I18nTestSupport;
import java.util.Locale;
import org.bukkit.inventory.Inventory;

public final class VisibilityLocalizationHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var f = new VisibilityMenuFixture(VisibilitySettingsMenu.DEFAULT_LAYOUT, VisiblePlayersMenu.DEFAULT_LAYOUT);
        var german = f.player("Deutsch", true);
        var english = f.player("English", true);
        german.locale = Locale.GERMANY;
        f.visibilityMenu.startUpdates();
        f.visibilityMenu.open(german.player);
        f.visibilityMenu.open(english.player);
        Inventory deInventory = german.open;
        Inventory enInventory = english.open;
        java.util.function.BiFunction<Inventory, Integer, String> text = (inventory, slot) -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(VisibilityMenuFixture.visibilitySpec(inventory, slot).name());
        int close = f.visibilityMenu.layout().slot(VisibilitySettingsMenu.Action.CLOSE);
        check(text.apply(deInventory, close).equals("Schließen"), "German viewer's own close item");
        check(text.apply(enInventory, close).equals("Close"), "simultaneous English viewer");
        check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(f.titles.get(deInventory)).equals(I18nTestSupport.text(german.player, dev.vapee.core.i18n.Translations.tr("visibility.title"))), "German inventory title resolves for owner");
        check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(f.titles.get(enInventory)).equals(I18nTestSupport.english("visibility.title")), "English inventory title resolves for owner");
        Object holder = deInventory.getHolder();
        f.messages.localization().localeChanged(german.player, Locale.US);
        check(german.open == deInventory && deInventory.getHolder() == holder, "locale event preserves exact inventory and holder binding");
        check(text.apply(deInventory, close).equals("Close"), "locale event immediately rerenders active items");
        check(text.apply(enInventory, close).equals("Close"), "other viewer is unaffected by locale event");
        f.visibilityMenu.stopUpdates();
        f.messages.localization().localeChanged(german.player, Locale.GERMANY);
        check(text.apply(deInventory, close).equals("Close"), "disabled observer never updates old views");
        System.out.println("VisibilityLocalizationHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}

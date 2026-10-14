package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.i18n.I18nTestSupport;
import java.util.Locale;
import org.bukkit.inventory.Inventory;

public final class QuestLocalizationHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var f = new QuestMenuFixture(DailyQuestMenu.DEFAULT_LAYOUT);
        var german = f.player();
        var english = f.player();
        german.locale = Locale.GERMANY;
        f.domain.define(new dev.vapee.core.quest.QuestDefinition("localized-quest",
                new dev.vapee.core.i18n.LocalizedText(java.util.Map.of("en", "English quest", "de", "Deutsche Quest")),
                new dev.vapee.core.i18n.LocalizedText(java.util.Map.of("en", "<red>Literal English", "de", "<red>Wörtlich Deutsch")),
                dev.vapee.core.quest.QuestProgressKey.of("playtime:minute"), 30, 250));
        f.menu.startUpdates();
        f.menu.open(german.player, 0);
        f.menu.open(english.player, 0);
        Inventory deInventory = german.open;
        Inventory enInventory = english.open;
        java.util.function.BiFunction<Inventory, Integer, String> text = QuestMenuFixture::text;
        int close = f.menu.layout().slot(DailyQuestMenu.Action.CLOSE);
        check(text.apply(deInventory, close).equals("Schließen"), "German viewer's own close item");
        check(text.apply(enInventory, close).equals("Close"), "simultaneous English viewer");
        check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(f.titles.get(deInventory)).equals(I18nTestSupport.text(german.player, dev.vapee.core.i18n.Translations.tr("daily-quests.title"))), "German inventory title resolves for owner");
        check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(f.titles.get(enInventory)).equals(I18nTestSupport.english("daily-quests.title")), "English inventory title resolves for owner");
        int questSlot = DailyQuestMenu.DEFAULT_LAYOUT.slots(1).getFirst();
        check(text.apply(deInventory, questSlot).equals("Deutsche Quest") && text.apply(enInventory, questSlot).equals("English quest"), "localized quest names in simultaneous actual inventories");
        check(QuestMenuFixture.lore(deInventory, questSlot).contains("<red>Wörtlich Deutsch")
                && QuestMenuFixture.lore(enInventory, questSlot).contains("<red>Literal English"), "localized quest description is literal plain text");
        check(((DailyQuestInventoryHolder) deInventory.getHolder()).target(questSlot).orElseThrow().equals("localized-quest")
                && ((DailyQuestInventoryHolder) enInventory.getHolder()).target(questSlot).orElseThrow().equals("localized-quest"), "same technical quest ID independent of language");
        Object holder = deInventory.getHolder();
        f.messages.localization().localeChanged(german.player, Locale.US);
        check(german.open == deInventory && deInventory.getHolder() == holder, "locale event preserves exact inventory and holder binding");
        check(text.apply(deInventory, close).equals("Close"), "locale event immediately rerenders active items");
        check(text.apply(enInventory, close).equals("Close"), "other viewer is unaffected by locale event");
        f.menu.stopUpdates();
        f.messages.localization().localeChanged(german.player, Locale.GERMANY);
        check(text.apply(deInventory, close).equals("Close"), "disabled observer never updates old views");
        System.out.println("QuestLocalizationHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}

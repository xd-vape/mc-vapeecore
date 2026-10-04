package dev.vapee.core.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Spec/text contract without bootstrapping a Paper registry or replacing menu renderer seams. */
public final class UiItemsHarness {
    private static int checks;

    public static void main(String[] args) {
        String literal = "<red>Player & Clan</red>";
        Component name = UiItems.text(literal, NamedTextColor.AQUA);
        check(PlainTextComponentSerializer.plainText().serialize(name).equals(literal), "markup stays literal");
        check(name.color().equals(NamedTextColor.AQUA), "requested color");
        check(name.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE, "explicit nonitalic text");
        check(name.clickEvent() == null && name.hoverEvent() == null && name.children().isEmpty(), "no injected events");
        List<Component> lore = new ArrayList<>(List.of(UiItems.text("First", NamedTextColor.GREEN),
                Component.empty(), UiItems.text("Last", NamedTextColor.YELLOW)));
        UiItemSpec spec = new UiItemSpec(Material.PLAYER_HEAD, name, lore);
        check(spec.material() == Material.PLAYER_HEAD && spec.name().equals(name), "material and name retained");
        check(spec.lore().equals(lore) && spec.lore().get(1).equals(Component.empty()), "lore order and empty line retained");
        lore.clear();
        check(spec.lore().size() == 3, "defensive lore snapshot");
        rejects(UnsupportedOperationException.class, () -> spec.lore().clear(), "immutable lore");
        rejects(NullPointerException.class, () -> new UiItemSpec(null, name, List.of()), "null material");
        rejects(NullPointerException.class, () -> new UiItemSpec(Material.PAPER, null, List.of()), "null name");
        rejects(NullPointerException.class, () -> new UiItemSpec(Material.PAPER, name, null), "null lore");
        rejects(NullPointerException.class, () -> new UiItemSpec(Material.PAPER, name, Arrays.asList((Component) null)), "null lore line");
        UiItemSpec plain = UiItems.literal(Material.BOOK, literal, NamedTextColor.GOLD, List.of("A", "", "<blue>B"));
        check(plain.material() == Material.BOOK && plain.name().equals(UiItems.text(literal, NamedTextColor.GOLD)), "literal factory name/material");
        check(plain.lore().equals(List.of(UiItems.text("A", NamedTextColor.GRAY), UiItems.text("", NamedTextColor.GRAY),
                UiItems.text("<blue>B", NamedTextColor.GRAY))), "literal factory preserves order, blanks, color and decoration");
        check(Arrays.stream(UiItemSpec.class.getRecordComponents()).map(c -> c.getName()).toList()
                .equals(List.of("material", "name", "lore")), "spec has no domain metadata or action authority");
        rejects(NullPointerException.class, () -> UiItems.text(null, NamedTextColor.GRAY), "null text");
        rejects(NullPointerException.class, () -> UiItems.text("A", null), "null color");
        System.out.println("UiItemsHarness passed " + checks + " checks.");
    }

    private static void rejects(Class<? extends RuntimeException> type, Runnable action, String label) {
        try { action.run(); } catch (RuntimeException exception) {
            check(type.isInstance(exception), label); return;
        }
        throw new AssertionError(label);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

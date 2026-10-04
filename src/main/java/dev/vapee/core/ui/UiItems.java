package dev.vapee.core.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/** Stateless literal text and name/lore rendering shared by feature menus. */
public final class UiItems {
    private UiItems() { }

    public static Component text(String value, NamedTextColor color) {
        return Component.text(Objects.requireNonNull(value, "value"), Objects.requireNonNull(color, "color"))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Convenience for menus whose literal lore lines are all gray. */
    public static UiItemSpec literal(Material material, String name, NamedTextColor color, List<String> lore) {
        return new UiItemSpec(material, text(name, color),
                lore.stream().map(line -> text(line, NamedTextColor.GRAY)).toList());
    }

    public static ItemStack render(UiItemSpec spec) {
        ItemStack item = new ItemStack(spec.material());
        var meta = item.getItemMeta();
        meta.displayName(spec.name());
        meta.lore(spec.lore());
        item.setItemMeta(meta);
        return item;
    }
}

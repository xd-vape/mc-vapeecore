package dev.vapee.core.message;

import dev.vapee.core.i18n.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Map;

/** Small semantic palette for core chat feedback; it never changes operator templates or user text. */
public final class MessageStyle {
    public static final NamedTextColor BODY = NamedTextColor.GRAY;
    public static final NamedTextColor HIGHLIGHT = NamedTextColor.AQUA;
    public static final NamedTextColor SUCCESS = NamedTextColor.GREEN;
    public static final NamedTextColor WARNING = NamedTextColor.YELLOW;
    public static final NamedTextColor ERROR = NamedTextColor.RED;
    private MessageStyle() { }

    public static Component value(String literal) { return Component.text(literal, HIGHLIGHT); }
    public static Component activity(Component label, Component body) {
        return Translations.tr("activity.context", Map.of("activity", label, "body", body.colorIfAbsent(BODY)));
    }
}

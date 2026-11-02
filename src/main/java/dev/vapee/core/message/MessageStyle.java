package dev.vapee.core.message;

import dev.vapee.core.i18n.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import java.util.Map;

/** Small semantic palette for core chat feedback; it never changes operator templates or user text. */
public final class MessageStyle {
    public static final NamedTextColor BODY = NamedTextColor.GRAY;
    public static final TextColor DEFAULT_HIGHLIGHT = TextColor.color(0xE5BF75);
    /** Server-wide palette owned by ConfigService's applied snapshot; read when constructing new output. */
    public static volatile TextColor HIGHLIGHT = DEFAULT_HIGHLIGHT;
    public static final NamedTextColor SUCCESS = NamedTextColor.GREEN;
    public static final NamedTextColor WARNING = NamedTextColor.YELLOW;
    public static final NamedTextColor ERROR = NamedTextColor.RED;
    private MessageStyle() { }

    public static void applyAccentColor(TextColor color) { HIGHLIGHT = java.util.Objects.requireNonNull(color); }

    /** Only explicit accent tags use the palette; ordinary colors and rank metadata are never recolored. */
    public static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver accentTag() {
        return net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver("accent", (args, context) ->
                net.kyori.adventure.text.minimessage.tag.Tag.styling(HIGHLIGHT));
    }

    public static Component value(String literal) { return Component.text(literal, HIGHLIGHT); }
    /** Literal command syntax: only the command root is accented, arguments stay neutral. */
    public static Component syntax(String syntax) {
        int split = syntax.indexOf(' ');
        return split < 0 ? value(syntax) : value(syntax.substring(0, split))
                .append(Component.text(syntax.substring(split), BODY));
    }
    public static Component activity(Component label, Component body) {
        return Translations.tr("activity.context", Map.of("activity", label, "body", body.colorIfAbsent(BODY)));
    }
}

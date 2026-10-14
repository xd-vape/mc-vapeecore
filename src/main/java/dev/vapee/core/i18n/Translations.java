package dev.vapee.core.i18n;

import net.kyori.adventure.text.Component;
import java.util.Map;

/** Locale-independent component templates; dynamic arguments remain Components, never markup. */
public final class Translations {
    static final String PREFIX = "vapeecore.i18n.";
    static final String ARGUMENT = "vapeecore.argument.";
    private Translations() { }
    public static Component tr(String key) { return tr(key, Map.of()); }
    public static Component tr(String key, Map<String, Component> values) {
        return Component.translatable(PREFIX + key, values.entrySet().stream()
                .map(entry -> Component.translatable(ARGUMENT + entry.getKey(), entry.getValue())).toList())
                .fallback(Canonical.ENGLISH.getOrDefault(key, "[missing:" + key + "]"));
    }
    /** Explicit catalog reference for operator MiniMessage templates; never a command/action router. */
    public static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver tagResolver() {
        return net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver("i18n", (arguments, context) ->
                net.kyori.adventure.text.minimessage.tag.Tag.inserting(tr(arguments.popOr("Translation key is required").value())));
    }
    private static final class Canonical {
        private static final Map<String, String> ENGLISH = LocalizationService.bundledSnapshot().bundled().get(Language.ENGLISH);
    }
}

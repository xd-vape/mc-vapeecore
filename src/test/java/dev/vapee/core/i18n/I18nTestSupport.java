package dev.vapee.core.i18n;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.logging.Logger;

/** Fixtures exercise the same recipient rendering boundary as MessageService delivery. */
public final class I18nTestSupport {
    private static final LocalizationService CATALOGS = LocalizationService.bundled(Logger.getLogger(I18nTestSupport.class.getName()));
    private I18nTestSupport() { }
    public static Component render(Audience audience, Component template) { return CATALOGS.render(audience, template); }
    public static String english(String key) { return PlainTextComponentSerializer.plainText().serialize(CATALOGS.english(key)); }
    public static String english(Component template) { return text(net.kyori.adventure.audience.Audience.empty(), template); }
    public static String text(Audience audience, Component template) { return PlainTextComponentSerializer.plainText().serialize(render(audience, template)); }
}

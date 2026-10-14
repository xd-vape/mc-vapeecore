package dev.vapee.core.message;

import dev.vapee.core.config.ConfigService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Objects;
import java.util.function.Supplier;

public final class MessageService {

    private final Supplier<String> prefixSupplier;
    private final MiniMessage miniMessage;
    private final dev.vapee.core.i18n.LocalizationService localization;

    public MessageService(ConfigService configService) {
        this(Objects.requireNonNull(configService, "configService")::getMessagePrefix);
    }

    public MessageService(ConfigService configService, dev.vapee.core.i18n.LocalizationService localization) {
        this(Objects.requireNonNull(configService, "configService")::getMessagePrefix, localization);
    }

    MessageService(Supplier<String> prefixSupplier) {
        this(prefixSupplier, dev.vapee.core.i18n.LocalizationService.bundled(java.util.logging.Logger.getLogger(MessageService.class.getName())));
    }

    public MessageService(Supplier<String> prefixSupplier, dev.vapee.core.i18n.LocalizationService localization) {
        this.prefixSupplier = Objects.requireNonNull(prefixSupplier, "prefixSupplier");
        this.miniMessage = MiniMessage.miniMessage();
        this.localization = Objects.requireNonNull(localization);
    }

    public dev.vapee.core.i18n.LocalizationService localization() { return localization; }
    public Component component(Audience audience, String key, TagResolver... placeholders) {
        return localization.component(audience, key, placeholders);
    }
    public Component localize(Audience audience, Component component) { return localization.render(audience, component); }
    public void sendTranslated(Audience audience, String key, TagResolver... placeholders) {
        send(audience, component(audience, key, placeholders));
    }
    /** Channels with an operator-owned complete wrapper (PM/join notices) retain their lack of global prefix. */
    public void sendRaw(Audience audience, Component component) {
        Objects.requireNonNull(audience).sendMessage(localize(audience, Objects.requireNonNull(component)));
    }

    public Component deserialize(String message) {
        return miniMessage.deserialize(Objects.requireNonNull(message, "message"), dev.vapee.core.i18n.Translations.tagResolver());
    }

    public Component deserialize(String message, TagResolver resolver) {
        return miniMessage.deserialize(
                Objects.requireNonNull(message, "message"),
                TagResolver.resolver(Objects.requireNonNull(resolver, "resolver"), dev.vapee.core.i18n.Translations.tagResolver())
        );
    }

    public void send(Audience audience, String message) {
        send(audience, deserialize(Objects.requireNonNull(message, "message")));
    }

    public void send(Audience audience, String message, TagResolver resolver) {
        send(
                audience,
                deserialize(
                        Objects.requireNonNull(message, "message"),
                        Objects.requireNonNull(resolver, "resolver")
                )
        );
    }

    public void send(Audience audience, Component message) {
        Objects.requireNonNull(audience, "audience").sendMessage(
                localize(audience, deserialize(Objects.requireNonNull(prefixSupplier.get(), "messagePrefix")))
                        .append(localize(audience, Objects.requireNonNull(message, "message")))
        );
    }
}

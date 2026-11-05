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

    public Component commandDenied(Audience audience) {
        return commandDenied(audience, "common.command-denied");
    }

    /** Ordinary command denial, retaining an explicitly customized former feature message. */
    public Component commandDenied(Audience audience, String legacyKey) {
        var language = dev.vapee.core.i18n.LanguageResolver.resolve(localization.locale(audience));
        var snapshot = localization.snapshot();
        String live = snapshot.live().getOrDefault(language, java.util.Map.of()).get(legacyKey);
        String bundled = snapshot.bundled().getOrDefault(language, java.util.Map.of()).get(legacyKey);
        // Exact, bounded compatibility for the sole changed canonical default, not a text heuristic.
        boolean copiedGermanDefault = language == dev.vapee.core.i18n.Language.GERMAN
                && legacyKey.equals("common.command-denied")
                && "Du darfst diesen Command nicht verwenden.".equals(live);
        if (live != null && !live.equals(bundled) && !copiedGermanDefault)
            return component(audience, legacyKey).colorIfAbsent(MessageStyle.ERROR);
        if (!legacyKey.equals("common.command-denied")) return commandDenied(audience);
        if (copiedGermanDefault) return deserialize(bundled).colorIfAbsent(MessageStyle.ERROR);
        return localize(audience, MessageStyle.commandDenied());
    }

    /** Keep complete operator-owned legacy usage texts; bundled defaults use the shared composition. */
    public Component usage(Audience audience, String legacyKey, String command, String arguments) {
        var language = dev.vapee.core.i18n.LanguageResolver.resolve(localization.locale(audience));
        var snapshot = localization.snapshot();
        String live = snapshot.live().getOrDefault(language, java.util.Map.of()).get(legacyKey);
        String bundled = snapshot.bundled().getOrDefault(language, java.util.Map.of()).get(legacyKey);
        // Startup copies the bundled catalog to disk too; an unchanged declared default is not custom wording.
        // Step 06D.1 retires only these exact copied staff defaults; deliberate overrides remain complete.
        boolean copiedStaffDefault = legacyKey.equals("staff.usage") && (language == dev.vapee.core.i18n.Language.GERMAN
                ? "Mit /staff öffnest du das Panel, mit /staff list die öffentliche Teamliste."
                : "Use /staff to open the panel or /staff list for the public team list.").equals(live);
        if (live != null && !live.equals(bundled) && !copiedStaffDefault)
            return dev.vapee.core.i18n.Translations.tr(legacyKey).colorIfAbsent(MessageStyle.TEXT);
        return MessageStyle.usage(command, arguments);
    }
    public void sendTranslated(Audience audience, String key, TagResolver... placeholders) {
        send(audience, component(audience, key, placeholders));
    }
    /** Channels with an operator-owned complete wrapper (PM/join notices) retain their lack of global prefix. */
    public void sendRaw(Audience audience, Component component) {
        Objects.requireNonNull(audience).sendMessage(localize(audience, Objects.requireNonNull(component)));
    }

    /** One raw packet with surrounding empty lines and uniform indentation, after localization. */
    public void sendRawBlock(Audience audience, Component... lines) {
        Objects.requireNonNull(audience);
        Objects.requireNonNull(lines);
        Component[] localized = java.util.Arrays.stream(lines)
                .map(line -> localize(audience, Objects.requireNonNull(line))).toArray(Component[]::new);
        audience.sendMessage(MessageStyle.block(localized));
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
                        .append(localize(audience, Objects.requireNonNull(message, "message")).colorIfAbsent(MessageStyle.BODY))
        );
    }
}

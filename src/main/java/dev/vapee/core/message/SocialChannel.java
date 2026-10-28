package dev.vapee.core.message;

import static dev.vapee.core.i18n.Translations.tr;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** One localized channel heading per notification; MessageService still owns the global prefix. */
public enum SocialChannel {
    FRIENDS("friends.prefix", NamedTextColor.GOLD),
    CLAN("clan.prefix", NamedTextColor.DARK_PURPLE);

    private final String prefixKey;
    private final NamedTextColor color;

    SocialChannel(String prefixKey, NamedTextColor color) {
        this.prefixKey = prefixKey;
        this.color = color;
    }

    public Component message(Component body) {
        return Component.empty().color(MessageStyle.BODY)
                .append(tr(prefixKey).color(color)).append(Component.space())
                .append(body.colorIfAbsent(MessageStyle.BODY));
    }

    public void send(MessageService messages, Audience recipient, Component body) {
        messages.send(recipient, message(body));
    }

    public void error(MessageService messages, Audience recipient, Component body) {
        send(messages, recipient, tr("common.error-label").color(MessageStyle.ERROR)
                .append(body.color(MessageStyle.BODY)));
    }
}

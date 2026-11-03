package dev.vapee.core.message;

import static dev.vapee.core.i18n.Translations.tr;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

/** Existing social wrappers keep operator prefix behavior; Staff owns its single raw heading. */
public enum SocialChannel {
    FRIENDS("friends.prefix"),
    CLAN("clan.prefix"),
    STAFF("staff.prefix");

    private final String prefixKey;

    SocialChannel(String prefixKey) {
        this.prefixKey = prefixKey;
    }

    public Component message(Component body) {
        return MessageStyle.message(tr(prefixKey), body);
    }

    public void send(MessageService messages, Audience recipient, Component body) {
        if (this == STAFF) messages.sendRaw(recipient, message(body));
        else messages.send(recipient, message(body));
    }

    public void error(MessageService messages, Audience recipient, Component body) {
        send(messages, recipient, tr("common.error-label").color(MessageStyle.ERROR)
                .append(body.color(MessageStyle.BODY)));
    }
}

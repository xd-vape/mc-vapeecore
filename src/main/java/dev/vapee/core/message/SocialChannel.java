package dev.vapee.core.message;

import static dev.vapee.core.i18n.Translations.tr;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Existing social wrappers keep operator prefix behavior; Staff owns its single raw heading. */
public enum SocialChannel {
    FRIENDS("friends.prefix", MessageStyle.FRIENDS_LABEL),
    CLAN("clan.prefix", MessageStyle.CLAN_LABEL),
    STAFF("staff.prefix", MessageStyle.STAFF_LABEL);

    private final String prefixKey;
    private final TextColor labelColor;

    SocialChannel(String prefixKey, TextColor labelColor) {
        this.prefixKey = prefixKey;
        this.labelColor = labelColor;
    }

    public Component message(Component body) {
        return MessageStyle.message(tr(prefixKey), labelColor, body);
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

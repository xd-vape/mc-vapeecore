package dev.vapee.core.presence;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.friend.FriendService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.social.SocialService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** A single private join line, using the existing presence state and no message wrapper. */
public final class FriendJoinSummary {
    private final FriendService friends;
    private final PresenceService presence;
    private final SocialService social;
    private final MessageService messages;
    private final PlayerSettingsService settings;

    public FriendJoinSummary(FriendService friends, PresenceService presence,
                             SocialService social, MessageService messages, PlayerSettingsService settings) {
        this.friends = Objects.requireNonNull(friends, "friends");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.social = Objects.requireNonNull(social, "social");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void send(Player recipient) {
        UUID self = Objects.requireNonNull(recipient, "recipient").getUniqueId();
        if (!settings.areFriendPresenceNotificationsEnabled(self).orElse(false)) return;
        var ids = friends.getFriends(self);
        long online = ids.stream().filter(presence::isOnline)
                .filter(id -> !social.isKnownIgnoring(self, id) && !social.isKnownIgnoring(id, self))
                .count();
        Component body;
        if (online == 0) {
            body = tr("friends.join-summary.none");
        } else {
            Map<String, Component> counts = Map.of("online", Component.text(online, NamedTextColor.GREEN),
                    "total", Component.text(ids.size(), NamedTextColor.WHITE));
            body = online == 1 ? tr("friends.join-summary.one", counts)
                    : tr("friends.join-summary.many", counts);
        }
        messages.sendRaw(recipient, body.color(NamedTextColor.GRAY));
    }
}

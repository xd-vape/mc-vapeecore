package dev.vapee.core.presence;

import dev.vapee.core.friend.FriendService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.SocialService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FriendPresenceNotifier {
    private final FriendLookup friendLookup;
    private final Function<UUID, Player> onlinePlayer;
    private final NotificationPreference notificationPreference;
    private final BiPredicate<UUID, UUID> ignoreLookup;
    private final MessageSender messageSender;
    private final Logger logger;

    public FriendPresenceNotifier(
            FriendService friendService,
            PlayerSettingsService playerSettingsService,
            SocialService socialService,
            Function<UUID, Player> onlinePlayer,
            MessageService messageService,
            Logger logger
    ) {
        this(
                Objects.requireNonNull(friendService, "friendService")::getFriends,
                onlinePlayer,
                Objects.requireNonNull(playerSettingsService,
                        "playerSettingsService")::areFriendPresenceNotificationsEnabled,
                Objects.requireNonNull(socialService, "socialService")::isKnownIgnoring,
                Objects.requireNonNull(messageService, "messageService")::send,
                logger
        );
    }

    FriendPresenceNotifier(
            FriendLookup friendLookup,
            Function<UUID, Player> onlinePlayer,
            NotificationPreference notificationPreference,
            BiPredicate<UUID, UUID> ignoreLookup,
            MessageSender messageSender,
            Logger logger
    ) {
        this.friendLookup = Objects.requireNonNull(friendLookup, "friendLookup");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.notificationPreference = Objects.requireNonNull(notificationPreference, "notificationPreference");
        this.ignoreLookup = Objects.requireNonNull(ignoreLookup, "ignoreLookup");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void notifyStatus(UUID subject, String subjectName, PresenceStatus status) {
        UUID validatedSubject = Objects.requireNonNull(subject, "subject");
        String validatedName = Objects.requireNonNull(subjectName, "subjectName");
        PresenceStatus validatedStatus = Objects.requireNonNull(status, "status");
        List<UUID> friends;
        try {
            friends = Objects.requireNonNull(friendLookup.getFriends(validatedSubject), "friends");
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "Could not resolve friends for presence subject " + validatedSubject + ".", exception);
            return;
        }

        Component message = createMessage(validatedName, validatedStatus);
        for (UUID recipientId : friends) {
            try {
                UUID validatedRecipient = Objects.requireNonNull(recipientId, "recipientId");
                Player recipient = onlinePlayer.apply(validatedRecipient);
                if (recipient == null || !recipient.isOnline()) continue;
                Optional<Boolean> enabled = notificationPreference.isEnabled(validatedRecipient);
                if (enabled.isEmpty() || !enabled.get()) continue;
                if (ignoreLookup.test(validatedRecipient, validatedSubject)
                        || ignoreLookup.test(validatedSubject, validatedRecipient)) continue;
                messageSender.send(recipient, message);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING,
                        "Could not deliver presence notification for subject " + validatedSubject
                                + " to recipient " + recipientId + "; continuing.",
                        exception);
            }
        }
    }

    private static Component createMessage(String subjectName, PresenceStatus status) {
        Component prefix = Component.text("Friend ", NamedTextColor.GRAY)
                .append(Component.text(subjectName, NamedTextColor.AQUA));
        return switch (status) {
            case ONLINE -> prefix.append(Component.text(" is now online.", NamedTextColor.GREEN));
            case OFFLINE -> prefix.append(Component.text(" went offline.", NamedTextColor.GRAY));
        };
    }

    @FunctionalInterface
    interface FriendLookup {
        List<UUID> getFriends(UUID subject);
    }

    @FunctionalInterface
    interface NotificationPreference {
        Optional<Boolean> isEnabled(UUID recipient);
    }

    @FunctionalInterface
    interface MessageSender {
        void send(Player recipient, Component message);
    }
}

package dev.vapee.core.onlinereward;

import dev.vapee.core.message.MessageService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

final class OnlineRewardMessageRenderer {

    private final NotificationSender sender;
    private final Logger logger;

    OnlineRewardMessageRenderer(MessageService messageService, Logger logger) {
        this(
                Objects.requireNonNull(messageService, "messageService")::send,
                logger
        );
    }

    OnlineRewardMessageRenderer(NotificationSender sender, Logger logger) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    boolean notifyReward(
            Audience audience,
            OnlineRewardConfig config,
            OnlineRewardProcessResult result
    ) {
        Objects.requireNonNull(audience, "audience");
        OnlineRewardConfig validatedConfig = Objects.requireNonNull(config, "config");
        OnlineRewardProcessResult validatedResult = Objects.requireNonNull(result, "result");
        if (!validatedConfig.messageEnabled()
                || validatedResult.status() != OnlineRewardProcessStatus.REWARDED) {
            return false;
        }

        try {
            sender.send(
                    audience,
                    validatedConfig.messageFormat(),
                    placeholders(validatedResult)
            );
            return true;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Online reward was granted, but its player notification could not be sent.",
                    exception
            );
            return false;
        }
    }

    static TagResolver placeholders(OnlineRewardProcessResult result) {
        OnlineRewardProcessResult validatedResult = Objects.requireNonNull(result, "result");
        if (validatedResult.status() != OnlineRewardProcessStatus.REWARDED) {
            throw new IllegalArgumentException("Message placeholders require a rewarded result");
        }
        return TagResolver.resolver(
                Placeholder.unparsed("coins", Long.toString(validatedResult.coinsGranted())),
                Placeholder.unparsed("minutes", Long.toString(validatedResult.rewardedMinutes())),
                Placeholder.unparsed("intervals", Long.toString(validatedResult.intervals())),
                Placeholder.unparsed(
                        "balance",
                        Long.toString(validatedResult.resultingBalance().orElseThrow())
                )
        );
    }

    @FunctionalInterface
    interface NotificationSender {

        void send(Audience audience, String format, TagResolver resolver);
    }
}

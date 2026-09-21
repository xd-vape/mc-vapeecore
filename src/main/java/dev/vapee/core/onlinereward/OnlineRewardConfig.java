package dev.vapee.core.onlinereward;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Objects;

public record OnlineRewardConfig(
        boolean enabled,
        long intervalMinutes,
        long coins,
        boolean messageEnabled,
        String messageFormat
) {

    public static final boolean DEFAULT_ENABLED = true;
    public static final long DEFAULT_INTERVAL_MINUTES = 60L;
    public static final long DEFAULT_COINS = 250L;
    public static final boolean DEFAULT_MESSAGE_ENABLED = true;
    public static final String DEFAULT_MESSAGE_FORMAT = "<green>You received <gold><coins> Coins</gold> "
            + "for <yellow><minutes> minutes</yellow> of playtime.</green>";
    public static final long TICKS_PER_MINUTE = 20L * 60L;

    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private static final TagResolver VALIDATION_PLACEHOLDERS = TagResolver.resolver(
            Placeholder.unparsed("coins", "0"),
            Placeholder.unparsed("minutes", "0"),
            Placeholder.unparsed("intervals", "0"),
            Placeholder.unparsed("balance", "0")
    );

    public OnlineRewardConfig {
        if (intervalMinutes <= 0L) {
            throw new IllegalArgumentException("intervalMinutes must be positive");
        }
        if (coins <= 0L) {
            throw new IllegalArgumentException("coins must be positive");
        }
        Math.multiplyExact(intervalMinutes, TICKS_PER_MINUTE);
        messageFormat = Objects.requireNonNull(messageFormat, "messageFormat");
        if (messageFormat.isBlank()) {
            throw new IllegalArgumentException("messageFormat must not be blank");
        }
    }

    public long intervalTicks() {
        return Math.multiplyExact(intervalMinutes, TICKS_PER_MINUTE);
    }

    public static OnlineRewardConfig defaults() {
        return new OnlineRewardConfig(
                DEFAULT_ENABLED,
                DEFAULT_INTERVAL_MINUTES,
                DEFAULT_COINS,
                DEFAULT_MESSAGE_ENABLED,
                DEFAULT_MESSAGE_FORMAT
        );
    }

    public static boolean isValidMessageFormat(String format) {
        try {
            STRICT_MINI_MESSAGE.deserialize(
                    Objects.requireNonNull(format, "format"),
                    VALIDATION_PLACEHOLDERS
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

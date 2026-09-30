package dev.vapee.core.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/** All dynamic text is literal, including names, reasons and full record IDs. */
public final class ModerationComponents {
    private static final DateTimeFormatter UTC = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private ModerationComponents() { }

    public static String time(Instant instant) {
        try { return UTC.format(instant); }
        catch (DateTimeException exception) { return instant.toString() + " UTC"; }
    }

    public static Component banScreen(ModerationRecord record) {
        return Component.text("You are banned from this server.", NamedTextColor.RED)
                .append(Component.newline()).append(Component.text("Reason: " + record.reason(), NamedTextColor.WHITE))
                .append(Component.newline()).append(Component.text("Expires: "
                        + record.expiresAt().map(ModerationComponents::time).orElse("Permanent"), NamedTextColor.GRAY));
    }

    public static Component kickScreen(ModerationRecord record) {
        return Component.text("You were kicked from the server.", NamedTextColor.RED)
                .append(Component.newline()).append(Component.text("Reason: " + record.reason(), NamedTextColor.WHITE));
    }

    public static String status(ModerationRecord record, Instant now) {
        if (!record.action().supportsActiveState()) return "Recorded";
        if (record.revocation().isPresent()) return "Revoked";
        if (record.isActiveAt(now)) return "Active";
        if (record.expiresAt().isPresent() && !now.isBefore(record.expiresAt().get())) return "Expired";
        return "Inactive";
    }

    public static Component historyEntry(ModerationRecord record, Instant now,
                                         Function<ModerationActor, String> actorName) {
        Component result = Component.text(record.action().name() + " • " + time(record.createdAt())
                        + " • " + status(record, now), NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Record ID: " + record.id())))
                .append(Component.newline()).append(Component.text("Actor: " + actorName.apply(record.actor()), NamedTextColor.GRAY))
                .append(Component.newline()).append(Component.text("Reason: " + record.reason(), NamedTextColor.WHITE));
        if (record.action().supportsActiveState()) result = result.append(Component.newline())
                .append(Component.text("Expires: " + record.expiresAt().map(ModerationComponents::time)
                        .orElse("Permanent"), NamedTextColor.GRAY));
        if (record.revocation().isPresent()) {
            ModerationRevocation revoke = record.revocation().get();
            result = result.append(Component.newline()).append(Component.text("Revoked by: "
                    + actorName.apply(revoke.actor()) + " • " + time(revoke.revokedAt()), NamedTextColor.GRAY));
            if (revoke.reason().isPresent()) result = result.append(Component.newline())
                    .append(Component.text("Revocation reason: " + revoke.reason().get(), NamedTextColor.WHITE));
        }
        return result;
    }
}

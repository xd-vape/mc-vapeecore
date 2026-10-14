package dev.vapee.core.moderation;

import static dev.vapee.core.i18n.Translations.tr;

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
        return tr("moderation.ban.screen").color(NamedTextColor.RED).append(Component.newline())
                .append(reason(record.reason())).append(Component.newline()).append(expiry(record));
    }
    public static Component kickScreen(ModerationRecord record) {
        return tr("moderation.kick.screen").color(NamedTextColor.RED).append(Component.newline()).append(reason(record.reason()));
    }
    public static Component muteNotice(ModerationRecord record) {
        return tr("moderation.mute.screen").color(NamedTextColor.RED).append(Component.newline())
                .append(reason(record.reason())).append(Component.newline()).append(expiry(record));
    }
    public static Component unmuteNotice(ModerationRecord record) {
        Component result = tr("moderation.unmute.screen").color(NamedTextColor.GREEN);
        var reason = record.revocation().flatMap(ModerationRevocation::reason);
        return reason.isEmpty() ? result : result.append(Component.newline()).append(reason(reason.get()));
    }
    private static Component reason(String reason) {
        return tr("moderation.reason", java.util.Map.of("reason", Component.text(reason))).color(NamedTextColor.WHITE);
    }
    private static Component expiry(ModerationRecord record) {
        Component expiry = record.expiresAt().map(value -> (Component) Component.text(time(value))).orElseGet(() -> tr("moderation.permanent"));
        return tr("moderation.expires", java.util.Map.of("expiry", expiry)).color(NamedTextColor.GRAY);
    }

    public static String status(ModerationRecord record, Instant now) {
        if (!record.action().supportsActiveState()) return "Recorded";
        if (record.revocation().isPresent()) return "Revoked";
        if (record.isActiveAt(now)) return "Active";
        if (record.expiresAt().isPresent() && !now.isBefore(record.expiresAt().get())) return "Expired";
        return "Inactive";
    }

    public static Component historyEntry(ModerationRecord record, Instant now, Function<ModerationActor, String> actorName) {
        Component status = tr("moderation.status." + status(record, now).toLowerCase(Locale.ROOT));
        Component result = tr("moderation.action." + record.action().name()).append(Component.text(" • " + time(record.createdAt()) + " • "))
                .append(status).color(NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(tr("moderation.record-id", java.util.Map.of("id", Component.text(record.id().toString())))))
                .append(Component.newline()).append(tr("moderation.actor", java.util.Map.of("actor", actor(record.actor(), actorName))).color(NamedTextColor.GRAY))
                .append(Component.newline()).append(reason(record.reason()));
        if (record.action().supportsActiveState()) result = result.append(Component.newline()).append(expiry(record));
        if (record.revocation().isPresent()) {
            ModerationRevocation revoke = record.revocation().get();
            result = result.append(Component.newline()).append(tr("moderation.revoked", java.util.Map.of("actor", actor(revoke.actor(), actorName),
                    "time", Component.text(time(revoke.revokedAt())))).color(NamedTextColor.GRAY));
            if (revoke.reason().isPresent()) result = result.append(Component.newline()).append(tr("moderation.revocation-reason",
                    java.util.Map.of("reason", Component.text(revoke.reason().get()))).color(NamedTextColor.WHITE));
        }
        return result;
    }
    private static Component actor(ModerationActor actor, Function<ModerationActor, String> name) {
        return actor.playerId().isEmpty() ? tr("moderation.console") : Component.text(name.apply(actor));
    }
}

package dev.vapee.core.moderation.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;
import net.kyori.adventure.text.Component;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BanCommand extends AbstractModerationCommand {
    private static final List<String> DURATIONS = List.of("10m", "30m", "1h", "6h", "12h", "1d", "3d", "7d", "30d", "permanent");
    public BanCommand(ModerationCommandContext context) {
        super(context, "ban", "<player|uuid> <duration|permanent> <reason...>");
    }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 3 && !reason(args, 2).isBlank()) return true;
        usage(sender, args.length == 0 ? tr("moderation.missing-all")
                : args.length == 1 ? tr("moderation.missing-duration-reason") : tr("moderation.missing-reason"));
        return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        ModerationDurationParser.ParsedDuration duration;
        Optional<Instant> expiry;
        try {
            duration = ModerationDurationParser.parse(args[1]);
            expiry = duration.expiresAt(context.clock().instant());
        } catch (IllegalArgumentException exception) {
            context.send(sender, tr("moderation.duration-invalid"));
            return;
        }
        ModerationRecord record = mutate(sender, actor, target,
                () -> context.service().issueBan(target.uniqueId(), actor, reason(args, 2), expiry));
        if (record == null) return;
        context.send(sender, tr(expiry.isEmpty() ? "moderation.ban.saved-permanent" : "moderation.ban.saved-duration", java.util.Map.of("player", Component.text(target.name(), MessageStyle.HIGHLIGHT), "duration", Component.text(duration.display(), MessageStyle.HIGHLIGHT), "reason", Component.text(record.reason()))));
        context.disconnect(sender, actor, target, record, true);
    }

    @Override protected List<String> complete(String[] args) {
        return args.length == 2 ? DURATIONS.stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList()
                : List.of();
    }
}

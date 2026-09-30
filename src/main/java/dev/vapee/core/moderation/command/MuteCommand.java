package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MuteCommand extends AbstractModerationCommand {
    private static final List<String> DURATIONS = List.of("10m", "30m", "1h", "6h", "12h", "1d", "3d", "7d", "30d", "permanent");
    public MuteCommand(ModerationCommandContext context) {
        super(context, "mute", "<player|uuid> <duration|permanent> <reason...>");
    }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 3 && !reason(args, 2).isBlank()) return true;
        usage(sender, args.length == 0 ? "Missing player, duration and reason."
                : args.length == 1 ? "Missing duration and reason." : "Missing reason.");
        return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        ModerationDurationParser.ParsedDuration duration;
        Optional<Instant> expiry;
        try {
            duration = ModerationDurationParser.parse(args[1]);
            expiry = duration.expiresAt(context.clock().instant());
        } catch (IllegalArgumentException exception) {
            context.send(sender, "Invalid duration. Use a positive whole number with s, m, h, d or w, or permanent.");
            return;
        }
        ModerationRecord record = mutate(sender, actor, target,
                () -> context.service().issueMute(target.uniqueId(), actor, reason(args, 2), expiry));
        if (record == null) return;
        context.send(sender, "Muted " + target.name() + (expiry.isEmpty() ? " permanently."
                : " for " + duration.display() + ".") + "\nReason: " + record.reason());
        context.notifyMute(sender, actor, target, record, false);
    }

    @Override protected List<String> complete(String[] args) {
        return args.length == 2 ? DURATIONS.stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList()
                : List.of();
    }
}

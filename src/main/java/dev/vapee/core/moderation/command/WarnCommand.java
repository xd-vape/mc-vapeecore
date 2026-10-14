package dev.vapee.core.moderation.command;

import static dev.vapee.core.i18n.Translations.tr;
import net.kyori.adventure.text.Component;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WarnCommand extends AbstractModerationCommand {
    public WarnCommand(ModerationCommandContext context) { super(context, "warn", "<player|uuid> <reason...>"); }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 2 && !reason(args, 1).isBlank()) return true;
        usage(sender, args.length == 0 ? tr("moderation.missing-player-reason") : tr("moderation.missing-reason"));
        return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        ModerationRecord record = mutate(sender, actor, target,
                () -> context.service().issueWarning(target.uniqueId(), actor, reason(args, 1)));
        if (record == null) return;
        context.send(sender, tr("moderation.warn.saved", java.util.Map.of("player", Component.text(target.name()), "reason", Component.text(record.reason()))));
        try {
            Player online = context.online(target.uniqueId());
            if (online != null) context.send(online, tr("moderation.warn.received", java.util.Map.of("reason", Component.text(record.reason()))));
        } catch (RuntimeException exception) {
            context.failure("WARNING_NOTIFICATION", actor, target.uniqueId().toString(), exception);
            context.send(sender, tr("moderation.warn-notify-failed"));
        }
    }
}

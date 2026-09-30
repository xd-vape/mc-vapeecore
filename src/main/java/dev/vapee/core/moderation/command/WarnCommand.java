package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WarnCommand extends AbstractModerationCommand {
    public WarnCommand(ModerationCommandContext context) { super(context, "warn", "<player|uuid> <reason...>"); }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 2 && !reason(args, 1).isBlank()) return true;
        usage(sender, args.length == 0 ? "Missing player and reason." : "Missing reason.");
        return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        ModerationRecord record = mutate(sender, actor, target,
                () -> context.service().issueWarning(target.uniqueId(), actor, reason(args, 1)));
        if (record == null) return;
        context.send(sender, "Warned " + target.name() + ".\nReason: " + record.reason());
        try {
            Player online = context.online(target.uniqueId());
            if (online != null) context.send(online, "You received a warning.\nReason: " + record.reason());
        } catch (RuntimeException exception) {
            context.failure("WARNING_NOTIFICATION", actor, target.uniqueId().toString(), exception);
            context.send(sender, "Warning was saved, but the online player could not be notified.");
        }
    }
}

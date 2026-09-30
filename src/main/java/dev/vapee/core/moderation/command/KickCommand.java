package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;

public final class KickCommand extends AbstractModerationCommand {
    public KickCommand(ModerationCommandContext context) { super(context, "kick", "<player|uuid> <reason...>"); }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 2 && !reason(args, 1).isBlank()) return true;
        usage(sender, args.length == 0 ? "Missing player and reason." : "Missing reason."); return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        if (context.online(target.uniqueId()) == null) {
            context.send(sender, "That player is known, but is not currently online."); return;
        }
        ModerationRecord record = mutate(sender, actor, target,
                () -> context.service().recordKick(target.uniqueId(), actor, reason(args, 1)));
        if (record == null) return;
        context.send(sender, "Kicked " + target.name() + ".\nReason: " + record.reason());
        context.disconnect(sender, actor, target, record, false);
    }
}

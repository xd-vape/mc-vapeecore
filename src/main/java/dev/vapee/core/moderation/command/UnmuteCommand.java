package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public final class UnmuteCommand extends AbstractModerationCommand {
    public UnmuteCommand(ModerationCommandContext context) { super(context, "unmute", "<player|uuid> [reason...]"); }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 1) return true;
        usage(sender, "Missing player."); return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        String reason = reason(args, 1);
        ModerationRecord record = mutate(sender, actor, target, () -> context.service().revokeMute(target.uniqueId(), actor,
                reason.isBlank() ? Optional.empty() : Optional.of(reason)));
        if (record == null) return;
        context.send(sender, "Unmuted " + target.name() + ".");
        context.notifyMute(sender, actor, target, record, true);
    }
}

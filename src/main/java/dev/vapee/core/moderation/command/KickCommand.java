package dev.vapee.core.moderation.command;

import static dev.vapee.core.i18n.Translations.tr;
import net.kyori.adventure.text.Component;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;

public final class KickCommand extends AbstractModerationCommand {
    public KickCommand(ModerationCommandContext context) { super(context, "kick", "<player|uuid> <reason...>"); }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 2 && !reason(args, 1).isBlank()) return true;
        usage(sender, args.length == 0 ? tr("moderation.missing-player-reason") : tr("moderation.missing-reason")); return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        if (context.online(target.uniqueId()) == null) {
            context.send(sender, tr("moderation.target-offline")); return;
        }
        ModerationRecord record = mutate(sender, actor, target,
                () -> context.service().recordKick(target.uniqueId(), actor, reason(args, 1)));
        if (record == null) return;
        context.send(sender, tr("moderation.kick.saved", java.util.Map.of("player", Component.text(target.name()), "reason", Component.text(record.reason()))));
        context.disconnect(sender, actor, target, record, false);
    }
}

package dev.vapee.core.moderation.command;

import static dev.vapee.core.i18n.Translations.tr;
import net.kyori.adventure.text.Component;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public final class UnmuteCommand extends AbstractModerationCommand {
    public UnmuteCommand(ModerationCommandContext context) { super(context, "unmute", "<player|uuid> [reason...]"); }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length >= 1) return true;
        usage(sender, tr("common.missing-player")); return false;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        String reason = reason(args, 1);
        ModerationRecord record = mutate(sender, actor, target, () -> context.service().revokeMute(target.uniqueId(), actor,
                reason.isBlank() ? Optional.empty() : Optional.of(reason)));
        if (record == null) return;
        context.send(sender, tr("moderation.unmute.saved", java.util.Map.of("player", Component.text(target.name()))));
        context.notifyMute(sender, actor, target, record, true);
    }
}

package dev.vapee.core.privatemessage.command;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.privatemessage.PrivateMessageResult;
import dev.vapee.core.privatemessage.PrivateMessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReplyCommand implements TabExecutor {

    private final PrivateMessageService privateMessageService;
    private final MessageService messageService;
    private final Logger logger;

    public ReplyCommand(
            PrivateMessageService privateMessageService,
            MessageService messageService,
            Logger logger
    ) {
        this.privateMessageService = Objects.requireNonNull(privateMessageService, "privateMessageService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, tr("private-messages.error.player-only"));
            return true;
        }
        if (!sender.hasPermission(MessageCommand.PERMISSION)) {
            messageService.send(sender, tr("private-messages.error.denied"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String rawMessage = String.join(" ", args);
        if (rawMessage.isBlank()) {
            sendUsage(player);
            return true;
        }

        try {
            sendResult(player, privateMessageService.reply(player, rawMessage));
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE,
                    "Could not process a private-message reply for " + player.getUniqueId() + ".",
                    exception
            );
            messageService.send(player, tr("private-messages.error.send-failed"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        return List.of();
    }

    private void sendUsage(Player player) {
        messageService.send(player, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/reply <message>", NamedTextColor.AQUA)));
    }

    private void sendResult(Player player, PrivateMessageResult result) {
        switch (result) {
            case SUCCESS -> {
            }
            case FEATURE_DISABLED -> messageService.send(player, tr("private-messages.error.disabled"));
            case SENDER_NOT_LOADED -> messageService.send(player, tr("private-messages.error.profile-unavailable"));
            case SENDER_MUTED -> messageService.send(player, tr("private-messages.error.muted"));
            case RECIPIENT_NOT_LOADED -> messageService.send(player, tr("private-messages.error.recipient-unavailable"));
            case RECIPIENT_DISABLED -> messageService.send(player, tr("private-messages.error.recipient-disabled"));
            case SENDER_IGNORES_RECIPIENT -> messageService.send(player, tr("private-messages.error.ignored"));
            case RECIPIENT_IGNORES_SENDER -> messageService.send(player, tr("private-messages.error.recipient-disabled"));
            case TARGET_OFFLINE -> messageService.send(player, tr("private-messages.error.reply-offline"));
            case CANNOT_MESSAGE_SELF -> messageService.send(player, tr("private-messages.error.self"));
            case NO_REPLY_TARGET -> messageService.send(player, tr("private-messages.error.no-reply"));
        }
    }
}

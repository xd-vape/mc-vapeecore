package dev.vapee.core.privatemessage.command;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.privatemessage.PrivateMessageResult;
import dev.vapee.core.privatemessage.PrivateMessageService;
import dev.vapee.core.utility.OnlinePlayerResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MessageCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.message.use";

    private final Server server;
    private final PrivateMessageService privateMessageService;
    private final MessageService messageService;
    private final Logger logger;

    public MessageCommand(
            Server server,
            PrivateMessageService privateMessageService,
            MessageService messageService,
            Logger logger
    ) {
        this.server = Objects.requireNonNull(server, "server");
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
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, tr("private-messages.error.denied"));
            return true;
        }
        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        String rawMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (rawMessage.isBlank()) {
            sendUsage(player);
            return true;
        }
        if (!privateMessageService.isEnabled()) {
            messageService.send(player, tr("private-messages.error.disabled"));
            return true;
        }

        Player recipient = new OnlinePlayerResolver(server::getOnlinePlayers).resolveExact(args[0]);
        if (recipient == null) {
            messageService.send(player, tr("private-messages.error.offline"));
            return true;
        }

        try {
            sendResult(player, privateMessageService.send(player, recipient, rawMessage));
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE,
                    "Could not process a private-message command for " + player.getUniqueId() + ".",
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
        if (!(sender instanceof Player player) || !sender.hasPermission(PERMISSION) || args.length != 1) {
            return List.of();
        }
        return new OnlinePlayerResolver(server::getOnlinePlayers).suggest(args[0], player.getUniqueId());
    }

    private void sendUsage(Player player) {
        messageService.send(player, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/msg <player> <message>", NamedTextColor.AQUA)));
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
            case TARGET_OFFLINE -> messageService.send(player, tr("private-messages.error.target-offline"));
            case CANNOT_MESSAGE_SELF -> messageService.send(player, tr("private-messages.error.self"));
            case NO_REPLY_TARGET -> messageService.send(player, tr("private-messages.error.no-reply"));
        }
    }
}

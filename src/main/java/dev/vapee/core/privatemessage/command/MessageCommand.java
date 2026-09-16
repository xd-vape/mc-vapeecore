package dev.vapee.core.privatemessage.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.privatemessage.PrivateMessageResult;
import dev.vapee.core.privatemessage.PrivateMessageService;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MessageCommand implements CommandExecutor {

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
            messageService.send(sender, "<red>Only players can use private messages.</red>");
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
            messageService.send(player, "<red>Private messages are currently disabled.</red>");
            return true;
        }

        Player recipient = server.getPlayerExact(args[0]);
        if (recipient == null) {
            messageService.send(player, "<red>Player is not online.</red>");
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
            messageService.send(player, "<red>The private message could not be sent. Check the server log.</red>");
        }
        return true;
    }

    private void sendUsage(Player player) {
        messageService.send(player, "<yellow>Usage:</yellow> <white>/msg <player> <message></white>");
    }

    private void sendResult(Player player, PrivateMessageResult result) {
        switch (result) {
            case SUCCESS -> {
            }
            case FEATURE_DISABLED -> messageService.send(player, "<red>Private messages are currently disabled.</red>");
            case SENDER_NOT_LOADED -> messageService.send(player, "<red>Your player profile is not available.</red>");
            case RECIPIENT_NOT_LOADED -> messageService.send(player, "<red>That player's profile is not available.</red>");
            case RECIPIENT_DISABLED -> messageService.send(player, "<red>That player is not accepting private messages.</red>");
            case SENDER_IGNORES_RECIPIENT -> messageService.send(player, "<red>You are ignoring that player.</red>");
            case RECIPIENT_IGNORES_SENDER -> messageService.send(player, "<red>That player is not accepting private messages.</red>");
            case TARGET_OFFLINE -> messageService.send(player, "<red>That player is no longer online.</red>");
            case CANNOT_MESSAGE_SELF -> messageService.send(player, "<red>You cannot message yourself.</red>");
            case NO_REPLY_TARGET -> messageService.send(player, "<red>You have no player to reply to.</red>");
        }
    }
}

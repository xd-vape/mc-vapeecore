package dev.vapee.core.social.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.social.IgnoreResult;
import dev.vapee.core.social.SocialService;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IgnoreCommand implements TabExecutor {

    private final Server server;
    private final SocialService socialService;
    private final MessageService messageService;
    private final Logger logger;

    public IgnoreCommand(
            Server server,
            SocialService socialService,
            MessageService messageService,
            Logger logger
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.socialService = Objects.requireNonNull(socialService, "socialService");
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
            messageService.send(sender, "<red>Only players can manage ignored players.</red>");
            return true;
        }
        if (args.length != 1) {
            messageService.send(player, "<yellow>Usage:</yellow> <white>/ignore \\<player></white>");
            return true;
        }

        Player target = server.getPlayerExact(args[0]);
        if (target == null) {
            messageService.send(player, "<red>That player is not online.</red>");
            return true;
        }

        try {
            sendResult(player, target, socialService.ignore(player.getUniqueId(), target.getUniqueId()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not persist an ignore for " + player.getUniqueId() + ".", exception);
            messageService.send(player, "<red>Your ignore list could not be saved. Check the server log.</red>");
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
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player candidate : server.getOnlinePlayers()) {
            if (candidate.getUniqueId().equals(player.getUniqueId())
                    || socialService.isIgnoring(player.getUniqueId(), candidate.getUniqueId())) {
                continue;
            }
            if (candidate.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(candidate.getName());
            }
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    private void sendResult(Player player, Player target, IgnoreResult result) {
        switch (result) {
            case SUCCESS -> messageService.send(
                    player,
                    "<green>You are now ignoring <white>" + target.getName() + "</white>.</green>"
            );
            case OWNER_NOT_LOADED -> messageService.send(player, "<red>Your player profile is not available.</red>");
            case CANNOT_IGNORE_SELF -> messageService.send(player, "<red>You cannot ignore yourself.</red>");
            case ALREADY_IGNORED -> messageService.send(player, "<red>That player is already ignored.</red>");
            case NOT_IGNORED -> throw new IllegalStateException("Ignore returned an unignore-only result");
        }
    }
}

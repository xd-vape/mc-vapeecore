package dev.vapee.core.social.command;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.social.IgnoreResult;
import dev.vapee.core.social.SocialService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IgnoreCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.social.ignore";

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
            messageService.send(sender, tr("social.player-only"));
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, tr("social.denied"));
            return true;
        }
        if (args.length != 1) {
            sendUsage(player);
            return true;
        }

        Player target = new OnlinePlayerResolver(server::getOnlinePlayers).resolveExact(args[0]);
        if (target == null) {
            messageService.send(player, tr("social.offline"));
            return true;
        }

        try {
            sendResult(player, target, socialService.ignore(player.getUniqueId(), target.getUniqueId()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not persist an ignore for " + player.getUniqueId() + ".", exception);
            messageService.send(player, tr("social.save-failed"));
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

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player candidate : server.getOnlinePlayers()) {
            if (!candidate.isOnline() || candidate.getUniqueId().equals(player.getUniqueId())
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
            case SUCCESS -> messageService.send(player,
                    tr("social.ignored", java.util.Map.of("player", Component.text(target.getName(), NamedTextColor.WHITE))).color(NamedTextColor.GREEN));
            case OWNER_NOT_LOADED -> messageService.send(player, tr("social.profile-unavailable"));
            case CANNOT_IGNORE_SELF -> messageService.send(player, tr("social.ignore-self"));
            case ALREADY_IGNORED -> messageService.send(player, tr("social.already-ignored"));
            case NOT_IGNORED -> throw new IllegalStateException("Ignore returned an unignore-only result");
        }
    }

    private void sendUsage(Player player) {
        messageService.send(player, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/ignore <player>", NamedTextColor.AQUA)));
    }
}

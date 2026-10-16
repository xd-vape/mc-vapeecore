package dev.vapee.core.social.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.social.IgnoreResult;
import dev.vapee.core.social.SocialService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UnignoreCommand implements TabExecutor {

    private final SocialService socialService;
    private final MessageService messageService;
    private final Logger logger;

    public UnignoreCommand(SocialService socialService, MessageService messageService, Logger logger) {
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
        if (!sender.hasPermission(IgnoreCommand.PERMISSION)) {
            messageService.send(sender, tr("social.denied"));
            return true;
        }
        if (args.length != 1) {
            sendUsage(player);
            return true;
        }

        try {
            List<ResolvedTarget> targets = resolveTargets(player.getUniqueId(), args[0]);
            if (targets.isEmpty()) {
                messageService.send(player, tr("social.not-ignored"));
                return true;
            }
            if (targets.size() > 1) {
                messageService.send(player, tr("social.ambiguous"));
                return true;
            }
            ResolvedTarget target = targets.getFirst();
            sendResult(player, target, socialService.unignore(player.getUniqueId(), target.uniqueId()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not persist an unignore for " + player.getUniqueId() + ".", exception);
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
        if (!(sender instanceof Player player) || !sender.hasPermission(IgnoreCommand.PERMISSION)
                || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<ResolvedTarget> entries = socialService.getIgnoredPlayers(player.getUniqueId()).stream()
                .map(id -> new ResolvedTarget(id, socialService.findKnownPlayerName(id).orElse(id.toString())))
                .toList();
        return entries.stream().map(entry -> {
                    boolean unique = entries.stream().filter(other -> other.displayName()
                            .equalsIgnoreCase(entry.displayName())).count() == 1;
                    return unique && entry.displayName().matches("\\S+")
                            ? entry.displayName() : entry.uniqueId().toString();
                })
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private List<ResolvedTarget> resolveTargets(UUID owner, String input) {
        Set<UUID> ignoredPlayers = socialService.getIgnoredPlayers(owner);
        try {
            UUID uniqueId = UUID.fromString(input);
            if (uniqueId.toString().equalsIgnoreCase(input)) {
                return ignoredPlayers.contains(uniqueId) ? List.of(new ResolvedTarget(
                        uniqueId,
                        socialService.findKnownPlayerName(uniqueId).orElse(uniqueId.toString())
                )) : List.of();
            }
        } catch (IllegalArgumentException ignored) {
            // The argument may be an exact persisted player name instead.
        }

        return ignoredPlayers.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(uniqueId -> socialService.findKnownPlayerName(uniqueId)
                        .map(name -> new ResolvedTarget(uniqueId, name)))
                .flatMap(Optional::stream)
                .filter(candidate -> candidate.displayName().equalsIgnoreCase(input))
                .toList();
    }

    private void sendResult(Player player, ResolvedTarget target, IgnoreResult result) {
        switch (result) {
            case SUCCESS -> messageService.send(player,
                    tr("social.unignored", java.util.Map.of("player", Component.text(target.displayName(), MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
            case OWNER_NOT_LOADED -> messageService.send(player, tr("social.profile-unavailable"));
            case NOT_IGNORED -> messageService.send(player, tr("social.not-ignored"));
            case CANNOT_IGNORE_SELF -> messageService.send(player, tr("social.unignore-self"));
            case ALREADY_IGNORED -> throw new IllegalStateException("Unignore returned an ignore-only result");
        }
    }

    private void sendUsage(Player player) {
        messageService.send(player, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/unignore <player|uuid>", NamedTextColor.AQUA)));
    }

    private record ResolvedTarget(UUID uniqueId, String displayName) {

        private ResolvedTarget {
            Objects.requireNonNull(uniqueId, "uniqueId");
            Objects.requireNonNull(displayName, "displayName");
        }
    }
}

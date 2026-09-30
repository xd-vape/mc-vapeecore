package dev.vapee.core.social.command;

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
            messageService.send(sender, "<red>Only players can manage ignored players.</red>");
            return true;
        }
        if (!sender.hasPermission(IgnoreCommand.PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to manage ignored players.</red>");
            return true;
        }
        if (args.length != 1) {
            sendUsage(player);
            return true;
        }

        try {
            List<ResolvedTarget> targets = resolveTargets(player.getUniqueId(), args[0]);
            if (targets.isEmpty()) {
                messageService.send(player, "<red>That player is not ignored.</red>");
                return true;
            }
            if (targets.size() > 1) {
                messageService.send(player, "<yellow>That name is ambiguous. Use the player's UUID.</yellow>");
                return true;
            }
            ResolvedTarget target = targets.getFirst();
            sendResult(player, target, socialService.unignore(player.getUniqueId(), target.uniqueId()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not persist an unignore for " + player.getUniqueId() + ".", exception);
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
                    Component.text("You are no longer ignoring ", NamedTextColor.GREEN)
                            .append(Component.text(target.displayName(), NamedTextColor.WHITE))
                            .append(Component.text(".", NamedTextColor.GREEN)));
            case OWNER_NOT_LOADED -> messageService.send(player, "<red>Your player profile is not available.</red>");
            case NOT_IGNORED -> messageService.send(player, "<red>That player is not ignored.</red>");
            case CANNOT_IGNORE_SELF -> messageService.send(player, "<red>You cannot unignore yourself.</red>");
            case ALREADY_IGNORED -> throw new IllegalStateException("Unignore returned an ignore-only result");
        }
    }

    private void sendUsage(Player player) {
        messageService.send(player, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text("/unignore <player|uuid>", NamedTextColor.AQUA)));
    }

    private record ResolvedTarget(UUID uniqueId, String displayName) {

        private ResolvedTarget {
            Objects.requireNonNull(uniqueId, "uniqueId");
            Objects.requireNonNull(displayName, "displayName");
        }
    }
}

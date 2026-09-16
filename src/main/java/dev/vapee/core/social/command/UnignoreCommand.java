package dev.vapee.core.social.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.social.IgnoreResult;
import dev.vapee.core.social.SocialService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
        if (args.length != 1) {
            messageService.send(player, "<yellow>Usage:</yellow> <white>/unignore \\<player|uuid></white>");
            return true;
        }

        Optional<ResolvedTarget> target = resolveTarget(player.getUniqueId(), args[0]);
        if (target.isEmpty()) {
            messageService.send(player, "<red>That player is not ignored.</red>");
            return true;
        }

        try {
            sendResult(player, target.get(), socialService.unignore(player.getUniqueId(), target.get().uniqueId()));
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
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (UUID ignoredPlayer : socialService.getIgnoredPlayers(player.getUniqueId())) {
            socialService.findKnownPlayerName(ignoredPlayer)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .ifPresent(matches::add);
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches.stream().distinct().toList();
    }

    private Optional<ResolvedTarget> resolveTarget(UUID owner, String input) {
        Set<UUID> ignoredPlayers = socialService.getIgnoredPlayers(owner);
        try {
            UUID uniqueId = UUID.fromString(input);
            if (ignoredPlayers.contains(uniqueId)) {
                return Optional.of(new ResolvedTarget(
                        uniqueId,
                        socialService.findKnownPlayerName(uniqueId).orElse(uniqueId.toString())
                ));
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
                .findFirst();
    }

    private void sendResult(Player player, ResolvedTarget target, IgnoreResult result) {
        switch (result) {
            case SUCCESS -> messageService.send(
                    player,
                    "<green>You are no longer ignoring <white>" + target.displayName() + "</white>.</green>"
            );
            case OWNER_NOT_LOADED -> messageService.send(player, "<red>Your player profile is not available.</red>");
            case NOT_IGNORED -> messageService.send(player, "<red>That player is not ignored.</red>");
            case CANNOT_IGNORE_SELF -> messageService.send(player, "<red>You cannot unignore yourself.</red>");
            case ALREADY_IGNORED -> throw new IllegalStateException("Unignore returned an ignore-only result");
        }
    }

    private record ResolvedTarget(UUID uniqueId, String displayName) {

        private ResolvedTarget {
            Objects.requireNonNull(uniqueId, "uniqueId");
            Objects.requireNonNull(displayName, "displayName");
        }
    }
}

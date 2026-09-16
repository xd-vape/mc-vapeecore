package dev.vapee.core.social.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.social.SocialService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class IgnoreListCommand implements CommandExecutor {

    private final SocialService socialService;
    private final MessageService messageService;

    public IgnoreListCommand(SocialService socialService, MessageService messageService) {
        this.socialService = Objects.requireNonNull(socialService, "socialService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "<red>Only players can view ignored players.</red>");
            return true;
        }
        if (args.length != 0) {
            messageService.send(player, "<yellow>Usage:</yellow> <white>/ignorelist</white>");
            return true;
        }

        List<KnownEntry> knownEntries = new ArrayList<>();
        List<UUID> unknownEntries = new ArrayList<>();
        for (UUID uniqueId : socialService.getIgnoredPlayers(player.getUniqueId())) {
            socialService.findKnownPlayerName(uniqueId)
                    .ifPresentOrElse(
                            name -> knownEntries.add(new KnownEntry(name, uniqueId)),
                            () -> unknownEntries.add(uniqueId)
                    );
        }
        knownEntries.sort(Comparator
                .comparing(KnownEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.uniqueId().toString()));
        unknownEntries.sort(Comparator.comparing(UUID::toString));

        int size = knownEntries.size() + unknownEntries.size();
        messageService.send(player, "<gray>Ignored players (</gray><white>" + size + "</white><gray>):</gray>");
        for (KnownEntry entry : knownEntries) {
            messageService.send(player, "<gray>-</gray> <white>" + entry.name() + "</white>");
        }
        for (UUID uniqueId : unknownEntries) {
            messageService.send(player, "<gray>-</gray> <white>" + uniqueId + "</white>");
        }
        return true;
    }

    private record KnownEntry(String name, UUID uniqueId) {

        private KnownEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(uniqueId, "uniqueId");
        }
    }
}

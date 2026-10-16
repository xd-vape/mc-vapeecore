package dev.vapee.core.social.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.social.SocialService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class IgnoreListCommand implements TabExecutor {

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
            messageService.send(sender, tr("social.list-player-only"));
            return true;
        }
        if (!sender.hasPermission(IgnoreCommand.PERMISSION)) {
            messageService.send(sender, tr("social.denied"));
            return true;
        }
        if (args.length != 0) {
            messageService.send(player, tr("common.invalid-usage").color(NamedTextColor.RED)
                    .append(Component.newline())
                    .append(tr("common.use").color(NamedTextColor.YELLOW))
                    .append(Component.text("/ignorelist", NamedTextColor.AQUA)));
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
        if (size == 0) {
            messageService.send(player, tr("social.empty"));
            return true;
        }
        Component output = tr("social.list-heading", java.util.Map.of("count", Component.text(size, MessageStyle.HIGHLIGHT))).color(NamedTextColor.GRAY);
        for (KnownEntry entry : knownEntries) {
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.GRAY))
                    .append(Component.text(entry.name(), MessageStyle.HIGHLIGHT));
        }
        for (UUID uniqueId : unknownEntries) {
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.GRAY))
                    .append(Component.text(uniqueId.toString(), MessageStyle.HIGHLIGHT));
        }
        messageService.send(player, output);
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

    private record KnownEntry(String name, UUID uniqueId) {

        private KnownEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(uniqueId, "uniqueId");
        }
    }
}

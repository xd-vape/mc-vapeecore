package dev.vapee.core.rank.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
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
import java.util.Optional;
import java.util.function.Supplier;

public final class RanksCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.ranks.view";

    private final RankService rankService;
    private final Supplier<String> serverNameSupplier;
    private final MessageService messageService;

    public RanksCommand(
            RankService rankService,
            Supplier<String> serverNameSupplier,
            MessageService messageService
    ) {
        this.rankService = Objects.requireNonNull(rankService, "rankService");
        this.serverNameSupplier = Objects.requireNonNull(serverNameSupplier, "serverNameSupplier");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length != 0) {
            messageService.send(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                    .append(Component.newline())
                    .append(Component.text("Use: ", NamedTextColor.YELLOW))
                    .append(Component.text("/ranks", NamedTextColor.AQUA)));
            return true;
        }

        RankService.RankTrackResult result = rankService.getPublicRanks();
        if (result.status() == RankService.RankTrackStatus.MISSING_TRACK) {
            messageService.send(sender, Component.text(
                    "The configured LuckPerms rank track '" + result.trackName() + "' does not exist.",
                    NamedTextColor.RED
            ));
            return true;
        }
        if (result.ranks().isEmpty()) {
            messageService.send(sender, "<yellow>No public ranks are configured yet.</yellow>");
            return true;
        }

        Optional<String> currentRankId = sender instanceof Player player
                ? rankService.getPrimaryRank(player.getUniqueId()).map(RankInfo::id)
                : Optional.empty();
        messageService.send(sender, ranksMessage(
                serverNameSupplier.get(),
                result.ranks(),
                currentRankId
        ));
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

    static Component ranksMessage(
            String serverName,
            List<RankInfo> ranks,
            Optional<String> currentRankId
    ) {
        Component output = Component.text(serverName + " • Ranks", NamedTextColor.GOLD);
        for (int index = 0; index < ranks.size(); index++) {
            RankInfo rank = ranks.get(index);
            output = output.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text((index + 1) + ". ", NamedTextColor.GRAY))
                    .append(Component.text(rank.displayName(), NamedTextColor.WHITE));
            if (currentRankId.filter(rank.id()::equals).isPresent()) {
                output = output.append(Component.text(" • You", NamedTextColor.AQUA));
            }
            if (rank.description().isPresent()) {
                output = output.append(Component.newline())
                        .append(Component.text("   " + rank.description().orElseThrow(), NamedTextColor.GRAY));
            }
        }
        return output;
    }
}

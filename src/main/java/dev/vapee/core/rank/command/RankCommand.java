package dev.vapee.core.rank.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RankCommand implements TabExecutor {
    private final java.util.Set<java.util.function.Consumer<org.bukkit.entity.Player>> successListeners = new java.util.LinkedHashSet<>();
    public void addSuccessListener(java.util.function.Consumer<org.bukkit.entity.Player> listener) { successListeners.add(java.util.Objects.requireNonNull(listener)); }
    public void removeSuccessListener(java.util.function.Consumer<org.bukkit.entity.Player> listener) { successListeners.remove(listener); }
    private void succeeded(org.bukkit.entity.Player player) {
        for (var listener : java.util.List.copyOf(successListeners)) {
            try { listener.accept(player); }
            catch (RuntimeException failure) {
                try { java.util.logging.Logger.getLogger(getClass().getName()).log(java.util.logging.Level.WARNING,
                        "Success observer failed; completed action is retained.", failure); }
                catch (RuntimeException diagnostic) { if (diagnostic != failure) failure.addSuppressed(diagnostic); }
            }
        }
    }


    public static final String PERMISSION = "vapeecore.rank.view";

    private final RankService rankService;
    private final Supplier<String> serverNameSupplier;
    private final MessageService messageService;
    private final Function<String, Player> onlinePlayerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;

    public RankCommand(
            RankService rankService,
            Supplier<String> serverNameSupplier,
            MessageService messageService,
            Function<String, Player> onlinePlayerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayers
    ) {
        this.rankService = Objects.requireNonNull(rankService, "rankService");
        this.serverNameSupplier = Objects.requireNonNull(serverNameSupplier, "serverNameSupplier");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.onlinePlayerLookup = Objects.requireNonNull(onlinePlayerLookup, "onlinePlayerLookup");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, messageService.commandDenied(sender, "rank.denied"));
            return true;
        }
        if (args.length > 1) {
            sendUsage(sender);
            return true;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messageService.send(sender, tr("rank.specify").color(NamedTextColor.YELLOW)
                        .append(Component.newline())
                        .append(Component.text("/rank <player>", NamedTextColor.AQUA)));
                return true;
            }
            target = player;
        } else {
            target = onlinePlayerLookup.apply(args[0]);
            if (target == null || !target.isOnline()) {
                messageService.send(sender, tr("rank.offline"));
                return true;
            }
        }

        RankInfo rank = rankService.getPrimaryRank(target.getUniqueId()).orElse(null);
        if (rank == null) {
            messageService.send(sender, tr("rank.unavailable"));
            return true;
        }
        messageService.send(sender, rankMessage(serverNameSupplier.get(), target.getName(), rank));
        if (args.length == 0 && sender instanceof Player player) succeeded(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(PERMISSION) || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return onlinePlayers.get().stream()
                .filter(Player::isOnline)
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void sendUsage(CommandSender sender) {
        messageService.send(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(MessageStyle.usage("/rank", "[player]")));
    }

    static Component rankMessage(String serverName, String playerName, RankInfo rank) {
        Component output = Component.empty().color(NamedTextColor.GOLD)
                .append(MiniMessage.miniMessage().deserialize(serverName))
                .append(tr("rank.heading-suffix"))
                .append(Component.newline())
                .append(label(tr("rank.player-label"))).append(Component.text(playerName, MessageStyle.HIGHLIGHT))
                .append(Component.newline())
                .append(label(tr("rank.rank-label"))).append(rank.displayComponent())
                .append(Component.newline())
                .append(label(tr("rank.group-label"))).append(Component.text(rank.id(), NamedTextColor.WHITE));
        if (rank.description().isPresent()) {
            output = output.append(Component.newline())
                    .append(label(tr("rank.description-label")))
                    .append(Component.text(rank.description().orElseThrow(), NamedTextColor.WHITE));
        }
        return output;
    }

    private static Component label(Component value) {
        return value.color(NamedTextColor.GRAY);
    }
}

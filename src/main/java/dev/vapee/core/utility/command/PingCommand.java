package dev.vapee.core.utility.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.OnlinePlayerResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PingCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.ping";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.ping.others";

    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final BiConsumer<CommandSender, Component> messageSender;

    public PingCommand(JavaPlugin plugin, MessageService messageService) {
        this(
                new OnlinePlayerResolver(
                        () -> Objects.requireNonNull(plugin, "plugin").getServer().getOnlinePlayers()
                )::resolveExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(messageService, "messageService")::send
        );
    }

    PingCommand(
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            BiConsumer<CommandSender, Component> messageSender
    ) {
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length > 1) {
            invalidUsage(sender);
            return true;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                error(sender, tr("common.console-target"));
                return true;
            }
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, tr("utility.ping.denied-self"));
                return true;
            }
            target = player;
        } else {
            if (!sender.hasPermission(OTHERS_PERMISSION)
                    && (!(sender instanceof Player player) || !player.getName().equalsIgnoreCase(args[0]))) {
                error(sender, tr("utility.ping.denied-other"));
                return true;
            }
            target = playerLookup.apply(args[0]);
            if (target == null) {
                playerNotOnline(sender, args[0]);
                return true;
            }
            boolean self = sender instanceof Player player
                    && player.getUniqueId().equals(target.getUniqueId());
            if (!sender.hasPermission(self ? PERMISSION : OTHERS_PERMISSION)) {
                error(sender, self
                        ? tr("utility.ping.denied-self")
                        : tr("utility.ping.denied-other"));
                return true;
            }
        }

        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        Component message = (self ? tr("utility.ping.self", java.util.Map.of("ping", Component.text(target.getPing(), MessageStyle.HIGHLIGHT)))
                : tr("utility.ping.other", java.util.Map.of("ping", Component.text(target.getPing(), MessageStyle.HIGHLIGHT), "player", Component.text(target.getName(), MessageStyle.HIGHLIGHT))))
                .color(MessageStyle.BODY);
        messageSender.accept(sender, message);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1 || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        return new OnlinePlayerResolver(onlinePlayersSupplier).suggest(args[0], null);
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/ping [player]", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, tr("common.player-offline", java.util.Map.of("player", Component.text(name, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED));
    }

    private void error(CommandSender sender, Component message) {
        messageSender.accept(sender, message.color(NamedTextColor.RED));
    }
}

package dev.vapee.core.utility.command;

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
                error(sender, "A player target is required when using this command from the console.");
                return true;
            }
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, "You do not have permission to view your ping.");
                return true;
            }
            target = player;
        } else {
            if (!sender.hasPermission(OTHERS_PERMISSION)
                    && (!(sender instanceof Player player) || !player.getName().equalsIgnoreCase(args[0]))) {
                error(sender, "You do not have permission to view another player's ping.");
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
                        ? "You do not have permission to view your ping."
                        : "You do not have permission to view another player's ping.");
                return true;
            }
        }

        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        Component message = self
                ? Component.text("Your ping: " + target.getPing() + " ms", NamedTextColor.GREEN)
                : Component.text(target.getName(), NamedTextColor.WHITE)
                        .append(Component.text("'s ping: " + target.getPing() + " ms", NamedTextColor.GREEN));
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
        messageSender.accept(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text("/ping [player]", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, Component.text("Player '", NamedTextColor.RED)
                .append(Component.text(name, NamedTextColor.WHITE))
                .append(Component.text("' is not online.", NamedTextColor.RED)));
    }

    private void error(CommandSender sender, String message) {
        messageSender.accept(sender, Component.text(message, NamedTextColor.RED));
    }
}

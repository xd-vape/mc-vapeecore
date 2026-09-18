package dev.vapee.core.utility.command;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.UtilityService;
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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class FeedCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.feed";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.feed.others";

    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Predicate<UUID> activityCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public FeedCommand(
            JavaPlugin plugin,
            UtilityService utilityService,
            ActivityService activityService,
            MessageService messageService
    ) {
        this(
                utilityService,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayerExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(messageService, "messageService")::send
        );
    }

    FeedCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> activityCheck,
            BiConsumer<CommandSender, Component> messageSender
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        Player target = resolveTarget(sender, args);
        if (target == null) {
            return true;
        }
        if (activityCheck.test(target.getUniqueId())) {
            error(sender, target.equals(sender)
                    ? "You cannot use this command while participating in an activity."
                    : "That player is participating in an activity.");
            return true;
        }
        utilityService.feed(target);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        messageSender.accept(sender, self
                ? Component.text("Fed.", NamedTextColor.GREEN)
                : Component.text("Fed ", NamedTextColor.GREEN)
                        .append(Component.text(target.getName(), NamedTextColor.WHITE))
                        .append(Component.text(".", NamedTextColor.GREEN)));
        return true;
    }

    private Player resolveTarget(CommandSender sender, String[] args) {
        if (args.length > 1) {
            invalidUsage(sender);
            return null;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                error(sender, "A player target is required when using this command from the console.");
                return null;
            }
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, "You do not have permission to feed yourself.");
                return null;
            }
            return player;
        }
        Player target = playerLookup.apply(args[0]);
        if (target == null) {
            playerNotOnline(sender, args[0]);
            return null;
        }
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        if (!sender.hasPermission(self ? PERMISSION : OTHERS_PERMISSION)) {
            error(sender, self
                    ? "You do not have permission to feed yourself."
                    : "You do not have permission to feed another player.");
            return null;
        }
        return target;
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
        return playerNames(onlinePlayersSupplier.get(), args[0]);
    }

    private static List<String> playerNames(Collection<? extends Player> players, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return players.stream()
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text("/feed [player]", NamedTextColor.AQUA)));
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

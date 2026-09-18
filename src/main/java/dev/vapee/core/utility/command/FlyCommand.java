package dev.vapee.core.utility.command;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.UtilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
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

public final class FlyCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.fly";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.fly.others";

    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Predicate<UUID> buildCheck;
    private final Predicate<UUID> activityCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public FlyCommand(
            JavaPlugin plugin,
            UtilityService utilityService,
            LobbyPlayerStateService lobbyPlayerStateService,
            ActivityService activityService,
            MessageService messageService
    ) {
        this(
                utilityService,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayerExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(lobbyPlayerStateService, "lobbyPlayerStateService")::isBuildMode,
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(messageService, "messageService")::send
        );
    }

    FlyCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> buildCheck,
            Predicate<UUID> activityCheck,
            BiConsumer<CommandSender, Component> messageSender
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.buildCheck = Objects.requireNonNull(buildCheck, "buildCheck");
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
                error(sender, "You do not have permission to change your flight state.");
                return true;
            }
            target = player;
        } else {
            target = playerLookup.apply(args[0]);
            if (target == null) {
                playerNotOnline(sender, args[0]);
                return true;
            }
            boolean self = sender instanceof Player player
                    && player.getUniqueId().equals(target.getUniqueId());
            String permission = self ? PERMISSION : OTHERS_PERMISSION;
            if (!sender.hasPermission(permission)) {
                error(sender, self
                        ? "You do not have permission to change your flight state."
                        : "You do not have permission to change another player's flight state.");
                return true;
            }
        }

        if (activityCheck.test(target.getUniqueId())) {
            error(sender, target.equals(sender)
                    ? "You cannot use this command while participating in an activity."
                    : "That player is participating in an activity.");
            return true;
        }
        if (buildCheck.test(target.getUniqueId())) {
            error(sender, "Flight is controlled by build mode while the player is in BUILD.");
            return true;
        }
        GameMode gameMode = target.getGameMode();
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            utilityService.clearManagedFlight(target);
            error(sender, "Flight is controlled by the player's current game mode.");
            return true;
        }

        boolean enabled = utilityService.toggleFlight(target);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        Component message = Component.text("Flight " + (enabled ? "enabled" : "disabled"), NamedTextColor.GREEN);
        if (!self) {
            message = message.append(Component.text(" for ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE));
        }
        messageSender.accept(sender, message.append(Component.text(".", NamedTextColor.GREEN)));
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
                .append(Component.text("/fly [player]", NamedTextColor.AQUA)));
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

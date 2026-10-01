package dev.vapee.core.utility.command;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.OnlinePlayerResolver;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class TeleportHereCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.teleport.here";
    public static final String BYPASS_PERMISSION = TeleportCommand.BYPASS_PERMISSION;

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Predicate<UUID> activityCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public TeleportHereCommand(
            JavaPlugin plugin,
            UtilityService utilityService,
            ActivityService activityService,
            MessageService messageService,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this(
                utilityService,
                new OnlinePlayerResolver(
                        () -> Objects.requireNonNull(plugin, "plugin").getServer().getOnlinePlayers()
                )::resolveExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(messageService, "messageService")::send,
                staffTargetGuard
        );
    }

    TeleportHereCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> activityCheck,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        this.staffTargetGuard = Objects.requireNonNull(staffTargetGuard, "staffTargetGuard");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            invalidUsage(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            error(sender, "A destination player is required; console has no location.");
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            error(sender, "You do not have permission to teleport another player to you.");
            return true;
        }

        Player target = playerLookup.apply(args[0]);
        if (target == null) {
            playerNotOnline(sender, args[0]);
            return true;
        }
        if (player.getUniqueId().equals(target.getUniqueId())) {
            error(sender, "You are already here.");
            return true;
        }
        if (!staffTargetGuard.authorize(sender, target, "tphere", messageSender)) return true;

        if (!sender.hasPermission(BYPASS_PERMISSION)) {
            if (activityCheck.test(player.getUniqueId())) {
                error(sender, "You cannot use this command while participating in an activity.");
                return true;
            }
            if (activityCheck.test(target.getUniqueId())) {
                error(sender, "That player is participating in an activity.");
                return true;
            }
        }
        if (!utilityService.teleport(target, player)) {
            error(sender, "Teleport failed or was cancelled.");
            return true;
        }
        messageSender.accept(sender, Component.text("Teleported ", NamedTextColor.GREEN)
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(" to you.", NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player) || args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        UUID selfId = sender instanceof Player player ? player.getUniqueId() : null;
        return new OnlinePlayerResolver(
                () -> staffTargetGuard.suggestiblePlayers(sender, onlinePlayersSupplier.get())).suggest(args[0], selfId);
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text("/tphere <player>", NamedTextColor.AQUA)));
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

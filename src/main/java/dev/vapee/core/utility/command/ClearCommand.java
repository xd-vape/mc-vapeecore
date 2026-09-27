package dev.vapee.core.utility.command;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
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

public final class ClearCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.clear";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.clear.others";

    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Predicate<UUID> activityCheck;
    private final Predicate<UUID> buildCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public ClearCommand(
            JavaPlugin plugin,
            UtilityService utilityService,
            ActivityService activityService,
            LobbyPlayerStateService lobbyPlayerStateService,
            MessageService messageService
    ) {
        this(
                utilityService,
                new OnlinePlayerResolver(
                        () -> Objects.requireNonNull(plugin, "plugin").getServer().getOnlinePlayers()
                )::resolveExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(lobbyPlayerStateService, "lobbyPlayerStateService")::isBuildMode,
                Objects.requireNonNull(messageService, "messageService")::send
        );
    }

    ClearCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> activityCheck,
            Predicate<UUID> buildCheck,
            BiConsumer<CommandSender, Component> messageSender
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
        this.buildCheck = Objects.requireNonNull(buildCheck, "buildCheck");
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
                error(sender, "You do not have permission to clear your inventory.");
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
            if (!sender.hasPermission(self ? PERMISSION : OTHERS_PERMISSION)) {
                error(sender, self
                        ? "You do not have permission to clear your inventory."
                        : "You do not have permission to clear another player's inventory.");
                return true;
            }
        }

        if (activityCheck.test(target.getUniqueId())) {
            error(sender, "That inventory is controlled by an activity and cannot be cleared.");
            return true;
        }
        if (buildCheck.test(target.getUniqueId())) {
            error(sender, "That inventory is controlled by build mode and cannot be cleared.");
            return true;
        }

        utilityService.clearInventory(target);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        messageSender.accept(sender, self
                ? Component.text("Inventory cleared.", NamedTextColor.GREEN)
                : Component.text("Cleared ", NamedTextColor.GREEN)
                        .append(Component.text(target.getName(), NamedTextColor.WHITE))
                        .append(Component.text("'s inventory.", NamedTextColor.GREEN)));
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
                .append(Component.text("/clear [player]", NamedTextColor.AQUA)));
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

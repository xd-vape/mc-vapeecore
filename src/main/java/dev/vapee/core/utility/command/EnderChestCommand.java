package dev.vapee.core.utility.command;

import dev.vapee.core.command.OnlineStaffTargetGuard;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EnderChestCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.enderchest";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.enderchest.others";

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final BiConsumer<CommandSender, Component> messageSender;

    public EnderChestCommand(JavaPlugin plugin, UtilityService utilityService, MessageService messageService,
                            OnlineStaffTargetGuard staffTargetGuard) {
        this(
                utilityService,
                new OnlinePlayerResolver(
                        () -> Objects.requireNonNull(plugin, "plugin").getServer().getOnlinePlayers()
                )::resolveExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(messageService, "messageService")::send,
                staffTargetGuard
        );
    }

    EnderChestCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
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
        if (args.length > 1) {
            invalidUsage(sender);
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            error(sender, "This command can only be used by a player because it opens an inventory.");
            return true;
        }

        Player owner;
        if (args.length == 0) {
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, "You do not have permission to open your ender chest.");
                return true;
            }
            owner = viewer;
        } else {
            owner = playerLookup.apply(args[0]);
            if (owner == null) {
                playerNotOnline(sender, args[0]);
                return true;
            }
            boolean self = viewer.getUniqueId().equals(owner.getUniqueId());
            if (!sender.hasPermission(self ? PERMISSION : OTHERS_PERMISSION)) {
                error(sender, self
                        ? "You do not have permission to open your ender chest."
                        : "You do not have permission to open another player's ender chest.");
                return true;
            }
        }

        if (!staffTargetGuard.authorize(sender, owner, "enderchest", messageSender)) return true;

        utilityService.openEnderChest(viewer, owner);
        if (!viewer.getUniqueId().equals(owner.getUniqueId())) {
            messageSender.accept(sender, Component.text("Opened ", NamedTextColor.GREEN)
                    .append(Component.text(owner.getName(), NamedTextColor.WHITE))
                    .append(Component.text("'s ender chest.", NamedTextColor.GREEN)));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player) || args.length != 1 || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        return new OnlinePlayerResolver(
                () -> staffTargetGuard.suggestiblePlayers(sender, onlinePlayersSupplier.get())).suggest(args[0], null);
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text("/enderchest [player]", NamedTextColor.AQUA)));
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

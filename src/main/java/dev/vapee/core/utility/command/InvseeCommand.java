package dev.vapee.core.utility.command;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.InvseeService;
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

public final class InvseeCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.invsee";
    public static final String MODIFY_PERMISSION = "vapeecore.utility.invsee.modify";

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final InvseeService invseeService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final BiConsumer<CommandSender, Component> messageSender;

    public InvseeCommand(
            JavaPlugin plugin,
            InvseeService invseeService,
            MessageService messageService,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this(
                invseeService,
                new OnlinePlayerResolver(
                        () -> Objects.requireNonNull(plugin, "plugin").getServer().getOnlinePlayers()
                )::resolveExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(messageService, "messageService")::send,
                staffTargetGuard
        );
    }

    InvseeCommand(
            InvseeService invseeService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this.invseeService = Objects.requireNonNull(invseeService, "invseeService");
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
        if (args.length != 1) {
            invalidUsage(sender);
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            error(sender, tr("common.inventory-player-only"));
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            error(sender, tr("utility.invsee.denied"));
            return true;
        }

        Player target = playerLookup.apply(args[0]);
        if (target == null) {
            playerNotOnline(sender, args[0]);
            return true;
        }
        if (!staffTargetGuard.authorize(sender, target, "invsee", messageSender)) return true;

        if (invseeService.openSnapshot(viewer, target) == null) {
            error(sender, tr("utility.invsee.failed"));
            return true;
        }
        messageSender.accept(sender, tr("utility.invsee.opened", java.util.Map.of("player", Component.text(target.getName(), NamedTextColor.WHITE))).color(NamedTextColor.GREEN));
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
        return new OnlinePlayerResolver(
                () -> staffTargetGuard.suggestiblePlayers(sender, onlinePlayersSupplier.get())).suggest(args[0], null);
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/invsee <player>", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, tr("common.player-offline", java.util.Map.of("player", Component.text(name, NamedTextColor.WHITE))).color(NamedTextColor.RED));
    }

    private void error(CommandSender sender, Component message) {
        messageSender.accept(sender, message.color(NamedTextColor.RED));
    }
}

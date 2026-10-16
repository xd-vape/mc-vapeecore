package dev.vapee.core.utility.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

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

public final class FeedCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.feed";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.feed.others";

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Predicate<UUID> activityCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public FeedCommand(
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

    FeedCommand(
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
        Player target = resolveTarget(sender, args);
        if (target == null) {
            return true;
        }
        if (!staffTargetGuard.authorize(sender, target, "feed", messageSender)) return true;

        if (activityCheck.test(target.getUniqueId())) {
            error(sender, target.equals(sender)
                    ? tr("common.activity-self")
                    : tr("common.activity-target"));
            return true;
        }
        utilityService.feed(target);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        messageSender.accept(sender, self
                ? tr("utility.feed.self").color(MessageStyle.BODY)
                : tr("utility.feed.other", java.util.Map.of("player", Component.text(target.getName(), MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
        return true;
    }

    private Player resolveTarget(CommandSender sender, String[] args) {
        if (args.length > 1) {
            invalidUsage(sender);
            return null;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                error(sender, tr("common.console-target"));
                return null;
            }
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, tr("utility.feed.denied-self"));
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
                    ? tr("utility.feed.denied-self")
                    : tr("utility.feed.denied-other"));
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
        return playerNames(staffTargetGuard.suggestiblePlayers(sender, onlinePlayersSupplier.get()), args[0]);
    }

    private static List<String> playerNames(Collection<? extends Player> players, String input) {
        return NameSuggestions.matching(players.stream()
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .map(Player::getName), input);
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/feed [player]", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, tr("common.player-offline", java.util.Map.of("player", Component.text(name, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED));
    }

    private void error(CommandSender sender, Component message) {
        messageSender.accept(sender, message.color(NamedTextColor.RED));
    }
}

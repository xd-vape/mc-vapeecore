package dev.vapee.core.utility.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.OnlinePlayerResolver;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class FlyCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.fly";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.fly.others";

    private final OnlineStaffTargetGuard staffTargetGuard;
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
            MessageService messageService,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this(
                utilityService,
                new OnlinePlayerResolver(
                        () -> Objects.requireNonNull(plugin, "plugin").getServer().getOnlinePlayers()
                )::resolveExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(lobbyPlayerStateService, "lobbyPlayerStateService")::isBuildMode,
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(messageService, "messageService")::send,
                staffTargetGuard
        );
    }

    FlyCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> buildCheck,
            Predicate<UUID> activityCheck,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.buildCheck = Objects.requireNonNull(buildCheck, "buildCheck");
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
                error(sender, tr("utility.fly.denied-self"));
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
                        ? tr("utility.fly.denied-self")
                        : tr("utility.fly.denied-other"));
                return true;
            }
        }

        if (!staffTargetGuard.authorize(sender, target, "fly", messageSender)) return true;

        if (activityCheck.test(target.getUniqueId())) {
            error(sender, target.equals(sender)
                    ? tr("common.activity-self")
                    : tr("common.activity-target"));
            return true;
        }
        if (buildCheck.test(target.getUniqueId())) {
            error(sender, tr("utility.fly.build"));
            return true;
        }
        GameMode gameMode = target.getGameMode();
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            utilityService.clearManagedFlight(target);
            error(sender, tr("utility.fly.game-mode"));
            return true;
        }

        boolean enabled = utilityService.toggleFlight(target);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        Component state = tr(enabled ? "common.enabled-lower" : "common.disabled-lower").color(MessageStyle.HIGHLIGHT);
        Component message = self ? tr("utility.fly.self", java.util.Map.of("state", state))
                : tr("utility.fly.other", java.util.Map.of("state", state, "player", Component.text(target.getName(), MessageStyle.HIGHLIGHT)));
        messageSender.accept(sender, message.color(MessageStyle.BODY));
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
                .append(Component.text("/fly [player]", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, tr("common.player-offline", java.util.Map.of("player", Component.text(name, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED));
    }

    private void error(CommandSender sender, Component message) {
        messageSender.accept(sender, message.color(NamedTextColor.RED));
    }
}

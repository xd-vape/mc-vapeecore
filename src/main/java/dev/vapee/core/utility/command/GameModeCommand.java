package dev.vapee.core.utility.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyService;
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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class GameModeCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.gamemode";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.gamemode.others";
    private static final List<String> MODES = List.of("survival", "creative", "adventure", "spectator");

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Predicate<UUID> buildCheck;
    private final Predicate<UUID> activityCheck;
    private final Predicate<Player> lobbyWorldCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public GameModeCommand(
            JavaPlugin plugin,
            UtilityService utilityService,
            LobbyService lobbyService,
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
                player -> Objects.requireNonNull(lobbyService, "lobbyService").isLobbyWorld(player.getWorld()),
                Objects.requireNonNull(messageService, "messageService")::send,
                staffTargetGuard
        );
    }

    GameModeCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> buildCheck,
            Predicate<UUID> activityCheck,
            Predicate<Player> lobbyWorldCheck,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.buildCheck = Objects.requireNonNull(buildCheck, "buildCheck");
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
        this.lobbyWorldCheck = Objects.requireNonNull(lobbyWorldCheck, "lobbyWorldCheck");
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
        if (args.length < 1 || args.length > 2) {
            invalidUsage(sender);
            return true;
        }
        GameMode gameMode = parseGameMode(args[0]);
        if (gameMode == null) {
            error(sender, tr("utility.game-mode.invalid"));
            return true;
        }

        Player target;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                error(sender, tr("common.console-target"));
                return true;
            }
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, tr("utility.game-mode.denied-self"));
                return true;
            }
            target = player;
        } else {
            target = playerLookup.apply(args[1]);
            if (target == null) {
                playerNotOnline(sender, args[1]);
                return true;
            }
            boolean self = sender instanceof Player player
                    && player.getUniqueId().equals(target.getUniqueId());
            if (!sender.hasPermission(self ? PERMISSION : OTHERS_PERMISSION)) {
                error(sender, self
                        ? tr("utility.game-mode.denied-self")
                        : tr("utility.game-mode.denied-other"));
                return true;
            }
        }

        if (!staffTargetGuard.authorize(sender, target, "gamemode", messageSender)) return true;

        if (activityCheck.test(target.getUniqueId())) {
            error(sender, target.equals(sender)
                    ? tr("common.activity-self")
                    : tr("common.activity-target"));
            return true;
        }
        if (buildCheck.test(target.getUniqueId())) {
            error(sender, tr("utility.game-mode.build"));
            return true;
        }

        utilityService.setGameMode(target, gameMode);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        Component mode = tr("utility.game-mode." + gameMode.name().toLowerCase(java.util.Locale.ROOT)).color(MessageStyle.HIGHLIGHT);
        Component message = (self ? tr("utility.game-mode.self", java.util.Map.of("mode", mode))
                : tr("utility.game-mode.other", java.util.Map.of("mode", mode, "player", Component.text(target.getName(), MessageStyle.HIGHLIGHT))))
                .color(MessageStyle.BODY);
        messageSender.accept(sender, message);
        if (gameMode == GameMode.CREATIVE && lobbyWorldCheck.test(target)) {
            messageSender.accept(sender, tr("utility.game-mode.lobby").color(NamedTextColor.YELLOW));
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
        if (args.length == 1) {
            if (!sender.hasPermission(PERMISSION) && !sender.hasPermission(OTHERS_PERMISSION)) {
                return List.of();
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return MODES.stream().filter(mode -> mode.startsWith(prefix)).toList();
        }
        if (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            return playerNames(staffTargetGuard.suggestiblePlayers(sender, onlinePlayersSupplier.get()), args[1]);
        }
        return List.of();
    }

    static @Nullable GameMode parseGameMode(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static String displayName(GameMode gameMode) {
        String name = gameMode.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
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
                .append(Component.text("/gamemode <mode> [player]", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, tr("common.player-offline", java.util.Map.of("player", Component.text(name, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED));
    }

    private void error(CommandSender sender, Component message) {
        messageSender.accept(sender, message.color(NamedTextColor.RED));
    }
}

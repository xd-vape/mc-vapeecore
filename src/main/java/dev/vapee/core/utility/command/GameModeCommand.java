package dev.vapee.core.utility.command;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyService;
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

public final class GameModeCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.gamemode";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.gamemode.others";
    private static final List<String> MODES = List.of("survival", "creative", "adventure", "spectator");

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
            MessageService messageService
    ) {
        this(
                utilityService,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayerExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(lobbyPlayerStateService, "lobbyPlayerStateService")::isBuildMode,
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                player -> Objects.requireNonNull(lobbyService, "lobbyService").isLobbyWorld(player.getWorld()),
                Objects.requireNonNull(messageService, "messageService")::send
        );
    }

    GameModeCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> buildCheck,
            Predicate<UUID> activityCheck,
            Predicate<Player> lobbyWorldCheck,
            BiConsumer<CommandSender, Component> messageSender
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.buildCheck = Objects.requireNonNull(buildCheck, "buildCheck");
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
        this.lobbyWorldCheck = Objects.requireNonNull(lobbyWorldCheck, "lobbyWorldCheck");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
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
            error(sender, "Game mode must be survival, creative, adventure or spectator.");
            return true;
        }

        Player target;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                error(sender, "A player target is required when using this command from the console.");
                return true;
            }
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, "You do not have permission to change your game mode.");
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
                        ? "You do not have permission to change your game mode."
                        : "You do not have permission to change another player's game mode.");
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
            error(sender, "Exit build mode before changing the player's game mode.");
            return true;
        }

        utilityService.setGameMode(target, gameMode);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        Component message = self
                ? Component.text("Game mode changed to " + displayName(gameMode) + ".", NamedTextColor.GREEN)
                : Component.text(target.getName(), NamedTextColor.WHITE)
                        .append(Component.text("'s game mode changed to " + displayName(gameMode) + ".",
                                NamedTextColor.GREEN));
        messageSender.accept(sender, message);
        if (gameMode == GameMode.CREATIVE && lobbyWorldCheck.test(target)) {
            messageSender.accept(sender, Component.text(
                    "Lobby protection remains active. Use /build to enable building.",
                    NamedTextColor.YELLOW
            ));
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
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return MODES.stream().filter(mode -> mode.startsWith(prefix)).toList();
        }
        if (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            return playerNames(onlinePlayersSupplier.get(), args[1]);
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
                .append(Component.text("/gamemode <mode> [player]", NamedTextColor.AQUA)));
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

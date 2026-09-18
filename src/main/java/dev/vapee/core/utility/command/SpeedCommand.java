package dev.vapee.core.utility.command;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.UtilityService;
import dev.vapee.core.utility.UtilitySpeedResult;
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
import java.util.stream.IntStream;

public final class SpeedCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.speed";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.speed.others";

    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Predicate<UUID> activityCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public SpeedCommand(
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

    SpeedCommand(
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
        if (args.length < 1 || args.length > 2) {
            invalidUsage(sender);
            return true;
        }
        int level = parseLevel(args[0]);
        if (level == -1) {
            error(sender, "Speed must be a whole number between 1 and 10.");
            return true;
        }

        Player target;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                error(sender, "A player target is required when using this command from the console.");
                return true;
            }
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, "You do not have permission to change your speed.");
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
                        ? "You do not have permission to change your speed."
                        : "You do not have permission to change another player's speed.");
                return true;
            }
        }

        if (activityCheck.test(target.getUniqueId())) {
            error(sender, target.equals(sender)
                    ? "You cannot use this command while participating in an activity."
                    : "That player is participating in an activity.");
            return true;
        }

        UtilitySpeedResult result = utilityService.setSpeed(target, level);
        boolean self = sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
        Component message = Component.text(result.type().displayName() + " speed", NamedTextColor.GREEN);
        if (!self) {
            message = message.append(Component.text(" for ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE));
        }
        messageSender.accept(sender, message
                .append(Component.text(" set to " + level + "/10.", NamedTextColor.GREEN)));
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
            return IntStream.rangeClosed(1, 10)
                    .mapToObj(Integer::toString)
                    .filter(value -> value.startsWith(args[0]))
                    .toList();
        }
        if (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            return playerNames(onlinePlayersSupplier.get(), args[1]);
        }
        return List.of();
    }

    static int parseLevel(String input) {
        try {
            int level = Integer.parseInt(input);
            return level >= 1 && level <= 10 ? level : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
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
                .append(Component.text("/speed <1-10> [player]", NamedTextColor.AQUA)));
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

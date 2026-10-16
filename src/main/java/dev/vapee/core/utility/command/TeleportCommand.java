package dev.vapee.core.utility.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.OnlinePlayerResolver;
import dev.vapee.core.utility.UtilityService;
import dev.vapee.core.utility.teleport.TeleportParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class TeleportCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.teleport";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.teleport.others";
    public static final String WORLD_PERMISSION = "vapeecore.utility.teleport.world";
    public static final String OTHERS_WORLD_PERMISSION = "vapeecore.utility.teleport.others.world";
    public static final String BYPASS_PERMISSION = "vapeecore.utility.teleport.bypass";

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final Supplier<? extends Collection<? extends World>> worldsSupplier;
    private final Predicate<UUID> activityCheck;
    private final BiConsumer<CommandSender, Component> messageSender;

    public TeleportCommand(
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
                () -> plugin.getServer().getWorlds(),
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(messageService, "messageService")::send,
                staffTargetGuard
        );
    }

    TeleportCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Predicate<UUID> activityCheck,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this(utilityService, playerLookup, onlinePlayersSupplier, List::of, activityCheck, messageSender, staffTargetGuard);
    }

    TeleportCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            Supplier<? extends Collection<? extends World>> worldsSupplier,
            Predicate<UUID> activityCheck,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.worldsSupplier = Objects.requireNonNull(worldsSupplier, "worldsSupplier");
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
        TeleportParser.Request request;
        try {
            request = TeleportParser.parse(args);
        } catch (IllegalArgumentException exception) {
            invalidUsage(sender);
            return true;
        }
        if (!request.other() && !(sender instanceof Player)) {
            error(sender, tr("utility.teleport.console-source"));
            return true;
        }
        String permission = request.other()
                ? request.explicitWorld() ? OTHERS_WORLD_PERMISSION : OTHERS_PERMISSION
                : request.explicitWorld() ? WORLD_PERMISSION : PERMISSION;
        if (!sender.hasPermission(permission)) {
            error(sender, request.other()
                    ? request.playerTarget() ? tr("utility.teleport.denied-pair")
                    : tr("utility.teleport.denied-other")
                    : tr("utility.teleport.denied-self"));
            return true;
        }
        Player source = request.other() ? playerLookup.apply(request.source()) : (Player) sender;
        if (source == null || !source.isOnline()) {
            playerNotOnline(sender, request.source());
            return true;
        }
        if (!staffTargetGuard.authorize(sender, source, "tp", messageSender)) return true;

        Player target = null;
        Location destination = null;
        if (request.playerTarget()) {
            target = playerLookup.apply(request.target());
            if (target == null || !target.isOnline()) {
                playerNotOnline(sender, request.target());
                return true;
            }
            if (source.getUniqueId().equals(target.getUniqueId())) {
                error(sender, request.other() ? tr("utility.teleport.same-pair")
                        : tr("utility.teleport.same-self"));
                return true;
            }
        } else {
            Location sourceLocation = source.getLocation().clone();
            World world = request.explicitWorld() ? resolveWorld(request.world()) : sourceLocation.getWorld();
            if (world == null) {
                if (request.explicitWorld()) {
                    messageSender.accept(sender, tr("utility.teleport.world-unknown", java.util.Map.of("world", Component.text(request.world(), MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED));
                } else error(sender, tr("utility.teleport.no-world"));
                return true;
            }
            try {
                destination = TeleportParser.destination(request, sourceLocation, world);
            } catch (IllegalArgumentException exception) {
                error(sender, teleportError(exception));
                return true;
            }
        }
        if (!sender.hasPermission(BYPASS_PERMISSION)) {
            if (activityCheck.test(source.getUniqueId())) {
                error(sender, source.equals(sender)
                        ? tr("common.activity-self")
                        : tr("utility.teleport.activity-source"));
                return true;
            }
            if (target != null && activityCheck.test(target.getUniqueId())) {
                error(sender, tr("utility.teleport.activity-destination"));
                return true;
            }
        }
        boolean success = target != null ? utilityService.teleport(source, target)
                : utilityService.teleport(source, destination);
        if (!success) {
            error(sender, tr("utility.teleport.failed"));
            return true;
        }
        Component destinationText;
        if (target != null) destinationText = Component.text(target.getName(), MessageStyle.HIGHLIGHT);
        else {
            Component coordinates = Component.text(String.format(Locale.ROOT, "%.2f, %.2f, %.2f",
                    destination.getX(), destination.getY(), destination.getZ()), MessageStyle.HIGHLIGHT);
            destinationText = request.explicitWorld() ? tr("utility.teleport.world-destination",
                    java.util.Map.of("coordinates", coordinates, "world", Component.text(destination.getWorld().getName(), MessageStyle.HIGHLIGHT))) : coordinates;
        }
        Component message = request.other() ? tr("utility.teleport.other", java.util.Map.of("player", Component.text(source.getName(), MessageStyle.HIGHLIGHT), "destination", destinationText))
                : tr("utility.teleport.self", java.util.Map.of("destination", destinationText));
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
        if (args.length == 0) return List.of();
        String input = args[args.length - 1];
        OnlinePlayerResolver resolver = new OnlinePlayerResolver(onlinePlayersSupplier);
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            if (sender instanceof Player player) {
                if (sender.hasPermission(PERMISSION)) {
                    result.addAll(resolver.suggest(input, player.getUniqueId()));
                    result.addAll(suggest(List.of("~", "^"), input));
                }
                if (sender.hasPermission(WORLD_PERMISSION)) result.addAll(suggest(List.of("world"), input));
            } else if (sender.hasPermission(OTHERS_PERMISSION)) result.addAll(new OnlinePlayerResolver(
                    () -> staffTargetGuard.suggestiblePlayers(sender, onlinePlayersSupplier.get())).suggest(input, null));
            return sorted(result);
        }
        if (args[0].equalsIgnoreCase("world")) {
            if (!(sender instanceof Player) || !sender.hasPermission(WORLD_PERMISSION)) return List.of();
            if (args.length == 2) return worlds(input);
            return coordinateSuggestions(args.length - 3, input);
        }
        Player source = playerLookup.apply(args[0]);
        if (source != null && sender.hasPermission(OTHERS_PERMISSION)) {
            if (args.length == 2) {
                List<String> result = new ArrayList<>(resolver.suggest(input, source.getUniqueId()));
                result.addAll(suggest(List.of("~", "^"), input));
                if (sender.hasPermission(OTHERS_WORLD_PERMISSION)) result.addAll(suggest(List.of("world"), input));
                return sorted(result);
            }
            if (args[1].equalsIgnoreCase("world")) {
                if (!sender.hasPermission(OTHERS_WORLD_PERMISSION)) return List.of();
                return args.length == 3 ? worlds(input) : coordinateSuggestions(args.length - 4, input);
            }
            return coordinateSuggestions(args.length - 2, input);
        }
        if (sender instanceof Player && sender.hasPermission(PERMISSION) && coordinateStart(args[0]))
            return coordinateSuggestions(args.length - 1, input);
        return List.of();
    }

    private static boolean coordinateStart(String token) {
        if (token.startsWith("~") || token.startsWith("^")) return true;
        try {
            return Double.isFinite(Double.parseDouble(token));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private List<String> coordinateSuggestions(int index, String input) {
        if (index < 0 || index > 4) return List.of();
        return sorted(suggest(index < 3 ? List.of("~", "^") : List.of("~"), input));
    }

    private List<String> worlds(String input) {
        return sorted(worldsSupplier.get().stream().filter(Objects::nonNull).map(World::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))).toList());
    }

    private World resolveWorld(String name) {
        World match = null;
        for (World world : worldsSupplier.get()) {
            if (world != null && world.getName().equalsIgnoreCase(name)) {
                if (match != null && match != world) return null;
                match = world;
            }
        }
        return match;
    }

    private static List<String> suggest(List<String> candidates, String input) {
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT)
                .startsWith(input.toLowerCase(Locale.ROOT))).toList();
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().distinct().sorted(Comparator.comparingInt((String value) ->
                        value.startsWith("^") ? 0 : value.startsWith("~") ? 1 : 2)
                .thenComparing(value -> value.toLowerCase(Locale.ROOT))
                .thenComparing(Comparator.naturalOrder())).toList();
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/tp <player> | /tp <x> <y> <z> [yaw pitch] | "
                        + "/tp <source> <target> | /tp <source> <x> <y> <z> [yaw pitch] | "
                        + "/tp world <world> <x> <y> <z> [yaw pitch] | "
                        + "/tp <source> world <world> <x> <y> <z> [yaw pitch]", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, tr("common.player-offline", java.util.Map.of("player", Component.text(name, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED));
    }

    private static Component teleportError(IllegalArgumentException exception) {
        if (!(exception instanceof dev.vapee.core.utility.teleport.TeleportParseException failure)) return tr("utility.teleport.failed");
        return switch (failure.reason()) {
            case USAGE -> tr("utility.teleport.parse.usage");
            case MIXED_COORDINATES -> tr("utility.teleport.parse.mixed");
            case NONFINITE_DESTINATION -> tr("utility.teleport.parse.destination");
            case NONFINITE_ROTATION -> tr("utility.teleport.parse.rotation");
            case INVALID_COORDINATE -> tr("utility.teleport.parse.coordinate-value", java.util.Map.of("value", Component.text(failure.value(), MessageStyle.HIGHLIGHT)));
            case INVALID_ROTATION -> tr("utility.teleport.parse.rotation-value", java.util.Map.of("value", Component.text(failure.value(), MessageStyle.HIGHLIGHT)));
        };
    }

    private void error(CommandSender sender, Component message) {
        messageSender.accept(sender, message.color(NamedTextColor.RED));
    }
}

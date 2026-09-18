package dev.vapee.core.lobby.warp.command;

import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpResult;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

public final class WarpCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.warp.admin";
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "set", "remove", "list", "info", "name", "icon"
    );
    private static final CommandHelpPage HELP_PAGE = new CommandHelpPage(
            "Warp Administration",
            null,
            List.of(
                    new CommandHelpSection("Create & Edit", List.of(
                            new CommandHelpEntry("/warp set <id>", "Creates a warp or updates its location."),
                            new CommandHelpEntry("/warp name <id> <display name...>",
                                    "Changes the navigator display name."),
                            new CommandHelpEntry("/warp icon <id> <material>",
                                    "Changes the navigator item icon.")
                    )),
                    new CommandHelpSection("Information", List.of(
                            new CommandHelpEntry("/warp info <id>", "Shows warp details."),
                            new CommandHelpEntry("/warp list", "Lists all configured warps.")
                    )),
                    new CommandHelpSection("Management", List.of(
                            new CommandHelpEntry("/warp remove <id>", "Permanently removes a warp.")
                    ))
            ),
            "Use /warp info <id> to review a warp before changing it."
    );

    private final JavaPlugin plugin;
    private final WarpService warpService;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;

    public WarpCommand(
            JavaPlugin plugin,
            WarpService warpService,
            MessageService messageService,
            CommandHelpRenderer helpRenderer
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.warpService = Objects.requireNonNull(warpService, "warpService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to configure warps.</red>");
            return true;
        }
        if (args.length == 0) {
            helpRenderer.send(sender, HELP_PAGE);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> {
                    if (args.length == 1) {
                        helpRenderer.send(sender, HELP_PAGE);
                    } else {
                        sendInvalidUsage(sender, "/warp help");
                    }
                }
                case "set" -> set(sender, args);
                case "remove" -> remove(sender, args);
                case "list" -> list(sender, args);
                case "info" -> info(sender, args);
                case "name" -> name(sender, args);
                case "icon" -> icon(sender, args);
                default -> sendUnknown(sender, args[0]);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not update warp configuration.", exception);
            messageService.send(sender, "<red>The warp configuration could not be saved. Check the server log.</red>");
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
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return rootSuggestions(args[0]);
        }
        if (args.length == 2 && List.of("remove", "info", "name", "icon")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return matches(warpService.getWarps().stream().map(WarpPoint::id).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("icon")) {
            return matches(Arrays.stream(Material.values())
                    .filter(WarpPoint::isDisplayableItem)
                    .map(Material::name)
                    .toList(), args[2]);
        }
        return List.of();
    }

    static List<String> rootSuggestions(String input) {
        return matches(SUBCOMMANDS, input);
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length != 2 || !(sender instanceof Player player)) {
            if (args.length != 2) {
                sendInvalidUsage(sender, "/warp set <id>");
            } else {
                messageService.send(sender, "<red>Only players can set warp positions.</red>");
            }
            return;
        }
        boolean existed = warpService.hasWarp(args[1]);
        WarpResult result = warpService.setWarp(args[1], player.getLocation());
        if (result == WarpResult.SUCCESS) {
            WarpPoint warp = warpService.getWarp(args[1]).orElseThrow();
            messageService.send(sender, setSuccessMessage(warp, existed));
        } else {
            sendResult(sender, result);
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendInvalidUsage(sender, "/warp remove <id>");
            return;
        }
        WarpResult result = warpService.removeWarp(args[1]);
        if (result == WarpResult.SUCCESS) {
            messageService.send(sender, Component.text("Warp '", NamedTextColor.GREEN)
                    .append(Component.text(args[1].toLowerCase(Locale.ROOT), NamedTextColor.WHITE))
                    .append(Component.text("' removed.", NamedTextColor.GREEN)));
        } else {
            sendResult(sender, result);
        }
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendInvalidUsage(sender, "/warp list");
            return;
        }
        List<WarpPoint> warps = warpService.getWarps();
        if (warps.isEmpty()) {
            messageService.send(sender, "<yellow>No warps are configured.</yellow>");
            return;
        }
        messageService.send(sender, listMessage(warps));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendInvalidUsage(sender, "/warp info <id>");
            return;
        }
        WarpPoint warp = warpService.getWarp(args[1]).orElse(null);
        if (warp == null) {
            sendResult(sender, WarpResult.NOT_FOUND);
            return;
        }
        messageService.send(sender, infoMessage(warp));
    }

    private void name(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendInvalidUsage(sender, "/warp name <id> <display name...>");
            return;
        }
        WarpResult result = warpService.setDisplayName(args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (result == WarpResult.SUCCESS) {
            WarpPoint warp = warpService.getWarp(args[1]).orElseThrow();
            messageService.send(sender, Component.text("Warp '", NamedTextColor.GREEN)
                    .append(Component.text(warp.id(), NamedTextColor.WHITE))
                    .append(Component.text("' display name changed to '", NamedTextColor.GREEN))
                    .append(Component.text(warp.displayName(), NamedTextColor.WHITE))
                    .append(Component.text("'.", NamedTextColor.GREEN)));
        } else {
            sendResult(sender, result);
        }
    }

    private void icon(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/warp icon <id> <material>");
            return;
        }
        Material material = Material.matchMaterial(args[2]);
        WarpResult result = warpService.setIcon(args[1], material);
        if (result == WarpResult.SUCCESS) {
            WarpPoint warp = warpService.getWarp(args[1]).orElseThrow();
            messageService.send(sender, Component.text("Warp '", NamedTextColor.GREEN)
                    .append(Component.text(warp.id(), NamedTextColor.WHITE))
                    .append(Component.text("' icon changed to ", NamedTextColor.GREEN))
                    .append(Component.text(warp.icon().name(), NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        } else {
            sendResult(sender, result);
        }
    }

    private void sendResult(CommandSender sender, WarpResult result) {
        String message = switch (result) {
            case INVALID_ID -> "<red>Warp IDs must match [a-z0-9_-]+.</red>";
            case INVALID_NAME -> "<red>The display name must not be blank.</red>";
            case INVALID_ICON -> "<red>The specified material cannot be displayed as an item.</red>";
            case NOT_FOUND -> "<red>That warp does not exist.</red>";
            case PLAYER_OFFLINE -> "<red>The player is no longer online.</red>";
            case WORLD_NOT_LOADED -> "<red>The warp world is not loaded.</red>";
            case TELEPORT_FAILED -> "<red>The teleport was cancelled or failed.</red>";
            case SUCCESS -> throw new IllegalArgumentException("Success is not an error result");
        };
        messageService.send(sender, message);
    }

    private void sendUnknown(CommandSender sender, String subcommand) {
        messageService.send(sender, Component.text("Unknown subcommand '", NamedTextColor.RED)
                .append(Component.text(subcommand, NamedTextColor.WHITE))
                .append(Component.text("'.", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text("/warp help", NamedTextColor.AQUA)));
    }

    private void sendInvalidUsage(CommandSender sender, String syntax) {
        messageService.send(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text(syntax, NamedTextColor.AQUA)));
    }

    private static Component setSuccessMessage(WarpPoint warp, boolean existed) {
        String id = warp.id();
        Component message = Component.text("Warp '", NamedTextColor.GREEN)
                .append(Component.text(id, NamedTextColor.WHITE))
                .append(Component.text(existed ? "' location updated." : "' created.", NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("Location: ", NamedTextColor.YELLOW))
                .append(Component.text(warp.position().worldName() + " @ "
                        + coordinate(warp.position().x()) + ", "
                        + coordinate(warp.position().y()) + ", "
                        + coordinate(warp.position().z()), NamedTextColor.GRAY));
        if (!existed) {
            String nameCommand = "/warp name " + id + " <display name>";
            String iconCommand = "/warp icon " + id + " <material>";
            message = message.append(Component.newline())
                    .append(Component.text("Next: ", NamedTextColor.YELLOW))
                    .append(Component.text(nameCommand, NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(nameCommand)))
                    .append(Component.newline())
                    .append(Component.text("      " + iconCommand, NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(iconCommand)));
        }
        return message;
    }

    private static Component listMessage(List<WarpPoint> warps) {
        Component output = Component.text("Configured Warps", NamedTextColor.GOLD)
                .append(Component.newline())
                .append(Component.text(warps.size() + " warp(s) configured.", NamedTextColor.GRAY));
        for (WarpPoint warp : warps) {
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(warp.id(), NamedTextColor.AQUA));
        }
        return output;
    }

    private static Component infoMessage(WarpPoint warp) {
        return Component.text("Warp • ", NamedTextColor.GOLD)
                .append(Component.text(warp.id(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(label("Display Name: ")).append(Component.text(warp.displayName(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(label("Icon: ")).append(Component.text(warp.icon().name(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(label("World: ")).append(Component.text(warp.position().worldName(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(label("Position: ")).append(Component.text(
                        coordinate(warp.position().x()) + ", " + coordinate(warp.position().y()) + ", "
                                + coordinate(warp.position().z()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(label("Rotation: ")).append(Component.text(
                        coordinate(warp.position().yaw()) + " / " + coordinate(warp.position().pitch()),
                        NamedTextColor.WHITE));
    }

    private static Component label(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static String coordinate(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static List<String> matches(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(100)
                .toList();
    }
}

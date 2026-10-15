package dev.vapee.core.lobby.warp.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

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
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WarpCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.warp.admin";
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "set", "remove", "list", "info", "name", "icon", "show", "hide", "order"
    );
    private static final CommandHelpPage HELP_PAGE = new CommandHelpPage(
            tr("warp.help.title"),
            null,
            List.of(
                    new CommandHelpSection(tr("warp.help.section.create-edit"), List.of(
                            new CommandHelpEntry("/warp set <id>", tr("warp.help.warp.set.description")),
                            new CommandHelpEntry("/warp name <id> <display name...>",
                                    tr("warp.help.warp.name.name.description")),
                            new CommandHelpEntry("/warp icon <id> <material>",
                                    tr("warp.help.warp.icon.description"))
                    )),
                    new CommandHelpSection(tr("blackjack.help.section.information"), List.of(
                            new CommandHelpEntry("/warp info <id>", tr("warp.help.warp.info.description")),
                            new CommandHelpEntry("/warp list", tr("warp.help.warp.list.description"))
                    )),
                    new CommandHelpSection(tr("blackjack.help.section.management"), List.of(
                            new CommandHelpEntry("/warp show <id>", tr("warp.help.warp.show.description")),
                            new CommandHelpEntry("/warp hide <id>", tr("warp.help.warp.hide.description")),
                            new CommandHelpEntry("/warp order <id> <order>",
                                    tr("warp.help.warp.order.description")),
                            new CommandHelpEntry("/warp remove <id>", tr("warp.help.warp.remove.description"))
                    ))
            ),
            tr("warp.help.footer")
    );

    private final Logger logger;
    private final WarpService warpService;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;

    public WarpCommand(
            JavaPlugin plugin,
            WarpService warpService,
            MessageService messageService,
            CommandHelpRenderer helpRenderer
    ) {
        this(Objects.requireNonNull(plugin, "plugin").getLogger(), warpService, messageService, helpRenderer);
    }

    WarpCommand(Logger logger, WarpService warpService, MessageService messageService,
                CommandHelpRenderer helpRenderer) {
        this.logger = Objects.requireNonNull(logger, "logger");
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
            messageService.send(sender, tr("warp.denied"));
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
                case "show" -> visibility(sender, args, true);
                case "hide" -> visibility(sender, args, false);
                case "order" -> order(sender, args);
                default -> sendUnknown(sender, args[0]);
            }
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not update warp configuration.", exception);
            messageService.send(sender, tr("warp.save-failed"));
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
        if (args.length == 2 && List.of("remove", "info", "name", "icon", "show", "hide", "order")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            String action = args[0].toLowerCase(Locale.ROOT);
            return matches(warpService.getWarps().stream()
                    .filter(warp -> !action.equals("show") || !warp.navigation().visible())
                    .filter(warp -> !action.equals("hide") || warp.navigation().visible())
                    .map(WarpPoint::id).toList(), args[1]);
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
                messageService.send(sender, tr("warp.player-only"));
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
            messageService.send(sender, tr("warp.removed", Map.of("id", Component.text(args[1].toLowerCase(Locale.ROOT), MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
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
            messageService.send(sender, tr("warp.empty"));
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
            messageService.send(sender, tr("warp.renamed", Map.of("id", Component.text(warp.id(), MessageStyle.HIGHLIGHT), "name", Component.text(warp.displayName(), MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
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
            messageService.send(sender, tr("warp.icon-changed", Map.of("id", Component.text(warp.id(), MessageStyle.HIGHLIGHT), "material", Component.text(warp.icon().name(), MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
        } else {
            sendResult(sender, result);
        }
    }

    private void visibility(CommandSender sender, String[] args, boolean visible) {
        if (args.length != 2) {
            sendInvalidUsage(sender, visible ? "/warp show <id>" : "/warp hide <id>");
            return;
        }
        WarpResult result = warpService.setNavigatorVisible(args[1], visible);
        if (result != WarpResult.SUCCESS) {
            sendResult(sender, result);
            return;
        }
        WarpPoint warp = warpService.getWarp(args[1]).orElseThrow();
        messageService.send(sender, tr(visible ? "warp.shown" : "warp.hidden", Map.of("id", Component.text(warp.id(), MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
    }

    private void order(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/warp order <id> <order>");
            return;
        }
        Integer value = parseOrder(args[2]);
        if (value == null) {
            sendResult(sender, WarpResult.INVALID_ORDER);
            return;
        }
        WarpResult result = warpService.setNavigatorOrder(args[1], value);
        if (result != WarpResult.SUCCESS) {
            sendResult(sender, result);
            return;
        }
        WarpPoint warp = warpService.getWarp(args[1]).orElseThrow();
        messageService.send(sender, tr("warp.order-changed", Map.of("id", Component.text(warp.id(), MessageStyle.HIGHLIGHT), "order", Component.text(warp.navigation().order()))).color(MessageStyle.BODY));
    }

    static Integer parseOrder(String input) {
        if (input == null || !input.matches("[0-9]+")) {
            return null;
        }
        try {
            return Integer.valueOf(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sendResult(CommandSender sender, WarpResult result) {
        Component message = switch (result) {
            case INVALID_ID -> tr("warp.result.INVALID_ID");
            case INVALID_NAME -> tr("warp.result.INVALID_NAME");
            case INVALID_ICON -> tr("warp.result.INVALID_ICON");
            case INVALID_ORDER -> tr("warp.result.INVALID_ORDER");
            case NOT_FOUND -> tr("warp.result.NOT_FOUND");
            case NOT_NAVIGABLE -> tr("warp.result.NOT_NAVIGABLE");
            case PLAYER_OFFLINE -> tr("warp.result.PLAYER_OFFLINE");
            case WORLD_NOT_LOADED -> tr("warp.result.WORLD_NOT_LOADED");
            case TELEPORT_FAILED -> tr("warp.result.TELEPORT_FAILED");
            case SUCCESS -> throw new IllegalArgumentException("Success is not an error result");
        };
        messageService.send(sender, message);
    }

    private void sendUnknown(CommandSender sender, String subcommand) {
        messageService.send(sender, tr("warp.unknown", Map.of("command", Component.text(subcommand, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/warp help", NamedTextColor.AQUA)));
    }

    private void sendInvalidUsage(CommandSender sender, String syntax) {
        messageService.send(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text(syntax, NamedTextColor.AQUA)));
    }

    private static Component setSuccessMessage(WarpPoint warp, boolean existed) {
        String id = warp.id();
        Component message = tr(existed ? "warp.location-updated" : "warp.created", Map.of("id", Component.text(id, MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY)
                .append(Component.newline())
                .append(tr("warp.label.location").color(NamedTextColor.YELLOW))
                .append(Component.text(warp.position().worldName() + " @ "
                        + coordinate(warp.position().x()) + ", "
                        + coordinate(warp.position().y()) + ", "
                        + coordinate(warp.position().z()), NamedTextColor.GRAY));
        if (!existed) {
            String nameCommand = "/warp name " + id + " <display name>";
            String iconCommand = "/warp icon " + id + " <material>";
            message = message.append(Component.newline())
                    .append(tr("warp.label.next").color(NamedTextColor.YELLOW))
                    .append(Component.text(nameCommand, NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(nameCommand)))
                    .append(Component.newline())
                    .append(Component.text("      " + iconCommand, NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(iconCommand)));
        }
        return message;
    }

    private static Component listMessage(List<WarpPoint> warps) {
        Component output = tr("warp.list.title").color(NamedTextColor.GOLD)
                .append(Component.newline())
                .append(tr("warp.list.count", Map.of("count", Component.text(warps.size()))).color(NamedTextColor.GRAY));
        for (WarpPoint warp : warps) {
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(warp.id(), NamedTextColor.AQUA))
                    .append(tr("warp.list.entry", Map.of("visibility", tr(warp.navigation().visible() ? "warp.visible" : "warp.hidden-state"), "order", Component.text(warp.navigation().order()))).color(NamedTextColor.GRAY));
        }
        return output;
    }

    private static Component infoMessage(WarpPoint warp) {
        return tr("warp.heading-prefix").color(NamedTextColor.GOLD)
                .append(Component.text(warp.id(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(tr("warp.label.display-name").color(NamedTextColor.GRAY)).append(Component.text(warp.displayName(), MessageStyle.HIGHLIGHT))
                .append(Component.newline())
                .append(tr("warp.label.icon").color(NamedTextColor.GRAY)).append(Component.text(warp.icon().name(), MessageStyle.HIGHLIGHT))
                .append(Component.newline())
                .append(tr("warp.label.navigator").color(NamedTextColor.GRAY)).append(tr(warp.navigation().visible() ? "warp.visible" : "warp.hidden-state").color(MessageStyle.HIGHLIGHT))
                .append(Component.newline())
                .append(tr("warp.label.order").color(NamedTextColor.GRAY)).append(Component.text(warp.navigation().order(), MessageStyle.HIGHLIGHT))
                .append(Component.newline())
                .append(tr("warp.label.world").color(NamedTextColor.GRAY)).append(Component.text(warp.position().worldName(), MessageStyle.HIGHLIGHT))
                .append(Component.newline())
                .append(tr("warp.label.position").color(NamedTextColor.GRAY)).append(Component.text(
                        coordinate(warp.position().x()) + ", " + coordinate(warp.position().y()) + ", "
                                + coordinate(warp.position().z()), MessageStyle.HIGHLIGHT))
                .append(Component.newline())
                .append(tr("warp.label.rotation").color(NamedTextColor.GRAY)).append(Component.text(
                        coordinate(warp.position().yaw()) + " / " + coordinate(warp.position().pitch()),
                        MessageStyle.HIGHLIGHT));
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

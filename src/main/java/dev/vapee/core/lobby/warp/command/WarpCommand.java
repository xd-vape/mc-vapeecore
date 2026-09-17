package dev.vapee.core.lobby.warp.command;

import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpResult;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.message.MessageService;
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
    private static final List<String> SUBCOMMANDS = List.of("set", "remove", "list", "info", "name", "icon");

    private final JavaPlugin plugin;
    private final WarpService warpService;
    private final MessageService messageService;

    public WarpCommand(JavaPlugin plugin, WarpService warpService, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.warpService = Objects.requireNonNull(warpService, "warpService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
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
            sendUsage(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set" -> set(sender, args);
                case "remove" -> remove(sender, args);
                case "list" -> list(sender, args);
                case "info" -> info(sender, args);
                case "name" -> name(sender, args);
                case "icon" -> icon(sender, args);
                default -> sendUsage(sender);
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
            return matches(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && List.of("remove", "info", "name", "icon")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return matches(warpService.getWarps().stream().map(WarpPoint::id).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("icon")) {
            return matches(Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .filter(material -> !material.isAir())
                    .map(Material::name)
                    .toList(), args[2]);
        }
        return List.of();
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length != 2 || !(sender instanceof Player player)) {
            messageService.send(sender, args.length != 2
                    ? "<yellow>Usage:</yellow> <white>/warp set \\<id></white>"
                    : "<red>Only players can set warp positions.</red>");
            return;
        }
        WarpResult result = warpService.setWarp(args[1], player.getLocation());
        if (result == WarpResult.SUCCESS) {
            messageService.send(sender, "<green>Warp <white>" + args[1].toLowerCase(Locale.ROOT)
                    + "</white> has been saved.</green>");
        } else {
            sendResult(sender, result);
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            messageService.send(sender, "<yellow>Usage:</yellow> <white>/warp remove \\<id></white>");
            return;
        }
        WarpResult result = warpService.removeWarp(args[1]);
        if (result == WarpResult.SUCCESS) {
            messageService.send(sender, "<green>Warp removed.</green>");
        } else {
            sendResult(sender, result);
        }
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return;
        }
        List<WarpPoint> warps = warpService.getWarps();
        if (warps.isEmpty()) {
            messageService.send(sender, "<yellow>No warps are configured.</yellow>");
            return;
        }
        messageService.send(sender, "<gold>Warps:</gold> <white>"
                + String.join(", ", warps.stream().map(WarpPoint::id).toList()) + "</white>");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            messageService.send(sender, "<yellow>Usage:</yellow> <white>/warp info \\<id></white>");
            return;
        }
        WarpPoint warp = warpService.getWarp(args[1]).orElse(null);
        if (warp == null) {
            sendResult(sender, WarpResult.NOT_FOUND);
            return;
        }
        messageService.send(sender, "<gold>Warp " + warp.id() + "</gold>");
        messageService.send(sender, "<gray>Name:</gray> <white>" + warp.displayName() + "</white>");
        messageService.send(sender, "<gray>Icon:</gray> <white>" + warp.icon().name() + "</white>");
        messageService.send(sender, "<gray>World:</gray> <white>" + warp.position().worldName() + "</white>");
        messageService.send(sender, "<gray>XYZ:</gray> <white>" + warp.position().x() + ", "
                + warp.position().y() + ", " + warp.position().z() + "</white>");
        messageService.send(sender, "<gray>Yaw/Pitch:</gray> <white>" + warp.position().yaw() + ", "
                + warp.position().pitch() + "</white>");
    }

    private void name(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageService.send(sender, "<yellow>Usage:</yellow> <white>/warp name \\<id> \\<display name...></white>");
            return;
        }
        WarpResult result = warpService.setDisplayName(args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (result == WarpResult.SUCCESS) {
            messageService.send(sender, "<green>Warp display name updated.</green>");
        } else {
            sendResult(sender, result);
        }
    }

    private void icon(CommandSender sender, String[] args) {
        if (args.length != 3) {
            messageService.send(sender, "<yellow>Usage:</yellow> <white>/warp icon \\<id> \\<material></white>");
            return;
        }
        Material material = Material.matchMaterial(args[2]);
        WarpResult result = warpService.setIcon(args[1], material);
        if (result == WarpResult.SUCCESS) {
            messageService.send(sender, "<green>Warp icon updated.</green>");
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
            case SUCCESS -> "<green>Done.</green>";
        };
        messageService.send(sender, message);
    }

    private void sendUsage(CommandSender sender) {
        messageService.send(sender,
                "<yellow>Usage:</yellow> <white>/warp \\<set|remove|list|info|name|icon> ...</white>"
        );
    }

    private List<String> matches(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .limit(100)
                .toList();
    }
}

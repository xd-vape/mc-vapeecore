package dev.vapee.core.command;

import dev.vapee.core.VapeeCore;
import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CoreCommand implements TabExecutor {

    private static final String ADMIN_PERMISSION = "vapeecore.admin";
    private static final CommandHelpPage HELP_PAGE = new CommandHelpPage(
            "Command Overview",
            "Commands are shown only when you have permission to use them.",
            List.of(
                    new CommandHelpSection("General", List.of(
                            new CommandHelpEntry("/core", "Shows the current VapeeCore status.", null,
                                    "Alias of /vapeecore."),
                            new CommandHelpEntry("/core version", "Shows plugin, Paper and Java versions."),
                            new CommandHelpEntry("/rank [player]", "Shows an online player's server rank.",
                                    "vapeecore.rank.view"),
                            new CommandHelpEntry("/ranks", "Shows the public server rank progression.",
                                    "vapeecore.ranks.view")
                    )),
                    new CommandHelpSection("Social", List.of(
                            new CommandHelpEntry("/msg <player> <message>", "Sends a private message.",
                                    "vapeecore.message.use"),
                            new CommandHelpEntry("/reply <message>", "Replies to the last conversation.",
                                    "vapeecore.message.use", "Alias: /r <message>"),
                            new CommandHelpEntry("/ignore <player>", "Ignores an online player.",
                                    "vapeecore.social.ignore"),
                            new CommandHelpEntry("/unignore <player|uuid>", "Stops ignoring a player.",
                                    "vapeecore.social.ignore"),
                            new CommandHelpEntry("/ignorelist", "Lists ignored players.",
                                    "vapeecore.social.ignore")
                    )),
                    new CommandHelpSection("Lobby", List.of(
                            new CommandHelpEntry("/spawn", "Teleports you to the lobby spawn.",
                                    "vapeecore.lobby.spawn"),
                            new CommandHelpEntry("/settings", "Opens your player settings.",
                                    "vapeecore.settings.use")
                    )),
                    new CommandHelpSection("Economy", List.of(
                            new CommandHelpEntry("/coins", "Shows your current coin balance.",
                                    "vapeecore.economy.coins"),
                            new CommandHelpEntry("/coins help", "Shows coin administration commands.",
                                    "vapeecore.economy.coins")
                    )),
                    new CommandHelpSection("Utilities", List.of(
                            new CommandHelpEntry("/build", "Toggles temporary lobby build mode.",
                                    "vapeecore.utility.build"),
                            new CommandHelpEntry("/fly [player]", "Toggles flight for an online player.",
                                    "vapeecore.utility.fly"),
                            new CommandHelpEntry("/speed <1-10> [player]", "Changes walk or flight speed.",
                                    "vapeecore.utility.speed"),
                            new CommandHelpEntry("/gamemode <mode> [player]", "Changes game mode.",
                                    "vapeecore.utility.gamemode", "Alias: /gm"),
                            new CommandHelpEntry("/tp <player>", "Teleports you to an online player.",
                                    "vapeecore.utility.teleport"),
                            new CommandHelpEntry("/tphere <player>", "Teleports an online player to you.",
                                    "vapeecore.utility.teleport.here"),
                            new CommandHelpEntry("/heal [player]", "Restores health.",
                                    "vapeecore.utility.heal"),
                            new CommandHelpEntry("/feed [player]", "Restores hunger.",
                                    "vapeecore.utility.feed")
                    )),
                    new CommandHelpSection("Administration", List.of(
                            new CommandHelpEntry("/core reload", "Reloads all coordinated configurations.",
                                    ADMIN_PERMISSION),
                            new CommandHelpEntry("/setspawn", "Sets the lobby spawn.",
                                    "vapeecore.lobby.setspawn"),
                            new CommandHelpEntry("/warp help", "Shows warp administration commands.",
                                    "vapeecore.warp.admin"),
                            new CommandHelpEntry("/blackjack help", "Shows blackjack administration commands.",
                                    "vapeecore.blackjack.admin")
                    ))
            ),
            "Click a command to insert it into chat."
    );

    private final VapeeCore plugin;
    private final ConfigService configService;
    private final MessageService messageService;
    private final ModuleManager moduleManager;
    private final PlayerService playerService;
    private final LuckPermsService luckPermsService;
    private final ReloadService reloadService;
    private final CommandHelpRenderer helpRenderer;

    public CoreCommand(
            VapeeCore plugin,
            ConfigService configService,
            MessageService messageService,
            ModuleManager moduleManager,
            PlayerService playerService,
            LuckPermsService luckPermsService,
            ReloadService reloadService,
            CommandHelpRenderer helpRenderer
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.luckPermsService = Objects.requireNonNull(luckPermsService, "luckPermsService");
        this.reloadService = Objects.requireNonNull(reloadService, "reloadService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendOverview(sender);
            return true;
        }

        if (args.length > 1) {
            sendInvalidUsage(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                helpRenderer.send(sender, HELP_PAGE);
                yield true;
            }
            case "version" -> {
                sendVersion(sender);
                yield true;
            }
            case "reload" -> {
                reloadConfig(sender);
                yield true;
            }
            default -> {
                messageService.send(sender,
                        "<red>Unknown subcommand '<white><value></white>'.</red>\n"
                                + "<yellow>Use:</yellow> <aqua>/core help</aqua>",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                                "value",
                                args[0]
                        )
                );
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }
        return rootSuggestions(args[0], sender.hasPermission(ADMIN_PERMISSION));
    }

    static List<String> rootSuggestions(String input, boolean admin) {
        List<String> options = new ArrayList<>(List.of("help", "version"));
        if (admin) {
            options.add("reload");
        }
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(value -> value.startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void sendOverview(CommandSender sender) {
        messageService.send(sender, labeledValue("Plugin: ", plugin.getPluginMeta().getName(), NamedTextColor.AQUA));
        messageService.send(sender, labeledValue("Version: ", plugin.getPluginMeta().getVersion(), NamedTextColor.WHITE));
        messageService.send(sender, labeledValue("Server: ", plugin.getServer().getVersion(), NamedTextColor.WHITE));
        messageService.send(sender, "<gray>Status:</gray> <green>Running</green>");
        messageService.send(sender, labeledValue("Active Modules: ",
                Integer.toString(moduleManager.getEnabledModules().size()), NamedTextColor.WHITE));
        messageService.send(sender, labeledValue("Loaded Players: ",
                Integer.toString(playerService.getLoadedPlayers().size()), NamedTextColor.WHITE));
        messageService.send(sender, "<gray>LuckPerms:</gray> <green>Connected</green>");
        if (sender instanceof Player player) {
            luckPermsService.getPrimaryGroup(player.getUniqueId()).ifPresent(primaryGroup ->
                    messageService.send(sender, labeledValue("Primary Group: ", primaryGroup, NamedTextColor.WHITE))
            );
        }
        boolean debug = configService.isDebugEnabled();
        messageService.send(sender, labeledValue("Debug: ", debug ? "Enabled" : "Disabled",
                debug ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void sendVersion(CommandSender sender) {
        messageService.send(sender, labeledValue(
                "VapeeCore version: ", plugin.getPluginMeta().getVersion(), NamedTextColor.WHITE));
        messageService.send(sender, labeledValue("Paper/Bukkit version: ",
                plugin.getServer().getVersion() + " / " + plugin.getServer().getBukkitVersion(),
                NamedTextColor.WHITE));
        messageService.send(sender, labeledValue(
                "Java version: ", System.getProperty("java.version"), NamedTextColor.WHITE));
    }

    private void reloadConfig(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to reload VapeeCore.</red>");
            return;
        }

        ReloadResult result = reloadService.reload();
        switch (result.status()) {
            case SUCCESS -> messageService.send(
                    sender,
                    "<green>VapeeCore configuration reloaded successfully.</green>"
            );
            case PREPARE_FAILED -> {
                messageService.send(sender, reloadFailure(
                        "Reload validation failed for ", result.failedComponent(), ". No changes were applied."));
                messageService.send(sender, "<gray>Check the server log.</gray>");
            }
            case APPLY_FAILED -> {
                messageService.send(sender, reloadFailure(
                        "Reload failed while applying ", result.failedComponent(), "."));
                messageService.send(sender, "<gray>The previous runtime configuration was restored. "
                        + "Check the server log.</gray>"
                );
            }
            case ROLLBACK_INCOMPLETE -> {
                messageService.send(sender, "<red>Reload failed and the previous state could not be restored "
                        + "completely.</red>"
                );
                messageService.send(sender, "<yellow>A controlled server restart is recommended. "
                        + "Check the server log.</yellow>"
                );
            }
            case ALREADY_RUNNING -> messageService.send(
                    sender,
                    "<yellow>A VapeeCore configuration reload is already running.</yellow>"
            );
        }
    }

    private void sendInvalidUsage(CommandSender sender) {
        messageService.send(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text("/core <help|version|reload>", NamedTextColor.AQUA)));
    }

    private Component labeledValue(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }

    private Component reloadFailure(String before, String component, String after) {
        return Component.text(before, NamedTextColor.RED)
                .append(Component.text(component, NamedTextColor.WHITE))
                .append(Component.text(after, NamedTextColor.RED));
    }
}

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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CoreCommand implements TabExecutor {

    private static final String ADMIN_PERMISSION = "vapeecore.admin";
    private static final CommandHelpPage HELP_PAGE = new CommandHelpPage(
            "Command Overview",
            "Commands are shown only when you have permission to use them.",
            List.of(
                    new CommandHelpSection("General", List.of(
                            new CommandHelpEntry("/core", "Shows the current VapeeCore status.", null,
                                    "Alias of /vapeecore."),
                            new CommandHelpEntry("/core help", "Shows this permission-filtered command overview."),
                            new CommandHelpEntry("/core version", "Shows plugin, Paper and Java versions."),
                            new CommandHelpEntry("/profile [player|uuid]", "Shows a known player's profile.",
                                    "vapeecore.profile.view"),
                            new CommandHelpEntry("/rank [player]", "Shows an online player's server rank.",
                                    "vapeecore.rank.view"),
                            new CommandHelpEntry("/ranks", "Shows the public server rank progression.",
                                    "vapeecore.ranks.view")
                    )),
                    new CommandHelpSection("Social", List.of(
                            new CommandHelpEntry("/friend help", "Shows friend requests and management commands.",
                                    "vapeecore.friend.use", "Alias: /friends"),
                            new CommandHelpEntry("/clan help", "Shows clan and invitation commands.",
                                    "vapeecore.clan.use", "Alias: /clans"),
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
                    new CommandHelpSection("Lobby & Settings", List.of(
                            new CommandHelpEntry("/spawn", "Teleports you to the lobby spawn.",
                                    "vapeecore.lobby.spawn"),
                            new CommandHelpEntry("/settings", "Opens your player settings.",
                                    "vapeecore.settings.use"),
                            new CommandHelpEntry("/settings visibility", "Opens visibility settings and player management.",
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
                                    "vapeecore.utility.feed"),
                            new CommandHelpEntry("/ping [player]", "Shows measured latency in milliseconds.",
                                    "vapeecore.utility.ping"),
                            new CommandHelpEntry("/clear [player]", "Clears a safe player inventory.",
                                    "vapeecore.utility.clear"),
                            new CommandHelpEntry("/invsee <player>", "Opens a read-only inventory snapshot.",
                                    "vapeecore.utility.invsee"),
                            new CommandHelpEntry("/enderchest [player]", "Opens an online player's ender chest.",
                                    "vapeecore.utility.enderchest")
                    )),
                    new CommandHelpSection("Moderation", List.of(
                            new CommandHelpEntry("/warn <player|uuid> <reason...>", "Records a warning for a known player.",
                                    "vapeecore.moderation.warn"),
                            new CommandHelpEntry("/ban <player|uuid> <duration|permanent> <reason...>", "Bans a known player temporarily or permanently.",
                                    "vapeecore.moderation.ban"),
                            new CommandHelpEntry("/unban <player|uuid> [reason...]", "Revokes an active ban.", "vapeecore.moderation.unban"),
                            new CommandHelpEntry("/kick <player|uuid> <reason...>", "Records a kick and disconnects an online player.", "vapeecore.moderation.kick"),
                            new CommandHelpEntry("/history <player|uuid> [page]", "Shows a known player's moderation history.", "vapeecore.moderation.history")
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

    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;
    private final Supplier<RuntimeInfo> runtimeInfo;
    private final Function<UUID, Optional<String>> primaryGroup;
    private final Supplier<ReloadResult> reload;

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
        this(messageService, helpRenderer, runtimeInfoSupplier(plugin, configService, moduleManager, playerService),
                Objects.requireNonNull(luckPermsService, "luckPermsService")::getPrimaryGroup,
                Objects.requireNonNull(reloadService, "reloadService")::reload);
    }

    CoreCommand(MessageService messageService, CommandHelpRenderer helpRenderer,
                Supplier<RuntimeInfo> runtimeInfo, Function<UUID, Optional<String>> primaryGroup,
                Supplier<ReloadResult> reload) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
        this.runtimeInfo = Objects.requireNonNull(runtimeInfo, "runtimeInfo");
        this.primaryGroup = Objects.requireNonNull(primaryGroup, "primaryGroup");
        this.reload = Objects.requireNonNull(reload, "reload");
    }

    private static Supplier<RuntimeInfo> runtimeInfoSupplier(VapeeCore plugin, ConfigService configService,
                                                            ModuleManager moduleManager, PlayerService players) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configService, "configService");
        Objects.requireNonNull(moduleManager, "moduleManager");
        Objects.requireNonNull(players, "players");
        return () -> new RuntimeInfo(plugin.getPluginMeta().getName(), plugin.getPluginMeta().getVersion(),
                plugin.getServer().getVersion(), plugin.getServer().getBukkitVersion(),
                System.getProperty("java.version"), moduleManager.getEnabledModules().size(),
                players.getLoadedPlayers().size(), configService.isDebugEnabled());
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
            String action = args[0].toLowerCase(Locale.ROOT);
            sendInvalidUsage(sender, List.of("help", "version", "reload").contains(action)
                    ? "/core " + action : "/core help");
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
        RuntimeInfo info = runtimeInfo.get();
        messageService.send(sender, labeledValue("Plugin: ", info.pluginName(), NamedTextColor.AQUA));
        messageService.send(sender, labeledValue("Version: ", info.pluginVersion(), NamedTextColor.WHITE));
        messageService.send(sender, labeledValue("Server: ", info.serverVersion(), NamedTextColor.WHITE));
        messageService.send(sender, "<gray>Status:</gray> <green>Running</green>");
        messageService.send(sender, labeledValue("Active Modules: ",
                Integer.toString(info.modules()), NamedTextColor.WHITE));
        messageService.send(sender, labeledValue("Loaded Players: ",
                Integer.toString(info.loadedPlayers()), NamedTextColor.WHITE));
        messageService.send(sender, "<gray>LuckPerms:</gray> <green>Connected</green>");
        if (sender instanceof Player player) {
            primaryGroup.apply(player.getUniqueId()).ifPresent(primaryGroup ->
                    messageService.send(sender, labeledValue("Primary Group: ", primaryGroup, NamedTextColor.WHITE))
            );
        }
        boolean debug = info.debug();
        messageService.send(sender, labeledValue("Debug: ", debug ? "Enabled" : "Disabled",
                debug ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void sendVersion(CommandSender sender) {
        RuntimeInfo info = runtimeInfo.get();
        messageService.send(sender, labeledValue(
                "VapeeCore version: ", info.pluginVersion(), NamedTextColor.WHITE));
        messageService.send(sender, labeledValue("Paper/Bukkit version: ",
                info.serverVersion() + " / " + info.bukkitVersion(),
                NamedTextColor.WHITE));
        messageService.send(sender, labeledValue(
                "Java version: ", info.javaVersion(), NamedTextColor.WHITE));
    }

    private void reloadConfig(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to reload VapeeCore.</red>");
            return;
        }

        ReloadResult result = reload.get();
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

    private void sendInvalidUsage(CommandSender sender, String syntax) {
        messageService.send(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text(syntax, NamedTextColor.AQUA)));
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

    record RuntimeInfo(String pluginName, String pluginVersion, String serverVersion, String bukkitVersion,
                       String javaVersion, int modules, int loadedPlayers, boolean debug) { }
}

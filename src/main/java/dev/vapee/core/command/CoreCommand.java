package dev.vapee.core.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

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
            tr("core.help.title"),
            tr("core.help.description"),
            List.of(
                    new CommandHelpSection(tr("core.help.section.general"), List.of(
                            new CommandHelpEntry("/core", tr("core.help.core.description"), null,
                                    tr("core.help.core.hint")),
                            new CommandHelpEntry("/core help", tr("core.help.core.help.description")),
                            new CommandHelpEntry("/core version", tr("core.help.core.version.description")),
                            new CommandHelpEntry("/profile [player|uuid]", tr("core.help.profile.description"),
                                    "vapeecore.profile.view"),
                            new CommandHelpEntry("/rank [player]", tr("core.help.rank.description"),
                                    "vapeecore.rank.view"),
                            new CommandHelpEntry("/ranks", tr("core.help.ranks.description"),
                                    "vapeecore.ranks.view")
                    )),
                    new CommandHelpSection(tr("core.help.section.social"), List.of(
                            new CommandHelpEntry("/friend help", tr("core.help.friend.help.description"),
                                    "vapeecore.friend.use", tr("core.help.friend.help.hint")),
                            new CommandHelpEntry("/clan help", tr("core.help.clan.help.description"),
                                    "vapeecore.clan.use", tr("core.help.clan.help.hint")),
                            new CommandHelpEntry("/msg <player> <message>", tr("core.help.msg.description"),
                                    "vapeecore.message.use"),
                            new CommandHelpEntry("/reply <message>", tr("core.help.reply.description"),
                                    "vapeecore.message.use", tr("core.help.reply.hint")),
                            new CommandHelpEntry("/ignore <player>", tr("core.help.ignore.description"),
                                    "vapeecore.social.ignore"),
                            new CommandHelpEntry("/unignore <player|uuid>", tr("core.help.unignore.description"),
                                    "vapeecore.social.ignore"),
                            new CommandHelpEntry("/ignorelist", tr("core.help.ignorelist.description"),
                                    "vapeecore.social.ignore")
                    )),
                    new CommandHelpSection(tr("core.help.section.lobby-settings"), List.of(
                            new CommandHelpEntry("/spawn", tr("core.help.spawn.description"),
                                    "vapeecore.lobby.spawn"),
                            new CommandHelpEntry("/settings", tr("core.help.settings.description"),
                                    "vapeecore.settings.use"),
                            new CommandHelpEntry("/settings visibility", tr("core.help.settings.visibility.description"),
                                    "vapeecore.settings.use")
                    )),
                    new CommandHelpSection(tr("core.help.section.gameplay"), List.of(
                            new CommandHelpEntry("/quests", tr("core.help.quests.description"),
                                    "vapeecore.quest.use", tr("core.help.quests.hint"))
                    )),
                    new CommandHelpSection(tr("core.help.section.economy"), List.of(
                            new CommandHelpEntry("/coins", tr("core.help.coins.description"),
                                    "vapeecore.economy.coins"),
                            new CommandHelpEntry("/coins help", tr("core.help.coins.help.description"),
                                    "vapeecore.economy.coins")
                    )),
                    new CommandHelpSection(tr("core.help.section.utilities"), List.of(
                            new CommandHelpEntry("/build", tr("core.help.build.description"),
                                    "vapeecore.utility.build"),
                            new CommandHelpEntry("/fly [player]", tr("core.help.fly.description"),
                                    "vapeecore.utility.fly"),
                            new CommandHelpEntry("/speed <1-10> [player]", tr("core.help.speed.description"),
                                    "vapeecore.utility.speed"),
                            new CommandHelpEntry("/gamemode <mode> [player]", tr("core.help.gamemode.description"),
                                    "vapeecore.utility.gamemode", tr("core.help.gamemode.hint")),
                            new CommandHelpEntry("/tp <player>", tr("core.help.tp.description"),
                                    "vapeecore.utility.teleport"),
                            new CommandHelpEntry("/tphere <player>", tr("core.help.tphere.description"),
                                    "vapeecore.utility.teleport.here"),
                            new CommandHelpEntry("/heal [player]", tr("core.help.heal.description"),
                                    "vapeecore.utility.heal"),
                            new CommandHelpEntry("/feed [player]", tr("core.help.feed.description"),
                                    "vapeecore.utility.feed"),
                            new CommandHelpEntry("/ping [player]", tr("core.help.ping.description"),
                                    "vapeecore.utility.ping"),
                            new CommandHelpEntry("/clear [player]", tr("core.help.clear.description"),
                                    "vapeecore.utility.clear"),
                            new CommandHelpEntry("/invsee <player>", tr("core.help.invsee.description"),
                                    "vapeecore.utility.invsee"),
                            new CommandHelpEntry("/enderchest [player]", tr("core.help.enderchest.description"),
                                    "vapeecore.utility.enderchest")
                    )),
                    new CommandHelpSection(tr("core.help.section.moderation"), List.of(
                            new CommandHelpEntry("/mute <player|uuid> <duration|permanent> <reason...>", tr("core.help.mute.description"), "vapeecore.moderation.mute"),
                            new CommandHelpEntry("/unmute <player|uuid> [reason...]", tr("core.help.unmute.description"), "vapeecore.moderation.unmute"),
                            new CommandHelpEntry("/warn <player|uuid> <reason...>", tr("core.help.warn.description"),
                                    "vapeecore.moderation.warn"),
                            new CommandHelpEntry("/ban <player|uuid> <duration|permanent> <reason...>", tr("core.help.ban.description"),
                                    "vapeecore.moderation.ban"),
                            new CommandHelpEntry("/unban <player|uuid> [reason...]", tr("core.help.unban.description"), "vapeecore.moderation.unban"),
                            new CommandHelpEntry("/kick <player|uuid> <reason...>", tr("core.help.kick.description"), "vapeecore.moderation.kick"),
                            new CommandHelpEntry("/history <player|uuid> [page]", tr("core.help.history.description"), "vapeecore.moderation.history")
                    )),
                    new CommandHelpSection(tr("core.help.section.administration"), List.of(
                            new CommandHelpEntry("/core reload", tr("core.help.core.reload.description"),
                                    ADMIN_PERMISSION),
                            new CommandHelpEntry("/setspawn", tr("core.help.setspawn.description"),
                                    "vapeecore.lobby.setspawn"),
                            new CommandHelpEntry("/warp help", tr("core.help.warp.help.description"),
                                    "vapeecore.warp.admin"),
                            new CommandHelpEntry("/blackjack help", tr("core.help.blackjack.help.description"),
                                    "vapeecore.blackjack.admin")
                    ))
            ),
            tr("core.help.footer")
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
                messageService.sendTranslated(sender, "core.unknown-command",
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
        messageService.send(sender, labeledValue("core.plugin", info.pluginName(), NamedTextColor.AQUA));
        messageService.send(sender, labeledValue("core.version", info.pluginVersion(), MessageStyle.HIGHLIGHT));
        messageService.send(sender, labeledValue("core.server", info.serverVersion(), MessageStyle.HIGHLIGHT));
        messageService.send(sender, tr("core.status"));
        messageService.send(sender, labeledValue("core.active-modules",
                Integer.toString(info.modules()), MessageStyle.HIGHLIGHT));
        messageService.send(sender, labeledValue("core.loaded-players",
                Integer.toString(info.loadedPlayers()), MessageStyle.HIGHLIGHT));
        messageService.send(sender, tr("core.luckperms"));
        if (sender instanceof Player player) {
            primaryGroup.apply(player.getUniqueId()).ifPresent(primaryGroup ->
                    messageService.send(sender, labeledValue("core.primary-group", primaryGroup, MessageStyle.HIGHLIGHT))
            );
        }
        boolean debug = info.debug();
        messageService.send(sender, tr("core.debug", java.util.Map.of("value", tr(debug ? "common.enabled" : "common.disabled")
                .color(debug ? NamedTextColor.GREEN : NamedTextColor.RED))).color(NamedTextColor.GRAY));
    }

    private void sendVersion(CommandSender sender) {
        RuntimeInfo info = runtimeInfo.get();
        messageService.send(sender, labeledValue(
                "core.plugin-version", info.pluginVersion(), MessageStyle.HIGHLIGHT));
        messageService.send(sender, labeledValue("core.paper-version",
                info.serverVersion() + " / " + info.bukkitVersion(),
                MessageStyle.HIGHLIGHT));
        messageService.send(sender, labeledValue(
                "core.java-version", info.javaVersion(), MessageStyle.HIGHLIGHT));
    }

    private void reloadConfig(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messageService.send(sender, tr("core.reload.denied"));
            return;
        }

        ReloadResult result = reload.get();
        switch (result.status()) {
            case SUCCESS -> messageService.send(
                    sender,
                    tr("core.reload.success")
            );
            case PREPARE_FAILED -> {
                messageService.send(sender, reloadFailure(
                        "core.reload.validation", result.failedComponent()));
                messageService.send(sender, tr("core.reload.log"));
            }
            case APPLY_FAILED -> {
                messageService.send(sender, reloadFailure(
                        "core.reload.apply", result.failedComponent()));
                messageService.send(sender, tr("core.reload.restored")
                );
            }
            case ROLLBACK_INCOMPLETE -> {
                messageService.send(sender, tr("core.reload.incomplete")
                );
                messageService.send(sender, tr("core.reload.restart")
                );
            }
            case ALREADY_RUNNING -> messageService.send(
                    sender,
                    tr("core.reload.running")
            );
        }
    }

    private void sendInvalidUsage(CommandSender sender, String syntax) {
        messageService.send(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text(syntax, NamedTextColor.AQUA)));
    }

    private Component labeledValue(String label, String value, NamedTextColor valueColor) {
        return tr(label, java.util.Map.of("value", Component.text(value, valueColor))).color(NamedTextColor.GRAY);
    }

    private Component reloadFailure(String key, String component) {
        return tr(key, java.util.Map.of("participant", Component.text(component, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED);
    }

    record RuntimeInfo(String pluginName, String pluginVersion, String serverVersion, String bukkitVersion,
                       String javaVersion, int modules, int loadedPlayers, boolean debug) { }
}

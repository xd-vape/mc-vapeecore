package dev.vapee.core.command;

import dev.vapee.core.VapeeCore;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

public final class CoreCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "vapeecore.admin";

    private final VapeeCore plugin;
    private final ConfigService configService;
    private final MessageService messageService;
    private final ModuleManager moduleManager;
    private final PlayerService playerService;
    private final LuckPermsService luckPermsService;
    private final ReloadService reloadService;

    public CoreCommand(
            VapeeCore plugin,
            ConfigService configService,
            MessageService messageService,
            ModuleManager moduleManager,
            PlayerService playerService,
            LuckPermsService luckPermsService,
            ReloadService reloadService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.luckPermsService = Objects.requireNonNull(luckPermsService, "luckPermsService");
        this.reloadService = Objects.requireNonNull(reloadService, "reloadService");
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
            sendUsage(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> {
                sendVersion(sender);
                yield true;
            }
            case "reload" -> {
                reloadConfig(sender);
                yield true;
            }
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private void sendOverview(CommandSender sender) {
        messageService.send(sender, "<gray>Plugin:</gray> <aqua>" + plugin.getPluginMeta().getName() + "</aqua>");
        messageService.send(sender, "<gray>Version:</gray> <white>" + plugin.getPluginMeta().getVersion() + "</white>");
        messageService.send(sender, "<gray>Server:</gray> <white>" + plugin.getServer().getVersion() + "</white>");
        messageService.send(sender, "<gray>Status:</gray> <green>Running</green>");
        messageService.send(sender, "<gray>Active Modules:</gray> <white>"
                + moduleManager.getEnabledModules().size() + "</white>"
        );
        messageService.send(sender, "<gray>Loaded Players:</gray> <white>"
                + playerService.getLoadedPlayers().size() + "</white>"
        );
        messageService.send(sender, "<gray>LuckPerms:</gray> <green>Connected</green>");
        if (sender instanceof Player player) {
            luckPermsService.getPrimaryGroup(player.getUniqueId()).ifPresent(primaryGroup ->
                    messageService.send(sender, "<gray>Primary Group:</gray> <white>"
                            + primaryGroup + "</white>"
                    )
            );
        }
        String debugStatus = configService.isDebugEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>";
        messageService.send(sender, "<gray>Debug:</gray> " + debugStatus);
    }

    private void sendVersion(CommandSender sender) {
        messageService.send(sender, "<gray>VapeeCore version:</gray> <white>" + plugin.getPluginMeta().getVersion() + "</white>");
        messageService.send(sender, "<gray>Paper/Bukkit version:</gray> <white>"
                + plugin.getServer().getVersion() + " / " + plugin.getServer().getBukkitVersion() + "</white>"
        );
        messageService.send(sender, "<gray>Java version:</gray> <white>" + System.getProperty("java.version") + "</white>");
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
                messageService.send(sender, "<red>Reload validation failed for "
                        + result.failedComponent() + ". No changes were applied.</red>"
                );
                messageService.send(sender, "<gray>Check the server log.</gray>");
            }
            case APPLY_FAILED -> {
                messageService.send(sender, "<red>Reload failed while applying "
                        + result.failedComponent() + ".</red>"
                );
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

    private void sendUsage(CommandSender sender) {
        messageService.send(sender, "<yellow>Usage:</yellow> <white>/vapeecore [version|reload]</white>");
    }
}

package dev.vapee.core.rank;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.rank.command.RankCommand;
import dev.vapee.core.rank.command.RanksCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class RankModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final PermissionModule permissionModule;
    private final MessageService messageService;

    private RankService rankService;
    private PluginCommand rankCommand;
    private PluginCommand ranksCommand;

    public RankModule(
            JavaPlugin plugin,
            ConfigService configService,
            PermissionModule permissionModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.permissionModule = Objects.requireNonNull(permissionModule, "permissionModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Rank";
    }

    @Override
    public void enable() {
        RankService newRankService = new RankService(
                configService,
                permissionModule.getLuckPermsService()
        );
        PluginCommand newRankCommand = Objects.requireNonNull(
                plugin.getCommand("rank"),
                "Command 'rank' is missing from plugin.yml"
        );
        PluginCommand newRanksCommand = Objects.requireNonNull(
                plugin.getCommand("ranks"),
                "Command 'ranks' is missing from plugin.yml"
        );
        RankCommand rankExecutor = new RankCommand(
                newRankService,
                configService::getServerName,
                messageService,
                plugin.getServer()::getPlayerExact,
                plugin.getServer()::getOnlinePlayers
        );
        RanksCommand ranksExecutor = new RanksCommand(
                newRankService,
                configService::getServerName,
                messageService
        );

        try {
            newRankCommand.setExecutor(rankExecutor);
            newRankCommand.setTabCompleter(rankExecutor);
            newRanksCommand.setExecutor(ranksExecutor);
            newRanksCommand.setTabCompleter(ranksExecutor);
        } catch (RuntimeException exception) {
            clearCommand(newRankCommand);
            clearCommand(newRanksCommand);
            throw exception;
        }

        rankService = newRankService;
        rankCommand = newRankCommand;
        ranksCommand = newRanksCommand;
        plugin.getLogger().info("Rank module enabled with LuckPerms as the source of truth.");
    }

    @Override
    public void disable() {
        clearCommand(rankCommand);
        clearCommand(ranksCommand);
        ranksCommand = null;
        rankCommand = null;
        rankService = null;
    }

    public RankService getRankService() {
        return Objects.requireNonNull(rankService, "RankModule is not enabled");
    }

    private static void clearCommand(PluginCommand command) {
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }
}

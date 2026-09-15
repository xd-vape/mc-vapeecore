package dev.vapee.core.economy;

import dev.vapee.core.economy.command.CoinsCommand;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class EconomyModule implements CoreModule {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final MessageService messageService;

    private EconomyService economyService;
    private PluginCommand coinsCommand;

    public EconomyModule(JavaPlugin plugin, PlayerModule playerModule, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Economy";
    }

    @Override
    public void enable() {
        PlayerService playerService = playerModule.getPlayerService();
        EconomyService newEconomyService = new EconomyService(playerService);
        PluginCommand newCoinsCommand = Objects.requireNonNull(
                plugin.getCommand("coins"),
                "Command 'coins' is missing from plugin.yml"
        );

        try {
            newCoinsCommand.setExecutor(new CoinsCommand(plugin, newEconomyService, messageService));
        } catch (RuntimeException exception) {
            newCoinsCommand.setExecutor(null);
            throw exception;
        }

        economyService = newEconomyService;
        coinsCommand = newCoinsCommand;
        plugin.getLogger().info("Economy module enabled.");
    }

    @Override
    public void disable() {
        if (coinsCommand != null) {
            coinsCommand.setExecutor(null);
        }

        coinsCommand = null;
        economyService = null;
    }

    public EconomyService getEconomyService() {
        return Objects.requireNonNull(economyService, "EconomyModule is not enabled");
    }
}

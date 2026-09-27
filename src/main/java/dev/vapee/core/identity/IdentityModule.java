package dev.vapee.core.identity;

import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.identity.command.ProfileCommand;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.rank.RankModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class IdentityModule implements CoreModule {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final RankModule rankModule;
    private final EconomyModule economyModule;
    private final MessageService messageService;

    private PlayerIdentityService identityService;
    private PlayerProfileService profileService;
    private PluginCommand profileCommand;

    public IdentityModule(JavaPlugin plugin, PlayerModule playerModule, RankModule rankModule,
                          EconomyModule economyModule, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.rankModule = Objects.requireNonNull(rankModule, "rankModule");
        this.economyModule = Objects.requireNonNull(economyModule, "economyModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Identity";
    }

    @Override
    public void enable() {
        PlayerIdentityService newIdentityService = new PlayerIdentityService(playerModule.getPlayerService());
        PlayerProfileService newProfileService = new PlayerProfileService(
                plugin, newIdentityService, economyModule.getEconomyService(), rankModule.getRankService());
        PluginCommand command = Objects.requireNonNull(plugin.getCommand("profile"),
                "Command 'profile' is missing from plugin.yml");
        try {
            ProfileCommand executor = new ProfileCommand(plugin, newProfileService, messageService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } catch (RuntimeException exception) {
            command.setExecutor(null);
            command.setTabCompleter(null);
            throw exception;
        }
        identityService = newIdentityService;
        profileService = newProfileService;
        profileCommand = command;
        plugin.getLogger().info("Identity module enabled.");
    }

    @Override
    public void disable() {
        if (profileCommand != null) {
            profileCommand.setExecutor(null);
            profileCommand.setTabCompleter(null);
        }
        profileCommand = null;
        profileService = null;
        identityService = null;
    }

    public PlayerIdentityService getIdentityService() {
        return Objects.requireNonNull(identityService, "IdentityModule is not enabled");
    }

    public PlayerProfileService getProfileService() {
        return Objects.requireNonNull(profileService, "IdentityModule is not enabled");
    }
}

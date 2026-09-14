package dev.vapee.core.player;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.repository.FilePlayerRepository;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Objects;

public final class PlayerModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final MessageService messageService;

    private FilePlayerRepository repository;
    private PlayerService playerService;
    private PlayerListener playerListener;

    public PlayerModule(JavaPlugin plugin, ConfigService configService, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Player";
    }

    @Override
    public void enable() {
        Path playersDirectory = plugin.getDataFolder().toPath().resolve("players");
        FilePlayerRepository newRepository = new FilePlayerRepository(playersDirectory, plugin.getLogger());
        newRepository.initialize();

        PlayerService newPlayerService = new PlayerService(newRepository, plugin.getLogger());
        PlayerListener newPlayerListener = new PlayerListener(
                newPlayerService,
                configService,
                messageService,
                plugin.getLogger()
        );

        plugin.getServer().getPluginManager().registerEvents(newPlayerListener, plugin);

        repository = newRepository;
        playerService = newPlayerService;
        playerListener = newPlayerListener;
    }

    @Override
    public void disable() {
        if (playerListener != null) {
            HandlerList.unregisterAll(playerListener);
        }
        if (playerService != null) {
            playerService.saveAll();
            playerService.clearLoadedPlayers();
        }

        playerListener = null;
        playerService = null;
        repository = null;
    }

    public PlayerService getPlayerService() {
        return Objects.requireNonNull(playerService, "PlayerModule is not enabled");
    }
}

package dev.vapee.core.player;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class PlayerModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final MessageService messageService;
    private final Consumer<EnableStage> enableCheckpoint;

    private FilePlayerRepository repository;
    private PlayerService playerService;
    private PlayerSettingsService playerSettingsService;
    private PlayerListener playerListener;
    private boolean enabling;
    private boolean cleaning;
    private CleanupResources pendingCleanup;

    enum EnableStage { SERVICES_CREATED, LISTENER_REGISTERED, RUNTIME_PUBLISHED }

    public PlayerModule(JavaPlugin plugin, ConfigService configService, MessageService messageService) {
        this(plugin, configService, messageService, stage -> { });
    }

    PlayerModule(JavaPlugin plugin, ConfigService configService, MessageService messageService,
                 Consumer<EnableStage> enableCheckpoint) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.enableCheckpoint = Objects.requireNonNull(enableCheckpoint, "enableCheckpoint");
    }

    @Override
    public String getName() {
        return "Player";
    }

    @Override
    public void enable() {
        if (playerService != null || enabling || cleaning) {
            throw new IllegalStateException("Player module is already enabled or changing lifecycle");
        }
        if (pendingCleanup != null) {
            throw new IllegalStateException(
                    "Previous Player cleanup is incomplete; resolve storage/listener failures and retry disable() before enable().",
                    pendingCleanup.failure);
        }
        enabling = true;
        PlayerService newPlayerService = null;
        PlayerListener newPlayerListener = null;
        try {
            Path playersDirectory = plugin.getDataFolder().toPath().resolve("players");
            FilePlayerRepository newRepository = new FilePlayerRepository(playersDirectory, plugin.getLogger());
            newRepository.initialize();
            newPlayerService = new PlayerService(newRepository, plugin.getLogger());
            PlayerSettingsService newSettings = new PlayerSettingsService(newPlayerService);
            enableCheckpoint.accept(EnableStage.SERVICES_CREATED);
            PlayerService listenerService = newPlayerService;
            newPlayerListener = new PlayerListener(listenerService, configService, messageService,
                    plugin.getLogger(), () -> !enabling && !cleaning && playerService == listenerService);
            plugin.getServer().getPluginManager().registerEvents(newPlayerListener, plugin);
            enableCheckpoint.accept(EnableStage.LISTENER_REGISTERED);
            repository = newRepository;
            playerService = newPlayerService;
            playerSettingsService = newSettings;
            playerListener = newPlayerListener;
            enableCheckpoint.accept(EnableStage.RUNTIME_PUBLISHED);
        } catch (RuntimeException exception) {
            clearRuntime();
            CleanupResources acquired = new CleanupResources(newPlayerService, newPlayerListener);
            cleanup(acquired, exception);
            if (!acquired.released()) pendingCleanup = acquired;
            throw exception;
        } finally {
            enabling = false;
        }
    }

    @Override
    public void disable() {
        if (enabling || cleaning) {
            throw new IllegalStateException("Player module is already changing lifecycle");
        }
        cleaning = true;
        CleanupResources resources = pendingCleanup == null
                ? new CleanupResources(playerService, playerListener) : pendingCleanup;
        clearRuntime(); // Revoke getters and the exact generation's event authority before external cleanup.
        RuntimeException failure = new IllegalStateException("Player cleanup failed.");
        try {
            cleanup(resources, failure);
            pendingCleanup = resources.released() ? null : resources;
            if (failure.getSuppressed().length != 0) throw failure;
        } finally {
            cleaning = false;
        }
    }

    private void cleanup(CleanupResources resources, RuntimeException failure) {
        if (resources.listener != null) {
            try {
                HandlerList.unregisterAll(resources.listener);
                resources.listener = null;
            } catch (RuntimeException exception) {
                failure.addSuppressed(new IllegalStateException("Could not unregister Player listener", exception));
            }
        }
        // A refused listener removal must never skip independent profile commits.
        if (resources.service != null) {
            try {
                resources.service.saveAllOrThrow();
                resources.service.clearLoadedPlayers();
                resources.service = null;
            } catch (RuntimeException exception) {
                failure.addSuppressed(exception);
                try {
                    plugin.getLogger().log(Level.SEVERE,
                            "INCOMPLETE PLAYER SHUTDOWN: " + exception.getMessage()
                                    + " In-memory profiles retained for explicit same-process disable() retry."
                                    + " Operator intervention required; JVM exit loses unsaved changes.", exception);
                } catch (RuntimeException diagnostic) {
                    if (diagnostic != exception) failure.addSuppressed(diagnostic);
                }
            }
        }
        resources.failure = failure;
    }

    private void clearRuntime() {
        playerListener = null;
        playerSettingsService = null;
        playerService = null;
        repository = null;
    }

    public PlayerService getPlayerService() {
        return Objects.requireNonNull(enabling || cleaning ? null : playerService, "PlayerModule is not enabled");
    }

    public PlayerSettingsService getPlayerSettingsService() {
        return Objects.requireNonNull(enabling || cleaning ? null : playerSettingsService, "PlayerModule is not enabled");
    }

    // Temporary ownership of the existing cache, never a second active runtime or persistence authority.
    private static final class CleanupResources {
        private PlayerService service;
        private PlayerListener listener;
        private RuntimeException failure;

        private CleanupResources(PlayerService service, PlayerListener listener) {
            this.service = service;
            this.listener = listener;
        }

        private boolean released() { return service == null && listener == null; }
    }
}

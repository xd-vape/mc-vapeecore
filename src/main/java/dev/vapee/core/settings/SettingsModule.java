package dev.vapee.core.settings;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presentation.PresentationModule;
import dev.vapee.core.presentation.PresentationService;
import dev.vapee.core.settings.command.SettingsCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SettingsModule implements CoreModule {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final PresentationModule presentationModule;
    private final MessageService messageService;

    private PlayerSettingsService playerSettingsService;
    private PresentationService presentationService;
    private SettingsMenu settingsMenu;
    private SettingsListener settingsListener;
    private PluginCommand settingsCommand;

    public SettingsModule(
            JavaPlugin plugin,
            PlayerModule playerModule,
            PresentationModule presentationModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.presentationModule = Objects.requireNonNull(presentationModule, "presentationModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Settings";
    }

    @Override
    public void enable() {
        PlayerSettingsService newPlayerSettingsService = playerModule.getPlayerSettingsService();
        PresentationService newPresentationService = presentationModule.getPresentationService();
        SettingsMenu newSettingsMenu = new SettingsMenu(plugin, newPlayerSettingsService, messageService);
        SettingsListener newSettingsListener = new SettingsListener(
                newSettingsMenu,
                newPlayerSettingsService,
                newPresentationService,
                messageService,
                plugin.getLogger()
        );
        PluginCommand newSettingsCommand = Objects.requireNonNull(
                plugin.getCommand("settings"),
                "Command 'settings' is missing from plugin.yml"
        );

        try {
            plugin.getServer().getPluginManager().registerEvents(newSettingsListener, plugin);
            newSettingsCommand.setExecutor(new SettingsCommand(newSettingsMenu, messageService));
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newSettingsListener);
            newSettingsCommand.setExecutor(null);
            throw exception;
        }

        playerSettingsService = newPlayerSettingsService;
        presentationService = newPresentationService;
        settingsMenu = newSettingsMenu;
        settingsListener = newSettingsListener;
        settingsCommand = newSettingsCommand;
        plugin.getLogger().info("Settings module enabled.");
    }

    @Override
    public void disable() {
        if (settingsListener != null) {
            HandlerList.unregisterAll(settingsListener);
        }
        if (settingsCommand != null) {
            settingsCommand.setExecutor(null);
        }

        settingsCommand = null;
        settingsListener = null;
        settingsMenu = null;
        presentationService = null;
        playerSettingsService = null;
    }

    public SettingsMenu getSettingsMenu() {
        return Objects.requireNonNull(settingsMenu, "SettingsModule is not enabled");
    }
}

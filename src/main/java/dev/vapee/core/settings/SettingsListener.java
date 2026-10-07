package dev.vapee.core.settings;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presentation.PresentationService;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SettingsListener implements Listener {

    private final SettingsMenu settingsMenu;
    private final PlayerSettingsService playerSettingsService;
    private final Consumer<Player> presentationRefresh;
    private final Consumer<Player> visibilityMenuOpener;
    private final Consumer<Player> soundFeedback;
    private final MessageService messageService;
    private final Logger logger;

    public SettingsListener(
            SettingsMenu settingsMenu,
            PlayerSettingsService playerSettingsService,
            PresentationService presentationService,
            Consumer<Player> visibilityMenuOpener,
            MessageService messageService,
            Logger logger
    ) {
        this(settingsMenu, playerSettingsService,
                Objects.requireNonNull(presentationService, "presentationService")::updatePlayer,
                visibilityMenuOpener,
                player -> playFeedbackSound(player, playerSettingsService, logger),
                messageService, logger);
    }

    SettingsListener(
            SettingsMenu settingsMenu,
            PlayerSettingsService playerSettingsService,
            Consumer<Player> presentationRefresh,
            Consumer<Player> visibilityMenuOpener,
            Consumer<Player> soundFeedback,
            MessageService messageService,
            Logger logger
    ) {
        this.settingsMenu = Objects.requireNonNull(settingsMenu, "settingsMenu");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.presentationRefresh = Objects.requireNonNull(presentationRefresh, "presentationRefresh");
        this.visibilityMenuOpener = Objects.requireNonNull(visibilityMenuOpener, "visibilityMenuOpener");
        this.soundFeedback = Objects.requireNonNull(soundFeedback, "soundFeedback");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof SettingsInventoryHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !settingsMenu.isActive(player, topInventory, holder)) {
            return;
        }
        if (!player.hasPermission(SettingsCommand.PERMISSION)) {
            settingsMenu.forgetIfActive(player.getUniqueId(), topInventory);
            if (player.getOpenInventory().getTopInventory() == topInventory) player.closeInventory();
            messageService.send(player, "<red>You do not have permission to use settings.</red>");
            return;
        }
        if (event.getClickedInventory() != topInventory) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == SettingsMenu.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == SettingsMenu.REFRESH_SLOT) {
            refreshOrClose(player, topInventory);
            return;
        }
        settingsMenu.entryAt(slot).ifPresent(entry -> executeEntry(player, topInventory, entry));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingsInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SettingsInventoryHolder holder) {
            settingsMenu.forgetIfActive(holder.getOwnerUniqueId(), top);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        settingsMenu.forget(event.getPlayer().getUniqueId());
    }

    private void executeEntry(Player player, Inventory inventory, SettingsMenuEntry entry) {
        UUID uniqueId = player.getUniqueId();
        Optional<PlayerSettings> optionalSettings = playerSettingsService.getSettings(uniqueId);
        if (optionalSettings.isEmpty()) {
            handleUnavailableProfile(player);
            return;
        }

        SettingsMenuEntry.Result result;
        try {
            result = entry.action().execute(player, optionalSettings.get(),
                    new SettingsMenuEntry.Context(playerSettingsService, presentationRefresh, visibilityMenuOpener));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not save player settings for " + uniqueId + ".", exception);
            messageService.send(player, "<red>Your setting could not be saved. Please try again.</red>");
            refreshOrClose(player, inventory);
            return;
        }

        if (result == SettingsMenuEntry.Result.OPENED) return;
        if (result == SettingsMenuEntry.Result.UNAVAILABLE) {
            handleUnavailableProfile(player);
            return;
        }
        if (!refreshOrClose(player, inventory)) {
            return;
        }

        if (playerSettingsService.areSoundsEnabled(uniqueId).orElse(false)) soundFeedback.accept(player);
    }

    private boolean refreshOrClose(Player player, Inventory inventory) {
        if (settingsMenu.refresh(player, inventory)) {
            return true;
        }

        handleUnavailableProfile(player);
        return false;
    }

    private void handleUnavailableProfile(Player player) {
        settingsMenu.forget(player.getUniqueId());
        player.closeInventory();
        messageService.send(player, "<red>Your player profile is not available.</red>");
    }

    private static void playFeedbackSound(Player player, PlayerSettingsService settings, Logger logger) {
        if (!settings.areSoundsEnabled(player.getUniqueId()).orElse(false)) return;
        try {
            player.playSound(
                    player.getLocation(),
                    Sound.UI_BUTTON_CLICK,
                    SoundCategory.MASTER,
                    0.5F,
                    1.0F
            );
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not play settings feedback sound for " + player.getUniqueId() + ".",
                    exception
            );
        }
    }
}

package dev.vapee.core.settings;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presentation.PresentationService;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
        SettingsInventoryHolder holder = getSettingsHolder(topInventory);
        if (holder == null) {
            return;
        }

        event.setCancelled(true);
        HumanEntity clickingEntity = event.getWhoClicked();
        if (!(clickingEntity instanceof Player player)
                || !holder.getOwnerUniqueId().equals(player.getUniqueId())) {
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
        if (slot == SettingsMenu.VISIBILITY_SLOT) {
            visibilityMenuOpener.accept(player);
            return;
        }
        if (slot != SettingsMenu.SCOREBOARD_SLOT
                && slot != SettingsMenu.SOUNDS_SLOT
                && slot != SettingsMenu.PRIVATE_MESSAGES_SLOT
                && slot != SettingsMenu.FRIEND_REQUESTS_SLOT) {
            return;
        }

        toggleSetting(player, topInventory, slot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        SettingsInventoryHolder holder = getSettingsHolder(topInventory);
        if (holder == null) {
            return;
        }

        if (!holder.getOwnerUniqueId().equals(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int topSize = topInventory.getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private void toggleSetting(Player player, Inventory inventory, int slot) {
        UUID uniqueId = player.getUniqueId();
        Optional<PlayerSettings> optionalSettings = playerSettingsService.getSettings(uniqueId);
        if (optionalSettings.isEmpty()) {
            handleUnavailableProfile(player);
            return;
        }

        PlayerSettings settings = optionalSettings.get();
        boolean saved;
        try {
            saved = switch (slot) {
                case SettingsMenu.SCOREBOARD_SLOT -> playerSettingsService.setScoreboardEnabled(
                        uniqueId,
                        !settings.isScoreboardEnabled()
                );
                case SettingsMenu.SOUNDS_SLOT -> playerSettingsService.setSoundsEnabled(
                        uniqueId,
                        !settings.isSoundsEnabled()
                );
                case SettingsMenu.PRIVATE_MESSAGES_SLOT -> playerSettingsService.setPrivateMessagesEnabled(
                        uniqueId,
                        !settings.isPrivateMessagesEnabled()
                );
                case SettingsMenu.FRIEND_REQUESTS_SLOT -> playerSettingsService.setFriendRequestsEnabled(
                        uniqueId,
                        !settings.isFriendRequestsEnabled()
                );
                default -> false;
            };
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not save player settings for " + uniqueId + ".", exception);
            messageService.send(player, "<red>Your setting could not be saved. Please try again.</red>");
            refreshOrClose(player, inventory);
            return;
        }

        if (!saved) {
            handleUnavailableProfile(player);
            return;
        }
        if (slot == SettingsMenu.SCOREBOARD_SLOT) {
            presentationRefresh.accept(player);
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

    private SettingsInventoryHolder getSettingsHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof SettingsInventoryHolder holder)) {
            return null;
        }
        return holder.getInventory() == inventory ? holder : null;
    }
}

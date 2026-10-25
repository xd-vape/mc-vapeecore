package dev.vapee.core.presence;

import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class PresenceModule implements CoreModule {
    private final JavaPlugin plugin;
    private final Supplier<PlayerService> playerServiceProvider;
    private final Supplier<PlayerSettingsService> playerSettingsServiceProvider;
    private final Supplier<SocialService> socialServiceProvider;
    private final Supplier<FriendService> friendServiceProvider;
    private final MessageService messageService;

    private PresenceService service;
    private FriendPresenceNotifier notifier;
    private PresenceListener listener;

    public PresenceModule(
            JavaPlugin plugin,
            PlayerModule playerModule,
            SocialModule socialModule,
            FriendModule friendModule,
            MessageService messageService) {
        this(
                plugin,
                Objects.requireNonNull(playerModule, "playerModule")::getPlayerService,
                Objects.requireNonNull(playerModule, "playerModule")::getPlayerSettingsService,
                Objects.requireNonNull(socialModule, "socialModule")::getSocialService,
                Objects.requireNonNull(friendModule, "friendModule")::getFriendService,
                messageService);
    }

    public PresenceModule(
            JavaPlugin plugin,
            Supplier<PlayerService> playerServiceProvider,
            Supplier<PlayerSettingsService> playerSettingsServiceProvider,
            Supplier<SocialService> socialServiceProvider,
            Supplier<FriendService> friendServiceProvider,
            MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerServiceProvider =
                Objects.requireNonNull(playerServiceProvider, "playerServiceProvider");
        this.playerSettingsServiceProvider =
                Objects.requireNonNull(
                        playerSettingsServiceProvider, "playerSettingsServiceProvider");
        this.socialServiceProvider =
                Objects.requireNonNull(socialServiceProvider, "socialServiceProvider");
        this.friendServiceProvider =
                Objects.requireNonNull(friendServiceProvider, "friendServiceProvider");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Presence";
    }

    @Override
    public void enable() {
        if (service != null) throw new IllegalStateException("Presence module is already enabled");
        PlayerService playerService =
                Objects.requireNonNull(
                        playerServiceProvider.get(), "PlayerService provider returned null");
        PlayerSettingsService playerSettingsService =
                Objects.requireNonNull(
                        playerSettingsServiceProvider.get(),
                        "PlayerSettingsService provider returned null");
        SocialService socialService =
                Objects.requireNonNull(
                        socialServiceProvider.get(), "SocialService provider returned null");
        FriendService friendService =
                Objects.requireNonNull(
                        friendServiceProvider.get(), "FriendService provider returned null");
        PresenceService newService = new PresenceService();
        FriendPresenceNotifier newNotifier =
                new FriendPresenceNotifier(
                        friendService,
                        playerSettingsService,
                        socialService,
                        plugin.getServer()::getPlayer,
                        messageService,
                        plugin.getLogger());
        PresenceListener newListener =
                new PresenceListener(newService, playerService, newNotifier, plugin.getLogger());

        try {
            plugin.getServer().getPluginManager().registerEvents(newListener, plugin);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player == null || !player.isOnline()) continue;
                UUID uniqueId = player.getUniqueId();
                if (playerService.isLoaded(uniqueId)) newService.markOnline(uniqueId);
            }
            service = newService;
            notifier = newNotifier;
            listener = newListener;
            plugin.getLogger().info("Presence module enabled.");
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newListener);
            newService.clear();
            clearRuntime();
            throw exception;
        }
    }

    @Override
    public void disable() {
        if (listener != null) HandlerList.unregisterAll(listener);
        if (service != null) service.clear();
        clearRuntime();
    }

    private void clearRuntime() {
        listener = null;
        notifier = null;
        service = null;
    }

    public PresenceService getPresenceService() {
        return Objects.requireNonNull(service, "PresenceModule is not enabled");
    }
}

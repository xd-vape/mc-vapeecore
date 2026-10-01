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
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class PresenceModule implements CoreModule {
    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final SocialModule socialModule;
    private final FriendModule friendModule;
    private final MessageService messageService;

    private PresenceService service;
    private FriendPresenceNotifier notifier;
    private PresenceListener listener;

    public PresenceModule(
            JavaPlugin plugin,
            PlayerModule playerModule,
            SocialModule socialModule,
            FriendModule friendModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.socialModule = Objects.requireNonNull(socialModule, "socialModule");
        this.friendModule = Objects.requireNonNull(friendModule, "friendModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Presence";
    }

    @Override
    public void enable() {
        PlayerService playerService = playerModule.getPlayerService();
        PlayerSettingsService playerSettingsService = playerModule.getPlayerSettingsService();
        SocialService socialService = socialModule.getSocialService();
        FriendService friendService = friendModule.getFriendService();
        PresenceService newService = new PresenceService();
        FriendPresenceNotifier newNotifier = new FriendPresenceNotifier(
                friendService,
                playerSettingsService,
                socialService,
                plugin.getServer()::getPlayer,
                messageService,
                plugin.getLogger()
        );
        PresenceListener newListener = new PresenceListener(
                newService, playerService, newNotifier, plugin.getLogger());

        try {
            plugin.getServer().getPluginManager().registerEvents(newListener, plugin);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player == null || !player.isOnline()) continue;
                UUID uniqueId = player.getUniqueId();
                if (playerService.isLoaded(uniqueId)) newService.markOnline(uniqueId);
            }
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newListener);
            newService.clear();
            throw exception;
        }

        service = newService;
        notifier = newNotifier;
        listener = newListener;
        plugin.getLogger().info("Presence module enabled.");
    }

    @Override
    public void disable() {
        if (listener != null) HandlerList.unregisterAll(listener);
        if (service != null) service.clear();
        listener = null;
        notifier = null;
        service = null;
    }

    public PresenceService getPresenceService() {
        return Objects.requireNonNull(service, "PresenceModule is not enabled");
    }
}

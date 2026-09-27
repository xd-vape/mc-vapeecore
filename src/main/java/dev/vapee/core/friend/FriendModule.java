package dev.vapee.core.friend;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

public final class FriendModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final PlayerModule playerModule;
    private final SocialModule socialModule;
    private final IdentityModule identityModule;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;

    private FriendService friendService;
    private PluginCommand friendCommand;

    public FriendModule(JavaPlugin plugin, ConfigService configService, PlayerModule playerModule,
                        SocialModule socialModule, IdentityModule identityModule,
                        MessageService messageService, CommandHelpRenderer helpRenderer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.socialModule = Objects.requireNonNull(socialModule, "socialModule");
        this.identityModule = Objects.requireNonNull(identityModule, "identityModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
    }

    @Override
    public String getName() {
        return "Friend";
    }

    @Override
    public void enable() {
        Path file = plugin.getDataFolder().toPath().resolve("friends.yml");
        FriendRepository repository = new FileFriendRepository(file, plugin.getLogger());
        SocialService social = socialModule.getSocialService();
        PlayerSettingsService settings = playerModule.getPlayerSettingsService();
        FriendRequestPolicy policy = createPolicy(social, settings);
        FriendService service = new FriendService(repository,
                configService::getFriendLimits, policy, Clock.systemUTC());
        PluginCommand command = Objects.requireNonNull(plugin.getCommand("friend"),
                "Command 'friend' is missing from plugin.yml");
        try {
            FriendCommand executor = new FriendCommand(plugin, service,
                    identityModule.getIdentityService(), messageService, helpRenderer);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } catch (RuntimeException exception) {
            command.setExecutor(null);
            command.setTabCompleter(null);
            throw exception;
        }
        friendService = service;
        friendCommand = command;
        plugin.getLogger().info("Friend module enabled.");
    }

    static FriendRequestPolicy createPolicy(SocialService social, PlayerSettingsService settings) {
        Objects.requireNonNull(social, "social");
        Objects.requireNonNull(settings, "settings");
        return (sender, recipient) -> {
            if (social.isKnownIgnoring(sender, recipient)
                    || social.isKnownIgnoring(recipient, sender)) {
                return FriendRequestDecision.BLOCKED;
            }
            if (!settings.areKnownFriendRequestsEnabled(recipient).orElse(false)) {
                return FriendRequestDecision.REQUESTS_DISABLED;
            }
            return FriendRequestDecision.ALLOW;
        };
    }

    @Override
    public void disable() {
        if (friendCommand != null) {
            friendCommand.setExecutor(null);
            friendCommand.setTabCompleter(null);
        }
        friendCommand = null;
        friendService = null;
    }

    public FriendService getFriendService() {
        return Objects.requireNonNull(friendService, "FriendModule is not enabled");
    }
}

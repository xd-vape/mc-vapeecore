package dev.vapee.core.friend;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.friend.gui.FriendMenu;
import dev.vapee.core.friend.gui.FriendMenuListener;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;

public final class FriendModule implements CoreModule {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final PlayerModule playerModule;
    private final SocialModule socialModule;
    private final IdentityModule identityModule;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;
    private final Consumer<EnableStage> enableCheckpoint;

    enum EnableStage {
        LISTENER_REGISTERED, EXECUTOR_BOUND, COMPLETER_BOUND, OBSERVERS_STARTED, RUNTIME_PUBLISHED
    }

    private FriendService friendService;
    private FriendMenu friendMenu;
    private FriendMenuListener friendMenuListener;
    private PluginCommand friendCommand;
    private boolean active;
    private boolean enabling;

    public FriendModule(JavaPlugin plugin, ConfigService configService, PlayerModule playerModule,
                        SocialModule socialModule, IdentityModule identityModule,
                        MessageService messageService, CommandHelpRenderer helpRenderer) {
        this(plugin, configService, playerModule, socialModule, identityModule,
                messageService, helpRenderer, stage -> { });
    }

    FriendModule(JavaPlugin plugin, ConfigService configService, PlayerModule playerModule,
                 SocialModule socialModule, IdentityModule identityModule,
                 MessageService messageService, CommandHelpRenderer helpRenderer,
                 Consumer<EnableStage> enableCheckpoint) {
        this.enableCheckpoint = Objects.requireNonNull(enableCheckpoint, "enableCheckpoint");
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
        if (active || enabling) {
            throw new IllegalStateException("Friend module is already enabled or starting");
        }
        enabling = true;
        FriendMenu menu = null;
        FriendMenuListener listener = null;
        PluginCommand command = null;
        try {
            Path file = plugin.getDataFolder().toPath().resolve("friends.yml");
            FriendRepository repository = new FileFriendRepository(file, plugin.getLogger());
            SocialService social = socialModule.getSocialService();
            PlayerSettingsService settings = playerModule.getPlayerSettingsService();
            FriendRequestPolicy policy = createPolicy(social, settings);
            FriendService service = new FriendService(repository,
                    configService::getFriendLimits, policy, Clock.systemUTC());
            menu = new FriendMenu(plugin, service, identityModule.getIdentityService(), messageService);
            FriendMessages feedback = new FriendMessages(messageService, plugin.getServer()::getPlayer,
                    id -> FriendMessages.commandArgument(identityModule.getIdentityService(), id));
            listener = new FriendMenuListener(menu, service, feedback,
                    messageService, plugin.getLogger());
            command = Objects.requireNonNull(plugin.getCommand("friend"),
                    "Command 'friend' is missing from plugin.yml");
            FriendCommand executor = new FriendCommand(plugin, service,
                    identityModule.getIdentityService(), messageService, helpRenderer, menu);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            enableCheckpoint.accept(EnableStage.LISTENER_REGISTERED);
            command.setExecutor(executor);
            enableCheckpoint.accept(EnableStage.EXECUTOR_BOUND);
            command.setTabCompleter(executor);
            enableCheckpoint.accept(EnableStage.COMPLETER_BOUND);
            menu.startUpdates();
            enableCheckpoint.accept(EnableStage.OBSERVERS_STARTED);
            friendService = service;
            friendMenu = menu;
            friendMenuListener = listener;
            friendCommand = command;
            enableCheckpoint.accept(EnableStage.RUNTIME_PUBLISHED);
            plugin.getLogger().info("Friend module enabled.");
            active = true;
        } catch (RuntimeException exception) {
            cleanup(menu, listener, command, exception);
            clearRuntime();
            throw exception;
        } finally {
            enabling = false;
        }
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
        active = false;
        RuntimeException failure = new IllegalStateException("Friend cleanup failed.");
        cleanup(friendMenu, friendMenuListener, friendCommand, failure);
        clearRuntime();
        if (failure.getSuppressed().length != 0) throw failure;
    }

    private void cleanup(FriendMenu menu, FriendMenuListener listener, PluginCommand command,
                         RuntimeException failure) {
        // Menu shutdown keeps the close handlers installed until owned views are detached.
        if (menu != null) cleanupStep(failure, "Friend menu", menu::shutdown);
        if (listener != null) {
            cleanupStep(failure, "Friend listener", () -> HandlerList.unregisterAll(listener));
        }
        if (command != null) {
            cleanupStep(failure, "Friend command executor", () -> command.setExecutor(null));
            cleanupStep(failure, "Friend tab completer", () -> command.setTabCompleter(null));
        }
    }

    private static void cleanupStep(RuntimeException failure, String resource, Runnable release) {
        try {
            release.run();
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not release " + resource, exception));
        }
    }

    private void clearRuntime() {
        active = false;
        friendCommand = null;
        friendMenuListener = null;
        friendMenu = null;
        friendService = null;
    }

    public FriendMenu getFriendMenu() {
        return Objects.requireNonNull(active ? friendMenu : null, "FriendModule is not enabled");
    }

    public FriendService getFriendService() {
        return Objects.requireNonNull(active ? friendService : null, "FriendModule is not enabled");
    }
}

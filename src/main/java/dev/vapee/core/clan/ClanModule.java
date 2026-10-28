package dev.vapee.core.clan;

import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.clan.gui.ClanMenu;
import dev.vapee.core.clan.gui.ClanMenuListener;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.PlayerNameFormatter;
import dev.vapee.core.module.CoreModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class ClanModule implements CoreModule {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final IdentityModule identity;
    private final MessageService messages;
    private final CommandHelpRenderer help;
    private final PlayerNameFormatter names;
    private final Consumer<EnableStage> enableCheckpoint;
    private ClanService service;
    private ClanMenu menu;
    private ClanMenuListener listener;
    private PluginCommand command;
    private ClanCommand binding;
    private boolean active;
    private boolean enabling;
    private CleanupResources pendingCleanup;

    enum EnableStage {
        LISTENER_REGISTERED, EXECUTOR_BOUND, COMPLETER_BOUND, OBSERVERS_STARTED, RUNTIME_PUBLISHED
    }

    public ClanModule(JavaPlugin plugin, ConfigService config, IdentityModule identity,
                      MessageService messages, CommandHelpRenderer help) {
        this(plugin, config, identity, messages, help, PlayerNameFormatter.neutral());
    }

    public ClanModule(JavaPlugin plugin, ConfigService config, IdentityModule identity,
                      MessageService messages, CommandHelpRenderer help, PlayerNameFormatter names) {
        this(plugin, config, identity, messages, help, stage -> { }, names);
    }

    ClanModule(JavaPlugin plugin, ConfigService config, IdentityModule identity,
               MessageService messages, CommandHelpRenderer help, Consumer<EnableStage> enableCheckpoint) {
        this(plugin, config, identity, messages, help, enableCheckpoint, PlayerNameFormatter.neutral());
    }

    private ClanModule(JavaPlugin plugin, ConfigService config, IdentityModule identity,
               MessageService messages, CommandHelpRenderer help, Consumer<EnableStage> enableCheckpoint,
               PlayerNameFormatter names) {
        this.names = Objects.requireNonNull(names, "names");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.help = Objects.requireNonNull(help, "help");
        this.enableCheckpoint = Objects.requireNonNull(enableCheckpoint, "enableCheckpoint");
    }

    @Override public String getName() { return "Clan"; }

    @Override public void enable() {
        if (active || enabling) {
            throw new IllegalStateException("Clan module is already enabled or starting");
        }
        enabling = true;
        ClanMenu nextMenu = null;
        ClanMenuListener nextListener = null;
        PluginCommand nextCommand = null;
        ClanCommand nextBinding = null;
        try {
            if (pendingCleanup != null) {
                RuntimeException failure = new IllegalStateException("Previous Clan cleanup is incomplete.");
                if (!cleanup(pendingCleanup, failure)) throw failure;
                pendingCleanup = null;
            }
            var identities = identity.getIdentityService();
            ClanService nextService = new ClanService(
                    new FileClanRepository(plugin.getDataFolder().toPath().resolve("clans.yml")),
                    config::getClanLimits, Clock.systemUTC(), UUID::randomUUID);
            nextMenu = new ClanMenu(plugin, nextService, identities, messages);
            ClanMessages feedback = new ClanMessages(messages, plugin.getServer()::getPlayer, names);
            nextListener = new ClanMenuListener(nextMenu, nextService, feedback,
                    messages, plugin.getLogger());
            nextCommand = Objects.requireNonNull(plugin.getCommand("clan"),
                    "Command 'clan' is missing from plugin.yml");
            nextBinding = new ClanCommand(plugin, nextService, identities, messages, help, nextMenu, names);
            plugin.getServer().getPluginManager().registerEvents(nextListener, plugin);
            enableCheckpoint.accept(EnableStage.LISTENER_REGISTERED);
            nextCommand.setExecutor(nextBinding);
            enableCheckpoint.accept(EnableStage.EXECUTOR_BOUND);
            nextCommand.setTabCompleter(nextBinding);
            enableCheckpoint.accept(EnableStage.COMPLETER_BOUND);
            nextMenu.startUpdates();
            enableCheckpoint.accept(EnableStage.OBSERVERS_STARTED);
            service = nextService;
            menu = nextMenu;
            listener = nextListener;
            command = nextCommand;
            binding = nextBinding;
            enableCheckpoint.accept(EnableStage.RUNTIME_PUBLISHED);
            plugin.getLogger().info("Clan module enabled.");
            active = true;
        } catch (RuntimeException exception) {
            CleanupResources acquired = new CleanupResources(nextMenu, nextListener, nextCommand, nextBinding);
            if (!cleanup(acquired, exception)) pendingCleanup = acquired;
            clearRuntime();
            throw exception;
        } finally {
            enabling = false;
        }
    }

    @Override public void disable() {
        active = false;
        CleanupResources resources = pendingCleanup == null
                ? new CleanupResources(menu, listener, command, binding) : pendingCleanup;
        RuntimeException failure = new IllegalStateException("Clan cleanup failed.");
        pendingCleanup = cleanup(resources, failure) ? null : resources;
        clearRuntime();
        if (failure.getSuppressed().length != 0) throw failure;
    }

    private boolean cleanup(CleanupResources resources, RuntimeException failure) {
        int failuresBefore = failure.getSuppressed().length;
        // Close events must reach the owning listener before its handlers are removed.
        if (resources.menu() != null) cleanupStep(failure, "Clan menu", resources.menu()::shutdown);
        if (resources.listener() != null) {
            cleanupStep(failure, "Clan listener", () -> HandlerList.unregisterAll(resources.listener()));
        }
        if (resources.command() != null && resources.binding() != null) {
            cleanupStep(failure, "Clan command executor", () -> {
                if (resources.command().getExecutor() == resources.binding()) resources.command().setExecutor(null);
            });
            cleanupStep(failure, "Clan tab completer", () -> {
                if (resources.command().getTabCompleter() == resources.binding()) resources.command().setTabCompleter(null);
            });
        }
        return failure.getSuppressed().length == failuresBefore;
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
        command = null;
        binding = null;
        listener = null;
        menu = null;
        service = null;
    }

    // Keep failed cleanup retryable without publishing runtime authority or acquiring new resources.
    private record CleanupResources(ClanMenu menu, ClanMenuListener listener, PluginCommand command,
                                    ClanCommand binding) { }

    public ClanService getClanService() {
        return Objects.requireNonNull(active ? service : null, "ClanModule is not enabled");
    }
}

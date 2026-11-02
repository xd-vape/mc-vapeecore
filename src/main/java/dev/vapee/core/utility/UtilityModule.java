package dev.vapee.core.utility;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.rank.RankModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.message.PlayerNameFormatter;
import dev.vapee.core.utility.tpa.TeleportRequests;
import dev.vapee.core.utility.tpa.TeleportRequestRegistration;
import dev.vapee.core.utility.command.BuildCommand;
import dev.vapee.core.utility.command.ClearCommand;
import dev.vapee.core.utility.command.EnderChestCommand;
import dev.vapee.core.utility.command.FeedCommand;
import dev.vapee.core.utility.command.FlyCommand;
import dev.vapee.core.utility.command.GameModeCommand;
import dev.vapee.core.utility.command.HealCommand;
import dev.vapee.core.utility.command.InvseeCommand;
import dev.vapee.core.utility.command.PingCommand;
import dev.vapee.core.utility.command.SpeedCommand;
import dev.vapee.core.utility.command.TeleportCommand;
import dev.vapee.core.utility.command.TeleportHereCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class UtilityModule implements CoreModule {

    private final Supplier<UtilityService> serviceFactory;
    private final Function<UtilityService, UtilityListener> listenerFactory;
    private final Supplier<InvseeService> invseeFactory;
    private final CommandRegistration commandRegistration;
    private final Hooks hooks;
    private final Logger logger;
    private final Function<UtilityService, TeleportRequestRegistration> requestsFactory;
    private final List<CommandHook> registeredCommands = new ArrayList<>();
    private final List<Listener> registeredListeners = new ArrayList<>();
    private UtilityService utilityService;
    private UtilityListener utilityListener;
    private InvseeService invseeService;
    private TeleportRequestRegistration teleportRequests;

    public UtilityModule(JavaPlugin plugin, LobbyModule lobbyModule, ActivityModule activityModule,
                         RankModule rankModule, MessageService messageService,
                         SocialModule socialModule, PlayerModule playerModule) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(lobbyModule, "lobbyModule");
        Objects.requireNonNull(activityModule, "activityModule");
        Objects.requireNonNull(rankModule, "rankModule");
        Objects.requireNonNull(messageService, "messageService");
        Objects.requireNonNull(socialModule, "socialModule");
        Objects.requireNonNull(playerModule, "playerModule");
        logger = plugin.getLogger();
        serviceFactory = () -> new UtilityService(plugin);
        listenerFactory = service -> new UtilityListener(plugin, service);
        invseeFactory = () -> new InvseeService(plugin, messageService);
        requestsFactory = service -> {
            var activity = activityModule.getActivityService();
            var states = lobbyModule.getLobbyPlayerStateService();
            var social = socialModule.getSocialService();
            var requests = new TeleportRequests(plugin.getServer(), service, activity, states, social,
                    playerModule.getPlayerSettingsService(), messageService,
                    new PlayerNameFormatter(rankModule::getRankService), plugin.getLogger());
            return new TeleportRequestRegistration(plugin, requests, activity, states, social);
        };
        commandRegistration = (newUtilityService, newInvseeService, register) -> {
            LobbyService lobbyService = lobbyModule.getLobbyService();
            LobbyPlayerStateService lobbyPlayerStateService = lobbyModule.getLobbyPlayerStateService();
            ActivityService activityService = activityModule.getActivityService();
            OnlineStaffTargetGuard targetGuard = new OnlineStaffTargetGuard(
                    rankModule.getStaffHierarchyService(), plugin.getLogger());
            register.accept("build", new BuildCommand(
                    plugin,
                    lobbyService,
                    lobbyPlayerStateService,
                    activityService,
                    newUtilityService,
                    messageService
            ));
            register.accept("fly", new FlyCommand(
                    plugin, newUtilityService, lobbyPlayerStateService, activityService, messageService, targetGuard));
            register.accept("speed", new SpeedCommand(
                    plugin, newUtilityService, activityService, messageService, targetGuard));
            register.accept("gamemode", new GameModeCommand(
                    plugin, newUtilityService, lobbyService, lobbyPlayerStateService, activityService, messageService, targetGuard));
            register.accept("tp", new TeleportCommand(
                    plugin, newUtilityService, activityService, messageService, targetGuard));
            register.accept("tphere", new TeleportHereCommand(
                    plugin, newUtilityService, activityService, messageService, targetGuard));
            register.accept("heal", new HealCommand(
                    plugin, newUtilityService, activityService, messageService, targetGuard));
            register.accept("feed", new FeedCommand(
                    plugin, newUtilityService, activityService, messageService, targetGuard));
            register.accept("ping", new PingCommand(plugin, messageService));
            register.accept("clear", new ClearCommand(
                    plugin, newUtilityService, activityService, lobbyPlayerStateService, messageService, targetGuard));
            register.accept("invsee", new InvseeCommand(plugin, newInvseeService, messageService, targetGuard));
            register.accept("enderchest", new EnderChestCommand(plugin, newUtilityService, messageService, targetGuard));

        };
        hooks = new Hooks() {
            public CommandHook command(String name) {
                var command = Objects.requireNonNull(plugin.getCommand(name),
                        "Command '" + name + "' is missing from plugin.yml");
                return new CommandHook(command::setExecutor, command::setTabCompleter);
            }
            public void register(Listener listener) {
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            }
            public void unregister(Listener listener) { HandlerList.unregisterAll(listener); }
        };
    }

    // Local I/O seams: the harness runs this module's acquisition, publication and cleanup paths.
    UtilityModule(Supplier<UtilityService> serviceFactory,
                  Function<UtilityService, UtilityListener> listenerFactory,
                  Supplier<InvseeService> invseeFactory, CommandRegistration commandRegistration,
                  Hooks hooks, Logger logger) {
        this(serviceFactory, listenerFactory, invseeFactory, commandRegistration, hooks, logger, service -> null);
    }

    UtilityModule(Supplier<UtilityService> serviceFactory,
                  Function<UtilityService, UtilityListener> listenerFactory,
                  Supplier<InvseeService> invseeFactory, CommandRegistration commandRegistration,
                  Hooks hooks, Logger logger, Function<UtilityService, TeleportRequestRegistration> requestsFactory) {
        this.serviceFactory = Objects.requireNonNull(serviceFactory, "serviceFactory");
        this.listenerFactory = Objects.requireNonNull(listenerFactory, "listenerFactory");
        this.invseeFactory = Objects.requireNonNull(invseeFactory, "invseeFactory");
        this.commandRegistration = Objects.requireNonNull(commandRegistration, "commandRegistration");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.requestsFactory = Objects.requireNonNull(requestsFactory, "requestsFactory");
    }

    @Override
    public String getName() { return "Utility"; }

    @Override
    public void enable() {
        if (utilityService != null) throw new IllegalStateException("Utility module is already enabled");
        UtilityService newUtilityService = null;
        UtilityListener newUtilityListener = null;
        InvseeService newInvseeService = null;
        TeleportRequestRegistration newRequests = null;
        try {
            newUtilityService = Objects.requireNonNull(serviceFactory.get(), "utilityService");
            newUtilityListener = Objects.requireNonNull(listenerFactory.apply(newUtilityService), "utilityListener");
            newInvseeService = Objects.requireNonNull(invseeFactory.get(), "invseeService");
            commandRegistration.register(newUtilityService, newInvseeService, this::registerCommand);
            registerListener(newUtilityListener);
            registerListener(newInvseeService);
            newRequests = requestsFactory.apply(newUtilityService);
            if (newRequests != null) newRequests.enable();
            logger.info("Utility module enabled with " + registeredCommands.size() + " command(s).");
        } catch (RuntimeException exception) {
            cleanup(newUtilityService, newUtilityListener, newInvseeService, newRequests, exception);
            throw exception;
        }
        // Publish only when all command pairs and owned listeners have been installed.
        utilityService = newUtilityService;
        utilityListener = newUtilityListener;
        invseeService = newInvseeService;
        teleportRequests = newRequests;
    }

    @Override
    public void disable() {
        RuntimeException failure;
        try {
            failure = cleanup(utilityService, utilityListener, invseeService, teleportRequests, null);
        } finally {
            utilityService = null;
            utilityListener = null;
            invseeService = null;
            teleportRequests = null;
        }
        if (failure != null) throw failure;
    }

    private RuntimeException cleanup(UtilityService service, UtilityListener listener,
                                     InvseeService invsee, TeleportRequestRegistration requests, RuntimeException failure) {
        if (requests != null) failure = attempt(failure, requests::disable);
        for (Listener owned : List.copyOf(registeredListeners)) {
            if (owned instanceof EnderChestCommand enderChest) {
                failure = attempt(failure, enderChest::disable);
                failure = attempt(failure, () -> hooks.unregister(enderChest));
            }
        }
        if (invsee != null) failure = attempt(failure, invsee::disable);
        if (invsee != null && registeredListeners.contains(invsee)) {
            failure = attempt(failure, () -> hooks.unregister(invsee));
        }
        if (listener != null) failure = attempt(failure, listener::disable);
        if (listener != null && registeredListeners.contains(listener)) {
            failure = attempt(failure, () -> hooks.unregister(listener));
        }
        registeredListeners.clear();
        if (service != null) failure = attempt(failure, service::cleanup);
        for (int index = registeredCommands.size() - 1; index >= 0; index--) {
            CommandHook command = registeredCommands.get(index);
            failure = attempt(failure, () -> command.executor().accept(null));
            failure = attempt(failure, () -> command.completer().accept(null));
        }
        registeredCommands.clear();
        return failure;
    }

    private static RuntimeException attempt(RuntimeException failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException exception) {
            if (failure == null) return exception;
            if (failure != exception) failure.addSuppressed(exception);
        }
        return failure;
    }

    public UtilityService getUtilityService() {
        if (utilityService == null) throw new IllegalStateException("Utility module is not enabled");
        return utilityService;
    }

    private void registerCommand(String name, TabExecutor executor) {
        CommandHook command = Objects.requireNonNull(hooks.command(name),
                "Command '" + name + "' is missing from plugin.yml");
        // Own the pair before either setter, including a half-installed command.
        registeredCommands.add(command);
        if (executor instanceof EnderChestCommand enderChest) registerListener(enderChest);
        command.executor().accept(executor);
        command.completer().accept(executor);
    }

    private void registerListener(Listener listener) {
        registeredListeners.add(listener); // Registration may throw after partially installing handlers.
        hooks.register(listener);
    }

    @FunctionalInterface
    interface CommandRegistration {
        void register(UtilityService service, InvseeService invsee, BiConsumer<String, TabExecutor> register);
    }

    record CommandHook(Consumer<CommandExecutor> executor, Consumer<TabCompleter> completer) {
        CommandHook {
            Objects.requireNonNull(executor, "executor");
            Objects.requireNonNull(completer, "completer");
        }
    }

    interface Hooks {
        CommandHook command(String name);
        void register(Listener listener);
        void unregister(Listener listener);
    }
}

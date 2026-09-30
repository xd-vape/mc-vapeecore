package dev.vapee.core.moderation;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.moderation.command.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns main-thread service, five staff commands and synchronous login enforcement. No tasks/reload. */
public final class ModerationModule implements CoreModule {
    private final Supplier<ModerationRepository> repositoryFactory;
    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private final Function<ModerationService, ModerationCommandContext> contextFactory;
    private final Hooks hooks;
    private ModerationService service;
    private ModerationLoginListener listener;

    public ModerationModule(JavaPlugin plugin, IdentityModule identityModule, MessageService messages) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(identityModule, "identityModule");
        Objects.requireNonNull(messages, "messages");
        repositoryFactory = () -> new FileModerationRepository(
                plugin.getDataFolder().toPath().resolve("moderation.yml"), plugin.getLogger());
        clock = Clock.systemUTC();
        idSupplier = UUID::randomUUID;
        contextFactory = loaded -> new ModerationCommandContext(loaded, identityModule.getIdentityService(), messages,
                plugin.getServer()::getPlayer, plugin.getServer()::getOnlinePlayers, clock, plugin.getLogger());
        hooks = new BukkitHooks(plugin);
    }

    // Local lifecycle seam exercises this actual module without inventing a Bukkit server.
    ModerationModule(Supplier<ModerationRepository> repositoryFactory, Clock clock, Supplier<UUID> idSupplier,
                     Function<ModerationService, ModerationCommandContext> contextFactory, Hooks hooks) {
        this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    @Override public String getName() { return "Moderation"; }

    @Override public void enable() {
        if (service != null) throw new IllegalStateException("ModerationModule already enabled");
        ModerationService loaded = new ModerationService(repositoryFactory.get(), clock, idSupplier);
        ModerationCommandContext context = contextFactory.apply(loaded);
        ModerationLoginListener newListener = new ModerationLoginListener(loaded, context.logger());
        try {
            hooks.install("warn", new WarnCommand(context));
            hooks.install("ban", new BanCommand(context));
            hooks.install("unban", new UnbanCommand(context));
            hooks.install("kick", new KickCommand(context));
            hooks.install("history", new HistoryCommand(context));
            hooks.register(newListener);
        } catch (RuntimeException exception) {
            try { cleanup(newListener); } catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            throw exception;
        }
        listener = newListener;
        service = loaded; // Publish only once all hooks have been installed successfully.
    }

    @Override public void disable() {
        try { cleanup(listener); }
        finally { listener = null; service = null; }
    }

    private void cleanup(ModerationLoginListener listener) {
        try { hooks.clearCommands(); }
        finally { if (listener != null) hooks.unregister(listener); }
    }

    public ModerationService getModerationService() {
        if (service == null) throw new IllegalStateException("ModerationModule is not enabled");
        return service;
    }

    // Local lifecycle boundary; tests exercise this module and the real executors.
    interface Hooks {
        void install(String name, TabExecutor executor);
        void register(ModerationLoginListener listener);
        void clearCommands();
        void unregister(ModerationLoginListener listener);
    }

    private static final class BukkitHooks implements Hooks {
        private final JavaPlugin plugin;
        private final List<PluginCommand> commands = new ArrayList<>();
        private BukkitHooks(JavaPlugin plugin) { this.plugin = plugin; }

        public void install(String name, TabExecutor executor) {
            PluginCommand command = Objects.requireNonNull(plugin.getCommand(name), "Missing command: " + name);
            commands.add(command); // Track before the first setter, including partially installed hooks.
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        public void register(ModerationLoginListener listener) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        public void clearCommands() {
            RuntimeException failure = null;
            for (PluginCommand command : commands) {
                try { command.setExecutor(null); } catch (RuntimeException exception) { failure = collect(failure, exception); }
                try { command.setTabCompleter(null); } catch (RuntimeException exception) { failure = collect(failure, exception); }
            }
            commands.clear();
            if (failure != null) throw failure;
        }
        public void unregister(ModerationLoginListener listener) { HandlerList.unregisterAll(listener); }
        private RuntimeException collect(RuntimeException first, RuntimeException next) {
            if (first == null) return next;
            first.addSuppressed(next); return first;
        }
    }
}

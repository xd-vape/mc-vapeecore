package dev.vapee.core.utility.tpa;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.social.SocialService;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/** Utility-owned command/listener leases; no new CoreModule, timer, persistence or reload participant. */
public final class TeleportRequestRegistration {
    private final TeleportRequests requests;
    private final Function<String, Command> lookup;
    private final java.util.function.Predicate<PluginCommand> owner;
    private final Consumer<TeleportRequests> register, unregister, subscribe, unsubscribe;
    private final List<Lease> leases = new ArrayList<>();
    private boolean listenerOwned, observersOwned, enabled;
    private record Lease(PluginCommand command, CommandExecutor executor, TabCompleter completer) { }

    public TeleportRequestRegistration(JavaPlugin plugin, TeleportRequests requests, ActivityService activity,
                                       LobbyPlayerStateService states, SocialService social) {
        this(requests, plugin.getServer().getCommandMap()::getCommand, command -> command.getPlugin() == plugin,
                listener -> plugin.getServer().getPluginManager().registerEvents(listener, plugin),
                HandlerList::unregisterAll,
                listener -> {
                    activity.addMembershipListener(listener); states.addStateListener(listener);
                    social.addRelationshipListener(listener);
                }, listener -> {
                    // Removal uses all three owners even if one rejects cleanup.
                    RuntimeException failure = attempt(null, () -> social.removeRelationshipListener(listener));
                    failure = attempt(failure, () -> states.removeStateListener(listener));
                    failure = attempt(failure, () -> activity.removeMembershipListener(listener));
                    if (failure != null) throw failure;
                });
    }

    TeleportRequestRegistration(TeleportRequests requests, Function<String, Command> lookup,
                                 java.util.function.Predicate<PluginCommand> owner,
                                 Consumer<TeleportRequests> register, Consumer<TeleportRequests> unregister,
                                 Consumer<TeleportRequests> subscribe, Consumer<TeleportRequests> unsubscribe) {
        this.requests = Objects.requireNonNull(requests); this.lookup = Objects.requireNonNull(lookup);
        this.owner = Objects.requireNonNull(owner); this.register = Objects.requireNonNull(register);
        this.unregister = Objects.requireNonNull(unregister); this.subscribe = Objects.requireNonNull(subscribe);
        this.unsubscribe = Objects.requireNonNull(unsubscribe);
    }

    public void enable() {
        if (enabled || !leases.isEmpty() || listenerOwned || observersOwned) throw new IllegalStateException("TPA already enabled");
        // Check every plain and namespaced route before mutating even the first executor.
        List<PluginCommand> commands = new ArrayList<>();
        for (String name : TeleportRequests.COMMANDS) {
            Command root = lookup.apply(name), qualified = lookup.apply("vapeecore:" + name);
            if (!(root instanceof PluginCommand command) || root != qualified || !owner.test(command)
                    || !command.getName().equals(name)) {
                throw new IllegalStateException("TPA command collision or missing ownership: " + name);
            }
            commands.add(command);
        }
        try {
            for (PluginCommand command : commands) {
                leases.add(new Lease(command, command.getExecutor(), command.getTabCompleter()));
                command.setExecutor(requests); command.setTabCompleter(requests);
            }
            listenerOwned = true; register.accept(requests);
            observersOwned = true; subscribe.accept(requests);
            requests.enable(); enabled = true;
        } catch (RuntimeException failure) {
            RuntimeException combined = cleanup(failure);
            throw combined;
        }
    }

    public void disable() {
        RuntimeException failure = cleanup(null);
        if (failure != null) throw failure;
    }

    public TeleportRequests requests() {
        if (!enabled) throw new IllegalStateException("TPA registration is not enabled");
        return requests;
    }

    private RuntimeException cleanup(RuntimeException failure) {
        enabled = false;
        failure = attempt(failure, requests::disable);
        if (observersOwned) { observersOwned = false; failure = attempt(failure, () -> unsubscribe.accept(requests)); }
        if (listenerOwned) { listenerOwned = false; failure = attempt(failure, () -> unregister.accept(requests)); }
        for (int i = leases.size() - 1; i >= 0; i--) {
            Lease lease = leases.get(i);
            if (lease.command.getExecutor() == requests)
                failure = attempt(failure, () -> lease.command.setExecutor(lease.executor));
            if (lease.command.getTabCompleter() == requests)
                failure = attempt(failure, () -> lease.command.setTabCompleter(lease.completer));
        }
        leases.clear();
        return failure;
    }

    private static RuntimeException attempt(RuntimeException failure, Runnable action) {
        try { action.run(); }
        catch (RuntimeException next) {
            if (failure == null) return next;
            if (failure != next) failure.addSuppressed(next);
        }
        return failure;
    }
}

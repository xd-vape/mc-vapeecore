package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.experience.navigator.NavigatorAccessPolicy;
import dev.vapee.core.lobby.item.LobbyItemDefinition;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.social.SocialService;
import dev.vapee.core.utility.UtilityService;
import dev.vapee.core.visibility.VisibilityService;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class GadgetsModule implements CoreModule {
    private final JavaPlugin plugin;
    private final LobbyModule lobby;
    private final PlayerModule players;
    private final ActivityModule activities;
    private final MessageService messages;
    private final Supplier<VisibilityService> visibility;
    private final Supplier<SocialService> social;
    private final Supplier<UtilityService> utility;
    private final Predicate<UUID> lobbyGlide;
    private GadgetsMenu menu;
    private PlayerRiding riding;
    private final Runnable reload = () -> {
        if (menu != null) menu.closeAll();
        if (riding != null) riding.reset();
    };

    public GadgetsModule(JavaPlugin plugin, LobbyModule lobby, PlayerModule players,
                         ActivityModule activities, MessageService messages, Supplier<VisibilityService> visibility,
                         Supplier<SocialService> social, Supplier<UtilityService> utility, Predicate<UUID> lobbyGlide) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.players = players;
        this.activities = activities;
        this.messages = messages;
        this.visibility = visibility;
        this.social = social;
        this.utility = utility;
        this.lobbyGlide = lobbyGlide;
    }

    @Override
    public String getName() {
        return "Gadgets";
    }

    @Override
    public void enable() {
        if (menu != null) throw new IllegalStateException("Gadgets already enabled");
        var items = lobby.getLobbyItemService();
        var access = new NavigatorAccessPolicy(players.getPlayerService(), lobby.getLobbyService(),
                lobby.getLobbyPlayerStateService(), activities.getActivityService());
        Predicate<Player> eligible = player -> lobby.getItemRegistry().isActive()
                && items.isEnabled(PlayerRiding.ITEM) && access.canAccess(player);
        var visibilityService = visibility.get();
        var socialService = social.get();
        var utilityService = utility.get();
        var candidateRiding = new PlayerRiding(plugin, items, messages, eligible,
                id -> lobbyGlide.test(id) || utilityService.hasManagedFlight(id),
                (first, second) -> visibilityService.canSee(first, second) && visibilityService.canSee(second, first)
                        && !socialService.isIgnoring(first.getUniqueId(), second.getUniqueId())
                        && !socialService.isIgnoring(second.getUniqueId(), first.getUniqueId()));
        var candidate = new GadgetsMenu(plugin, messages, eligible, candidateRiding);
        var states = lobby.getLobbyPlayerStateService();
        var activity = activities.getActivityService();
        Runnable unsubscribe = () -> {
            visibilityService.removeRefreshListener(visibilityChanged);
            states.removeStateListener(candidateRiding);
            activity.removeMembershipListener(candidateRiding);
            utilityService.removeFlightListener(candidateRiding);
        };
        try {
            plugin.getServer().getPluginManager().registerEvents(candidate, plugin);
            plugin.getServer().getPluginManager().registerEvents(candidateRiding, plugin);
            states.addStateListener(candidateRiding);
            activity.addMembershipListener(candidateRiding);
            utilityService.addFlightListener(candidateRiding);
            visibilityService.addRefreshListener(visibilityChanged);
            lobby.addReloadListener(reload);
            unsubscribeRiding = unsubscribe;
            riding = candidateRiding;
            menu = candidate;
        } catch (RuntimeException failure) {
            unsubscribe.run();
            lobby.removeReloadListener(reload);
            HandlerList.unregisterAll(candidate);
            HandlerList.unregisterAll(candidateRiding);
            throw failure;
        }
    }

    private Runnable unsubscribeRiding = () -> { };
    private final java.util.function.Consumer<UUID> visibilityChanged = id -> {
        if (riding != null) riding.reconcileVisibility(id);
    };

    public boolean openMenu(Player player) {
        return menu != null && menu.open(player);
    }

    public boolean handleHotbar(Player player, Action action) {
        return riding != null && riding.useHotbar(player, action, this::openMenu);
    }

    public LobbyItemDefinition.Appearance appearance(Player player, LobbyItemDefinition definition) {
        return riding == null ? definition.appearance() : riding.appearance(player, definition);
    }

    public void decorate(Player player, ItemMeta meta) {
        if (riding != null) riding.decorate(player, meta);
    }

    @Override
    public void disable() {
        var previous = menu;
        var previousRiding = riding;
        menu = null;
        riding = null;
        lobby.removeReloadListener(reload);
        unsubscribeRiding.run();
        unsubscribeRiding = () -> { };
        if (previousRiding != null) {
            try {
                previousRiding.shutdown();
            } finally {
                HandlerList.unregisterAll(previousRiding);
            }
        }
        if (previous != null) {
            try {
                previous.closeAll();
            } finally {
                HandlerList.unregisterAll(previous);
            }
        }
    }
}

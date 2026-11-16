package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.experience.navigator.NavigatorAccessPolicy;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class GadgetsModule implements CoreModule {
    public static final String USE_PERMISSION = "vapeecore.gadgets.use";
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
    private SizeChanger sizeChanger;
    private GlowGadget glow;
    private final Runnable reload = () -> {
        if (menu != null) menu.closeAll();
        if (sizeChanger != null) sizeChanger.reset();
        if (glow != null) glow.reset();
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
        var equipment = new GadgetEquipment(plugin, items, messages, lobby.getLobbyConfig()::getGadgetItemLimit);
        var candidateRiding = new PlayerRiding(plugin, equipment, messages, eligible,
                id -> lobbyGlide.test(id) || utilityService.hasManagedFlight(id),
                (first, second) -> visibilityService.canSee(first, second) && visibilityService.canSee(second, first)
                        && !socialService.isIgnoring(first.getUniqueId(), second.getUniqueId())
                        && !socialService.isIgnoring(second.getUniqueId(), first.getUniqueId()));
        var candidateSize = new SizeChanger(plugin, eligible);
        var candidateGlow = new GlowGadget(plugin, messages, eligible);
        var candidate = new GadgetsMenu(plugin, messages, eligible, candidateRiding, equipment, candidateSize, candidateGlow);
        var states = lobby.getLobbyPlayerStateService();
        var activity = activities.getActivityService();
        java.util.function.Consumer<Player> refreshItems = player -> {
            candidateSize.reconcile(player);
            candidateGlow.reconcile(player);
            candidateRiding.afterHotbarRefresh(player);
        };
        Runnable unsubscribe = () -> {
            visibilityService.removeRefreshListener(visibilityChanged);
            states.removeStateListener(candidateRiding);
            states.removeStateListener(candidateSize);
            states.removeStateListener(candidateGlow);
            states.removeStateListener(candidate);
            activity.removeMembershipListener(candidateSize);
            activity.removeMembershipListener(candidateGlow);
            activity.removeMembershipListener(candidate);
            activity.removeMembershipListener(candidateRiding);
            utilityService.removeFlightListener(candidateRiding);
            items.removeRefreshListener(refreshItems);
        };
        try {
            plugin.getServer().getPluginManager().registerEvents(candidate, plugin);
            plugin.getServer().getPluginManager().registerEvents(candidateRiding, plugin);
            plugin.getServer().getPluginManager().registerEvents(candidateSize, plugin);
            plugin.getServer().getPluginManager().registerEvents(candidateGlow, plugin);
            states.addStateListener(candidateRiding);
            states.addStateListener(candidateSize);
            states.addStateListener(candidateGlow);
            states.addStateListener(candidate);
            activity.addMembershipListener(candidateSize);
            activity.addMembershipListener(candidateGlow);
            activity.addMembershipListener(candidate);
            activity.addMembershipListener(candidateRiding);
            utilityService.addFlightListener(candidateRiding);
            visibilityService.addRefreshListener(visibilityChanged);
            items.addRefreshListener(refreshItems);
            lobby.addReloadListener(reload);
            candidateSize.reset();
            unsubscribeRuntime = unsubscribe;
            sizeChanger = candidateSize;
            glow = candidateGlow;
            riding = candidateRiding;
            menu = candidate;
        } catch (RuntimeException failure) {
            unsubscribe.run();
            lobby.removeReloadListener(reload);
            HandlerList.unregisterAll(candidate);
            HandlerList.unregisterAll(candidateRiding);
            HandlerList.unregisterAll(candidateSize);
            HandlerList.unregisterAll(candidateGlow);
            throw failure;
        }
    }

    private Runnable unsubscribeRuntime = () -> { };
    private final java.util.function.Consumer<UUID> visibilityChanged = id -> {
        if (riding != null) riding.reconcileVisibility(id);
    };

    public boolean openMenu(Player player) {
        return menu != null && menu.open(player);
    }

    public boolean handleHotbar(Player player, Action action) {
        return action.isRightClick() && openMenu(player);
    }

    @Override
    public void disable() {
        var previous = menu;
        var previousRiding = riding;
        var previousSize = sizeChanger;
        var previousGlow = glow;
        menu = null;
        riding = null;
        sizeChanger = null;
        glow = null;
        lobby.removeReloadListener(reload);
        unsubscribeRuntime.run();
        unsubscribeRuntime = () -> { };
        if (previousGlow != null) {
            try {
                previousGlow.reset();
            } finally {
                HandlerList.unregisterAll(previousGlow);
            }
        }
        if (previousSize != null) {
            try {
                previousSize.reset();
            } finally {
                HandlerList.unregisterAll(previousSize);
            }
        }
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

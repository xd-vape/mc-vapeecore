package dev.vapee.core.seat;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.module.CoreModule;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class SeatModule implements CoreModule {

    private final JavaPlugin plugin;
    private final LobbyModule lobbyModule;
    private final ActivityModule activityModule;

    private SeatService seatService;
    private SeatListener seatListener;

    public SeatModule(JavaPlugin plugin, LobbyModule lobbyModule, ActivityModule activityModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
    }

    @Override
    public String getName() {
        return "Seat";
    }

    @Override
    public void enable() {
        SeatService newService = new SeatService(plugin);
        SeatListener newListener = new SeatListener(
                lobbyModule.getLobbyService(),
                lobbyModule.getLobbyPlayerStateService(),
                activityModule.getActivityService(),
                newService,
                new SeatPositionResolver()
        );
        int staleSeats;
        try {
            staleSeats = newService.cleanupStaleSeats();
            plugin.getServer().getPluginManager().registerEvents(newListener, plugin);
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newListener);
            try {
                newService.cleanup();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        seatService = newService;
        seatListener = newListener;
        plugin.getLogger().info("Seat module enabled; removed " + staleSeats
                + " stale generic/legacy seat entity/entities.");
    }

    @Override
    public void disable() {
        if (seatListener != null) {
            HandlerList.unregisterAll(seatListener);
        }
        if (seatService != null) {
            try {
                seatService.cleanup();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not fully clean up seat entities.", exception);
            }
        }
        seatListener = null;
        seatService = null;
    }

    public SeatService getSeatService() {
        return Objects.requireNonNull(seatService, "SeatModule is not enabled");
    }
}

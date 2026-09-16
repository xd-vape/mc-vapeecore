package dev.vapee.core.activity;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class ActivityModule implements CoreModule {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;

    private ActivityService activityService;
    private ActivityListener activityListener;

    public ActivityModule(JavaPlugin plugin, PlayerModule playerModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
    }

    @Override
    public String getName() {
        return "Activity";
    }

    @Override
    public void enable() {
        PlayerService newPlayerService = playerModule.getPlayerService();
        ActivityService newActivityService = new ActivityService(
                plugin.getServer(),
                newPlayerService,
                plugin.getLogger()
        );
        ActivityListener newActivityListener = new ActivityListener(newActivityService);

        try {
            plugin.getServer().getPluginManager().registerEvents(newActivityListener, plugin);
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newActivityListener);
            try {
                newActivityService.shutdown();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        activityService = newActivityService;
        activityListener = newActivityListener;
        plugin.getLogger().info("Activity module enabled with "
                + newActivityService.getActivityTypeCount() + " activity type(s), "
                + newActivityService.getVenueCount() + " venue(s), and "
                + newActivityService.getSessionCount() + " session(s)."
        );
    }

    @Override
    public void disable() {
        if (activityListener != null) {
            HandlerList.unregisterAll(activityListener);
        }
        if (activityService != null) {
            try {
                activityService.shutdown();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not fully shut down the activity runtime.", exception);
            }
        }

        activityListener = null;
        activityService = null;
    }

    public ActivityService getActivityService() {
        return Objects.requireNonNull(activityService, "ActivityModule is not enabled");
    }
}

package dev.vapee.core.worlddisplay;

import dev.vapee.core.module.CoreModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class WorldDisplayModule implements CoreModule {

    private final JavaPlugin plugin;
    private WorldDisplayService worldDisplayService;

    public WorldDisplayModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String getName() {
        return "WorldDisplay";
    }

    @Override
    public void enable() {
        WorldDisplayService newService = new WorldDisplayService(plugin);
        int staleDisplays;
        try {
            staleDisplays = newService.cleanupStaleDisplays();
        } catch (RuntimeException exception) {
            try {
                newService.cleanup();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        worldDisplayService = newService;
        plugin.getLogger().info("WorldDisplay module enabled; removed " + staleDisplays
                + " stale display entity/entities.");
    }

    @Override
    public void disable() {
        if (worldDisplayService != null) {
            try {
                worldDisplayService.cleanup();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not fully clean up world displays.", exception);
            }
        }
        worldDisplayService = null;
    }

    public WorldDisplayService getWorldDisplayService() {
        return Objects.requireNonNull(worldDisplayService, "WorldDisplayModule is not enabled");
    }
}

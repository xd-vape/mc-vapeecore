package dev.vapee.core.utility;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class UtilityListener implements Listener {

    private final Consumer<Runnable> scheduler;
    private final BooleanSupplier pluginEnabled;
    private final UtilityService utilityService;
    private boolean active = true;

    public UtilityListener(JavaPlugin plugin, UtilityService utilityService) {
        this(utilityService, task -> plugin.getServer().getScheduler().runTask(plugin, task),
                Objects.requireNonNull(plugin, "plugin")::isEnabled);
    }

    UtilityListener(UtilityService utilityService, Consumer<Runnable> scheduler, BooleanSupplier pluginEnabled) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        utilityService.forgetPlayer(player.getUniqueId());
        scheduler.accept(() -> {
            if (active && pluginEnabled.getAsBoolean() && player.isOnline()) {
                utilityService.normalizeTransientState(player);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        utilityService.cleanupPlayer(event.getPlayer());
    }

    public void disable() {
        active = false;
    }
}

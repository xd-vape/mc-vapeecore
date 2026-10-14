package dev.vapee.core.i18n;

import dev.vapee.core.config.ConfigFiles;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Path;

public final class LocalizationModule implements CoreModule, ReloadParticipant, Listener {
    private final JavaPlugin plugin;
    private final LocalizationService service;
    private final Path directory;
    private final Runnable refresh;
    public LocalizationModule(JavaPlugin plugin) {
        this.plugin = plugin; directory = plugin.getDataFolder().toPath().resolve("lang");
        service = LocalizationService.bundled(plugin.getLogger());
        refresh = () -> { for (var player : plugin.getServer().getOnlinePlayers()) service.localeChanged(player, player.locale()); };
    }
    LocalizationModule(Path directory, LocalizationService service, Runnable refresh) {
        this.plugin = null; this.directory = java.util.Objects.requireNonNull(directory);
        this.service = java.util.Objects.requireNonNull(service); this.refresh = java.util.Objects.requireNonNull(refresh);
    }
    public LocalizationService service() { return service; }
    @Override public String getName() { return "Localization"; }
    @Override public String getReloadName() { return "Localization"; }
    @Override public void enable() {
        try {
            for (Language language : Language.values()) {
                String resource = "lang/" + language.code() + ".yml";
                ConfigFiles.copyDefault(directory.resolve(language.code() + ".yml"), () -> plugin.getResource(resource),
                        () -> new IllegalStateException("Missing bundled resource: " + resource));
            }
            service.apply(service.prepare(directory));
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        } catch (java.io.IOException exception) { throw new IllegalStateException("Could not initialize language overrides", exception); }
    }
    @Override public void disable() { HandlerList.unregisterAll(this); }
    @Override public ReloadPlan prepareReload() {
        var previous = service.snapshot(); var candidate = service.prepare(directory);
        return ReloadPlan.of(() -> { service.apply(candidate); refreshViews(); }, () -> { service.apply(previous); refreshViews(); });
    }
    private void refreshViews() {
        refresh.run();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocaleChange(PlayerLocaleChangeEvent event) { service.localeChanged(event.getPlayer(), event.locale()); }
}

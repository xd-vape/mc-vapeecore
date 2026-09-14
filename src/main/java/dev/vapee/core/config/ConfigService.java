package dev.vapee.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ConfigService {

    private static final String DEFAULT_SERVER_NAME = "Vapee Community";
    private static final String DEFAULT_MESSAGE_PREFIX = "<gray>[<aqua>VapeeCore</aqua>]</gray> ";

    private final JavaPlugin plugin;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public String getServerName() {
        return getConfig().getString("server.name", DEFAULT_SERVER_NAME);
    }

    public String getMessagePrefix() {
        return getConfig().getString("messages.prefix", DEFAULT_MESSAGE_PREFIX);
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("settings.debug", false);
    }
}

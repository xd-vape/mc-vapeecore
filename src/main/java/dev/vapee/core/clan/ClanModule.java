package dev.vapee.core.clan;

import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.clan.gui.ClanMenu;
import dev.vapee.core.clan.gui.ClanMenuListener;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class ClanModule implements CoreModule {
    private final JavaPlugin plugin;
    private final ConfigService config;
    private final IdentityModule identity;
    private final MessageService messages;
    private final CommandHelpRenderer help;
    private ClanService service;
    private ClanMenu menu;
    private ClanMenuListener listener;
    private PluginCommand command;

    public ClanModule(JavaPlugin plugin, ConfigService config, IdentityModule identity,
                      MessageService messages, CommandHelpRenderer help) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.help = Objects.requireNonNull(help, "help");
    }

    @Override public String getName() { return "Clan"; }

    @Override public void enable() {
        ClanService nextService = new ClanService(
                new FileClanRepository(plugin.getDataFolder().toPath().resolve("clans.yml")),
                config::getClanLimits, Clock.systemUTC(), UUID::randomUUID);
        ClanMenu nextMenu = new ClanMenu(plugin, nextService, identity.getIdentityService(), messages);
        ClanMessages feedback = new ClanMessages(messages, plugin.getServer()::getPlayer);
        ClanMenuListener nextListener = new ClanMenuListener(nextMenu, nextService, feedback,
                messages, plugin.getLogger());
        PluginCommand nextCommand = Objects.requireNonNull(plugin.getCommand("clan"),
                "Command 'clan' is missing from plugin.yml");
        try {
            ClanCommand executor = new ClanCommand(plugin, nextService, identity.getIdentityService(),
                    messages, help, nextMenu);
            plugin.getServer().getPluginManager().registerEvents(nextListener, plugin);
            nextCommand.setExecutor(executor);
            nextCommand.setTabCompleter(executor);
        } catch (RuntimeException exception) {
            nextMenu.closeOpenInventories();
            HandlerList.unregisterAll(nextListener);
            nextCommand.setExecutor(null);
            nextCommand.setTabCompleter(null);
            throw exception;
        }
        service = nextService;
        menu = nextMenu;
        listener = nextListener;
        command = nextCommand;
        plugin.getLogger().info("Clan module enabled.");
    }

    @Override public void disable() {
        try {
            if (menu != null) menu.closeOpenInventories();
        } finally {
            if (listener != null) HandlerList.unregisterAll(listener);
            if (command != null) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
            command = null;
            listener = null;
            menu = null;
            service = null;
        }
    }

    public ClanService getClanService() {
        return Objects.requireNonNull(service, "ClanModule is not enabled");
    }
}

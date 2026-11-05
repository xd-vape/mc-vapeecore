package dev.vapee.core.permission;

import dev.vapee.core.staff.StaffPanel;

import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.command.defaults.HelpCommand;
import org.bukkit.command.defaults.PluginsCommand;
import org.bukkit.command.defaults.VersionCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Remove-only root presentation. Paper and each executor retain execution authorization. */
public final class CommandVisibilityListener implements Listener {
    private static final Set<String> BUKKIT_INFORMATION =
            Set.of("?", "help", "about", "version", "ver", "plugins", "pl");
    private final Plugin owner;
    private final Supplier<CommandVisibilityConfig> configuration;
    private final Function<String, Command> commands;
    private boolean active = true;

    CommandVisibilityListener(
            Plugin owner,
            Supplier<CommandVisibilityConfig> configuration,
            Function<String, Command> commands) {
        this.owner = Objects.requireNonNull(owner);
        this.configuration = Objects.requireNonNull(configuration);
        this.commands = Objects.requireNonNull(commands);
    }

    void stop() {
        active = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommands(PlayerCommandSendEvent event) {
        if (!active) return;
        CommandVisibilityConfig settings = configuration.get();
        if (!settings.enabled()) return;
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        boolean operator = player.hasPermission("vapeecore.admin");
        event.getCommands()
                .removeIf(
                        label -> {
                            Command command = commands.apply(label);
                            if (command == null)
                                return false; // Unknown/future foreign roots stay with Paper/their
                            // owner.
                            if (command instanceof PluginIdentifiableCommand identified
                                    && identified.getPlugin() == owner) {
                                if (!command.testPermissionSilent(player)) return true;
                                // Keep the descriptor ungated so execution can deliver localized denials.
                                if (command.getName().equalsIgnoreCase("staff")
                                        && !player.hasPermission(StaffPanel.PERMISSION)) return true;
                                return settings.hideProfile()
                                        && !operator
                                        && command.getName().equalsIgnoreCase("profile");
                            }
                            String name = command.getName().toLowerCase(Locale.ROOT);
                            String root = name.substring(name.indexOf(':') + 1);
                            boolean information =
                                    BUKKIT_INFORMATION.contains(root)
                                            && platformOwner(command, "Bukkit");
                            boolean luckPerms =
                                    command instanceof PluginIdentifiableCommand identified
                                            && identified
                                                    .getPlugin()
                                                    .getName()
                                                    .equalsIgnoreCase("LuckPerms")
                                            && command.getName().equalsIgnoreCase("luckperms");
                            if (information || luckPerms)
                                return !operator || !command.testPermissionSilent(player);
                            if (root.equals("w") && platformOwner(command, "Minecraft")) {
                                Command privateMessages = commands.apply("msg");
                                if (privateMessages instanceof PluginIdentifiableCommand identified
                                        && identified.getPlugin() == owner)
                                    return !operator || !command.testPermissionSilent(player);
                            }
                            return false;
                        });
    }

    private static boolean platformOwner(Command command, String namespace) {
        if (namespace.equals("Bukkit")
                && (command.getClass() == HelpCommand.class
                        || command.getClass() == VersionCommand.class
                        || command.getClass() == PluginsCommand.class)) return true;
        // Paper 1.21.11 exposes platform AND foreign Brigadier roots through the same Bukkit
        // wrapper.
        // Its public help-owner metadata distinguishes them; unknown implementations remain
        // visible.
        if (!(command instanceof BukkitCommand)
                || !command.getClass()
                        .getName()
                        .equals("org.bukkit.craftbukkit.command.VanillaCommandWrapper"))
            return false;
        try {
            String helpOwner =
                    (String) command.getClass().getField("helpCommandNamespace").get(command);
            boolean matches =
                    namespace.equalsIgnoreCase(helpOwner)
                            || namespace.equals("Bukkit") && "Paper".equalsIgnoreCase(helpOwner);
            if (!matches) return false;
            Object node = command.getClass().getField("vanillaCommand").get(command);
            Object metadata = node.getClass().getField("apiCommandMeta").get(node);
            // A foreign plugin can use the same wrapper and even the same help-owner wording.
            return metadata == null
                    || metadata.getClass().getMethod("pluginMeta").invoke(metadata) == null;
        } catch (ReflectiveOperationException | ClassCastException unavailable) {
            return false;
        }
    }
}

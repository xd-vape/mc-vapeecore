package dev.vapee.core.lobby.message;

import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LobbyMessageService {

    private final LobbyConfig lobbyConfig;
    private final BiFunction<String, TagResolver, Component> renderer;
    private final Logger logger;

    public LobbyMessageService(JavaPlugin plugin, LobbyConfig lobbyConfig, MessageService messageService) {
        this(
                lobbyConfig,
                Objects.requireNonNull(messageService, "messageService")::deserialize,
                Objects.requireNonNull(plugin, "plugin").getLogger()
        );
    }

    public LobbyMessageService(
            LobbyConfig lobbyConfig,
            BiFunction<String, TagResolver, Component> renderer,
            Logger logger
    ) {
        this.lobbyConfig = Objects.requireNonNull(lobbyConfig, "lobbyConfig");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Component renderJoinMessage(Player player) {
        return render(player, lobbyConfig.getJoinMessageSettings(), true);
    }

    public Component renderQuitMessage(Player player) {
        return render(player, lobbyConfig.getQuitMessageSettings(), false);
    }

    private Component render(Player player, LobbyConfig.MessageSettings settings, boolean join) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!Objects.requireNonNull(settings, "settings").enabled()) {
            return null;
        }

        Component playerName = Component.text(validatedPlayer.getName());
        try {
            return renderer.apply(
                    settings.format(),
                    Placeholder.component("name", playerName)
            );
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not render the lobby " + (join ? "join" : "quit")
                            + " message for " + validatedPlayer.getUniqueId()
                            + "; using a safe component fallback.",
                    exception
            );
            return Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text(join ? "+" : "-", join ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(playerName.color(NamedTextColor.WHITE));
        }
    }
}

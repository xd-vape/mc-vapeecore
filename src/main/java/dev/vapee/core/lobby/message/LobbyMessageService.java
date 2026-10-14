package dev.vapee.core.lobby.message;

import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.i18n.LocalizationService;
import net.kyori.adventure.audience.Audience;
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
    private final LocalizationService localization;
    private final java.util.function.BiConsumer<Audience, Component> delivery;

    public LobbyMessageService(JavaPlugin plugin, LobbyConfig lobbyConfig, MessageService messageService) {
        this(
                lobbyConfig,
                Objects.requireNonNull(messageService, "messageService")::deserialize,
                Objects.requireNonNull(plugin, "plugin").getLogger(), messageService.localization(), messageService::sendRaw
        );
    }

    public LobbyMessageService(
            LobbyConfig lobbyConfig,
            BiFunction<String, TagResolver, Component> renderer,
            Logger logger
    ) {
        this(lobbyConfig, renderer, logger, LocalizationService.bundled(logger), null);
    }

    public LobbyMessageService(LobbyConfig lobbyConfig, BiFunction<String, TagResolver, Component> renderer,
                               Logger logger, LocalizationService localization,
                               java.util.function.BiConsumer<Audience, Component> delivery) {
        this.localization = Objects.requireNonNull(localization);
        this.delivery = delivery == null ? (viewer, message) -> viewer.sendMessage(localization.render(viewer, message)) : delivery;
        this.lobbyConfig = Objects.requireNonNull(lobbyConfig, "lobbyConfig");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Component renderJoinMessage(Player player) {
        return render(player, lobbyConfig.getJoinMessageSettings(), true, java.util.Locale.ENGLISH);
    }

    public Component renderQuitMessage(Player player) {
        return render(player, lobbyConfig.getQuitMessageSettings(), false, java.util.Locale.ENGLISH);
    }

    public boolean perRecipient(boolean join) {
        return (join ? lobbyConfig.getJoinMessageSettings() : lobbyConfig.getQuitMessageSettings()).perRecipient();
    }

    /** Resolves the wrapper for each viewer; the subject's identity stays a literal placeholder. */
    public void broadcast(Player subject, boolean join, Iterable<? extends Audience> viewers, Audience console) {
        LobbyConfig.MessageSettings settings = join ? lobbyConfig.getJoinMessageSettings() : lobbyConfig.getQuitMessageSettings();
        if (!settings.enabled()) return;
        for (Audience viewer : viewers) {
            Component message = render(subject, settings, join, localization.locale(viewer));
            if (message != null) delivery.accept(viewer, localization.render(viewer, message));
        }
        if (console != null) {
            Component message = render(subject, settings, join, java.util.Locale.ENGLISH);
            if (message != null) delivery.accept(console, localization.render(java.util.Locale.ENGLISH, message));
        }
    }

    private Component render(Player player, LobbyConfig.MessageSettings settings, boolean join, java.util.Locale locale) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!Objects.requireNonNull(settings, "settings").enabled()) {
            return null;
        }

        Component playerName = Component.text(validatedPlayer.getName());
        try {
            return renderer.apply(
                    settings.formats().resolve(locale),
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

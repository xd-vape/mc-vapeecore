package dev.vapee.core.presentation;

import dev.vapee.core.presentation.PresentationRenderer.RenderedPresentation;
import dev.vapee.core.presentation.config.PresentationConfig;
import dev.vapee.core.presentation.scoreboard.ScoreboardService;
import dev.vapee.core.presentation.tablist.TablistService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PresentationService {

    private final JavaPlugin plugin;
    private final PresentationConfig presentationConfig;
    private final PresentationRenderer presentationRenderer;
    private final ScoreboardService scoreboardService;
    private final TablistService tablistService;
    private final Logger logger;

    public PresentationService(
            JavaPlugin plugin,
            PresentationConfig presentationConfig,
            PresentationRenderer presentationRenderer,
            ScoreboardService scoreboardService,
            TablistService tablistService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.presentationConfig = Objects.requireNonNull(presentationConfig, "presentationConfig");
        this.presentationRenderer = Objects.requireNonNull(presentationRenderer, "presentationRenderer");
        this.scoreboardService = Objects.requireNonNull(scoreboardService, "scoreboardService");
        this.tablistService = Objects.requireNonNull(tablistService, "tablistService");
        this.logger = plugin.getLogger();
    }

    public void updatePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!presentationConfig.isEnabled() || !validatedPlayer.isOnline()) {
            removePlayer(validatedPlayer);
            return;
        }

        try {
            RenderedPresentation presentation = presentationRenderer.render(validatedPlayer);
            scoreboardService.updatePlayer(
                    validatedPlayer,
                    presentation.scoreboardTitle(),
                    presentation.scoreboardLines()
            );
            tablistService.updatePlayer(validatedPlayer, presentation);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not update presentation for " + validatedPlayer.getUniqueId() + ".",
                    exception
            );
        }
    }

    public void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void removePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        scoreboardService.removePlayer(validatedPlayer);
        tablistService.removePlayer(validatedPlayer);
    }

    public void removeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removePlayer(player);
        }
        scoreboardService.clearStates();
        tablistService.clearTrackedPlayers();
    }
}

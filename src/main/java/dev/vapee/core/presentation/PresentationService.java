package dev.vapee.core.presentation;

import dev.vapee.core.presentation.PresentationRenderer.RenderedPresentation;
import dev.vapee.core.presentation.config.PresentationConfig;
import dev.vapee.core.presentation.nametag.NametagService;
import dev.vapee.core.presentation.scoreboard.ScoreboardService;
import dev.vapee.core.presentation.tablist.TablistService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PresentationService {

    private final JavaPlugin plugin;
    private final PresentationConfig presentationConfig;
    private final PresentationRenderer presentationRenderer;
    private final ScoreboardService scoreboardService;
    private final TablistService tablistService;
    private final NametagService nametagService;
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
        this.nametagService = new NametagService(scoreboardService, logger);
    }

    public void updatePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!presentationConfig.isEnabled() || !validatedPlayer.isOnline()) {
            removePlayer(validatedPlayer);
            return;
        }

        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (!online.contains(validatedPlayer)) online.add(validatedPlayer);
        Map<UUID, RenderedPresentation> rendered = renderPlayers(
                presentationConfig.isNametagEnabled() ? online : List.of(validatedPlayer));
        updatePlayer(validatedPlayer, rendered.get(validatedPlayer.getUniqueId()), targets(online, rendered));
    }

    private void updatePlayer(Player player, RenderedPresentation presentation, List<NametagService.Target> targets) {
        try {
            if (presentation == null) {
                // Provider failure preserves tab/sidebar writes, but cannot preserve hidden relations.
                nametagService.updateViewer(player, targets);
                return;
            }
            scoreboardService.updatePlayer(
                    player,
                    presentation.scoreboardTitle(),
                    presentation.scoreboardLines(),
                    scoreboardService.isNametagEligible(player)
            );
            nametagService.updateViewer(player, targets);
            tablistService.updatePlayer(player, presentation);
        } catch (RuntimeException exception) {
            warn(player, exception);
        }
    }

    public void updateAll() {
        if (!presentationConfig.isEnabled()) { removeAll(); return; }
        List<Player> online = List.copyOf(plugin.getServer().getOnlinePlayers());
        Map<UUID, RenderedPresentation> rendered = renderPlayers(online);
        List<NametagService.Target> targets = targets(online, rendered);
        for (Player player : online) updatePlayer(player, rendered.get(player.getUniqueId()), targets);
    }

    private Map<UUID, RenderedPresentation> renderPlayers(List<Player> online) {
        Map<UUID, RenderedPresentation> rendered = new LinkedHashMap<>();
        for (Player player : online) {
            try { rendered.put(player.getUniqueId(), presentationRenderer.render(player)); }
            catch (RuntimeException exception) { warn(player, exception); }
        }
        return rendered;
    }

    private List<NametagService.Target> targets(List<Player> online, Map<UUID, RenderedPresentation> rendered) {
        List<NametagService.Target> targets = new ArrayList<>();
        for (Player player : online) {
            RenderedPresentation presentation = rendered.get(player.getUniqueId());
            if (presentation != null && scoreboardService.isNametagEligible(player)) {
                targets.add(new NametagService.Target(player.getUniqueId(), player.getName(),
                        presentation.nametagPrefix(), presentation.nametagSuffix(), presentation.nametagColor(), player));
            }
        }
        return targets;
    }

    private void warn(Player player, RuntimeException exception) {
        logger.log(Level.WARNING, "Could not update presentation for " + player.getUniqueId() + ".", exception);
    }

    public void removePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        nametagService.removeTarget(validatedPlayer.getUniqueId());
        nametagService.removeViewer(validatedPlayer.getUniqueId());
        scoreboardService.removePlayer(validatedPlayer);
        tablistService.removePlayer(validatedPlayer);
    }

    public void removeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removePlayer(player);
        }
        scoreboardService.clearStates();
        nametagService.removeAll();
        tablistService.clearTrackedPlayers();
    }
}

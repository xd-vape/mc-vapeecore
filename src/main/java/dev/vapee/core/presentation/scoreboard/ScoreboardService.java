package dev.vapee.core.presentation.scoreboard;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presentation.config.PresentationConfig;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ScoreboardService {

    private static final String OBJECTIVE_NAME = "vapeecore";
    private static final String ENTRY_PREFIX = "vapeecore_line_";

    private final PresentationConfig presentationConfig;
    private final PlayerSettingsService playerSettingsService;
    private final LobbyService lobbyService;
    private final ScoreboardManager scoreboardManager;
    private final Map<UUID, PlayerScoreboardState> states = new HashMap<>();

    public ScoreboardService(
            JavaPlugin plugin,
            PresentationConfig presentationConfig,
            PlayerSettingsService playerSettingsService,
            LobbyService lobbyService
    ) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        this.presentationConfig = Objects.requireNonNull(presentationConfig, "presentationConfig");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.scoreboardManager = Objects.requireNonNull(
                validatedPlugin.getServer().getScoreboardManager(),
                "scoreboardManager"
        );
    }

    public void updatePlayer(Player player, Component title, List<Component> lines) {
        updatePlayer(player, title, lines, false);
    }

    /** Sidebar and nametags share exactly one viewer-board owner. */
    public void updatePlayer(Player player, Component title, List<Component> lines, boolean nametagRequired) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Component validatedTitle = Objects.requireNonNull(title, "title");
        List<Component> validatedLines = List.copyOf(Objects.requireNonNull(lines, "lines"));

        boolean sidebarRequired = shouldShow(validatedPlayer);
        if (!sidebarRequired && !nametagRequired) {
            removePlayer(validatedPlayer);
            return;
        }

        UUID uniqueId = validatedPlayer.getUniqueId();
        PlayerScoreboardState state = states.get(uniqueId);
        if (state != null && !ownsScoreboard(validatedPlayer)) {
            states.remove(uniqueId);
            state.unregister();
            return;
        }

        if (state != null && state.objective != null && state.scores.size() != validatedLines.size()
                && !nametagRequired) {
            removePlayer(validatedPlayer);
            state = null;
        }

        if (state == null) {
            if (validatedPlayer.getScoreboard() != scoreboardManager.getMainScoreboard()) {
                return;
            }

            PlayerScoreboardState newState = new PlayerScoreboardState(scoreboardManager.getNewScoreboard());
            try {
                if (sidebarRequired) newState.update(validatedTitle, validatedLines);
                validatedPlayer.setScoreboard(newState.scoreboard);
                states.put(uniqueId, newState);
            } catch (RuntimeException exception) {
                newState.unregister();
                throw exception;
            }
            return;
        }

        if (sidebarRequired) state.update(validatedTitle, validatedLines);
        else state.unregister();
    }

    public void removePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        boolean owned = ownsScoreboard(validatedPlayer);
        PlayerScoreboardState state = states.remove(validatedPlayer.getUniqueId());
        if (state == null) {
            return;
        }

        if (owned) {
            validatedPlayer.setScoreboard(scoreboardManager.getMainScoreboard());
        }
        state.unregister();
    }

    /** Current viewer board identity only; this grants no target visibility or foreign-team ownership. */
    public boolean ownsScoreboard(Player viewer) {
        Player checked = Objects.requireNonNull(viewer, "viewer");
        PlayerScoreboardState state = states.get(checked.getUniqueId());
        return state != null && checked.getScoreboard() == state.scoreboard;
    }

    public Optional<Scoreboard> getOwnedScoreboard(Player viewer) {
        return ownsScoreboard(viewer) ? Optional.of(states.get(viewer.getUniqueId()).scoreboard) : Optional.empty();
    }

    /** Sidebar preference is not a nametag preference; loaded state and world scope still gate it. */
    public boolean isNametagEligible(Player player) {
        return presentationConfig.isEnabled() && presentationConfig.isNametagEnabled() && player.isOnline()
                && playerSettingsService.getSettings(player.getUniqueId()).isPresent()
                && (!presentationConfig.isNametagLobbyOnly() || lobbyService.isLobbyWorld(player.getWorld()));
    }

    public void clearStates() {
        for (PlayerScoreboardState state : states.values()) {
            state.unregister();
        }
        states.clear();
    }

    private boolean shouldShow(Player player) {
        if (!presentationConfig.isScoreboardEnabled()) {
            return false;
        }
        if (!playerSettingsService.isScoreboardEnabled(player.getUniqueId()).orElse(false)) {
            return false;
        }
        return !presentationConfig.isScoreboardLobbyOnly() || lobbyService.isLobbyWorld(player.getWorld());
    }

    private static final class PlayerScoreboardState {

        private final Scoreboard scoreboard;
        private Objective objective;
        private List<Score> scores = List.of();

        private Component title;
        private List<Component> lines;

        private PlayerScoreboardState(Scoreboard scoreboard) {
            this.scoreboard = scoreboard;
        }

        private void update(Component newTitle, List<Component> newLines) {
            if (objective != null && !objective.equals(scoreboard.getObjective(OBJECTIVE_NAME))) {
                // A same-name foreign objective is never adopted or cleared.
                objective = null;
                scores = List.of();
            }
            if (objective != null && scores.size() != newLines.size()) unregister();
            if (objective == null) {
                if (scoreboard.getObjective(OBJECTIVE_NAME) != null) return;
                objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, newTitle);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.numberFormat(NumberFormat.blank());
                List<Score> newScores = new ArrayList<>(newLines.size());
                for (int index = 0; index < newLines.size(); index++) {
                    Score score = objective.getScore(ENTRY_PREFIX + index);
                    score.setScore(newLines.size() - index);
                    score.customName(newLines.get(index));
                    newScores.add(score);
                }
                scores = List.copyOf(newScores);
                title = newTitle;
                lines = List.copyOf(newLines);
                return;
            }
            if (!title.equals(newTitle)) {
                objective.displayName(newTitle);
                title = newTitle;
            }

            for (int index = 0; index < scores.size(); index++) {
                Component newLine = newLines.get(index);
                if (!lines.get(index).equals(newLine)) {
                    scores.get(index).customName(newLine);
                }
            }
            lines = List.copyOf(newLines);
        }

        private void unregister() {
            if (objective != null && objective.equals(scoreboard.getObjective(OBJECTIVE_NAME))) objective.unregister();
            objective = null;
            scores = List.of();
        }
    }
}

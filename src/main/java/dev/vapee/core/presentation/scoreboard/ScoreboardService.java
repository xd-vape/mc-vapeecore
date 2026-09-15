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
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Component validatedTitle = Objects.requireNonNull(title, "title");
        List<Component> validatedLines = List.copyOf(Objects.requireNonNull(lines, "lines"));

        if (!shouldShow(validatedPlayer)) {
            removePlayer(validatedPlayer);
            return;
        }

        UUID uniqueId = validatedPlayer.getUniqueId();
        PlayerScoreboardState state = states.get(uniqueId);
        if (state != null && validatedPlayer.getScoreboard() != state.scoreboard) {
            states.remove(uniqueId);
            state.unregister();
            return;
        }

        if (state != null && state.scores.size() != validatedLines.size()) {
            removePlayer(validatedPlayer);
            state = null;
        }

        if (state == null) {
            if (validatedPlayer.getScoreboard() != scoreboardManager.getMainScoreboard()) {
                return;
            }

            PlayerScoreboardState newState = createState(validatedTitle, validatedLines);
            try {
                validatedPlayer.setScoreboard(newState.scoreboard);
                states.put(uniqueId, newState);
            } catch (RuntimeException exception) {
                newState.unregister();
                throw exception;
            }
            return;
        }

        state.update(validatedTitle, validatedLines);
    }

    public void removePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        PlayerScoreboardState state = states.remove(validatedPlayer.getUniqueId());
        if (state == null) {
            return;
        }

        if (validatedPlayer.getScoreboard() == state.scoreboard) {
            validatedPlayer.setScoreboard(scoreboardManager.getMainScoreboard());
        }
        state.unregister();
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

    private PlayerScoreboardState createState(Component title, List<Component> lines) {
        Scoreboard scoreboard = scoreboardManager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                title
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        List<Score> scores = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Score score = objective.getScore(ENTRY_PREFIX + index);
            score.setScore(lines.size() - index);
            score.customName(lines.get(index));
            scores.add(score);
        }
        return new PlayerScoreboardState(scoreboard, objective, scores, title, lines);
    }

    private static final class PlayerScoreboardState {

        private final Scoreboard scoreboard;
        private final Objective objective;
        private final List<Score> scores;

        private Component title;
        private List<Component> lines;

        private PlayerScoreboardState(
                Scoreboard scoreboard,
                Objective objective,
                List<Score> scores,
                Component title,
                List<Component> lines
        ) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.scores = List.copyOf(scores);
            this.title = title;
            this.lines = List.copyOf(lines);
        }

        private void update(Component newTitle, List<Component> newLines) {
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
            objective.unregister();
        }
    }
}

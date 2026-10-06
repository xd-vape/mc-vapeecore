package dev.vapee.core.presentation.nametag;

import dev.vapee.core.presentation.scoreboard.ScoreboardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Team ownership only. ScoreboardService is the sole owner/acquirer of viewer boards. */
public final class NametagService {
    private final ScoreboardService scoreboards;
    private final Logger logger;
    private final Map<UUID, ViewerState> viewers = new HashMap<>();

    public NametagService(ScoreboardService scoreboards, Logger logger) {
        this.scoreboards = Objects.requireNonNull(scoreboards, "scoreboards");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void updateViewer(Player viewer, Collection<Target> targets) {
        UUID viewerId = viewer.getUniqueId();
        Scoreboard board = scoreboards.isNametagEligible(viewer)
                ? scoreboards.getOwnedScoreboard(viewer).orElse(null) : null;
        ViewerState state = viewers.get(viewerId);
        if (state != null && state.board != board) {
            removeViewer(viewerId);
            state = null;
        }
        if (board == null) return;
        if (state == null) {
            state = new ViewerState(board);
            viewers.put(viewerId, state);
        }
        Set<UUID> desired = new HashSet<>();
        for (Target target : targets) {
            // Presentation reads Paper's effective directional visibility; it never hides/shows.
            if (!target.visibleTo(viewer)) continue;
            desired.add(target.id());
            try {
                state.update(target);
            } catch (RuntimeException exception) {
                warn(viewerId, target.id(), exception);
            }
        }
        for (UUID targetId : List.copyOf(state.teams.keySet())) {
            if (!desired.contains(targetId)) removeRelation(viewerId, state, targetId);
        }
    }

    public void removeTarget(UUID targetId) {
        for (Map.Entry<UUID, ViewerState> viewer : List.copyOf(viewers.entrySet())) {
            removeRelation(viewer.getKey(), viewer.getValue(), targetId);
        }
    }

    public void removeViewer(UUID viewerId) {
        ViewerState state = viewers.remove(viewerId);
        if (state == null) return;
        for (UUID targetId : List.copyOf(state.teams.keySet())) removeRelation(viewerId, state, targetId);
    }

    public void removeAll() {
        for (UUID viewerId : List.copyOf(viewers.keySet())) removeViewer(viewerId);
    }

    private void removeRelation(UUID viewerId, ViewerState state, UUID targetId) {
        OwnedTeam team = state.teams.get(targetId);
        if (team == null) return;
        try {
            if (team.ownsCurrent()) team.team.unregister();
            state.teams.remove(targetId);
        } catch (RuntimeException exception) {
            warn(viewerId, targetId, exception);
        }
    }

    private void warn(UUID viewerId, UUID targetId, RuntimeException exception) {
        logger.log(Level.WARNING, "Could not update/clean nametag for viewer " + viewerId
                + " and target " + targetId + ".", exception);
    }

    /** Full UUID is bijective; 37 ASCII characters are within the verified Paper limit. */
    static String teamName(UUID targetId) { return "vc_n_" + targetId.toString().replace("-", ""); }

    private static final class ViewerState {
        private final Scoreboard board;
        private final Map<UUID, OwnedTeam> teams = new HashMap<>();

        private ViewerState(Scoreboard board) { this.board = board; }

        private void update(Target target) {
            OwnedTeam owned = teams.get(target.id());
            if (owned != null && !owned.ownsCurrent()) {
                // Relinquish, never overwrite or unregister an observable foreign takeover.
                teams.remove(target.id());
                owned = null;
            }
            if (owned != null && !owned.entry.equals(target.entry())) {
                owned.team.unregister();
                teams.remove(target.id());
                owned = null;
            }
            if (owned == null) {
                String name = teamName(target.id());
                if (board.getTeam(name) != null || board.getEntryTeam(target.entry()) != null) return;
                Team team = board.registerNewTeam(name);
                owned = new OwnedTeam(board, name, team, target.entry());
                teams.put(target.id(), owned);
            }
            owned.write(target.prefix(), target.suffix(), target.color());
            if (owned.entries.isEmpty()) {
                // Recheck immediately before addEntry: that API would otherwise steal membership.
                if (board.getEntryTeam(target.entry()) != null) return;
                try { owned.team.addEntry(target.entry()); }
                finally { owned.entries = Set.copyOf(owned.team.getEntries()); }
            }
        }
    }

    private static final class OwnedTeam {
        private final Scoreboard board;
        private final String name;
        private final Team team;
        private final String entry;
        private final Component displayName;
        private TextColor color;
        private final boolean friendlyFire;
        private final boolean friendlyInvisibles;
        private final Map<Team.Option, Team.OptionStatus> options = new EnumMap<>(Team.Option.class);
        private Component prefix;
        private Component suffix;
        private Component requestedPrefix;
        private Component requestedSuffix;
        private Set<String> entries;

        private OwnedTeam(Scoreboard board, String name, Team team, String entry) {
            this.board = board;
            this.name = name;
            this.team = team;
            this.entry = entry;
            displayName = team.displayName();
            prefix = team.prefix();
            suffix = team.suffix();
            requestedPrefix = prefix;
            requestedSuffix = suffix;
            color = readColor(team);
            friendlyFire = team.allowFriendlyFire();
            friendlyInvisibles = team.canSeeFriendlyInvisibles();
            for (Team.Option option : Team.Option.values()) options.put(option, team.getOption(option));
            entries = Set.copyOf(team.getEntries());
        }

        private boolean ownsCurrent() {
            // CraftTeam.equals compares the underlying PlayerTeam, not the wrapper or just its name.
            if (!team.equals(board.getTeam(name))) return false;
            if (team.getScoreboard() != board || !entries.equals(team.getEntries())
                    || (!entries.isEmpty() && (!entries.equals(Set.of(entry))
                    || !team.equals(board.getEntryTeam(entry))))) return false;
            return prefix.equals(team.prefix()) && suffix.equals(team.suffix())
                    && displayName.equals(team.displayName()) && Objects.equals(color, readColor(team))
                    && friendlyFire == team.allowFriendlyFire()
                    && friendlyInvisibles == team.canSeeFriendlyInvisibles()
                    && options.entrySet().stream().allMatch(option -> team.getOption(option.getKey()) == option.getValue());
        }

        private void write(Component nextPrefix, Component nextSuffix, NamedTextColor nextColor) {
            if (!Objects.equals(color, nextColor)) {
                try { team.color(nextColor); }
                finally { color = readColor(team); }
            }
            if (!requestedPrefix.equals(nextPrefix)) {
                try { team.prefix(nextPrefix); requestedPrefix = nextPrefix; }
                finally { prefix = team.prefix(); }
            }
            if (!requestedSuffix.equals(nextSuffix)) {
                try { team.suffix(nextSuffix); requestedSuffix = nextSuffix; }
                finally { suffix = team.suffix(); }
            }
        }

        private static TextColor readColor(Team team) {
            // Paper's default RESET has no RGB value; color() throws unless hasColor() is true.
            return team.hasColor() ? team.color() : null;
        }
    }

    /** Ephemeral refresh input only; no Player reference is retained in ownership metadata. */
    public record Target(UUID id, String entry, Component prefix, Component suffix, NamedTextColor color, Player player) {
        public Target {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(suffix, "suffix");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(player, "player");
        }
        private boolean visibleTo(Player viewer) { return player.isOnline() && viewer.canSee(player); }
    }
}

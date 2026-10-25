package dev.vapee.core.presentation.tablist;

import dev.vapee.core.presentation.PresentationRenderer.RenderedPresentation;
import dev.vapee.core.presentation.config.PresentationConfig;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TablistService {

    private final PresentationConfig presentationConfig;
    private final Function<UUID, Optional<RankInfo>> primaryRank;
    private final Supplier<List<RankInfo>> rankTrack;
    private final Map<UUID, PlayerFields> modifiedPlayers = new HashMap<>();

    public TablistService(PresentationConfig presentationConfig) {
        this(presentationConfig, ignored -> Optional.empty(), List::of);
    }

    public TablistService(PresentationConfig presentationConfig, RankService rankService) {
        this(presentationConfig, Objects.requireNonNull(rankService, "rankService")::getPrimaryRank,
                () -> rankService.getPublicRanks().ranks());
    }

    private TablistService(PresentationConfig presentationConfig,
                          Function<UUID, Optional<RankInfo>> primaryRank, Supplier<List<RankInfo>> rankTrack) {
        this.presentationConfig = Objects.requireNonNull(presentationConfig, "presentationConfig");
        this.primaryRank = primaryRank;
        this.rankTrack = rankTrack;
    }

    /** Reconciles the complete online roster; snapshots of LP data live only for this refresh. */
    public void updateOrder(Collection<? extends Player> players) {
        if (!presentationConfig.isEnabled() || !presentationConfig.isTablistEnabled()) {
            players.forEach(this::removePlayer);
            return;
        }
        List<OrderTarget> roster = new ArrayList<>();
        for (Player player : players) {
            if (player.isOnline()) {
                UUID id = player.getUniqueId();
                roster.add(new OrderTarget(player, id, player.getName(), primaryRank.apply(id)));
            }
        }
        boolean missingWeight = roster.stream().anyMatch(target -> target.rank().isEmpty()
                || target.rank().orElseThrow().weight().isEmpty());
        Map<String, Priority> fallback = missingWeight ? trackPriorities(rankTrack.get(), roster) : Map.of();
        roster.sort(Comparator.<OrderTarget, Priority>comparing(target -> priority(target.rank(), fallback),
                        Comparator.reverseOrder())
                .thenComparing(OrderTarget::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OrderTarget::id));
        for (int index = 0; index < roster.size(); index++) {
            OrderTarget target = roster.get(index);
            PlayerFields fields = modifiedPlayers.computeIfAbsent(target.id(), ignored -> new PlayerFields());
            fields.order.write(target.player(), roster.size() - index);
        }
    }

    private static Priority priority(Optional<RankInfo> rank, Map<String, Priority> fallback) {
        if (rank.isEmpty()) return Priority.UNKNOWN;
        RankInfo info = rank.orElseThrow();
        return info.weight().isPresent() ? new Priority(info.weight().getAsInt(), 0)
                : fallback.getOrDefault(groupId(info), Priority.UNKNOWN);
    }

    private static Map<String, Priority> trackPriorities(List<RankInfo> track, List<OrderTarget> roster) {
        Map<String, Integer> positions = new HashMap<>();
        Map<String, Integer> weights = new HashMap<>();
        for (int index = 0; index < track.size(); index++) {
            RankInfo rank = track.get(index);
            if (positions.put(groupId(rank), index) != null) return Map.of();
            rank.weight().ifPresent(weight -> weights.put(groupId(rank), weight));
        }
        // Prefer the actual primary-rank snapshot when LP changes during this frame's reads.
        for (OrderTarget target : roster) {
            target.rank().ifPresent(rank -> rank.weight().ifPresent(weight -> weights.put(groupId(rank), weight)));
        }
        Integer previous = null;
        Integer[] nextWeight = new Integer[track.size()];
        for (int index = track.size() - 1; index >= 0; index--) {
            String id = groupId(track.get(index));
            if (weights.containsKey(id)) previous = weights.get(id);
            nextWeight[index] = previous;
        }
        Integer lower = null;
        for (RankInfo rank : track) {
            Integer weight = weights.get(groupId(rank));
            if (weight == null) continue;
            if (lower != null && weight < lower) return Map.of(); // Conflicting priority evidence.
            lower = weight;
        }
        boolean comparable = weights.keySet().stream().allMatch(positions::containsKey);
        boolean anchored = track.stream().anyMatch(rank -> weights.containsKey(groupId(rank)));
        Map<String, Priority> priorities = new HashMap<>();
        lower = null;
        for (int index = 0; index < track.size(); index++) {
            String id = groupId(track.get(index));
            if (weights.containsKey(id)) {
                lower = weights.get(id);
            } else if (!comparable || lower == null) {
                // No invented conversion between track indices and unrelated numeric LP weights.
                priorities.put(id, new Priority(comparable && !anchored ? 0 : Long.MIN_VALUE, index + 1));
            } else {
                // Unweighted ranks sit above their lower anchor and below the next greater weight.
                // Equal weighted anchors provide no strict gap: retain the shared numeric priority.
                int step = Objects.equals(lower, nextWeight[index]) ? 0 : index + 1;
                priorities.put(id, new Priority(lower, step));
            }
        }
        return priorities;
    }

    private static String groupId(RankInfo rank) { return rank.id().toLowerCase(Locale.ROOT); }

    private record OrderTarget(Player player, UUID id, String name, Optional<RankInfo> rank) { }

    private record Priority(long weight, int trackStep) implements Comparable<Priority> {
        private static final Priority UNKNOWN = new Priority(Long.MIN_VALUE, 0);
        @Override public int compareTo(Priority other) {
            int weighted = Long.compare(weight, other.weight);
            return weighted != 0 ? weighted : Integer.compare(trackStep, other.trackStep);
        }
    }

    public void updatePlayer(Player player, RenderedPresentation presentation) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        RenderedPresentation validatedPresentation = Objects.requireNonNull(presentation, "presentation");

        if (!presentationConfig.isTablistEnabled()) {
            removePlayer(validatedPlayer);
            return;
        }

        PlayerFields fields = modifiedPlayers.computeIfAbsent(validatedPlayer.getUniqueId(), ignored -> new PlayerFields());
        fields.name.write(validatedPlayer::playerListName, validatedPlayer::playerListName,
                validatedPresentation.tablistName());
        fields.header.write(validatedPlayer::playerListHeader, validatedPlayer::sendPlayerListHeader,
                validatedPresentation.tablistHeader());
        fields.footer.write(validatedPlayer::playerListFooter, validatedPlayer::sendPlayerListFooter,
                validatedPresentation.tablistFooter());
    }

    public void removePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        PlayerFields fields = modifiedPlayers.remove(validatedPlayer.getUniqueId());
        if (fields == null) {
            return;
        }

        fields.name.restore(validatedPlayer::playerListName, validatedPlayer::playerListName);
        fields.header.restore(validatedPlayer::playerListHeader, value -> restoreHeader(validatedPlayer, value));
        fields.footer.restore(validatedPlayer::playerListFooter, value -> restoreFooter(validatedPlayer, value));
        fields.order.restore(validatedPlayer);
    }

    public void clearTrackedPlayers() {
        modifiedPlayers.clear();
    }

    @SuppressWarnings("deprecation") // Adventure sends cannot express Paper's nullable header sentinel.
    private static void restoreHeader(Player player, Component value) {
        if (value == null) player.setPlayerListHeader((String) null);
        else player.sendPlayerListHeader(value);
    }

    @SuppressWarnings("deprecation") // No styled Component is ever converted to a legacy String.
    private static void restoreFooter(Player player, Component value) {
        if (value == null) player.setPlayerListFooter((String) null);
        else player.sendPlayerListFooter(value);
    }

    private static final class PlayerFields {
        private final Field name = new Field();
        private final Field header = new Field();
        private final Field footer = new Field();
        private final OrderField order = new OrderField();
    }

    private static final class OrderField {
        private int previous;
        private int lastWritten;
        private boolean written;

        private void write(Player player, int value) {
            int current = player.getPlayerListOrder();
            if (current == value) return;
            int before = written ? previous : current;
            player.setPlayerListOrder(value);
            previous = before;
            lastWritten = player.getPlayerListOrder();
            written = true;
        }

        private void restore(Player player) {
            // Paper 1.21.11 accepts zero (its default), despite the Javadoc's "positive" wording.
            if (written && previous >= 0 && player.getPlayerListOrder() == lastWritten
                    && lastWritten != previous) player.setPlayerListOrder(previous);
        }
    }

    /** One active cycle retains its original value across all successful refreshes. */
    private static final class Field {
        private Component previous;
        private Component lastWritten;
        private boolean written;

        private void write(Supplier<Component> read, Consumer<Component> write, Component value) {
            Component before = written ? previous : read.get();
            write.accept(value);
            previous = before;
            // Paper may normalize a name during its Adventure/vanilla roundtrip.
            lastWritten = read.get();
            written = true;
        }

        private void restore(Supplier<Component> read, Consumer<Component> write) {
            if (written && Objects.equals(read.get(), lastWritten)) {
                write.accept(previous);
            }
        }
    }
}

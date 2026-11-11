package dev.vapee.core.quest.daily;

import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestProgressKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;

public final class DailyQuestSelector {
    private static final QuestProgressKey RETIRED_RANK_CHECK = QuestProgressKey.of("lobby:rank_check");
    private static final QuestProgressKey RETIRED_RANKS_CHECK = QuestProgressKey.of("lobby:ranks_check");

    static boolean retired(QuestDefinition definition) {
        var key = definition.progressKey();
        return key.equals(RETIRED_RANK_CHECK) || key.equals(RETIRED_RANKS_CHECK);
    }

    public List<String> select(UUID playerId, DailyQuestCycleId cycleId,
                               Collection<QuestDefinition> definitions, int requestedCount) {
        return select(playerId, cycleId, definitions, requestedCount, DailyQuestSelection.unrestricted());
    }

    public List<String> select(UUID playerId, DailyQuestCycleId cycleId,
                               Collection<QuestDefinition> definitions, int requestedCount,
                               DailyQuestSelection selection) {
        return select(playerId, cycleId, definitions, requestedCount, selection, List.of());
    }

    List<String> select(UUID playerId, DailyQuestCycleId cycleId,
                        Collection<QuestDefinition> definitions, int requestedCount,
                        DailyQuestSelection selection, List<String> retained) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        DailyQuestCycleId validatedCycleId = Objects.requireNonNull(cycleId, "cycleId");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(selection, "selection");
        if (requestedCount <= 0) {
            throw new IllegalArgumentException("requestedCount must be positive");
        }

        HashSet<String> seen = new HashSet<>();
        List<RankedQuest> ranked = definitions.stream()
                .map(definition -> {
                    QuestDefinition validated = Objects.requireNonNull(definition, "definition");
                    if (!seen.add(validated.id())) {
                        throw new IllegalArgumentException("Duplicate quest definition ID: " + validated.id());
                    }
                    return validated;
                })
                .filter(definition -> !retired(definition))
                .map(definition -> new RankedQuest(definition.id(),
                        digest(validatedPlayerId, validatedCycleId, definition.id())))
                .sorted(Comparator.comparing(RankedQuest::digest, Arrays::compareUnsigned)
                        .thenComparing(RankedQuest::id))
                .toList();
        List<String> selected = new ArrayList<>(retained);
        var categories = new HashMap<String, Integer>();
        var groups = new HashMap<String, Integer>();
        for (String id : retained) {
            var metadata = selection.quest(id);
            if (metadata.category() != null) categories.merge(metadata.category(), 1, Integer::sum);
            if (metadata.group() != null) groups.merge(metadata.group(), 1, Integer::sum);
        }
        for (RankedQuest candidate : ranked) {
            if (selected.size() >= requestedCount) break;
            var metadata = selection.quest(candidate.id());
            if (!metadata.enabled()) continue;
            if (metadata.category() != null && selection.maxPerCategory() != null
                    && categories.getOrDefault(metadata.category(), 0) >= selection.maxPerCategory()) continue;
            if (metadata.group() != null && selection.maxPerGroup() != null
                    && groups.getOrDefault(metadata.group(), 0) >= selection.maxPerGroup()) continue;
            selected.add(candidate.id());
            if (metadata.category() != null) categories.merge(metadata.category(), 1, Integer::sum);
            if (metadata.group() != null) groups.merge(metadata.group(), 1, Integer::sum);
        }
        return List.copyOf(selected);
    }

    private byte[] digest(UUID playerId, DailyQuestCycleId cycleId, String questId) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            // Quest IDs cannot contain newlines; these explicit separators make the input unambiguous.
            String input = playerId + "\n" + cycleId + "\n" + questId;
            return sha256.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record RankedQuest(String id, byte[] digest) {
    }
}

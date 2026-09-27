package dev.vapee.core.quest.daily;

import dev.vapee.core.quest.QuestDefinition;

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

public final class DailyQuestSelector {

    public List<String> select(UUID playerId, DailyQuestCycleId cycleId,
                               Collection<QuestDefinition> definitions, int requestedCount) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        DailyQuestCycleId validatedCycleId = Objects.requireNonNull(cycleId, "cycleId");
        Objects.requireNonNull(definitions, "definitions");
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
                    return new RankedQuest(
                            validated.id(),
                            digest(validatedPlayerId, validatedCycleId, validated.id())
                    );
                })
                .sorted(Comparator.comparing(RankedQuest::digest, Arrays::compareUnsigned)
                        .thenComparing(RankedQuest::id))
                .toList();
        return ranked.stream().limit(requestedCount).map(RankedQuest::id).toList();
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

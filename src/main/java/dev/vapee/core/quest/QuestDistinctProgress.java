package dev.vapee.core.quest;

import java.util.*;

/** Assignment-local evidence, persisted atomically with numeric progress. No global history. */
public record QuestDistinctProgress(String key, long target, Set<String> identities, boolean blocked) {
    public static final int MAX_IDENTITIES = 1024;
    public static final int MAX_PLAYER_IDENTITIES = 4096;

    public QuestDistinctProgress {
        Objects.requireNonNull(identities);
        if (identities.size() > MAX_IDENTITIES) throw new IllegalArgumentException("Distinct identity limit exceeded");
        identities = Collections.unmodifiableSet(new TreeSet<>(identities));
        if (blocked) {
            key = "blocked"; target = 0; identities = Set.of();
        } else {
            QuestProgressKey.of(key);
            if (target < 1 || target > MAX_IDENTITIES || identities.size() > target)
                throw new IllegalArgumentException("Unsupported distinct target or identity count");
            identities.forEach(QuestDistinctProgress::requireIdentity);
        }
    }

    public static QuestDistinctProgress blockedState() {
        return new QuestDistinctProgress("blocked", 0, Set.of(), true);
    }

    public static String requireIdentity(String identity) {
        if (identity == null || !identity.matches("[a-z0-9][a-z0-9_:/-]{0,127}"))
            throw new IllegalArgumentException("Invalid distinct identity");
        return identity;
    }

    public boolean compatible(QuestDefinition definition) {
        return !blocked && key.equals(definition.progressKey().value()) && target == definition.target();
    }

    QuestDistinctProgress including(String identity) {
        var copy = new TreeSet<>(identities); copy.add(requireIdentity(identity));
        return new QuestDistinctProgress(key, target, copy, false);
    }
}

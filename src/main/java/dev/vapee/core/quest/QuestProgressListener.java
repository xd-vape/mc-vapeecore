package dev.vapee.core.quest;

import java.util.List;
import java.util.UUID;

/** Transient, post-processing observation; never a persistence or reward participant. */
@FunctionalInterface
interface QuestProgressListener {
    void onProgress(
            UUID playerId,
            Object assignments,
            Object catalog,
            List<String> increasedQuestIds,
            QuestProgressResult result);
}

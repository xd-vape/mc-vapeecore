package dev.vapee.core.quest.daily;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.quest.PlayerQuestProgress;
import dev.vapee.core.quest.QuestAssignmentResult;
import dev.vapee.core.quest.QuestDefinitionRegistry;
import dev.vapee.core.quest.QuestService;
import dev.vapee.core.quest.QuestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main-thread-only daily assignment layer over QuestService. */
public final class DailyQuestService {

    private final PlayerService playerService;
    private final QuestService questService;
    private final QuestDefinitionRegistry registry;
    private final Supplier<DailyQuestConfig.State> configState;
    private final DailyQuestCycleResolver cycleResolver;
    private final DailyQuestSelector selector;
    private final Logger logger;
    private final Set<UUID> warnedUnknownPending = new HashSet<>();

    public DailyQuestService(PlayerService playerService, QuestService questService,
                             QuestDefinitionRegistry registry,
                             Supplier<DailyQuestConfig.State> configState,
                             Logger logger) {
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.questService = Objects.requireNonNull(questService, "questService");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.configState = Objects.requireNonNull(configState, "configState");
        this.cycleResolver = new DailyQuestCycleResolver();
        this.selector = new DailyQuestSelector();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public DailyQuestSyncResult syncPlayer(UUID playerId, Instant now) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        Instant validatedNow = Objects.requireNonNull(now, "now");
        DailyQuestConfig.State config = Objects.requireNonNull(configState.get(), "config state");
        if (!config.enabled()) return DailyQuestSyncResult.DISABLED;

        Optional<CorePlayer> loaded = playerService.getPlayer(validatedPlayerId);
        if (loaded.isEmpty()) return DailyQuestSyncResult.PLAYER_NOT_LOADED;
        if (config.definitions().isEmpty()) return DailyQuestSyncResult.NO_DEFINITIONS;

        CorePlayer player = loaded.get();
        DailyQuestCycleId currentCycle = cycleResolver.resolve(
                validatedNow, config.zone(), config.resetTime()
        );
        Optional<DailyQuestCycleId> savedCycle = player.getDailyQuestState().cycleId();
        if (savedCycle.isPresent() && savedCycle.get().equals(currentCycle)) {
            warnedUnknownPending.remove(validatedPlayerId);
            return DailyQuestSyncResult.CURRENT;
        }

        if (hasPendingReward(player)) {
            questService.retryPendingRewards(validatedPlayerId);
            if (hasPendingReward(player)) {
                warnUnknownPendingOnce(validatedPlayerId, player);
                return DailyQuestSyncResult.BLOCKED_PENDING_REWARD;
            }
        }
        warnedUnknownPending.remove(validatedPlayerId);

        List<String> selected = selector.select(validatedPlayerId, currentCycle,
                config.definitions(), config.questsPerDay());
        QuestAssignmentResult assignment;
        try {
            assignment = questService.replaceAssignments(validatedPlayerId, selected);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Daily quest assignment failed for " + validatedPlayerId
                    + "; the cycle and prior assignments were left unchanged.", exception);
            return DailyQuestSyncResult.ASSIGNMENT_FAILED;
        }
        if (assignment != QuestAssignmentResult.REPLACED) {
            logger.warning("Daily quest assignment for " + validatedPlayerId + " returned " + assignment
                    + "; the cycle was not advanced.");
            return DailyQuestSyncResult.ASSIGNMENT_FAILED;
        }
        player.getDailyQuestState().setCycleId(currentCycle);
        return savedCycle.isPresent() ? DailyQuestSyncResult.ROTATED : DailyQuestSyncResult.INITIALIZED;
    }

    private boolean hasPendingReward(CorePlayer player) {
        return player.getQuestState().snapshot().values().stream()
                .anyMatch(progress -> progress.status() == QuestStatus.REWARD_PENDING);
    }

    private void warnUnknownPendingOnce(UUID playerId, CorePlayer player) {
        List<String> unknown = player.getQuestState().snapshot().values().stream()
                .filter(progress -> progress.status() == QuestStatus.REWARD_PENDING)
                .map(PlayerQuestProgress::questId)
                .filter(id -> registry.findById(id).isEmpty())
                .toList();
        if (!unknown.isEmpty() && warnedUnknownPending.add(playerId)) {
            logger.warning("Daily quest reset for " + playerId + " is blocked by unknown pending quest ID(s) "
                    + unknown + ". Restore their definitions or resolve the player state administratively.");
        }
    }
}

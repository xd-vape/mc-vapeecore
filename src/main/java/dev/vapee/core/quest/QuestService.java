package dev.vapee.core.quest;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.reward.RewardResult;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.reward.RewardSource;
import dev.vapee.core.reward.RewardStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main-thread-owned quest assignment, progress, completion, and persistence coordinator.
 */
public final class QuestService {

    private static final String REWARD_REASON_PREFIX = "quest:";

    private final QuestDefinitionRegistry definitionRegistry;
    private final PlayerService playerService;
    private final RewardGranter rewardGranter;
    private final Logger logger;
    private final Set<UUID> dirtyPlayers = new LinkedHashSet<>();

    public QuestService(
            QuestDefinitionRegistry definitionRegistry,
            PlayerService playerService,
            RewardService rewardService,
            Logger logger
    ) {
        this(
                definitionRegistry,
                playerService,
                Objects.requireNonNull(rewardService, "rewardService")::grantCoins,
                logger
        );
    }

    QuestService(
            QuestDefinitionRegistry definitionRegistry,
            PlayerService playerService,
            RewardGranter rewardGranter,
            Logger logger
    ) {
        this.definitionRegistry = Objects.requireNonNull(definitionRegistry, "definitionRegistry");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.rewardGranter = Objects.requireNonNull(rewardGranter, "rewardGranter");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public QuestAssignmentResult assignQuest(UUID playerId, String questId) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        String validatedQuestId = QuestDefinition.requireValidId(questId);
        Optional<CorePlayer> player = playerService.getPlayer(validatedPlayerId);
        if (player.isEmpty()) {
            return QuestAssignmentResult.PLAYER_NOT_LOADED;
        }
        if (definitionRegistry.findById(validatedQuestId).isEmpty()) {
            return QuestAssignmentResult.DEFINITION_NOT_FOUND;
        }

        if (!player.get().getQuestState().assign(validatedQuestId)) {
            return QuestAssignmentResult.ALREADY_ASSIGNED;
        }
        dirtyPlayers.add(validatedPlayerId);
        return QuestAssignmentResult.ASSIGNED;
    }

    public QuestAssignmentResult replaceAssignments(
            UUID playerId,
            Collection<String> questIds
    ) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questIds, "questIds");
        Optional<CorePlayer> player = playerService.getPlayer(validatedPlayerId);
        if (player.isEmpty()) {
            return QuestAssignmentResult.PLAYER_NOT_LOADED;
        }

        LinkedHashSet<String> validatedQuestIds = new LinkedHashSet<>();
        for (String questId : questIds) {
            String validatedQuestId = QuestDefinition.requireValidId(questId);
            if (!validatedQuestIds.add(validatedQuestId)) {
                return QuestAssignmentResult.DUPLICATE_QUEST_ID;
            }
            if (definitionRegistry.findById(validatedQuestId).isEmpty()) {
                return QuestAssignmentResult.DEFINITION_NOT_FOUND;
            }
        }

        PlayerQuestState state = player.get().getQuestState();
        if (state.isEmpty() && validatedQuestIds.isEmpty()) {
            return QuestAssignmentResult.NO_CHANGE;
        }
        state.replaceAssignments(validatedQuestIds);
        dirtyPlayers.add(validatedPlayerId);
        return QuestAssignmentResult.REPLACED;
    }

    public QuestAssignmentResult clearAssignments(UUID playerId) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        Optional<CorePlayer> player = playerService.getPlayer(validatedPlayerId);
        if (player.isEmpty()) {
            return QuestAssignmentResult.PLAYER_NOT_LOADED;
        }
        if (!player.get().getQuestState().clear()) {
            return QuestAssignmentResult.NO_CHANGE;
        }
        dirtyPlayers.add(validatedPlayerId);
        return QuestAssignmentResult.CLEARED;
    }

    public Optional<List<QuestView>> getActiveQuests(UUID playerId) {
        Optional<CorePlayer> player = playerService.getPlayer(Objects.requireNonNull(playerId, "playerId"));
        if (player.isEmpty()) {
            return Optional.empty();
        }

        List<QuestView> views = new ArrayList<>();
        for (PlayerQuestProgress progress : player.get().getQuestState().progressEntries()) {
            definitionRegistry.findById(progress.questId()).ifPresent(definition -> views.add(
                    new QuestView(
                            definition,
                            Math.min(progress.progress(), definition.target()),
                            definition.target(),
                            progress.status()
                    )
            ));
        }
        return Optional.of(List.copyOf(views));
    }

    public QuestProgressResult addProgress(
            UUID playerId,
            QuestProgressKey progressKey,
            long amount
    ) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        QuestProgressKey validatedProgressKey = Objects.requireNonNull(progressKey, "progressKey");
        if (amount <= 0L) {
            throw new IllegalArgumentException("Quest progress amount must be positive");
        }

        Optional<CorePlayer> player = playerService.getPlayer(validatedPlayerId);
        if (player.isEmpty()) {
            return QuestProgressResult.playerNotLoaded();
        }

        PlayerQuestState state = player.get().getQuestState();
        List<String> completedQuestIds = new ArrayList<>();
        List<String> rewardPendingQuestIds = new ArrayList<>();
        int updatedQuests = 0;
        boolean matched = false;
        boolean rewardAttempted = false;

        for (PlayerQuestProgress progress : state.progressEntries()) {
            Optional<QuestDefinition> definitionResult = definitionRegistry.findById(progress.questId());
            if (definitionResult.isEmpty()) {
                continue;
            }
            QuestDefinition definition = definitionResult.get();
            if (!definition.progressKey().equals(validatedProgressKey)) {
                continue;
            }
            matched = true;

            if (progress.status() == QuestStatus.COMPLETED) {
                continue;
            }
            if (progress.status() == QuestStatus.REWARD_PENDING) {
                PlayerQuestProgress pendingProgress = progress;
                boolean normalized = progress.progress() != definition.target();
                if (normalized) {
                    pendingProgress = progress.with(definition.target(), QuestStatus.REWARD_PENDING);
                    state.update(pendingProgress);
                    updatedQuests++;
                }
                rewardAttempted = true;
                if (grantReward(validatedPlayerId, definition)) {
                    state.update(pendingProgress.with(definition.target(), QuestStatus.COMPLETED));
                    if (!normalized) {
                        updatedQuests++;
                    }
                    completedQuestIds.add(definition.id());
                } else {
                    rewardPendingQuestIds.add(definition.id());
                }
                continue;
            }

            long currentProgress = progress.progress();
            long newProgress;
            if (currentProgress >= definition.target()) {
                newProgress = definition.target();
            } else {
                long remaining = definition.target() - currentProgress;
                long applied = Math.min(amount, remaining);
                newProgress = currentProgress + applied;
            }

            if (newProgress < definition.target()) {
                state.update(progress.with(newProgress, QuestStatus.ACTIVE));
                updatedQuests++;
                continue;
            }

            PlayerQuestProgress pendingProgress = progress.with(
                    definition.target(),
                    QuestStatus.REWARD_PENDING
            );
            state.update(pendingProgress);
            updatedQuests++;
            rewardAttempted = true;
            if (grantReward(validatedPlayerId, definition)) {
                state.update(pendingProgress.with(definition.target(), QuestStatus.COMPLETED));
                completedQuestIds.add(definition.id());
            } else {
                rewardPendingQuestIds.add(definition.id());
            }
        }

        if (updatedQuests > 0) {
            dirtyPlayers.add(validatedPlayerId);
        }
        if (!matched) {
            return QuestProgressResult.noMatchingQuest();
        }
        if (updatedQuests == 0 && !rewardAttempted) {
            return QuestProgressResult.noChange();
        }
        return QuestProgressResult.processed(
                updatedQuests,
                completedQuestIds,
                rewardPendingQuestIds
        );
    }

    public QuestProgressResult retryPendingRewards(UUID playerId) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        Optional<CorePlayer> player = playerService.getPlayer(validatedPlayerId);
        if (player.isEmpty()) {
            return QuestProgressResult.playerNotLoaded();
        }

        PlayerQuestState state = player.get().getQuestState();
        List<String> completedQuestIds = new ArrayList<>();
        List<String> rewardPendingQuestIds = new ArrayList<>();
        int updatedQuests = 0;
        boolean attempted = false;

        for (PlayerQuestProgress progress : state.progressEntries()) {
            if (progress.status() != QuestStatus.REWARD_PENDING) {
                continue;
            }
            Optional<QuestDefinition> definitionResult = definitionRegistry.findById(progress.questId());
            if (definitionResult.isEmpty()) {
                continue;
            }

            attempted = true;
            QuestDefinition definition = definitionResult.get();
            PlayerQuestProgress pendingProgress = progress;
            boolean normalized = progress.progress() != definition.target();
            if (normalized) {
                pendingProgress = progress.with(definition.target(), QuestStatus.REWARD_PENDING);
                state.update(pendingProgress);
                updatedQuests++;
            }
            if (grantReward(validatedPlayerId, definition)) {
                state.update(pendingProgress.with(definition.target(), QuestStatus.COMPLETED));
                if (!normalized) {
                    updatedQuests++;
                }
                completedQuestIds.add(definition.id());
            } else {
                rewardPendingQuestIds.add(definition.id());
            }
        }

        if (updatedQuests > 0) {
            dirtyPlayers.add(validatedPlayerId);
        }
        if (!attempted) {
            return QuestProgressResult.noChange();
        }
        return QuestProgressResult.processed(
                updatedQuests,
                completedQuestIds,
                rewardPendingQuestIds
        );
    }

    public void flushPlayer(UUID playerId) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        if (!dirtyPlayers.contains(validatedPlayerId)) {
            return;
        }
        if (!playerService.isLoaded(validatedPlayerId)) {
            dirtyPlayers.remove(validatedPlayerId);
            return;
        }

        try {
            playerService.savePlayer(validatedPlayerId);
            dirtyPlayers.remove(validatedPlayerId);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Quest state for player " + validatedPlayerId
                            + " could not be flushed; the player remains dirty for a later retry.",
                    exception
            );
        }
    }

    public void flushAll() {
        for (UUID playerId : List.copyOf(dirtyPlayers)) {
            flushPlayer(playerId);
        }
    }

    boolean isDirty(UUID playerId) {
        return dirtyPlayers.contains(Objects.requireNonNull(playerId, "playerId"));
    }

    int dirtyPlayerCount() {
        return dirtyPlayers.size();
    }

    void clearDirtyTracking() {
        dirtyPlayers.clear();
    }

    private boolean grantReward(UUID playerId, QuestDefinition definition) {
        try {
            RewardResult result = rewardGranter.grantCoins(
                    playerId,
                    definition.rewardCoins(),
                    RewardSource.QUEST,
                    REWARD_REASON_PREFIX + definition.id()
            );
            return result.status() == RewardStatus.SUCCESS;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Quest reward for player " + playerId + " and quest " + definition.id()
                            + " failed unexpectedly; reward remains pending.",
                    exception
            );
            return false;
        }
    }

    @FunctionalInterface
    interface RewardGranter {

        RewardResult grantCoins(UUID playerId, long coins, RewardSource source, String reason);
    }
}

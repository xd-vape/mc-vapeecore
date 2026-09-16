package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivitySession;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.location.ActivityVenue;
import dev.vapee.core.activity.player.ActivityParticipant;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BlackjackSession extends ActivitySession {

    private final BlackjackService blackjackService;
    private final Map<UUID, BlackjackPlayerRound> playerRounds = new LinkedHashMap<>();

    private BlackjackMode mode = BlackjackMode.UNCLAIMED;
    private UUID soloOwner;
    private BlackjackRoundPhase roundPhase = BlackjackRoundPhase.IDLE;
    private BlackjackShoe currentShoe;
    private BlackjackHand dealerHand = new BlackjackHand();
    private UUID currentTurnPlayer;
    private long roundGeneration;
    private long turnGeneration;
    private BukkitTask turnTimeoutTask;
    private BukkitTask resultResetTask;

    BlackjackSession(
            UUID sessionId,
            ActivityVenue venue,
            BlackjackService blackjackService
    ) {
        super(sessionId, BlackjackActivityType.KEY, venue);
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
    }

    public BlackjackMode getMode() {
        return mode;
    }

    public Optional<UUID> getSoloOwner() {
        return Optional.ofNullable(soloOwner);
    }

    public BlackjackRoundPhase getRoundPhase() {
        return roundPhase;
    }

    public Optional<BlackjackShoe> getCurrentShoe() {
        return Optional.ofNullable(currentShoe);
    }

    public BlackjackHand getDealerHand() {
        return dealerHand;
    }

    public Map<UUID, BlackjackPlayerRound> getPlayerRounds() {
        return Map.copyOf(playerRounds);
    }

    public Optional<BlackjackPlayerRound> getPlayerRound(UUID playerId) {
        return Optional.ofNullable(playerRounds.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public Optional<UUID> getCurrentTurnPlayer() {
        return Optional.ofNullable(currentTurnPlayer);
    }

    public long getRoundGeneration() {
        return roundGeneration;
    }

    long getTurnGeneration() {
        return turnGeneration;
    }

    void claim(BlackjackMode mode, UUID soloOwner) {
        BlackjackMode validatedMode = Objects.requireNonNull(mode, "mode");
        if (validatedMode == BlackjackMode.UNCLAIMED) {
            throw new IllegalArgumentException("Use release() to unclaim a table");
        }
        if (getParticipantCount() != 0 || this.mode != BlackjackMode.UNCLAIMED) {
            throw new IllegalStateException("Only an empty unclaimed table can be claimed");
        }
        if ((validatedMode == BlackjackMode.SOLO) != (soloOwner != null)) {
            throw new IllegalArgumentException("Solo tables require exactly one owner");
        }
        this.mode = validatedMode;
        this.soloOwner = soloOwner;
    }

    void release() {
        if (getParticipantCount() != 0) {
            throw new IllegalStateException("A table with participants cannot be released");
        }
        mode = BlackjackMode.UNCLAIMED;
        soloOwner = null;
    }

    void beginRound(BlackjackShoe shoe) {
        currentShoe = Objects.requireNonNull(shoe, "shoe");
        dealerHand = new BlackjackHand();
        playerRounds.clear();
        for (ActivityParticipant participant : getParticipants()) {
            playerRounds.put(participant.uniqueId(), new BlackjackPlayerRound(participant.uniqueId()));
        }
        currentTurnPlayer = null;
        roundPhase = BlackjackRoundPhase.PLAYER_TURNS;
        roundGeneration++;
        turnGeneration = 0;
        turnTimeoutTask = null;
        resultResetTask = null;
    }

    BlackjackPlayerRound requirePlayerRound(UUID playerId) {
        return Objects.requireNonNull(playerRounds.get(playerId), "Player is not part of this blackjack round");
    }

    List<BlackjackPlayerRound> getPlayerRoundsInOrder() {
        return List.copyOf(playerRounds.values());
    }

    BlackjackShoe requireShoe() {
        return Objects.requireNonNull(currentShoe, "Blackjack round has no shoe");
    }

    void removePlayerRound(UUID playerId) {
        playerRounds.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    void setRoundPhase(BlackjackRoundPhase roundPhase) {
        this.roundPhase = Objects.requireNonNull(roundPhase, "roundPhase");
    }

    long beginTurn(UUID playerId) {
        currentTurnPlayer = Objects.requireNonNull(playerId, "playerId");
        return ++turnGeneration;
    }

    void clearCurrentTurn() {
        currentTurnPlayer = null;
        turnGeneration++;
    }

    void setTurnTimeoutTask(BukkitTask task) {
        cancelTurnTimeout();
        turnTimeoutTask = trackTask(Objects.requireNonNull(task, "task"));
    }

    boolean consumeTurnTimeoutTask(BukkitTask task) {
        if (turnTimeoutTask != task) {
            return false;
        }
        turnTimeoutTask = null;
        return untrackTask(task);
    }

    void cancelTurnTimeout() {
        BukkitTask task = turnTimeoutTask;
        turnTimeoutTask = null;
        cancelAndUntrack(task);
    }

    void setResultResetTask(BukkitTask task) {
        cancelResultReset();
        resultResetTask = trackTask(Objects.requireNonNull(task, "task"));
    }

    boolean consumeResultResetTask(BukkitTask task) {
        if (resultResetTask != task) {
            return false;
        }
        resultResetTask = null;
        return untrackTask(task);
    }

    private void cancelResultReset() {
        BukkitTask task = resultResetTask;
        resultResetTask = null;
        cancelAndUntrack(task);
    }

    private void cancelAndUntrack(BukkitTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } finally {
            untrackTask(task);
        }
    }

    private void clearRoundData() {
        currentShoe = null;
        dealerHand = new BlackjackHand();
        playerRounds.clear();
        currentTurnPlayer = null;
        turnTimeoutTask = null;
        resultResetTask = null;
        roundPhase = BlackjackRoundPhase.IDLE;
        turnGeneration++;
    }

    @Override
    protected void onParticipantJoined(ActivityParticipant participant) {
        blackjackService.onParticipantJoined(this, participant);
    }

    @Override
    protected void onParticipantLeft(ActivityParticipant participant, ActivityLeaveReason reason) {
        blackjackService.onParticipantLeft(this, participant, reason);
    }

    @Override
    protected void onActivated() {
        blackjackService.onActivated(this);
    }

    @Override
    protected void onReset() {
        clearRoundData();
        blackjackService.onReset(this);
    }

    @Override
    protected void onClosed() {
        clearRoundData();
        blackjackService.onClosed(this);
    }
}

package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.card.BlackjackHand;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BlackjackPlayerRound {

    private final UUID playerId;
    private final BlackjackHand hand = new BlackjackHand();
    private boolean finished;
    private boolean doubledDown;
    private BlackjackOutcome outcome;

    BlackjackPlayerRound(UUID playerId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public BlackjackHand getHand() {
        return hand;
    }

    public boolean isFinished() {
        return finished;
    }

    public Optional<BlackjackOutcome> getOutcome() {
        return Optional.ofNullable(outcome);
    }

    public boolean isDoubledDown() {
        return doubledDown;
    }

    void markDoubledDown() {
        doubledDown = true;
    }

    void finish() {
        finished = true;
    }

    void settle(BlackjackOutcome outcome) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        finished = true;
    }
}

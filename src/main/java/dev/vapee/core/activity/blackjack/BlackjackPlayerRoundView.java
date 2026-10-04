package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.card.BlackjackHandView;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable player-round snapshot for read-only consumers. */
public record BlackjackPlayerRoundView(
        UUID playerId,
        BlackjackHandView hand,
        boolean finished,
        boolean doubledDown,
        Optional<BlackjackOutcome> outcome
) {

    public BlackjackPlayerRoundView {
        playerId = Objects.requireNonNull(playerId, "playerId");
        hand = Objects.requireNonNull(hand, "hand");
        outcome = Objects.requireNonNull(outcome, "outcome");
    }
}

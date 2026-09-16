package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.card.BlackjackHand;

import java.util.Objects;

public enum BlackjackOutcome {
    BLACKJACK,
    WIN,
    PUSH,
    LOSS,
    BUST;

    public static BlackjackOutcome determine(BlackjackHand playerHand, BlackjackHand dealerHand) {
        BlackjackHand player = Objects.requireNonNull(playerHand, "playerHand");
        BlackjackHand dealer = Objects.requireNonNull(dealerHand, "dealerHand");
        if (player.isBust()) {
            return BUST;
        }
        if (player.isBlackjack()) {
            return dealer.isBlackjack() ? PUSH : BLACKJACK;
        }
        if (dealer.isBlackjack()) {
            return LOSS;
        }
        if (dealer.isBust()) {
            return WIN;
        }
        return Integer.compare(player.getValue(), dealer.getValue()) > 0
                ? WIN
                : player.getValue() == dealer.getValue() ? PUSH : LOSS;
    }
}

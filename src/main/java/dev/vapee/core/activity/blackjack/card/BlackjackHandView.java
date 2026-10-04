package dev.vapee.core.activity.blackjack.card;

import java.util.List;
import java.util.Objects;

/** Immutable hand snapshot; later engine mutations do not change this view. */
public record BlackjackHandView(List<BlackjackCard> cards, int value, boolean blackjack) {

    public BlackjackHandView {
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
    }

    public static BlackjackHandView from(BlackjackHand hand) {
        BlackjackHand validated = Objects.requireNonNull(hand, "hand");
        return new BlackjackHandView(validated.getCards(), validated.getValue(), validated.isBlackjack());
    }
}

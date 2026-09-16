package dev.vapee.core.activity.blackjack.card;

import java.util.Objects;

public record BlackjackCard(BlackjackRank rank, BlackjackSuit suit) {

    public BlackjackCard {
        rank = Objects.requireNonNull(rank, "rank");
        suit = Objects.requireNonNull(suit, "suit");
    }

    public String getDisplayText() {
        return rank.getLabel() + " " + suit.getSymbol();
    }
}

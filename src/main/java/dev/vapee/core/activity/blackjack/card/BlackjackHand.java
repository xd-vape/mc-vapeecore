package dev.vapee.core.activity.blackjack.card;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class BlackjackHand {

    private final List<BlackjackCard> cards = new ArrayList<>();

    public BlackjackHand() {
    }

    public BlackjackHand(Collection<BlackjackCard> cards) {
        Objects.requireNonNull(cards, "cards").forEach(this::add);
    }

    public void add(BlackjackCard card) {
        cards.add(Objects.requireNonNull(card, "card"));
    }

    public List<BlackjackCard> getCards() {
        return List.copyOf(cards);
    }

    public int getValue() {
        int value = 0;
        int aces = 0;
        for (BlackjackCard card : cards) {
            value += card.rank().getValue();
            if (card.rank() == BlackjackRank.ACE) {
                aces++;
            }
        }
        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }
        return value;
    }

    public boolean isSoft() {
        int hardValue = 0;
        int aces = 0;
        for (BlackjackCard card : cards) {
            if (card.rank() == BlackjackRank.ACE) {
                hardValue += 1;
                aces++;
            } else {
                hardValue += card.rank().getValue();
            }
        }
        return aces > 0 && hardValue + 10 <= 21;
    }

    public boolean isBlackjack() {
        return cards.size() == 2 && getValue() == 21;
    }

    public boolean isBust() {
        return getValue() > 21;
    }

    public int size() {
        return cards.size();
    }
}

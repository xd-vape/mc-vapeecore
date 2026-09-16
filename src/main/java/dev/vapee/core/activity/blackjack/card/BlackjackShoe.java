package dev.vapee.core.activity.blackjack.card;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class BlackjackShoe {

    public static final int STANDARD_DECK_SIZE = 52;
    public static final int DEFAULT_DECK_COUNT = 6;

    private final Deque<BlackjackCard> cards;

    public BlackjackShoe(int deckCount, RandomGenerator random) {
        if (deckCount < 1) {
            throw new IllegalArgumentException("deckCount must be positive");
        }
        RandomGenerator validatedRandom = Objects.requireNonNull(random, "random");
        List<BlackjackCard> shuffledCards = new ArrayList<>(deckCount * STANDARD_DECK_SIZE);
        for (int deck = 0; deck < deckCount; deck++) {
            for (BlackjackSuit suit : BlackjackSuit.values()) {
                for (BlackjackRank rank : BlackjackRank.values()) {
                    shuffledCards.add(new BlackjackCard(rank, suit));
                }
            }
        }
        for (int index = shuffledCards.size() - 1; index > 0; index--) {
            int swapIndex = validatedRandom.nextInt(index + 1);
            BlackjackCard temporary = shuffledCards.get(index);
            shuffledCards.set(index, shuffledCards.get(swapIndex));
            shuffledCards.set(swapIndex, temporary);
        }
        cards = new ArrayDeque<>(shuffledCards);
    }

    public BlackjackShoe(List<BlackjackCard> drawOrder) {
        List<BlackjackCard> validatedCards = List.copyOf(Objects.requireNonNull(drawOrder, "drawOrder"));
        if (validatedCards.isEmpty()) {
            throw new IllegalArgumentException("drawOrder must not be empty");
        }
        cards = new ArrayDeque<>(validatedCards);
    }

    public static BlackjackShoe sixDecks(RandomGenerator random) {
        return new BlackjackShoe(DEFAULT_DECK_COUNT, random);
    }

    public BlackjackCard draw() {
        BlackjackCard card = cards.pollFirst();
        if (card == null) {
            throw new NoSuchElementException("The blackjack shoe is empty");
        }
        return card;
    }

    public int remaining() {
        return cards.size();
    }

    public List<BlackjackCard> getRemainingCards() {
        return List.copyOf(cards);
    }
}

package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackRank;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.blackjack.card.BlackjackSuit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

public final class BlackjackFairnessHarness {

    private static int checks;

    private BlackjackFairnessHarness() {
    }

    public static void main(String[] args) {
        verifySixDeckDistribution();
        verifyDeterministicUnbiasedShuffleContract();
        verifyDrawsWithoutReplacement();
        verifyDealerAndOutcomeRules();
        System.out.println("BlackjackFairnessHarness passed " + checks + " checks.");
    }

    private static void verifySixDeckDistribution() {
        BlackjackShoe shoe = BlackjackShoe.sixDecks(new Random(151L));
        List<BlackjackCard> cards = shoe.getRemainingCards();
        check(cards.size() == 312, "six decks contain exactly 312 cards");

        Map<BlackjackRank, Integer> ranks = new EnumMap<>(BlackjackRank.class);
        Map<BlackjackSuit, Integer> suits = new EnumMap<>(BlackjackSuit.class);
        Map<BlackjackSuit, Map<BlackjackRank, Integer>> combinations = new EnumMap<>(BlackjackSuit.class);
        for (BlackjackCard card : cards) {
            ranks.merge(card.rank(), 1, Integer::sum);
            suits.merge(card.suit(), 1, Integer::sum);
            combinations.computeIfAbsent(card.suit(), ignored -> new EnumMap<>(BlackjackRank.class))
                    .merge(card.rank(), 1, Integer::sum);
        }
        for (BlackjackRank rank : BlackjackRank.values()) {
            check(ranks.getOrDefault(rank, 0) == 24, rank + " occurs 24 times");
        }
        for (BlackjackSuit suit : BlackjackSuit.values()) {
            check(suits.getOrDefault(suit, 0) == 78, suit + " occurs 78 times");
            for (BlackjackRank rank : BlackjackRank.values()) {
                check(combinations.get(suit).getOrDefault(rank, 0) == 6,
                        suit + " " + rank + " occurs once per deck");
            }
        }
    }

    private static void verifyDeterministicUnbiasedShuffleContract() {
        List<BlackjackCard> seedOneA = BlackjackShoe.sixDecks(new Random(991_337L)).getRemainingCards();
        List<BlackjackCard> seedOneB = BlackjackShoe.sixDecks(new Random(991_337L)).getRemainingCards();
        List<BlackjackCard> seedTwo = BlackjackShoe.sixDecks(new Random(991_338L)).getRemainingCards();
        check(seedOneA.equals(seedOneB), "the same fixed seed reproduces the shuffle");
        check(!seedOneA.equals(seedTwo), "different fixed seeds produce different orders");
    }

    private static void verifyDrawsWithoutReplacement() {
        BlackjackShoe shoe = BlackjackShoe.sixDecks(new Random(42L));
        List<BlackjackCard> expectedOrder = shoe.getRemainingCards();
        List<BlackjackCard> drawn = new ArrayList<>(expectedOrder.size());
        for (int remaining = expectedOrder.size(); remaining > 0; remaining--) {
            check(shoe.remaining() == remaining, "remaining count precedes draw " + remaining);
            drawn.add(shoe.draw());
        }
        check(drawn.equals(expectedOrder), "draw removes each shuffled card in order without replacement");
        check(shoe.remaining() == 0, "shoe is empty after exactly 312 draws");
        expectThrows(shoe::draw, "drawing from an empty shoe is rejected");
    }

    private static void verifyDealerAndOutcomeRules() {
        check(BlackjackService.shouldDealerHit(hand(BlackjackRank.TEN, BlackjackRank.SIX)),
                "dealer hits hard 16");
        check(!BlackjackService.shouldDealerHit(hand(BlackjackRank.TEN, BlackjackRank.SEVEN)),
                "dealer stands hard 17");
        check(!BlackjackService.shouldDealerHit(hand(BlackjackRank.ACE, BlackjackRank.SIX)),
                "dealer stands soft 17");
        check(BlackjackOutcome.determine(
                hand(BlackjackRank.KING, BlackjackRank.QUEEN, BlackjackRank.TWO),
                hand(BlackjackRank.TEN, BlackjackRank.SIX)
        ) == BlackjackOutcome.BUST, "player bust is final regardless of dealer total");
        check(BlackjackOutcome.determine(
                hand(BlackjackRank.ACE, BlackjackRank.KING),
                hand(BlackjackRank.TEN, BlackjackRank.SIX)
        ) == BlackjackOutcome.BLACKJACK, "natural blackjack remains a distinct outcome");
        check(BlackjackOutcome.determine(
                hand(BlackjackRank.TEN, BlackjackRank.EIGHT),
                hand(BlackjackRank.TEN, BlackjackRank.EIGHT)
        ) == BlackjackOutcome.PUSH, "equal live totals push");
    }

    private static BlackjackHand hand(BlackjackRank... ranks) {
        BlackjackHand hand = new BlackjackHand();
        for (BlackjackRank rank : ranks) {
            hand.add(new BlackjackCard(rank, BlackjackSuit.SPADES));
        }
        return hand;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (NoSuchElementException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}

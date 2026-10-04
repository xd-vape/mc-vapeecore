package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackHandView;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackRank;
import dev.vapee.core.activity.blackjack.card.BlackjackSuit;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/** Regression for API-001: ordinary public reads must not return mutable engine state. */
public final class BlackjackReadViewHarness {

    private static int checks;

    private BlackjackReadViewHarness() { }

    public static void main(String[] args) {
        assertPublicReadBoundary();
        assertHandSnapshots();
        assertRoundSnapshots();
        System.out.println("BlackjackReadViewHarness passed " + checks + " checks.");
    }

    private static void assertHandSnapshots() {
        BlackjackCard ace = new BlackjackCard(BlackjackRank.ACE, BlackjackSuit.SPADES);
        BlackjackCard king = new BlackjackCard(BlackjackRank.KING, BlackjackSuit.HEARTS);
        BlackjackCard two = new BlackjackCard(BlackjackRank.TWO, BlackjackSuit.CLUBS);
        BlackjackHand hand = new BlackjackHand(List.of(ace, king));
        BlackjackHandView before = BlackjackHandView.from(hand);
        expectThrows(UnsupportedOperationException.class, () -> before.cards().add(two), "cards reject add");
        expectThrows(UnsupportedOperationException.class, () -> before.cards().set(0, two), "cards reject replacement");
        expectThrows(UnsupportedOperationException.class, () -> before.cards().clear(), "cards reject clear");
        hand.add(two);
        check(before.cards().equals(List.of(ace, king)) && before.value() == 21 && before.blackjack(),
                "old hand snapshot retains card order, ace value and natural after domain mutation");
        BlackjackHandView after = BlackjackHandView.from(hand);
        check(after.cards().equals(List.of(ace, king, two)) && after.value() == 13 && !after.blackjack(),
                "new hand snapshot uses current domain scoring");
        List<BlackjackCard> input = new ArrayList<>(List.of(ace, king));
        BlackjackHandView constructed = new BlackjackHandView(input, 21, true);
        input.clear();
        check(constructed.cards().equals(List.of(ace, king)), "canonical constructor defensively copies input");
        expectThrows(NullPointerException.class, () -> new BlackjackHandView(null, 0, false), "null cards rejected");
        expectThrows(NullPointerException.class, () -> new BlackjackHandView(java.util.Arrays.asList(ace, null), 0, false),
                "null card rejected");
        expectThrows(NullPointerException.class, () -> BlackjackHandView.from(null), "null domain hand rejected");
        check(BlackjackHandView.class.isRecord() && BlackjackCard.class.isRecord(), "hand and card are immutable value records");
    }

    private static void assertRoundSnapshots() {
        UUID id = UUID.randomUUID();
        BlackjackPlayerRound round = new BlackjackPlayerRound(id);
        round.getHand().add(new BlackjackCard(BlackjackRank.FIVE, BlackjackSuit.SPADES));
        round.getHand().add(new BlackjackCard(BlackjackRank.SIX, BlackjackSuit.HEARTS));
        BlackjackPlayerRoundView initial = round.toView();
        round.finish();
        BlackjackPlayerRoundView finished = round.toView();
        check(!initial.finished() && finished.finished(), "finish affects only new snapshot");
        round.markDoubledDown();
        round.getHand().add(new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.CLUBS));
        BlackjackPlayerRoundView doubled = round.toView();
        check(!initial.doubledDown() && !finished.doubledDown() && doubled.doubledDown(),
                "double flag is a detached value");
        check(initial.hand().cards().size() == 2 && initial.hand().value() == 11
                        && doubled.hand().cards().size() == 3 && doubled.hand().value() == 21,
                "round includes a detached hand snapshot");
        round.settle(BlackjackOutcome.WIN);
        BlackjackPlayerRoundView settled = round.toView();
        check(initial.outcome().isEmpty() && finished.outcome().isEmpty() && doubled.outcome().isEmpty()
                        && settled.outcome().equals(Optional.of(BlackjackOutcome.WIN)) && settled.finished(),
                "settlement affects only newly requested outcome");
        check(settled.playerId().equals(id) && BlackjackPlayerRoundView.class.isRecord(), "round is an immutable identity-bearing record");
        expectThrows(NullPointerException.class, () -> new BlackjackPlayerRoundView(null, initial.hand(), false, false, Optional.empty()),
                "null player rejected");
        expectThrows(NullPointerException.class, () -> new BlackjackPlayerRoundView(id, null, false, false, Optional.empty()),
                "null hand rejected");
        expectThrows(NullPointerException.class, () -> new BlackjackPlayerRoundView(id, initial.hand(), false, false, null),
                "null optional rejected");
    }

    private static void expectThrows(Class<? extends RuntimeException> type, Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException exception) {
            if (type.isInstance(exception)) return;
            throw exception;
        }
        throw new AssertionError(message);
    }

    private static void assertPublicReadBoundary() {
        List<String> escapes = new ArrayList<>();
        for (Class<?> owner : List.of(BlackjackSession.class, BlackjackPlayerRound.class,
                BlackjackHandView.class, BlackjackPlayerRoundView.class)) {
            for (var method : owner.getMethods()) {
                if (containsMutableState(method.getGenericReturnType(), new HashSet<>())) {
                    escapes.add(owner.getSimpleName() + "#" + method.getName());
                }
            }
        }
        check(escapes.isEmpty(), "Public read API exposes mutable engine state: " + escapes);
        for (Class<?> view : List.of(BlackjackHandView.class, BlackjackPlayerRoundView.class)) {
            check(java.util.Arrays.stream(view.getDeclaredFields())
                            .allMatch(field -> java.lang.reflect.Modifier.isPrivate(field.getModifiers())
                                    && java.lang.reflect.Modifier.isFinal(field.getModifiers())),
                    view.getSimpleName() + " retains only private final snapshot fields");
        }
    }

    private static boolean containsMutableState(Type type, Set<Type> visited) {
        if (!visited.add(type)) return false;
        if (type instanceof Class<?> value) {
            if (value == BlackjackHand.class || value == BlackjackShoe.class
                    || value == BlackjackPlayerRound.class) return true;
            if (value.isArray()) return containsMutableState(value.componentType(), visited);
            if (value.isRecord()) {
                for (var component : value.getRecordComponents()) {
                    if (containsMutableState(component.getGenericType(), visited)) return true;
                }
            }
        } else if (type instanceof ParameterizedType value) {
            if (containsMutableState(value.getRawType(), visited)) return true;
            for (Type argument : value.getActualTypeArguments()) {
                if (containsMutableState(argument, visited)) return true;
            }
        } else if (type instanceof GenericArrayType value) {
            return containsMutableState(value.getGenericComponentType(), visited);
        } else if (type instanceof WildcardType value) {
            for (Type bound : value.getUpperBounds()) {
                if (containsMutableState(bound, visited)) return true;
            }
            for (Type bound : value.getLowerBounds()) {
                if (containsMutableState(bound, visited)) return true;
            }
        } else if (type instanceof TypeVariable<?> value) {
            for (Type bound : value.getBounds()) {
                if (containsMutableState(bound, visited)) return true;
            }
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

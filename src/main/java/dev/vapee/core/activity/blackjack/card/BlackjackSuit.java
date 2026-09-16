package dev.vapee.core.activity.blackjack.card;

public enum BlackjackSuit {
    CLUBS("♣"),
    DIAMONDS("♦"),
    HEARTS("♥"),
    SPADES("♠");

    private final String symbol;

    BlackjackSuit(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isRed() {
        return this == DIAMONDS || this == HEARTS;
    }
}

package dev.vapee.core.activity.blackjack.table;

import java.util.Objects;

public record BlackjackTableSeatReference(String tableId, int seatNumber) {

    public BlackjackTableSeatReference {
        tableId = Objects.requireNonNull(tableId, "tableId");
        if (seatNumber < 1 || seatNumber > 5) {
            throw new IllegalArgumentException("seatNumber must be between 1 and 5");
        }
    }
}

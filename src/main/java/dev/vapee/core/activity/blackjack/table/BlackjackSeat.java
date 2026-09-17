package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.location.ActivityPosition;

import java.util.Objects;

public record BlackjackSeat(int number, ActivityPosition position) implements Comparable<BlackjackSeat> {

    public BlackjackSeat {
        if (number < 1 || number > 5) {
            throw new IllegalArgumentException("seat number must be between 1 and 5");
        }
        position = Objects.requireNonNull(position, "position");
    }

    @Override
    public int compareTo(BlackjackSeat other) {
        return Integer.compare(number, Objects.requireNonNull(other, "other").number);
    }
}

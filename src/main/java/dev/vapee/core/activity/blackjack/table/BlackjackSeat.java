package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.location.ActivityPosition;

import java.util.Objects;
import java.util.Optional;

public record BlackjackSeat(
        int number,
        ActivityPosition position,
        BlackjackBlockPosition block
) implements Comparable<BlackjackSeat> {

    public BlackjackSeat(int number, ActivityPosition position) {
        this(number, position, null);
    }

    public BlackjackSeat {
        position = Objects.requireNonNull(position, "position");
    }

    public boolean isModern() {
        return block != null;
    }

    public Optional<BlackjackBlockPosition> blockPosition() {
        return Optional.ofNullable(block);
    }

    @Override
    public int compareTo(BlackjackSeat other) {
        return Integer.compare(number, Objects.requireNonNull(other, "other").number);
    }
}

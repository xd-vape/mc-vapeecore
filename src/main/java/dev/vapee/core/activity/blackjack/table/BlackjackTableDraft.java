package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.location.ActivityPosition;

import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class BlackjackTableDraft {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]+");

    private final String id;
    private boolean enabled;
    private ActivityPosition pos1;
    private ActivityPosition pos2;
    private ActivityPosition dealer;
    private BlackjackBlockPosition interaction;
    private final NavigableMap<Integer, BlackjackSeat> seats = new TreeMap<>();

    public BlackjackTableDraft(String id) {
        this(id, false, null, null, null, null, Map.of());
    }

    public BlackjackTableDraft(
            String id,
            boolean enabled,
            ActivityPosition pos1,
            ActivityPosition pos2,
            ActivityPosition dealer,
            BlackjackBlockPosition interaction,
            Map<Integer, ActivityPosition> seats
    ) {
        this.id = requireId(id);
        this.enabled = enabled;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.dealer = dealer;
        this.interaction = interaction;
        Objects.requireNonNull(seats, "seats").forEach((number, position) ->
                this.seats.put(
                        Objects.requireNonNull(number, "seat number"),
                        new BlackjackSeat(number, Objects.requireNonNull(position, "seat position"))
                )
        );
    }

    public BlackjackTableDraft(
            String id,
            boolean enabled,
            ActivityPosition pos1,
            ActivityPosition pos2,
            ActivityPosition dealer,
            BlackjackBlockPosition interaction,
            Collection<BlackjackSeat> seats
    ) {
        this.id = requireId(id);
        this.enabled = enabled;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.dealer = dealer;
        this.interaction = interaction;
        for (BlackjackSeat seat : Objects.requireNonNull(seats, "seats")) {
            BlackjackSeat validated = Objects.requireNonNull(seat, "seat");
            this.seats.put(validated.number(), validated);
        }
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Optional<ActivityPosition> getPos1() {
        return Optional.ofNullable(pos1);
    }

    public void setPos1(ActivityPosition pos1) {
        this.pos1 = Objects.requireNonNull(pos1, "pos1");
    }

    public Optional<ActivityPosition> getPos2() {
        return Optional.ofNullable(pos2);
    }

    public void setPos2(ActivityPosition pos2) {
        this.pos2 = Objects.requireNonNull(pos2, "pos2");
    }

    public Optional<ActivityPosition> getDealer() {
        return Optional.ofNullable(dealer);
    }

    public void setDealer(ActivityPosition dealer) {
        this.dealer = Objects.requireNonNull(dealer, "dealer");
    }

    public Optional<BlackjackBlockPosition> getInteraction() {
        return Optional.ofNullable(interaction);
    }

    public void setInteraction(BlackjackBlockPosition interaction) {
        this.interaction = Objects.requireNonNull(interaction, "interaction");
    }

    public NavigableMap<Integer, ActivityPosition> getSeats() {
        NavigableMap<Integer, ActivityPosition> positions = new TreeMap<>();
        seats.forEach((number, seat) -> positions.put(number, seat.position()));
        return Collections.unmodifiableNavigableMap(positions);
    }

    public NavigableMap<Integer, BlackjackSeat> getSeatDefinitions() {
        return Collections.unmodifiableNavigableMap(new TreeMap<>(seats));
    }

    public void setSeat(int number, ActivityPosition position) {
        if (number < 1 || number > 5) {
            throw new IllegalArgumentException("seat number must be between 1 and 5");
        }
        seats.put(number, new BlackjackSeat(number, Objects.requireNonNull(position, "position")));
    }

    public void setSeat(int number, ActivityPosition position, BlackjackBlockPosition block) {
        if (number < 1 || number > 5) {
            throw new IllegalArgumentException("seat number must be between 1 and 5");
        }
        seats.put(number, new BlackjackSeat(number, position, Objects.requireNonNull(block, "block")));
    }

    public boolean removeSeat(int number) {
        return seats.remove(number) != null;
    }

    public BlackjackTableDraft copy() {
        return new BlackjackTableDraft(id, enabled, pos1, pos2, dealer, interaction, seats.values());
    }

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private static String requireId(String id) {
        String value = Objects.requireNonNull(id, "id");
        if (!isValidId(value)) {
            throw new IllegalArgumentException("id must match [a-z0-9_-]+");
        }
        return value;
    }
}

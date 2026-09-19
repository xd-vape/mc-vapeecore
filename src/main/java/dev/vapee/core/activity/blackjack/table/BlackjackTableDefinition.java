package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.location.ActivityArea;
import dev.vapee.core.activity.location.ActivityPosition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record BlackjackTableDefinition(
        String id,
        ActivityArea area,
        ActivityPosition dealer,
        BlackjackBlockPosition interaction,
        List<BlackjackSeat> seats,
        BlackjackTableInteractionMode interactionMode
) {

    public BlackjackTableDefinition(
            String id,
            ActivityArea area,
            ActivityPosition dealer,
            BlackjackBlockPosition interaction,
            List<BlackjackSeat> seats
    ) {
        this(id, area, dealer, interaction, seats, inferMode(seats));
    }

    public BlackjackTableDefinition {
        if (!BlackjackTableDraft.isValidId(id)) {
            throw new IllegalArgumentException("id must match [a-z0-9_-]+");
        }
        area = Objects.requireNonNull(area, "area");
        dealer = Objects.requireNonNull(dealer, "dealer");
        seats = Objects.requireNonNull(seats, "seats").stream().sorted().toList();
        interactionMode = Objects.requireNonNull(interactionMode, "interactionMode");

        List<String> errors = validateDefinition(area, dealer, interaction, seats, interactionMode);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    public int capacity() {
        return seats.size();
    }

    public static List<String> validate(BlackjackTableDraft draft) {
        BlackjackTableDraft validatedDraft = Objects.requireNonNull(draft, "draft");
        List<String> errors = new ArrayList<>();
        ActivityPosition pos1 = validatedDraft.getPos1().orElse(null);
        ActivityPosition pos2 = validatedDraft.getPos2().orElse(null);
        ActivityPosition dealer = validatedDraft.getDealer().orElse(null);
        BlackjackBlockPosition interaction = validatedDraft.getInteraction().orElse(null);

        if (pos1 == null) {
            errors.add("missing area pos1");
        }
        if (pos2 == null) {
            errors.add("missing area pos2");
        }
        if (dealer == null) {
            errors.add("missing dealer");
        }
        List<BlackjackSeat> seats = List.copyOf(validatedDraft.getSeatDefinitions().values());
        if (seats.isEmpty()) {
            errors.add("missing seat");
        }
        if (seats.size() > 5) {
            errors.add("more than 5 seats configured");
        }
        validatedDraft.getSeatDefinitions().keySet().stream()
                .filter(number -> number < 1 || number > 5)
                .forEach(number -> errors.add("seat number " + number + " is outside 1-5"));

        boolean anyModern = seats.stream().anyMatch(BlackjackSeat::isModern);
        boolean allModern = !seats.isEmpty() && seats.stream().allMatch(BlackjackSeat::isModern);
        if (anyModern && !allModern) {
            errors.add("legacy and modern seats cannot be mixed");
        }
        BlackjackTableInteractionMode mode = allModern
                ? BlackjackTableInteractionMode.MODERN_SEAT_CLICK
                : BlackjackTableInteractionMode.LEGACY_INTERACTION;
        if (mode == BlackjackTableInteractionMode.LEGACY_INTERACTION && interaction == null) {
            errors.add("missing interaction");
        }

        if (pos1 == null || pos2 == null || dealer == null
                || (mode == BlackjackTableInteractionMode.LEGACY_INTERACTION && interaction == null)) {
            return List.copyOf(errors);
        }

        String worldName = pos1.worldName();
        if (!worldName.equals(pos2.worldName())) {
            errors.add("area corners use different worlds");
        }
        if (!worldName.equals(dealer.worldName())) {
            errors.add("dealer uses a different world");
        }
        if (mode == BlackjackTableInteractionMode.LEGACY_INTERACTION
                && interaction != null && !worldName.equals(interaction.worldName())) {
            errors.add("interaction uses a different world");
        }
        for (BlackjackSeat seat : seats) {
            if (!worldName.equals(seat.position().worldName())) {
                errors.add("seat " + seat.number() + " uses a different world");
            }
            if (seat.block() != null && !worldName.equals(seat.block().worldName())) {
                errors.add("seat block " + seat.number() + " uses a different world");
            }
        }
        if (!errors.isEmpty()) {
            return List.copyOf(errors);
        }

        ActivityArea area = new ActivityArea(
                worldName,
                pos1.x(), pos1.y(), pos1.z(),
                pos2.x(), pos2.y(), pos2.z()
        );
        errors.addAll(validateDefinition(area, dealer, interaction, seats, mode));
        return List.copyOf(errors);
    }

    public static BlackjackTableDefinition fromDraft(BlackjackTableDraft draft) {
        List<String> errors = validate(draft);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        ActivityPosition pos1 = draft.getPos1().orElseThrow();
        ActivityPosition pos2 = draft.getPos2().orElseThrow();
        ActivityArea area = new ActivityArea(
                pos1.worldName(),
                pos1.x(), pos1.y(), pos1.z(),
                pos2.x(), pos2.y(), pos2.z()
        );
        return new BlackjackTableDefinition(
                draft.getId(),
                area,
                draft.getDealer().orElseThrow(),
                draft.getInteraction().orElse(null),
                List.copyOf(draft.getSeatDefinitions().values()),
                inferMode(List.copyOf(draft.getSeatDefinitions().values()))
        );
    }

    private static BlackjackTableInteractionMode inferMode(List<BlackjackSeat> seats) {
        return seats != null && !seats.isEmpty() && seats.stream().allMatch(BlackjackSeat::isModern)
                ? BlackjackTableInteractionMode.MODERN_SEAT_CLICK
                : BlackjackTableInteractionMode.LEGACY_INTERACTION;
    }

    private static List<String> validateDefinition(
            ActivityArea area,
            ActivityPosition dealer,
            BlackjackBlockPosition interaction,
            List<BlackjackSeat> seats,
            BlackjackTableInteractionMode mode
    ) {
        List<String> errors = new ArrayList<>();
        if (seats.isEmpty()) {
            errors.add("at least one seat is required");
        }
        if (seats.size() > 5) {
            errors.add("at most five seats are allowed");
        }
        Set<Integer> numbers = new HashSet<>();
        Set<BlackjackBlockPosition> blocks = new HashSet<>();
        for (BlackjackSeat seat : seats) {
            if (!numbers.add(seat.number())) {
                errors.add("duplicate seat number " + seat.number());
            }
            if (!area.worldName().equals(seat.position().worldName())) {
                errors.add("seat " + seat.number() + " uses a different world");
            } else if (mode == BlackjackTableInteractionMode.LEGACY_INTERACTION
                    && !area.contains(seat.position())) {
                errors.add("seat " + seat.number() + " is outside the area");
            }
            if (mode == BlackjackTableInteractionMode.MODERN_SEAT_CLICK) {
                BlackjackBlockPosition block = seat.block();
                if (block == null) {
                    errors.add("seat " + seat.number() + " has no block");
                } else {
                    if (!blocks.add(block)) {
                        errors.add("duplicate seat block at seat " + seat.number());
                    }
                    if (!area.worldName().equals(block.worldName())) {
                        errors.add("seat block " + seat.number() + " uses a different world");
                    } else if (!contains(area, block)) {
                        errors.add("seat block " + seat.number() + " is outside the area");
                    }
                }
            } else if (seat.isModern()) {
                errors.add("legacy and modern seats cannot be mixed");
            }
        }
        if (!area.worldName().equals(dealer.worldName())) {
            errors.add("dealer uses a different world");
        } else if (!area.contains(dealer)) {
            errors.add("dealer is outside the area");
        }
        if (mode == BlackjackTableInteractionMode.LEGACY_INTERACTION) {
            if (interaction == null) {
                errors.add("missing interaction");
            } else if (!area.worldName().equals(interaction.worldName())) {
                errors.add("interaction uses a different world");
            } else if (!contains(area, interaction)) {
                errors.add("interaction is outside the area");
            }
        }
        return errors;
    }

    private static boolean contains(ActivityArea area, BlackjackBlockPosition position) {
        return position.x() <= area.maxX() && position.x() + 1.0D >= area.minX()
                && position.y() <= area.maxY() && position.y() + 1.0D >= area.minY()
                && position.z() <= area.maxZ() && position.z() + 1.0D >= area.minZ();
    }
}

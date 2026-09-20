package dev.vapee.core.activity.blackjack.presentation;

import dev.vapee.core.activity.blackjack.table.BlackjackDisplayAnchor;
import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
import dev.vapee.core.activity.location.ActivityPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Objects;

public final class BlackjackDisplayGeometry {

    public static final float CARD_SCALE = 0.52F;
    public static final double CARD_SPACING = 0.36D;
    public static final double CARD_HOVER = 0.035D;
    public static final double PLAYER_CARD_DISTANCE = 0.72D;
    public static final double DEALER_HAND_FORWARD_OFFSET = 0.75D;
    public static final double DEALER_HAND_VERTICAL_OFFSET = 1.25D;
    public static final double STATUS_DISTANCE = 0.74D;
    public static final double RESULT_INSET = 0.10D;
    public static final double HAND_VALUE_HEIGHT = 0.18D;
    public static final double RESULT_HEIGHT = 0.22D;
    public static final double STATUS_HEIGHT = 0.48D;
    public static final float HAND_VALUE_SCALE = 0.22F;
    public static final float RESULT_SCALE = 0.25F;
    public static final float STATUS_SCALE = 0.34F;
    public static final float DEALER_HAND_SCALE = 0.30F;

    private BlackjackDisplayGeometry() { }

    public static BlackjackDisplayAnchor surfaceAnchor(BlackjackTableDefinition definition) {
        BlackjackTableDefinition validated = Objects.requireNonNull(definition, "definition");
        if (validated.displayAnchor() != null) {
            return validated.displayAnchor();
        }
        var area = validated.area();
        return new BlackjackDisplayAnchor(
                area.worldName(),
                (area.minX() + area.maxX()) / 2.0D,
                validated.dealer().y(),
                (area.minZ() + area.maxZ()) / 2.0D,
                validated.dealer().yaw()
        );
    }

    public static Vector tableForward(BlackjackDisplayAnchor anchor) {
        double radians = Math.toRadians(Objects.requireNonNull(anchor, "anchor").yaw());
        return new Vector(-Math.sin(radians), 0.0D, Math.cos(radians)).normalize();
    }

    public static Vector tableRight(BlackjackDisplayAnchor anchor) {
        Vector forward = tableForward(anchor);
        return new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();
    }

    public static Location tableCenter(World world, BlackjackTableDefinition definition) {
        return tableCenter(world, surfaceAnchor(definition));
    }

    public static Location tableCenter(World world, BlackjackDisplayAnchor anchor) {
        World validatedWorld = Objects.requireNonNull(world, "world");
        BlackjackDisplayAnchor validatedAnchor = Objects.requireNonNull(anchor, "anchor");
        return new Location(validatedWorld, validatedAnchor.x(), validatedAnchor.y(), validatedAnchor.z(),
                validatedAnchor.yaw(), 0.0F);
    }

    public static Location seatCardAnchor(
            World world,
            BlackjackDisplayAnchor anchor,
            ActivityPosition seatPosition
    ) {
        Location center = tableCenter(world, anchor);
        ActivityPosition seat = Objects.requireNonNull(seatPosition, "seatPosition");
        Location source = new Location(world, seat.x(), seat.y(), seat.z(), seat.yaw(), seat.pitch());
        Vector inward = horizontalDirection(source, center);
        return new Location(world,
                source.getX() + inward.getX() * PLAYER_CARD_DISTANCE,
                anchor.y() + CARD_HOVER,
                source.getZ() + inward.getZ() * PLAYER_CARD_DISTANCE,
                0.0F,
                0.0F);
    }

    public static Location seatCardAnchor(World world, BlackjackTableDefinition definition, BlackjackSeat seat) {
        return seatCardAnchor(world, surfaceAnchor(definition), Objects.requireNonNull(seat, "seat").position());
    }

    public static Vector dealerForward(ActivityPosition dealerPosition) {
        ActivityPosition dealer = Objects.requireNonNull(dealerPosition, "dealerPosition");
        double radians = Math.toRadians(dealer.yaw());
        return new Vector(-Math.sin(radians), 0.0D, Math.cos(radians)).normalize();
    }

    public static Location dealerHandLocation(World world, ActivityPosition dealerPosition) {
        World validatedWorld = Objects.requireNonNull(world, "world");
        ActivityPosition dealer = Objects.requireNonNull(dealerPosition, "dealerPosition");
        Vector forward = dealerForward(dealer);
        return new Location(validatedWorld,
                dealer.x() + forward.getX() * DEALER_HAND_FORWARD_OFFSET,
                dealer.y() + DEALER_HAND_VERTICAL_OFFSET,
                dealer.z() + forward.getZ() * DEALER_HAND_FORWARD_OFFSET,
                0.0F,
                0.0F);
    }

    public static Location dealerHandLocation(World world, BlackjackTableDefinition definition) {
        return dealerHandLocation(world, Objects.requireNonNull(definition, "definition").dealer());
    }

    public static Location statusAnchor(World world, BlackjackDisplayAnchor anchor) {
        Location center = tableCenter(world, anchor);
        Vector forward = tableForward(anchor);
        return new Location(world,
                center.getX() + forward.getX() * STATUS_DISTANCE,
                anchor.y() + STATUS_HEIGHT,
                center.getZ() + forward.getZ() * STATUS_DISTANCE,
                0.0F,
                0.0F);
    }

    public static Location resultAnchor(
            World world,
            BlackjackDisplayAnchor anchor,
            ActivityPosition seatPosition
    ) {
        Location cards = seatCardAnchor(world, anchor, seatPosition);
        Location center = tableCenter(world, anchor);
        Vector inward = horizontalDirection(cards, center);
        return new Location(world,
                cards.getX() + inward.getX() * RESULT_INSET,
                anchor.y() + RESULT_HEIGHT,
                cards.getZ() + inward.getZ() * RESULT_INSET,
                0.0F,
                0.0F);
    }

    public static Location handValueAnchor(
            World world,
            BlackjackDisplayAnchor anchor,
            ActivityPosition seatPosition
    ) {
        Location result = resultAnchor(world, anchor, seatPosition);
        result.setY(anchor.y() + HAND_VALUE_HEIGHT);
        return result;
    }

    public static Location seatCard(
            World world,
            BlackjackDisplayAnchor anchor,
            ActivityPosition seatPosition,
            int index,
            int count
    ) {
        Location center = tableCenter(world, anchor);
        Location cards = seatCardAnchor(world, anchor, seatPosition);
        Vector inward = horizontalDirection(cards, center);
        Vector handRight = perpendicularRight(inward);
        if (handRight.dot(tableRight(anchor)) < 0.0D) {
            handRight.multiply(-1.0D);
        }
        return spaced(cards, handRight, index, count);
    }

    public static Quaternionf seatCardRotation(
            World world,
            BlackjackDisplayAnchor anchor,
            ActivityPosition seatPosition
    ) {
        Location cards = seatCardAnchor(world, anchor, seatPosition);
        return horizontalCardRotation(horizontalDirection(cards, tableCenter(world, anchor)));
    }

    public static Quaternionf seatFacingRotation(
            World world,
            BlackjackDisplayAnchor anchor,
            ActivityPosition seatPosition
    ) {
        Location result = resultAnchor(world, anchor, seatPosition);
        Location seat = new Location(world, seatPosition.x(), seatPosition.y(), seatPosition.z());
        Vector towardSeat = horizontalDirection(result, seat);
        float yaw = (float) Math.atan2(towardSeat.getX(), towardSeat.getZ());
        return new Quaternionf().rotationY(yaw);
    }

    private static Location spaced(Location anchor, Vector right, int index, int count) {
        double offset = (index - (count - 1) / 2.0D) * CARD_SPACING;
        Location result = anchor.clone().add(right.clone().multiply(offset));
        result.setYaw(0.0F);
        result.setPitch(0.0F);
        return result;
    }

    private static Vector perpendicularRight(Vector forward) {
        return new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();
    }

    private static Quaternionf horizontalCardRotation(Vector textTopDirection) {
        float yaw = (float) Math.atan2(-textTopDirection.getX(), -textTopDirection.getZ());
        return new Quaternionf().rotationYXZ(yaw, (float) Math.toRadians(-90.0D), 0.0F);
    }

    private static Vector horizontalDirection(Location from, Location to) {
        Vector vector = to.toVector().subtract(from.toVector()).setY(0.0D);
        return vector.lengthSquared() < 0.0001D ? new Vector(0.0D, 0.0D, 1.0D) : vector.normalize();
    }
}

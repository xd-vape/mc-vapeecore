package dev.vapee.core.activity.blackjack.presentation;

import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
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
    public static final double DEALER_CARD_DISTANCE = 0.45D;
    public static final double PLAYER_LABEL_HEIGHT = 0.24D;
    public static final double DEALER_LABEL_HEIGHT = 0.26D;
    public static final double STATUS_HEIGHT = 0.68D;
    public static final float STATUS_SCALE = 0.38F;
    public static final float LABEL_SCALE = 0.25F;

    private BlackjackDisplayGeometry() { }

    public static Location tableCenter(World world, BlackjackTableDefinition definition) {
        Objects.requireNonNull(world, "world");
        BlackjackTableDefinition validated = Objects.requireNonNull(definition, "definition");
        if (validated.displayAnchor() != null) {
            var anchor = validated.displayAnchor();
            return new Location(world, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), 0.0F);
        }
        var area = validated.area();
        return new Location(world, (area.minX() + area.maxX()) / 2.0D,
                validated.dealer().y(), (area.minZ() + area.maxZ()) / 2.0D,
                validated.dealer().yaw(), 0.0F);
    }

    public static Location playerAnchor(World world, BlackjackTableDefinition definition, BlackjackSeat seat) {
        Location center = tableCenter(world, definition);
        Location source = new Location(world, seat.position().x(), seat.position().y(), seat.position().z(),
                seat.position().yaw(), seat.position().pitch());
        Vector inward = horizontalDirection(source, center);
        return new Location(world,
                source.getX() + inward.getX() * PLAYER_CARD_DISTANCE,
                center.getY() + CARD_HOVER,
                source.getZ() + inward.getZ() * PLAYER_CARD_DISTANCE);
    }

    public static Location dealerAnchor(World world, BlackjackTableDefinition definition) {
        Location center = tableCenter(world, definition);
        Location source = new Location(world, definition.dealer().x(), definition.dealer().y(),
                definition.dealer().z(), definition.dealer().yaw(), definition.dealer().pitch());
        Vector inward = horizontalDirection(source, center);
        return new Location(world,
                source.getX() + inward.getX() * DEALER_CARD_DISTANCE,
                center.getY() + CARD_HOVER,
                source.getZ() + inward.getZ() * DEALER_CARD_DISTANCE,
                0.0F, 0.0F);
    }

    public static Location card(Location anchor, Location center, int index, int count) {
        Vector inward = horizontalDirection(anchor, center);
        Vector right = new Vector(-inward.getZ(), 0.0D, inward.getX());
        double offset = (index - (count - 1) / 2.0D) * CARD_SPACING;
        Location result = anchor.clone().add(right.multiply(offset));
        result.setYaw(0.0F);
        result.setPitch(0.0F);
        return result;
    }

    public static Quaternionf playerCardRotation(Location anchor, Location center) {
        return horizontalCardRotation(horizontalDirection(anchor, center));
    }

    public static Quaternionf dealerCardRotation(Location anchor, Location center) {
        return horizontalCardRotation(horizontalDirection(center, anchor));
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

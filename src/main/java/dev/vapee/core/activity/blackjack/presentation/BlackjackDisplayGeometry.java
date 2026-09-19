package dev.vapee.core.activity.blackjack.presentation;

import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Objects;

public final class BlackjackDisplayGeometry {

    public static final float CARD_SCALE = 0.32F;
    public static final double CARD_SPACING = 0.30D;
    public static final double CARD_HOVER = 0.07D;
    public static final double PLAYER_DISTANCE = 0.72D;
    public static final double DEALER_DISTANCE = 0.45D;
    public static final double LABEL_HEIGHT = 0.42D;
    public static final double STATUS_HEIGHT = 1.28D;

    private BlackjackDisplayGeometry() { }

    public static Location tableCenter(World world, BlackjackTableDefinition definition) {
        Objects.requireNonNull(world, "world");
        var area = Objects.requireNonNull(definition, "definition").area();
        return new Location(world, (area.minX() + area.maxX()) / 2.0D,
                definition.dealer().y(), (area.minZ() + area.maxZ()) / 2.0D);
    }

    public static Location playerAnchor(World world, BlackjackTableDefinition definition, BlackjackSeat seat) {
        Location center = tableCenter(world, definition);
        Location source = new Location(world, seat.position().x(), seat.position().y(), seat.position().z(),
                seat.position().yaw(), seat.position().pitch());
        Vector inward = horizontalDirection(source, center);
        return source.clone().add(inward.multiply(PLAYER_DISTANCE))
                .set(source.getX() + inward.getX() * PLAYER_DISTANCE,
                        definition.dealer().y() + CARD_HOVER,
                        source.getZ() + inward.getZ() * PLAYER_DISTANCE);
    }

    public static Location dealerAnchor(World world, BlackjackTableDefinition definition) {
        Location center = tableCenter(world, definition);
        Location source = new Location(world, definition.dealer().x(), definition.dealer().y(),
                definition.dealer().z(), definition.dealer().yaw(), definition.dealer().pitch());
        Vector inward = horizontalDirection(source, center);
        return new Location(world,
                source.getX() + inward.getX() * DEALER_DISTANCE,
                source.getY() + CARD_HOVER,
                source.getZ() + inward.getZ() * DEALER_DISTANCE,
                source.getYaw(), 0.0F);
    }

    public static Location card(Location anchor, Location center, int index, int count) {
        Vector inward = horizontalDirection(anchor, center);
        Vector right = new Vector(-inward.getZ(), 0.0D, inward.getX());
        double offset = (index - (count - 1) / 2.0D) * CARD_SPACING;
        Location result = anchor.clone().add(right.multiply(offset));
        result.setYaw((float) Math.toDegrees(Math.atan2(-inward.getX(), inward.getZ())));
        return result;
    }

    private static Vector horizontalDirection(Location from, Location to) {
        Vector vector = to.toVector().subtract(from.toVector()).setY(0.0D);
        return vector.lengthSquared() < 0.0001D ? new Vector(0.0D, 0.0D, 1.0D) : vector.normalize();
    }
}

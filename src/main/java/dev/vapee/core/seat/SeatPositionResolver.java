package dev.vapee.core.seat;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

import java.util.Objects;
import java.util.Optional;

public final class SeatPositionResolver {

    public Optional<Location> resolve(Block block, float playerYaw) {
        Block validatedBlock = Objects.requireNonNull(block, "block");
        BlockData data = validatedBlock.getBlockData();
        double surfaceY;
        float yaw;

        if (data instanceof Slab slab) {
            if (slab.getType() == Slab.Type.DOUBLE) {
                return Optional.empty();
            }
            surfaceY = validatedBlock.getY() + (slab.getType() == Slab.Type.TOP ? 1.0D : 0.5D);
            yaw = normalizeYaw(playerYaw);
        } else if (data instanceof Stairs stairs) {
            if (stairs.getHalf() != Bisected.Half.BOTTOM) {
                return Optional.empty();
            }
            surfaceY = validatedBlock.getY() + 0.5D;
            yaw = yaw(stairs.getFacing().getOppositeFace());
        } else {
            return Optional.empty();
        }

        return Optional.of(new Location(
                validatedBlock.getWorld(),
                validatedBlock.getX() + 0.5D,
                surfaceY,
                validatedBlock.getZ() + 0.5D,
                yaw,
                0.0F
        ));
    }

    static float yaw(BlockFace facing) {
        return switch (Objects.requireNonNull(facing, "facing")) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> throw new IllegalArgumentException("Seat facing must be horizontal");
        };
    }

    private static float normalizeYaw(float yaw) {
        if (!Float.isFinite(yaw)) {
            return 0.0F;
        }
        float normalized = yaw % 360.0F;
        return normalized < -180.0F ? normalized + 360.0F
                : normalized > 180.0F ? normalized - 360.0F : normalized;
    }
}

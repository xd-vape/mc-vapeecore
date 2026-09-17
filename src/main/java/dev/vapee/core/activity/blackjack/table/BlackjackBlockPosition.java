package dev.vapee.core.activity.blackjack.table;

import org.bukkit.block.Block;

import java.util.Objects;

public record BlackjackBlockPosition(String worldName, int x, int y, int z) {

    public BlackjackBlockPosition {
        worldName = Objects.requireNonNull(worldName, "worldName").trim();
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
    }

    public static BlackjackBlockPosition fromBlock(Block block) {
        Block validatedBlock = Objects.requireNonNull(block, "block");
        return new BlackjackBlockPosition(
                validatedBlock.getWorld().getName(),
                validatedBlock.getX(),
                validatedBlock.getY(),
                validatedBlock.getZ()
        );
    }
}

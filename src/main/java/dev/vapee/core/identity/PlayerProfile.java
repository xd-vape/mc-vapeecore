package dev.vapee.core.identity;

import dev.vapee.core.rank.RankInfo;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record PlayerProfile(PlayerIdentity identity, boolean online, long coins,
                            Optional<RankInfo> rank, OptionalLong playtimeTicks) {

    public PlayerProfile {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(playtimeTicks, "playtimeTicks");
        if (coins < 0L || (playtimeTicks.isPresent() && playtimeTicks.getAsLong() < 0L)) {
            throw new IllegalArgumentException("Profile values must not be negative");
        }
        if (!online && (rank.isPresent() || playtimeTicks.isPresent())) {
            throw new IllegalArgumentException("Offline profiles cannot contain live rank or playtime");
        }
    }
}

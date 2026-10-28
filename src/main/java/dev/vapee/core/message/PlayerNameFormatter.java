package dev.vapee.core.message;

import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Literal names with current, loaded-only primary-rank colors; owns no rank state or lifecycle. */
public final class PlayerNameFormatter {
    private final Function<UUID, Optional<RankInfo>> ranks;

    public PlayerNameFormatter(Supplier<RankService> ranks) {
        Objects.requireNonNull(ranks, "ranks");
        this.ranks = id -> Objects.requireNonNull(ranks.get(), "RankService").getPrimaryRank(id);
    }

    private PlayerNameFormatter() { this.ranks = id -> Optional.empty(); }

    /** Compatibility for consumers constructed without a rank provider. */
    public static PlayerNameFormatter neutral() { return new PlayerNameFormatter(); }

    public Component format(UUID id, String literalName) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(literalName, "literalName");
        return Component.text(literalName,
                ranks.apply(id).map(RankInfo::effectiveColor).orElse(NamedTextColor.WHITE));
    }
}

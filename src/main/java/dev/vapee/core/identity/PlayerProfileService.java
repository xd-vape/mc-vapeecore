package dev.vapee.core.identity;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;

public final class PlayerProfileService {

    private final PlayerIdentityService identityService;
    private final EconomyService economyService;
    private final Function<UUID, Player> onlinePlayer;
    private final Function<UUID, Optional<RankInfo>> rankLookup;

    public PlayerProfileService(JavaPlugin plugin, PlayerIdentityService identityService,
                                EconomyService economyService, RankService rankService) {
        this(identityService, economyService,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                Objects.requireNonNull(rankService, "rankService")::getPrimaryRank);
    }

    PlayerProfileService(PlayerIdentityService identityService, EconomyService economyService,
                         Function<UUID, Player> onlinePlayer,
                         Function<UUID, Optional<RankInfo>> rankLookup) {
        this.identityService = Objects.requireNonNull(identityService, "identityService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.rankLookup = Objects.requireNonNull(rankLookup, "rankLookup");
    }

    public Optional<PlayerProfile> getProfile(UUID uniqueId) {
        return identityService.findById(Objects.requireNonNull(uniqueId, "uniqueId"))
                .map(this::buildProfile);
    }

    public PlayerProfileLookupResult resolveProfile(String input) {
        PlayerLookupResult lookup = identityService.resolve(Objects.requireNonNull(input, "input"));
        if (lookup.status() != PlayerLookupStatus.FOUND) {
            return new PlayerProfileLookupResult(lookup.status(), Optional.empty());
        }
        return new PlayerProfileLookupResult(PlayerLookupStatus.FOUND,
                getProfile(lookup.identity().orElseThrow().uniqueId()));
    }

    private PlayerProfile buildProfile(PlayerIdentity identity) {
        UUID uniqueId = identity.uniqueId();
        long coins = economyService.getKnownCoins(uniqueId).orElseThrow(
                () -> new IllegalStateException("Known player has no readable coin wallet: " + uniqueId));
        Player player = onlinePlayer.apply(uniqueId);
        if (player == null || !player.isOnline()) {
            return new PlayerProfile(identity, false, coins, Optional.empty(), OptionalLong.empty());
        }
        return new PlayerProfile(identity, true, coins, rankLookup.apply(uniqueId),
                OptionalLong.of(Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE))));
    }
}

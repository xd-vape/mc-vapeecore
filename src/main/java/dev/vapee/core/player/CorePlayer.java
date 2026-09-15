package dev.vapee.core.player;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.player.settings.PlayerSettings;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CorePlayer {

    private final UUID uniqueId;
    private final Instant firstJoin;
    private final PlayerSettings settings;
    private final CoinWallet wallet;
    private String name;
    private Instant lastJoin;

    public CorePlayer(
            UUID uniqueId,
            String name,
            Instant firstJoin,
            Instant lastJoin,
            PlayerSettings settings,
            CoinWallet wallet
    ) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = requireName(name);
        this.firstJoin = Objects.requireNonNull(firstJoin, "firstJoin");
        this.lastJoin = requireValidLastJoin(lastJoin);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getName() {
        return name;
    }

    public Instant getFirstJoin() {
        return firstJoin;
    }

    public Instant getLastJoin() {
        return lastJoin;
    }

    public PlayerSettings getSettings() {
        return settings;
    }

    public CoinWallet getWallet() {
        return wallet;
    }

    public void updateName(String name) {
        this.name = requireName(name);
    }

    public void updateLastJoin(Instant lastJoin) {
        this.lastJoin = requireValidLastJoin(lastJoin);
    }

    private String requireName(String name) {
        String validatedName = Objects.requireNonNull(name, "name");
        if (validatedName.isBlank()) {
            throw new IllegalArgumentException("Player names must not be blank");
        }
        return validatedName;
    }

    private Instant requireValidLastJoin(Instant lastJoin) {
        Instant validatedLastJoin = Objects.requireNonNull(lastJoin, "lastJoin");
        if (validatedLastJoin.isBefore(firstJoin)) {
            throw new IllegalArgumentException("lastJoin must not be before firstJoin");
        }
        return validatedLastJoin;
    }
}

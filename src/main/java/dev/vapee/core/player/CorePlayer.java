package dev.vapee.core.player;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.onlinereward.OnlineRewardProgress;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import dev.vapee.core.quest.PlayerQuestState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CorePlayer {

    private final UUID uniqueId;
    private final Instant firstJoin;
    private final PlayerSettings settings;
    private final CoinWallet wallet;
    private final PlayerSocial social;
    private final OnlineRewardProgress onlineRewardProgress;
    private final PlayerQuestState questState;
    private String name;
    private Instant lastJoin;

    public CorePlayer(
            UUID uniqueId,
            String name,
            Instant firstJoin,
            Instant lastJoin,
            PlayerSettings settings,
            CoinWallet wallet,
            PlayerSocial social
    ) {
        this(
                uniqueId,
                name,
                firstJoin,
                lastJoin,
                settings,
                wallet,
                social,
                OnlineRewardProgress.uninitialized(),
                PlayerQuestState.empty()
        );
    }

    public CorePlayer(
            UUID uniqueId,
            String name,
            Instant firstJoin,
            Instant lastJoin,
            PlayerSettings settings,
            CoinWallet wallet,
            PlayerSocial social,
            OnlineRewardProgress onlineRewardProgress
    ) {
        this(
                uniqueId,
                name,
                firstJoin,
                lastJoin,
                settings,
                wallet,
                social,
                onlineRewardProgress,
                PlayerQuestState.empty()
        );
    }

    public CorePlayer(
            UUID uniqueId,
            String name,
            Instant firstJoin,
            Instant lastJoin,
            PlayerSettings settings,
            CoinWallet wallet,
            PlayerSocial social,
            OnlineRewardProgress onlineRewardProgress,
            PlayerQuestState questState
    ) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = requireName(name);
        this.firstJoin = Objects.requireNonNull(firstJoin, "firstJoin");
        this.lastJoin = requireValidLastJoin(lastJoin);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.social = Objects.requireNonNull(social, "social");
        this.onlineRewardProgress = Objects.requireNonNull(
                onlineRewardProgress,
                "onlineRewardProgress"
        );
        this.questState = Objects.requireNonNull(questState, "questState");
        if (social.isIgnoring(uniqueId)) {
            throw new IllegalArgumentException("A player cannot ignore themselves");
        }
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

    public PlayerSocial getSocial() {
        return social;
    }

    public OnlineRewardProgress getOnlineRewardProgress() {
        return onlineRewardProgress;
    }

    public PlayerQuestState getQuestState() {
        return questState;
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

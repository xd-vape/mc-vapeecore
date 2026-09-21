package dev.vapee.core.economy;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class EconomyService {

    private final PlayerService playerService;

    public EconomyService(PlayerService playerService) {
        this.playerService = Objects.requireNonNull(playerService, "playerService");
    }

    public OptionalLong getCoins(UUID uniqueId) {
        Optional<CorePlayer> player = playerService.getPlayer(requireUniqueId(uniqueId));
        if (player.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(player.get().getWallet().getCoins());
    }

    public Optional<Boolean> hasCoins(UUID uniqueId, long amount) {
        UUID validatedUniqueId = requireUniqueId(uniqueId);
        requireNonNegative(amount);
        return playerService.getPlayer(validatedUniqueId)
                .map(player -> player.getWallet().getCoins() >= amount);
    }

    public EconomyResult setCoins(UUID uniqueId, long amount) {
        UUID validatedUniqueId = requireUniqueId(uniqueId);
        requireNonNegative(amount);

        Optional<CorePlayer> player = playerService.getPlayer(validatedUniqueId);
        if (player.isEmpty()) {
            return EconomyResult.PLAYER_NOT_LOADED;
        }
        return persistBalance(validatedUniqueId, player.get(), amount);
    }

    public EconomyResult addCoins(UUID uniqueId, long amount) {
        UUID validatedUniqueId = requireUniqueId(uniqueId);
        requirePositive(amount);

        return mutateAdd(validatedUniqueId, amount, this::persistBalance);
    }

    /**
     * Updates the loaded wallet without persisting it. Gameplay reward features must use
     * RewardService so its shared batch flush can track and persist this mutation.
     */
    public EconomyResult addCoinsDeferred(UUID uniqueId, long amount) {
        UUID validatedUniqueId = requireUniqueId(uniqueId);
        requirePositive(amount);

        return mutateAdd(validatedUniqueId, amount, this::applyBalance);
    }

    private EconomyResult mutateAdd(
            UUID uniqueId,
            long amount,
            BalanceApplication balanceApplication
    ) {
        Objects.requireNonNull(balanceApplication, "balanceApplication");

        Optional<CorePlayer> player = playerService.getPlayer(uniqueId);
        if (player.isEmpty()) {
            return EconomyResult.PLAYER_NOT_LOADED;
        }

        long newBalance;
        try {
            newBalance = Math.addExact(player.get().getWallet().getCoins(), amount);
        } catch (ArithmeticException exception) {
            return EconomyResult.BALANCE_OVERFLOW;
        }
        return balanceApplication.apply(uniqueId, player.get(), newBalance);
    }

    public EconomyResult removeCoins(UUID uniqueId, long amount) {
        UUID validatedUniqueId = requireUniqueId(uniqueId);
        requirePositive(amount);

        Optional<CorePlayer> player = playerService.getPlayer(validatedUniqueId);
        if (player.isEmpty()) {
            return EconomyResult.PLAYER_NOT_LOADED;
        }

        long currentBalance = player.get().getWallet().getCoins();
        if (currentBalance < amount) {
            return EconomyResult.INSUFFICIENT_FUNDS;
        }
        return persistBalance(validatedUniqueId, player.get(), currentBalance - amount);
    }

    private EconomyResult persistBalance(UUID uniqueId, CorePlayer player, long newBalance) {
        CoinWallet wallet = player.getWallet();
        long previousBalance = wallet.getCoins();
        wallet.setCoins(newBalance);

        try {
            playerService.savePlayer(uniqueId);
        } catch (RuntimeException exception) {
            wallet.setCoins(previousBalance);
            throw exception;
        }
        return EconomyResult.SUCCESS;
    }

    private EconomyResult applyBalance(UUID uniqueId, CorePlayer player, long newBalance) {
        player.getWallet().setCoins(newBalance);
        return EconomyResult.SUCCESS;
    }

    private UUID requireUniqueId(UUID uniqueId) {
        return Objects.requireNonNull(uniqueId, "uniqueId");
    }

    private void requireNonNegative(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Coin amount must not be negative");
        }
    }

    private void requirePositive(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("Coin amount must be positive");
        }
    }

    @FunctionalInterface
    private interface BalanceApplication {

        EconomyResult apply(UUID uniqueId, CorePlayer player, long newBalance);
    }
}

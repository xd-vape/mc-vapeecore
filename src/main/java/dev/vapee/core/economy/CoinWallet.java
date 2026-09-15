package dev.vapee.core.economy;

public final class CoinWallet {

    private long coins;

    private CoinWallet(long coins) {
        this.coins = requireValidCoins(coins);
    }

    public static CoinWallet empty() {
        return new CoinWallet(0L);
    }

    public static CoinWallet of(long coins) {
        return new CoinWallet(coins);
    }

    public long getCoins() {
        return coins;
    }

    void setCoins(long coins) {
        this.coins = requireValidCoins(coins);
    }

    private static long requireValidCoins(long coins) {
        if (coins < 0L) {
            throw new IllegalArgumentException("Coin balance must not be negative");
        }
        return coins;
    }
}

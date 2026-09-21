package dev.vapee.core.economy;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class EconomyServiceHarness {

    private static int checks;

    private EconomyServiceHarness() {
    }

    public static void main(String[] args) {
        testImmediateAndDeferredAdd();
        testOverflowAndValidation();
        testImmediateRollback();
        testSetAndRemoveSemantics();
        System.out.println("EconomyServiceHarness passed " + checks + " checks.");
    }

    private static void testImmediateAndDeferredAdd() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(100L);

        check(fixture.economy.addCoins(playerId, 10L) == EconomyResult.SUCCESS,
                "immediate add succeeds");
        check(fixture.balance(playerId) == 110L
                        && fixture.repository.persistedBalance(playerId) == 110L
                        && fixture.repository.saveAttempts(playerId) == 1,
                "immediate add mutates the wallet and saves exactly once");

        check(fixture.economy.addCoinsDeferred(playerId, 5L) == EconomyResult.SUCCESS,
                "deferred add succeeds");
        check(fixture.balance(playerId) == 115L
                        && fixture.repository.persistedBalance(playerId) == 110L
                        && fixture.repository.saveAttempts(playerId) == 1,
                "deferred add is immediately visible but performs no persistence write");
    }

    private static void testOverflowAndValidation() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(Long.MAX_VALUE);
        check(fixture.economy.addCoinsDeferred(playerId, 1L) == EconomyResult.BALANCE_OVERFLOW,
                "deferred overflow has an explicit result");
        check(fixture.balance(playerId) == Long.MAX_VALUE
                        && fixture.repository.saveAttempts(playerId) == 0,
                "deferred overflow leaves the wallet unchanged and does not save");
        expectIllegalArgument(() -> fixture.economy.addCoinsDeferred(playerId, 0L),
                "deferred zero amount is rejected");
        expectIllegalArgument(() -> fixture.economy.addCoinsDeferred(playerId, -1L),
                "deferred negative amount is rejected");
    }

    private static void testImmediateRollback() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(40L);
        fixture.repository.fail(playerId);
        expectRuntime(() -> fixture.economy.addCoins(playerId, 2L),
                "immediate add exposes persistence failure");
        check(fixture.balance(playerId) == 40L
                        && fixture.repository.persistedBalance(playerId) == 40L,
                "immediate add rolls its wallet mutation back after save failure");
    }

    private static void testSetAndRemoveSemantics() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(100L);
        check(fixture.economy.setCoins(playerId, 500L) == EconomyResult.SUCCESS
                        && fixture.balance(playerId) == 500L
                        && fixture.repository.persistedBalance(playerId) == 500L,
                "setCoins remains immediately durable");
        check(fixture.economy.removeCoins(playerId, 20L) == EconomyResult.SUCCESS
                        && fixture.balance(playerId) == 480L
                        && fixture.repository.persistedBalance(playerId) == 480L,
                "removeCoins remains immediately durable");
        check(fixture.repository.saveAttempts(playerId) == 2,
                "set and remove each perform one immediate save");
        check(fixture.economy.removeCoins(playerId, 481L) == EconomyResult.INSUFFICIENT_FUNDS
                        && fixture.balance(playerId) == 480L
                        && fixture.repository.saveAttempts(playerId) == 2,
                "insufficient remove neither mutates nor saves");
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void expectRuntime(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (PersistenceFailure expected) {
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture {

        private final MemoryRepository repository = new MemoryRepository();
        private final PlayerService players = new PlayerService(repository, logger());
        private final EconomyService economy = new EconomyService(players);

        private UUID load(long balance) {
            UUID playerId = UUID.randomUUID();
            repository.seed(player(playerId, balance));
            players.loadPlayer(playerId, "Player");
            repository.resetCounters();
            return playerId;
        }

        private long balance(UUID playerId) {
            return economy.getCoins(playerId).orElseThrow();
        }
    }

    private static CorePlayer player(UUID playerId, long balance) {
        Instant now = Instant.now();
        return new CorePlayer(
                playerId,
                "Player",
                now,
                now,
                PlayerSettings.defaults(),
                CoinWallet.of(balance),
                PlayerSocial.empty()
        );
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("EconomyServiceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static final class MemoryRepository implements PlayerRepository {

        private final Map<UUID, CorePlayer> players = new HashMap<>();
        private final Map<UUID, Long> persistedBalances = new HashMap<>();
        private final Map<UUID, Integer> saveAttempts = new HashMap<>();
        private final Set<UUID> failures = new HashSet<>();

        private void seed(CorePlayer player) {
            players.put(player.getUniqueId(), player);
            persistedBalances.put(player.getUniqueId(), player.getWallet().getCoins());
        }

        private void resetCounters() {
            saveAttempts.clear();
        }

        private void fail(UUID playerId) {
            failures.add(playerId);
        }

        private long persistedBalance(UUID playerId) {
            return persistedBalances.get(playerId);
        }

        private int saveAttempts(UUID playerId) {
            return saveAttempts.getOrDefault(playerId, 0);
        }

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(players.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            UUID playerId = player.getUniqueId();
            saveAttempts.merge(playerId, 1, Integer::sum);
            if (failures.contains(playerId)) {
                throw new PersistenceFailure();
            }
            players.put(playerId, player);
            persistedBalances.put(playerId, player.getWallet().getCoins());
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return players.containsKey(uniqueId);
        }
    }

    private static final class PersistenceFailure extends RuntimeException {
    }
}

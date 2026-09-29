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
        testReadsAndBoundaries();
        testAllRollbackOperations();
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

    private static void testReadsAndBoundaries() {
        Fixture f = new Fixture();
        UUID id = f.load(100L);
        UUID unknown = UUID.randomUUID();
        check(f.economy.getCoins(id).orElseThrow() == 100L, "loaded read");
        check(f.economy.getCoins(unknown).isEmpty(), "unloaded read empty");
        check(f.economy.getKnownCoins(id).orElseThrow() == 100L, "known loaded read");
        check(f.economy.getKnownCoins(unknown).isEmpty(), "unknown known read empty");
        check(f.economy.hasCoins(id, 100L).orElseThrow(), "has exact balance");
        check(!f.economy.hasCoins(id, 101L).orElseThrow(), "has insufficient false");
        check(f.economy.hasCoins(unknown, 0L).isEmpty(), "unloaded has empty");
        expectIllegalArgument(() -> f.economy.hasCoins(id, -1L), "negative has rejected");
        expectIllegalArgument(() -> f.economy.setCoins(id, -1L), "negative set rejected");
        for (long invalid : new long[]{0L, -1L}) {
            expectIllegalArgument(() -> f.economy.addCoins(id, invalid), "invalid immediate add");
            expectIllegalArgument(() -> f.economy.removeCoins(id, invalid), "invalid remove");
        }
        check(f.repository.saveAttempts(id) == 0 && f.balance(id) == 100L,
                "validation has no mutation or save");
        check(f.economy.setCoins(id, 0L) == EconomyResult.SUCCESS && f.balance(id) == 0L
                && f.repository.saveAttempts(id) == 1, "zero set saves exactly once");
        check(f.economy.setCoins(id, 0L) == EconomyResult.SUCCESS
                && f.repository.saveAttempts(id) == 2, "identical set retains immediate save semantics");
        check(f.economy.setCoins(id, Long.MAX_VALUE) == EconomyResult.SUCCESS
                && f.repository.persistedBalance(id) == Long.MAX_VALUE
                && f.repository.saveAttempts(id) == 3, "max long set durable");
        check(f.economy.addCoins(id, 1L) == EconomyResult.BALANCE_OVERFLOW
                && f.balance(id) == Long.MAX_VALUE && f.repository.saveAttempts(id) == 3,
                "immediate overflow neither mutates nor saves");
        f.players.unloadPlayer(id);
        check(f.economy.getCoins(id).isEmpty() && !f.players.isLoaded(id), "unload removes runtime balance");
        check(f.economy.getKnownCoins(id).orElseThrow() == Long.MAX_VALUE && !f.players.isLoaded(id),
                "offline known read does not load player");
        check(f.economy.addCoins(id, 1L) == EconomyResult.PLAYER_NOT_LOADED
                && f.economy.removeCoins(id, 1L) == EconomyResult.PLAYER_NOT_LOADED
                && f.economy.setCoins(id, 0L) == EconomyResult.PLAYER_NOT_LOADED
                && f.economy.addCoinsDeferred(id, 1L) == EconomyResult.PLAYER_NOT_LOADED,
                "all writes remain loaded only");
        check(CoinWallet.empty().getCoins() == 0L, "new wallet starts zero");
        expectIllegalArgument(() -> CoinWallet.of(-1L), "wallet construction invariant");
        expectIllegalArgument(() -> CoinWallet.empty().setCoins(-1L), "wallet setter invariant");
    }

    private static void testAllRollbackOperations() {
        for (String action : new String[]{"ADD", "REMOVE", "SET"}) {
            Fixture f = new Fixture();
            UUID id = f.load(100L);
            f.repository.fail(id);
            expectRuntime(() -> {
                switch (action) {
                    case "ADD" -> f.economy.addCoins(id, 10L);
                    case "REMOVE" -> f.economy.removeCoins(id, 10L);
                    case "SET" -> f.economy.setCoins(id, 10L);
                }
            }, action + " save failure propagates");
            check(f.balance(id) == 100L && f.repository.persistedBalance(id) == 100L
                    && f.repository.saveAttempts(id) == 1, action + " runtime/durable rollback and one save attempt");
        }
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

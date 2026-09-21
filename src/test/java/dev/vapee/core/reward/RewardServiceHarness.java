package dev.vapee.core.reward;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.economy.EconomyResult;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class RewardServiceHarness {

    private static int checks;

    private RewardServiceHarness() {
    }

    public static void main(String[] args) {
        testGrantAndValidation();
        testUnloadedAndOverflow();
        testBatching();
        testMultiPlayerBatch();
        testFailureIsolationAndRetry();
        testUnloadedDirtyCleanup();
        testEconomyInteroperability();
        System.out.println("RewardServiceHarness passed " + checks + " checks.");
    }

    private static void testGrantAndValidation() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(100L);
        RewardResult result = fixture.rewards.grantCoins(
                playerId,
                25L,
                RewardSource.EVENT,
                "  halloween_2026  "
        );
        check(result.status() == RewardStatus.SUCCESS
                        && result.grantedAmount() == 25L
                        && result.resultingBalance().orElseThrow() == 125L,
                "successful reward returns its exact amount and resulting balance");
        check(fixture.balance(playerId) == 125L && fixture.rewards.isDirty(playerId),
                "successful reward is immediately visible and marks the player dirty");
        check(fixture.repository.totalSaveAttempts() == 0,
                "successful reward performs no immediate persistence write");
        RewardGrant grant = new RewardGrant(playerId, 1L, RewardSource.SYSTEM, "  system:test  ");
        check(grant.reason().equals("system:test"), "technical reward reasons are trimmed");
        check(List.of(RewardSource.values()).equals(List.of(
                        RewardSource.PLAYTIME,
                        RewardSource.QUEST,
                        RewardSource.ACTIVITY,
                        RewardSource.EVENT,
                        RewardSource.ACHIEVEMENT,
                        RewardSource.ADMIN,
                        RewardSource.SYSTEM
                )),
                "reward sources remain generic and contain no mine-specific value");
        expectIllegalArgument(() -> new RewardGrant(playerId, 0L, RewardSource.SYSTEM, "zero"),
                "zero reward is rejected");
        expectIllegalArgument(() -> new RewardGrant(playerId, -1L, RewardSource.SYSTEM, "negative"),
                "negative reward is rejected");
        expectIllegalArgument(() -> new RewardGrant(playerId, 1L, null, "source"),
                "null source is rejected");
        expectIllegalArgument(() -> new RewardGrant(playerId, 1L, RewardSource.SYSTEM, "  "),
                "blank reason is rejected");
        expectIllegalArgument(() -> new RewardGrant(null, 1L, RewardSource.SYSTEM, "player"),
                "null player ID is rejected");
    }

    private static void testUnloadedAndOverflow() {
        Fixture fixture = new Fixture();
        UUID unloaded = UUID.randomUUID();
        RewardResult unloadedResult = fixture.rewards.grantCoins(
                unloaded,
                5L,
                RewardSource.ADMIN,
                "manual"
        );
        check(unloadedResult.status() == RewardStatus.PLAYER_NOT_LOADED
                        && unloadedResult.grantedAmount() == 0L
                        && unloadedResult.resultingBalance().isEmpty()
                        && !fixture.rewards.isDirty(unloaded),
                "unloaded reward returns a domain result without queueing or dirty state");

        UUID full = fixture.load(Long.MAX_VALUE);
        RewardResult overflow = fixture.rewards.grantCoins(
                full,
                1L,
                RewardSource.ACHIEVEMENT,
                "maximum"
        );
        check(overflow.status() == RewardStatus.BALANCE_OVERFLOW
                        && fixture.balance(full) == Long.MAX_VALUE
                        && !fixture.rewards.isDirty(full)
                        && fixture.repository.saveAttempts(full) == 0,
                "overflow leaves wallet, persistence, and dirty tracking unchanged");
    }

    private static void testBatching() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(0L);
        for (int index = 0; index < 100; index++) {
            RewardResult result = fixture.rewards.grantCoins(
                    playerId,
                    2L,
                    RewardSource.ACTIVITY,
                    "mine:stone"
            );
            check(result.status() == RewardStatus.SUCCESS, "batched reward grant succeeds");
        }
        check(fixture.balance(playerId) == 200L
                        && fixture.repository.persistedBalance(playerId) == 0L
                        && fixture.repository.saveAttempts(playerId) == 0,
                "one hundred rewards aggregate in memory without saves");
        fixture.rewards.flushPlayer(playerId);
        check(fixture.repository.saveAttempts(playerId) == 1
                        && fixture.repository.persistedBalance(playerId) == 200L
                        && !fixture.rewards.isDirty(playerId),
                "one player flush persists all one hundred rewards exactly once");
        fixture.rewards.flushPlayer(playerId);
        check(fixture.repository.saveAttempts(playerId) == 1,
                "flushPlayer is a no-op for a clean player");
    }

    private static void testMultiPlayerBatch() {
        Fixture fixture = new Fixture();
        UUID playerA = fixture.load(0L);
        UUID playerB = fixture.load(0L);
        UUID playerC = fixture.load(0L);
        grantMany(fixture, playerA, 50, 2L);
        grantMany(fixture, playerB, 30, 3L);
        grantMany(fixture, playerC, 1, 7L);
        check(fixture.repository.totalSaveAttempts() == 0 && fixture.rewards.dirtyPlayerCount() == 3,
                "multi-player rewards perform no writes before their shared flush");
        fixture.rewards.flushAll();
        check(fixture.repository.saveAttempts(playerA) == 1
                        && fixture.repository.saveAttempts(playerB) == 1
                        && fixture.repository.saveAttempts(playerC) == 1,
                "flushAll performs exactly one save per dirty player instead of one per grant");
        check(fixture.repository.persistedBalance(playerA) == 100L
                        && fixture.repository.persistedBalance(playerB) == 90L
                        && fixture.repository.persistedBalance(playerC) == 7L
                        && fixture.rewards.dirtyPlayerCount() == 0,
                "multi-player flush stores each aggregated current balance and clears dirty state");
    }

    private static void testFailureIsolationAndRetry() {
        Fixture fixture = new Fixture();
        UUID playerA = fixture.load(0L);
        UUID playerB = fixture.load(0L);
        fixture.rewards.grantCoins(playerA, 10L, RewardSource.QUEST, "daily:mine_250");
        fixture.rewards.grantCoins(playerB, 20L, RewardSource.EVENT, "event:test");
        fixture.repository.fail(playerA);

        fixture.rewards.flushAll();
        check(fixture.rewards.isDirty(playerA)
                        && !fixture.rewards.isDirty(playerB)
                        && fixture.repository.persistedBalance(playerA) == 0L
                        && fixture.repository.persistedBalance(playerB) == 20L,
                "flushAll isolates one save failure and continues with other players");
        check(fixture.balance(playerA) == 10L,
                "persistence failure never rolls the deferred wallet mutation back");

        fixture.repository.recover(playerA);
        fixture.rewards.flushAll();
        check(!fixture.rewards.isDirty(playerA)
                        && fixture.repository.persistedBalance(playerA) == 10L
                        && fixture.repository.saveAttempts(playerA) == 2,
                "a later flush retries and clears a formerly failed player");
    }

    private static void testUnloadedDirtyCleanup() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(10L);
        fixture.rewards.grantCoins(playerId, 5L, RewardSource.SYSTEM, "system:test");
        fixture.players.unloadPlayer(playerId);
        int savesAfterUnload = fixture.repository.saveAttempts(playerId);
        fixture.rewards.flushPlayer(playerId);
        check(!fixture.rewards.isDirty(playerId)
                        && fixture.repository.saveAttempts(playerId) == savesAfterUnload
                        && fixture.repository.persistedBalance(playerId) == 15L,
                "already-unloaded dirty player is cleaned without offline loading or another save");
    }

    private static void testEconomyInteroperability() {
        Fixture addFixture = new Fixture();
        UUID addPlayer = addFixture.load(100L);
        addFixture.rewards.grantCoins(addPlayer, 10L, RewardSource.ACTIVITY, "mine:stone");
        check(addFixture.economy.addCoins(addPlayer, 5L) == EconomyResult.SUCCESS
                        && addFixture.balance(addPlayer) == 115L
                        && addFixture.repository.persistedBalance(addPlayer) == 115L,
                "immediate admin add builds on the current deferred wallet balance");
        addFixture.rewards.flushAll();
        check(addFixture.repository.persistedBalance(addPlayer) == 115L,
                "later reward flush cannot restore a stale pre-admin balance");

        Fixture setFixture = new Fixture();
        UUID setPlayer = setFixture.load(100L);
        setFixture.rewards.grantCoins(setPlayer, 10L, RewardSource.ACTIVITY, "mine:stone");
        setFixture.economy.setCoins(setPlayer, 500L);
        setFixture.rewards.flushAll();
        check(setFixture.balance(setPlayer) == 500L
                        && setFixture.repository.persistedBalance(setPlayer) == 500L,
                "setCoins and the later reward flush retain the current 500-coin state");

        Fixture removeFixture = new Fixture();
        UUID removePlayer = removeFixture.load(100L);
        removeFixture.rewards.grantCoins(removePlayer, 10L, RewardSource.ACTIVITY, "mine:stone");
        removeFixture.economy.removeCoins(removePlayer, 20L);
        removeFixture.rewards.flushAll();
        check(removeFixture.balance(removePlayer) == 90L
                        && removeFixture.repository.persistedBalance(removePlayer) == 90L,
                "removeCoins and the later reward flush retain the current reduced state");
    }

    private static void grantMany(Fixture fixture, UUID playerId, int grants, long amount) {
        for (int index = 0; index < grants; index++) {
            fixture.rewards.grantCoins(playerId, amount, RewardSource.ACTIVITY, "activity:test");
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
        private final RewardService rewards = new RewardService(economy, players, logger());

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
        Logger logger = Logger.getLogger("RewardServiceHarness-" + System.nanoTime());
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

        private void recover(UUID playerId) {
            failures.remove(playerId);
        }

        private int saveAttempts(UUID playerId) {
            return saveAttempts.getOrDefault(playerId, 0);
        }

        private int totalSaveAttempts() {
            return saveAttempts.values().stream().mapToInt(Integer::intValue).sum();
        }

        private long persistedBalance(UUID playerId) {
            return persistedBalances.get(playerId);
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

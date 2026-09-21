package dev.vapee.core.onlinereward;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import dev.vapee.core.reward.RewardResult;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.reward.RewardSource;
import dev.vapee.core.reward.RewardStatus;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class OnlineRewardServiceHarness {

    private static final long MINUTE = OnlineRewardConfig.TICKS_PER_MINUTE;
    private static int checks;

    private OnlineRewardServiceHarness() {
    }

    public static void main(String[] args) {
        testLegacyBaselineAndExactInterval();
        testNewPlayerExactlyOnce();
        testCrossSessionAndRestartProgress();
        testRemainderAndMultipleIntervals();
        testDynamicConfig();
        testDisabledTime();
        testStatisticResetAndNegativeSafety();
        testRewardFailuresAndCalculationOverflow();
        testFoundationBatchPersistence();
        testMessagePlaceholdersAndFailureIsolation();
        System.out.println("OnlineRewardServiceHarness passed " + checks + " checks.");
    }

    private static void testLegacyBaselineAndExactInterval() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(0L, OnlineRewardProgress.uninitialized());
        long legacyTicks = 100L * 60L * MINUTE;

        OnlineRewardProcessResult initialized = fixture.service.process(playerId, legacyTicks);
        check(initialized.status() == OnlineRewardProcessStatus.INITIALIZED,
                "legacy player is initialized without a reward");
        check(fixture.processed(playerId) == legacyTicks && fixture.balance(playerId) == 0L,
                "legacy playtime becomes the baseline and is never paid retroactively");
        check(fixture.repository.saveAttempts(playerId) == 0,
                "baseline initialization performs no direct player save");

        OnlineRewardProcessResult beforeInterval = fixture.service.process(
                playerId,
                legacyTicks + 59L * MINUTE
        );
        check(beforeInterval.status() == OnlineRewardProcessStatus.NO_REWARD
                        && fixture.processed(playerId) == legacyTicks,
                "fifty-nine new minutes remain implicit progress");

        OnlineRewardProcessResult exact = fixture.service.process(
                playerId,
                legacyTicks + 60L * MINUTE
        );
        check(exact.status() == OnlineRewardProcessStatus.REWARDED
                        && exact.intervals() == 1L
                        && exact.coinsGranted() == 250L,
                "exactly one interval grants exactly one configured reward");
        check(fixture.balance(playerId) == 250L
                        && fixture.processed(playerId) == legacyTicks + 60L * MINUTE,
                "successful legacy reward updates wallet and processed baseline");
    }

    private static void testNewPlayerExactlyOnce() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(0L, OnlineRewardProgress.uninitialized());
        check(fixture.service.process(playerId, 0L).status() == OnlineRewardProcessStatus.INITIALIZED,
                "new player establishes a zero baseline without reward");
        check(fixture.service.process(playerId, 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.REWARDED,
                "new player receives the first reward after sixty minutes");
        check(fixture.service.process(playerId, 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.NO_REWARD
                        && fixture.service.process(playerId, 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.NO_REWARD,
                "the same interval is not rewarded again on later processing cycles");
        check(fixture.balance(playerId) == 250L,
                "exactly-once normal path leaves one reward in the wallet");
    }

    private static void testCrossSessionAndRestartProgress() {
        Fixture crossSession = new Fixture();
        UUID playerId = crossSession.load(0L, OnlineRewardProgress.initialized(0L));
        check(crossSession.service.process(playerId, 23L * MINUTE).status()
                        == OnlineRewardProcessStatus.NO_REWARD,
                "first session keeps twenty-three minutes as implicit progress");
        crossSession.players.unloadPlayer(playerId);
        crossSession.players.loadPlayer(playerId, "Player");
        check(crossSession.service.process(playerId, 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.REWARDED,
                "thirty-seven minutes in the next session complete the interval");

        Fixture restart = new Fixture();
        long hundredHours = 100L * 60L * MINUTE;
        UUID restartId = restart.load(0L, OnlineRewardProgress.initialized(hundredHours));
        check(restart.service.process(restartId, hundredHours + 40L * MINUTE).status()
                        == OnlineRewardProcessStatus.NO_REWARD,
                "forty pre-restart minutes remain pending");
        restart.players.unloadPlayer(restartId);
        restart.players.loadPlayer(restartId, "Player");
        check(restart.service.process(restartId, hundredHours + 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.REWARDED,
                "twenty post-restart minutes complete persisted progress");
    }

    private static void testRemainderAndMultipleIntervals() {
        Fixture remainder = new Fixture();
        UUID remainderId = remainder.load(0L, OnlineRewardProgress.initialized(0L));
        OnlineRewardProcessResult result = remainder.service.process(remainderId, 75L * MINUTE);
        check(result.status() == OnlineRewardProcessStatus.REWARDED
                        && remainder.processed(remainderId) == 60L * MINUTE,
                "seventy-five minutes reward one interval and retain fifteen minutes");
        check(remainder.service.process(remainderId, 119L * MINUTE).status()
                        == OnlineRewardProcessStatus.NO_REWARD,
                "retained remainder combines with later playtime without premature reward");
        check(remainder.service.process(remainderId, 120L * MINUTE).status()
                        == OnlineRewardProcessStatus.REWARDED,
                "retained remainder eventually completes the next interval");

        Fixture multi = new Fixture();
        UUID multiId = multi.load(0L, OnlineRewardProgress.initialized(0L));
        CountingGranter granter = new CountingGranter(RewardStatus.SUCCESS, 750L);
        OnlineRewardService service = multi.serviceWith(granter);
        OnlineRewardProcessResult multiResult = service.process(multiId, 185L * MINUTE);
        check(multiResult.status() == OnlineRewardProcessStatus.REWARDED
                        && multiResult.intervals() == 3L
                        && multiResult.coinsGranted() == 750L
                        && multiResult.rewardedMinutes() == 180L,
                "three due intervals aggregate into 750 coins and 180 rewarded minutes");
        check(multi.processed(multiId) == 180L * MINUTE,
                "multi-interval reward retains the five-minute remainder");
        check(granter.calls == 1
                        && granter.lastSource == RewardSource.PLAYTIME
                        && granter.lastReason.equals(OnlineRewardService.REWARD_REASON)
                        && granter.lastCoins == 750L,
                "multi-interval catch-up uses exactly one PLAYTIME RewardService grant");
    }

    private static void testDynamicConfig() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(0L, OnlineRewardProgress.initialized(0L));
        check(fixture.service.process(playerId, 40L * MINUTE).status()
                        == OnlineRewardProcessStatus.NO_REWARD,
                "forty minutes are pending under the sixty-minute interval");
        fixture.config.set(config(true, 30L, 500L, true));
        OnlineRewardProcessResult reloaded = fixture.service.process(playerId, 40L * MINUTE);
        check(reloaded.status() == OnlineRewardProcessStatus.REWARDED
                        && reloaded.coinsGranted() == 500L
                        && fixture.processed(playerId) == 30L * MINUTE,
                "reloaded thirty-minute interval and 500-coin value apply on the next check");
        check(fixture.service.process(playerId, 60L * MINUTE).coinsGranted() == 500L,
                "changed coin value applies to later intervals without rewriting earlier grants");
    }

    private static void testDisabledTime() {
        Fixture fixture = new Fixture();
        fixture.config.set(config(false, 60L, 250L, true));
        UUID playerId = fixture.load(0L, OnlineRewardProgress.uninitialized());
        check(fixture.service.process(playerId, 5L * 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.INITIALIZED,
                "uninitialized disabled player receives only a baseline");
        check(fixture.balance(playerId) == 0L
                        && fixture.processed(playerId) == 5L * 60L * MINUTE,
                "five disabled hours produce no coins and synchronize progress");
        fixture.config.set(config(true, 60L, 250L, true));
        check(fixture.service.process(playerId, 5L * 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.NO_REWARD,
                "re-enabling does not pay disabled time retroactively");
        check(fixture.service.process(playerId, 6L * 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.REWARDED,
                "a fresh enabled hour earns the next reward");

        fixture.config.set(config(false, 60L, 250L, true));
        check(fixture.service.process(playerId, 8L * 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.DISABLED
                        && fixture.processed(playerId) == 8L * 60L * MINUTE,
                "initialized disabled processing keeps moving its in-memory baseline");
    }

    private static void testStatisticResetAndNegativeSafety() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(0L, OnlineRewardProgress.initialized(500_000L));
        check(fixture.service.process(playerId, 100_000L).status()
                        == OnlineRewardProcessStatus.REBASED
                        && fixture.processed(playerId) == 100_000L,
                "statistic rollback rebases without reward or negative progress");
        check(fixture.service.process(playerId, -50L).status()
                        == OnlineRewardProcessStatus.REBASED
                        && fixture.processed(playerId) == 0L,
                "negative Minecraft statistic is safely clamped and rebased to zero");
        check(fixture.service.process(playerId, -1L).status()
                        == OnlineRewardProcessStatus.NO_REWARD,
                "repeated negative statistic does not repeatedly rebase or reward");
    }

    private static void testRewardFailuresAndCalculationOverflow() {
        Fixture overflow = new Fixture();
        UUID overflowId = overflow.load(Long.MAX_VALUE, OnlineRewardProgress.initialized(0L));
        OnlineRewardProcessResult balanceOverflow = overflow.service.process(overflowId, 60L * MINUTE);
        check(balanceOverflow.status() == OnlineRewardProcessStatus.REWARD_FAILED
                        && overflow.processed(overflowId) == 0L
                        && overflow.balance(overflowId) == Long.MAX_VALUE,
                "balance overflow leaves coins and processed progress unchanged");

        Fixture unloadedReward = new Fixture();
        UUID unloadedRewardId = unloadedReward.load(0L, OnlineRewardProgress.initialized(0L));
        CountingGranter notLoaded = new CountingGranter(RewardStatus.PLAYER_NOT_LOADED, 0L);
        check(unloadedReward.serviceWith(notLoaded).process(unloadedRewardId, 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.PLAYER_NOT_LOADED
                        && unloadedReward.processed(unloadedRewardId) == 0L,
                "RewardService PLAYER_NOT_LOADED preserves pending progress");

        Fixture missingCorePlayer = new Fixture();
        CountingGranter untouched = new CountingGranter(RewardStatus.SUCCESS, 250L);
        check(missingCorePlayer.serviceWith(untouched).process(UUID.randomUUID(), 60L * MINUTE).status()
                        == OnlineRewardProcessStatus.PLAYER_NOT_LOADED
                        && untouched.calls == 0,
                "missing CorePlayer performs no offline load and no reward call");

        Fixture calculation = new Fixture();
        calculation.config.set(config(true, 1L, Long.MAX_VALUE, true));
        UUID calculationId = calculation.load(0L, OnlineRewardProgress.initialized(0L));
        CountingGranter neverCalled = new CountingGranter(RewardStatus.SUCCESS, 0L);
        check(calculation.serviceWith(neverCalled).process(calculationId, 2L * MINUTE).status()
                        == OnlineRewardProcessStatus.REWARD_FAILED
                        && calculation.processed(calculationId) == 0L
                        && neverCalled.calls == 0,
                "interval-count multiplication overflow logs safely and preserves progress");
    }

    private static void testFoundationBatchPersistence() {
        Fixture fixture = new Fixture();
        UUID playerId = fixture.load(0L, OnlineRewardProgress.initialized(0L));
        OnlineRewardProcessResult result = fixture.service.process(playerId, 60L * MINUTE);
        check(result.status() == OnlineRewardProcessStatus.REWARDED
                        && fixture.balance(playerId) == 250L
                        && fixture.processed(playerId) == 60L * MINUTE,
                "playtime reward immediately updates wallet and processed state");
        check(fixture.repository.saveAttempts(playerId) == 0,
                "OnlineRewardService performs no direct player save");
        fixture.rewards.flushAll();
        check(fixture.repository.saveAttempts(playerId) == 1
                        && fixture.repository.persistedBalance(playerId) == 250L
                        && fixture.repository.persistedProcessed(playerId) == 60L * MINUTE,
                "one Reward Foundation flush persists coins and progress together");
    }

    private static void testMessagePlaceholdersAndFailureIsolation() {
        OnlineRewardProcessResult result = OnlineRewardProcessResult.rewarded(2L, 500L, 120L, 1_750L);
        String format = "<coins>|<minutes>|<intervals>|<balance>";
        String rendered = PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(
                        format,
                        OnlineRewardMessageRenderer.placeholders(result)
                )
        );
        check(rendered.equals("500|120|2|1750"),
                "message placeholders render exact granted values through safe resolvers");

        CapturingHandler handler = new CapturingHandler();
        Logger logger = logger(handler);
        OnlineRewardMessageRenderer failing = new OnlineRewardMessageRenderer(
                (audience, template, resolver) -> {
                    throw new IllegalStateException("synthetic notification failure");
                },
                logger
        );
        check(!failing.notifyReward(Audience.empty(), config(true, 60L, 250L, true), result),
                "notification failure is isolated after a successful reward");
        check(handler.warnings == 1,
                "notification failure is logged exactly once");

        AtomicInteger sends = new AtomicInteger();
        OnlineRewardMessageRenderer disabled = new OnlineRewardMessageRenderer(
                (audience, template, resolver) -> sends.incrementAndGet(),
                logger()
        );
        check(!disabled.notifyReward(Audience.empty(), config(true, 60L, 250L, false), result)
                        && sends.get() == 0,
                "message.enabled false suppresses notification without affecting reward result");
    }

    private static OnlineRewardConfig config(
            boolean enabled,
            long intervalMinutes,
            long coins,
            boolean messageEnabled
    ) {
        return new OnlineRewardConfig(
                enabled,
                intervalMinutes,
                coins,
                messageEnabled,
                OnlineRewardConfig.DEFAULT_MESSAGE_FORMAT
        );
    }

    private static CorePlayer player(
            UUID playerId,
            long balance,
            OnlineRewardProgress progress
    ) {
        Instant now = Instant.now();
        return new CorePlayer(
                playerId,
                "Player",
                now,
                now,
                PlayerSettings.defaults(),
                CoinWallet.of(balance),
                PlayerSocial.empty(),
                progress
        );
    }

    private static Logger logger() {
        return logger(null);
    }

    private static Logger logger(Handler handler) {
        Logger logger = Logger.getLogger("OnlineRewardServiceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        if (handler != null) {
            logger.addHandler(handler);
        }
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture {

        private final AtomicReference<OnlineRewardConfig> config = new AtomicReference<>(
                OnlineRewardConfig.defaults()
        );
        private final MemoryRepository repository = new MemoryRepository();
        private final PlayerService players = new PlayerService(repository, logger());
        private final EconomyService economy = new EconomyService(players);
        private final RewardService rewards = new RewardService(economy, players, logger());
        private final OnlineRewardService service = new OnlineRewardService(
                config::get,
                players,
                rewards::grantCoins,
                logger()
        );

        private UUID load(long balance, OnlineRewardProgress progress) {
            UUID playerId = UUID.randomUUID();
            repository.seed(player(playerId, balance, progress));
            players.loadPlayer(playerId, "Player");
            repository.resetCounters();
            return playerId;
        }

        private OnlineRewardService serviceWith(OnlineRewardService.RewardGranter granter) {
            return new OnlineRewardService(config::get, players, granter, logger());
        }

        private long processed(UUID playerId) {
            return players.getPlayer(playerId).orElseThrow()
                    .getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow();
        }

        private long balance(UUID playerId) {
            return economy.getCoins(playerId).orElseThrow();
        }
    }

    private static final class CountingGranter implements OnlineRewardService.RewardGranter {

        private final RewardStatus status;
        private final long resultingBalance;
        private int calls;
        private long lastCoins;
        private RewardSource lastSource;
        private String lastReason;

        private CountingGranter(RewardStatus status, long resultingBalance) {
            this.status = status;
            this.resultingBalance = resultingBalance;
        }

        @Override
        public RewardResult grantCoins(UUID playerId, long coins, RewardSource source, String reason) {
            calls++;
            lastCoins = coins;
            lastSource = source;
            lastReason = reason;
            if (status == RewardStatus.SUCCESS) {
                return new RewardResult(status, coins, OptionalLong.of(resultingBalance));
            }
            return new RewardResult(status, 0L, OptionalLong.empty());
        }
    }

    private static final class MemoryRepository implements PlayerRepository {

        private final Map<UUID, CorePlayer> players = new HashMap<>();
        private final Map<UUID, Integer> saveAttempts = new HashMap<>();
        private final Map<UUID, Long> persistedBalances = new HashMap<>();
        private final Map<UUID, OptionalLong> persistedProcessed = new HashMap<>();

        private void seed(CorePlayer player) {
            players.put(player.getUniqueId(), player);
            capture(player);
        }

        private void resetCounters() {
            saveAttempts.clear();
        }

        private int saveAttempts(UUID playerId) {
            return saveAttempts.getOrDefault(playerId, 0);
        }

        private long persistedBalance(UUID playerId) {
            return persistedBalances.get(playerId);
        }

        private long persistedProcessed(UUID playerId) {
            return persistedProcessed.get(playerId).orElseThrow();
        }

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(players.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            saveAttempts.merge(player.getUniqueId(), 1, Integer::sum);
            players.put(player.getUniqueId(), player);
            capture(player);
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return players.containsKey(uniqueId);
        }

        private void capture(CorePlayer player) {
            persistedBalances.put(player.getUniqueId(), player.getWallet().getCoins());
            persistedProcessed.put(
                    player.getUniqueId(),
                    player.getOnlineRewardProgress().getProcessedPlaytimeTicks()
            );
        }
    }

    private static final class CapturingHandler extends Handler {

        private int warnings;

        private CapturingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings++;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}

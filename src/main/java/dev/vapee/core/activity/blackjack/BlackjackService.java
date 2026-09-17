package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.ActivitySession;
import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.blackjack.table.BlackjackSeatService;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import dev.vapee.core.activity.player.ActivityParticipant;
import dev.vapee.core.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BlackjackService {

    public static final long TURN_TIMEOUT_TICKS = 20L * 20L;
    public static final long RESULT_VIEW_TICKS = 3L * 20L;

    private final ActivityService activityService;
    private final TableAccess tableAccess;
    private final SeatAccess seatAccess;
    private final Function<UUID, Player> onlinePlayerLookup;
    private final BiConsumer<Player, String> messageSender;
    private final TaskScheduler taskScheduler;
    private final Supplier<BlackjackShoe> shoeSupplier;
    private final Logger logger;

    private Consumer<BlackjackSession> tableRefresher = ignored -> {
    };

    public BlackjackService(
            JavaPlugin plugin,
            ActivityService activityService,
            BlackjackTableService tableService,
            BlackjackSeatService seatService,
            MessageService messageService
    ) {
        this(
                activityService,
                tableAccess(tableService),
                seatAccess(seatService),
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                Objects.requireNonNull(messageService, "messageService")::send,
                (action, delayTicks) -> plugin.getServer().getScheduler().runTaskLater(
                        plugin,
                        action,
                        delayTicks
                ),
                () -> BlackjackShoe.sixDecks(new Random()),
                plugin.getLogger()
        );
    }

    BlackjackService(
            ActivityService activityService,
            TableAccess tableAccess,
            SeatAccess seatAccess,
            Function<UUID, Player> onlinePlayerLookup,
            BiConsumer<Player, String> messageSender,
            TaskScheduler taskScheduler,
            Supplier<BlackjackShoe> shoeSupplier,
            Logger logger
    ) {
        this.activityService = Objects.requireNonNull(activityService, "activityService");
        this.tableAccess = Objects.requireNonNull(tableAccess, "tableAccess");
        this.seatAccess = Objects.requireNonNull(seatAccess, "seatAccess");
        this.onlinePlayerLookup = Objects.requireNonNull(onlinePlayerLookup, "onlinePlayerLookup");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.shoeSupplier = Objects.requireNonNull(shoeSupplier, "shoeSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void setTableRefresher(Consumer<BlackjackSession> tableRefresher) {
        this.tableRefresher = Objects.requireNonNull(tableRefresher, "tableRefresher");
    }

    public Optional<BlackjackSession> joinTable(Player player, String tableId) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        String validatedTableId = Objects.requireNonNull(tableId, "tableId");
        Optional<ActivitySession> currentActivity = activityService.getSessionForPlayer(validatedPlayer.getUniqueId());
        if (currentActivity.isPresent()) {
            ActivitySession currentSession = currentActivity.get();
            if (currentSession instanceof BlackjackSession blackjackSession) {
                if (blackjackSession.getVenue().id().equals(validatedTableId)) {
                    return Optional.of(blackjackSession);
                }
                send(validatedPlayer, "<red>You are already seated at another blackjack table.</red>");
                return Optional.empty();
            }
            send(validatedPlayer, "<red>You are already participating in another activity.</red>");
            return Optional.empty();
        }

        Optional<BlackjackTableDefinition> optionalDefinition = tableAccess.getDefinition(validatedTableId);
        Optional<BlackjackSession> optionalSession = tableAccess.getSession(validatedTableId);
        if (optionalDefinition.isEmpty() || optionalSession.isEmpty()) {
            send(validatedPlayer, "<red>This blackjack table is not available.</red>");
            return Optional.empty();
        }
        BlackjackTableDefinition definition = optionalDefinition.get();
        BlackjackSession session = optionalSession.get();
        if (session.getState() != ActivityState.AVAILABLE) {
            send(validatedPlayer, "<red>This blackjack round is already in progress.</red>");
            return Optional.empty();
        }
        if (session.getParticipantCount() >= definition.capacity()) {
            send(validatedPlayer, "<red>This blackjack table is full.</red>");
            return Optional.empty();
        }
        if (!seatAccess.reserveLowestFreeSeat(definition, validatedPlayer.getUniqueId())) {
            send(validatedPlayer, "<red>This blackjack table is full.</red>");
            return Optional.empty();
        }
        ActivityResult joinResult = activityService.joinSession(validatedPlayer, session.getSessionId());
        if (joinResult != ActivityResult.SUCCESS) {
            seatAccess.releaseSeat(validatedPlayer.getUniqueId());
            sendJoinFailure(validatedPlayer, joinResult);
            return Optional.empty();
        }

        try {
            seatAccess.mountReservedPlayer(validatedPlayer);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not seat player " + validatedPlayer.getUniqueId()
                    + " at blackjack table '" + validatedTableId + "'.", exception);
            activityService.leaveCurrentSession(validatedPlayer, ActivityLeaveReason.ERROR);
            seatAccess.releaseSeat(validatedPlayer.getUniqueId());
            send(validatedPlayer, "<red>You could not be seated at this blackjack table.</red>");
            return Optional.empty();
        }
        refresh(session);
        return Optional.of(session);
    }

    public Optional<BlackjackSession> getSessionForPlayer(Player player) {
        return getSessionForPlayer(Objects.requireNonNull(player, "player").getUniqueId());
    }

    public Optional<BlackjackSession> getSessionForPlayer(UUID playerId) {
        Optional<ActivitySession> session = activityService.getSessionForPlayer(
                Objects.requireNonNull(playerId, "playerId")
        );
        if (session.isEmpty() || !BlackjackActivityType.KEY.equals(session.get().getActivityKey())) {
            return Optional.empty();
        }
        if (session.get() instanceof BlackjackSession blackjackSession) {
            return Optional.of(blackjackSession);
        }
        throw new IllegalStateException("The blackjack activity type produced an incompatible session");
    }

    public ActivityResult startRound(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Optional<BlackjackSession> optionalSession = getSessionForPlayer(validatedPlayer);
        if (optionalSession.isEmpty()) {
            return ActivityResult.NOT_PARTICIPANT;
        }
        BlackjackSession session = optionalSession.get();
        if (session.getState() != ActivityState.AVAILABLE) {
            return ActivityResult.INVALID_STATE;
        }
        ActivityResult result = activityService.activateSession(session.getSessionId());
        if (result != ActivityResult.SUCCESS) {
            send(validatedPlayer, "<red>The blackjack round could not be started.</red>");
        }
        return result;
    }

    public ActivityResult hit(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Optional<BlackjackSession> optionalSession = actionableSession(validatedPlayer);
        if (optionalSession.isEmpty()) {
            return actionFailure(validatedPlayer);
        }
        BlackjackSession session = optionalSession.get();
        session.cancelTurnTimeout();
        BlackjackPlayerRound playerRound = session.requirePlayerRound(validatedPlayer.getUniqueId());
        playerRound.getHand().add(session.requireShoe().draw());
        if (playerRound.getHand().getValue() >= 21) {
            playerRound.finish();
            advanceTurn(session);
        } else {
            refresh(session);
            scheduleTurnTimeout(session, validatedPlayer.getUniqueId(), false);
        }
        return ActivityResult.SUCCESS;
    }

    public ActivityResult stand(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Optional<BlackjackSession> optionalSession = actionableSession(validatedPlayer);
        if (optionalSession.isEmpty()) {
            return actionFailure(validatedPlayer);
        }
        BlackjackSession session = optionalSession.get();
        session.cancelTurnTimeout();
        session.requirePlayerRound(validatedPlayer.getUniqueId()).finish();
        advanceTurn(session);
        return ActivityResult.SUCCESS;
    }

    public ActivityResult leave(Player player) {
        return activityService.leaveCurrentSession(
                Objects.requireNonNull(player, "player"),
                ActivityLeaveReason.VOLUNTARY
        );
    }

    public int getTableCapacity(BlackjackSession session) {
        return tableAccess.getDefinition(Objects.requireNonNull(session, "session").getVenue().id())
                .map(BlackjackTableDefinition::capacity)
                .orElse(BlackjackActivityType.MAX_PARTICIPANTS);
    }

    public Optional<Integer> getSeatNumber(UUID playerId) {
        return seatAccess.getSeatNumber(playerId);
    }

    public static boolean shouldDealerHit(BlackjackHand dealerHand) {
        return Objects.requireNonNull(dealerHand, "dealerHand").getValue() < 17;
    }

    public String getPlayerName(UUID playerId) {
        Player player = onlinePlayerLookup.apply(Objects.requireNonNull(playerId, "playerId"));
        if (player != null) {
            return player.getName();
        }
        String value = playerId.toString();
        return value.substring(0, 8);
    }

    public void shutdown() {
        tableRefresher = ignored -> {
        };
    }

    void onParticipantJoined(BlackjackSession session, ActivityParticipant participant) {
        refresh(session);
    }

    void onParticipantLeft(
            BlackjackSession session,
            ActivityParticipant participant,
            ActivityLeaveReason reason
    ) {
        UUID playerId = participant.uniqueId();
        seatAccess.releaseSeat(playerId);
        boolean wasCurrentTurn = session.getCurrentTurnPlayer().filter(playerId::equals).isPresent();
        session.removePlayerRound(playerId);

        if (reason == ActivityLeaveReason.PLUGIN_DISABLE
                || reason == ActivityLeaveReason.SESSION_CLOSED
                || reason == ActivityLeaveReason.ERROR) {
            if (wasCurrentTurn) {
                session.cancelTurnTimeout();
                session.clearCurrentTurn();
            }
            refresh(session);
            return;
        }

        if (session.getState() == ActivityState.AVAILABLE) {
            refresh(session);
            return;
        }
        if (session.getState() != ActivityState.ACTIVE) {
            refresh(session);
            return;
        }
        if (session.getPlayerRoundsInOrder().isEmpty()) {
            session.cancelTurnTimeout();
            activityService.resetSession(session.getSessionId());
            return;
        }
        if (wasCurrentTurn && session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS) {
            session.cancelTurnTimeout();
            session.clearCurrentTurn();
            advanceTurn(session);
            return;
        }
        refresh(session);
    }

    void onActivated(BlackjackSession session) {
        session.beginRound(shoeSupplier.get());
        List<BlackjackPlayerRound> rounds = session.getPlayerRoundsInOrder();
        if (rounds.isEmpty()) {
            throw new IllegalStateException("A blackjack round cannot start without participants");
        }

        for (BlackjackPlayerRound playerRound : rounds) {
            playerRound.getHand().add(session.requireShoe().draw());
        }
        session.getDealerHand().add(session.requireShoe().draw());
        for (BlackjackPlayerRound playerRound : rounds) {
            playerRound.getHand().add(session.requireShoe().draw());
            if (playerRound.getHand().getValue() >= 21) {
                playerRound.finish();
            }
        }
        session.getDealerHand().add(session.requireShoe().draw());

        refresh(session);
        if (session.getDealerHand().isBlackjack()) {
            runDealerTurn(session);
        } else {
            advanceTurn(session);
        }
    }

    void onReset(BlackjackSession session) {
        refresh(session);
    }

    void onClosed(BlackjackSession session) {
        refresh(session);
    }

    private Optional<BlackjackSession> actionableSession(Player player) {
        Optional<BlackjackSession> optionalSession = getSessionForPlayer(player);
        if (optionalSession.isEmpty()) {
            return Optional.empty();
        }
        BlackjackSession session = optionalSession.get();
        if (session.getState() != ActivityState.ACTIVE
                || session.getRoundPhase() != BlackjackRoundPhase.PLAYER_TURNS
                || session.getCurrentTurnPlayer().filter(player.getUniqueId()::equals).isEmpty()) {
            return Optional.empty();
        }
        return optionalSession;
    }

    private ActivityResult actionFailure(Player player) {
        if (getSessionForPlayer(player).isEmpty()) {
            return ActivityResult.NOT_PARTICIPANT;
        }
        send(player, "<yellow>It is not your turn.</yellow>");
        return ActivityResult.INVALID_STATE;
    }

    private void advanceTurn(BlackjackSession session) {
        Optional<BlackjackPlayerRound> nextRound = session.getPlayerRoundsInOrder().stream()
                .filter(playerRound -> !playerRound.isFinished())
                .findFirst();
        if (nextRound.isEmpty()) {
            runDealerTurn(session);
            return;
        }

        UUID nextPlayerId = nextRound.get().getPlayerId();
        session.setRoundPhase(BlackjackRoundPhase.PLAYER_TURNS);
        refresh(session);
        scheduleTurnTimeout(session, nextPlayerId, true);
    }

    private void scheduleTurnTimeout(BlackjackSession session, UUID expectedPlayerId, boolean announce) {
        long roundGeneration = session.getRoundGeneration();
        long turnGeneration = session.beginTurn(expectedPlayerId);
        BukkitTask[] taskReference = new BukkitTask[1];
        BukkitTask task = taskScheduler.schedule(() -> {
            BukkitTask executingTask = taskReference[0];
            if (executingTask == null || !session.consumeTurnTimeoutTask(executingTask)) {
                return;
            }
            if (session.getState() != ActivityState.ACTIVE
                    || session.getRoundPhase() != BlackjackRoundPhase.PLAYER_TURNS
                    || session.getRoundGeneration() != roundGeneration
                    || session.getTurnGeneration() != turnGeneration
                    || session.getCurrentTurnPlayer().filter(expectedPlayerId::equals).isEmpty()) {
                return;
            }

            Player player = onlinePlayerLookup.apply(expectedPlayerId);
            if (player != null) {
                send(player, "<yellow>Your turn timed out. You automatically stood.</yellow>");
            }
            session.requirePlayerRound(expectedPlayerId).finish();
            advanceTurn(session);
        }, TURN_TIMEOUT_TICKS);
        taskReference[0] = task;
        session.setTurnTimeoutTask(task);
        if (announce) {
            Player player = onlinePlayerLookup.apply(expectedPlayerId);
            if (player != null) {
                send(player, "<aqua>It is your turn. You have 20 seconds.</aqua>");
            }
        }
    }

    private void runDealerTurn(BlackjackSession session) {
        session.cancelTurnTimeout();
        session.clearCurrentTurn();
        session.setRoundPhase(BlackjackRoundPhase.DEALER_TURN);
        refresh(session);
        while (shouldDealerHit(session.getDealerHand())) {
            session.getDealerHand().add(session.requireShoe().draw());
        }
        settleRound(session);
    }

    private void settleRound(BlackjackSession session) {
        session.setRoundPhase(BlackjackRoundPhase.SETTLED);
        for (BlackjackPlayerRound playerRound : session.getPlayerRoundsInOrder()) {
            BlackjackOutcome outcome = BlackjackOutcome.determine(
                    playerRound.getHand(),
                    session.getDealerHand()
            );
            playerRound.settle(outcome);
            Player player = onlinePlayerLookup.apply(playerRound.getPlayerId());
            if (player != null) {
                send(player, outcomeMessage(outcome));
            }
        }
        refresh(session);
        scheduleResultReset(session);
    }

    private void scheduleResultReset(BlackjackSession session) {
        long roundGeneration = session.getRoundGeneration();
        BukkitTask[] taskReference = new BukkitTask[1];
        BukkitTask task = taskScheduler.schedule(() -> {
            BukkitTask executingTask = taskReference[0];
            if (executingTask == null || !session.consumeResultResetTask(executingTask)) {
                return;
            }
            if (session.getState() != ActivityState.ACTIVE
                    || session.getRoundPhase() != BlackjackRoundPhase.SETTLED
                    || session.getRoundGeneration() != roundGeneration) {
                return;
            }
            ActivityResult result = activityService.resetSession(session.getSessionId());
            if (result != ActivityResult.SUCCESS) {
                logger.warning("Could not reset blackjack session " + session.getSessionId() + ": " + result);
            }
        }, RESULT_VIEW_TICKS);
        taskReference[0] = task;
        session.setResultResetTask(task);
    }

    private void refresh(BlackjackSession session) {
        try {
            tableRefresher.accept(session);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not refresh blackjack table " + session.getSessionId() + ".", exception);
        }
    }

    private void sendJoinFailure(Player player, ActivityResult result) {
        String message = switch (result) {
            case PLAYER_ALREADY_IN_ACTIVITY -> "<red>You are already participating in another activity.</red>";
            case SESSION_NOT_AVAILABLE, INVALID_STATE -> "<red>This blackjack round is already in progress.</red>";
            case SESSION_FULL -> "<red>This blackjack table is full.</red>";
            default -> "<red>This blackjack table is currently unavailable.</red>";
        };
        send(player, message);
    }

    private void send(Player player, String message) {
        messageSender.accept(Objects.requireNonNull(player, "player"), Objects.requireNonNull(message, "message"));
    }

    private String outcomeMessage(BlackjackOutcome outcome) {
        return switch (outcome) {
            case BLACKJACK -> "<gold>Blackjack!</gold>";
            case WIN -> "<green>You win.</green>";
            case PUSH -> "<yellow>Push.</yellow>";
            case LOSS -> "<red>Dealer wins.</red>";
            case BUST -> "<red>Bust.</red>";
        };
    }

    private static TableAccess tableAccess(BlackjackTableService tableService) {
        BlackjackTableService service = Objects.requireNonNull(tableService, "tableService");
        return new TableAccess() {
            @Override
            public Optional<BlackjackTableDefinition> getDefinition(String tableId) {
                return service.getDefinition(tableId);
            }

            @Override
            public Optional<BlackjackSession> getSession(String tableId) {
                return service.getSession(tableId);
            }
        };
    }

    private static SeatAccess seatAccess(BlackjackSeatService seatService) {
        BlackjackSeatService service = Objects.requireNonNull(seatService, "seatService");
        return new SeatAccess() {
            @Override
            public boolean reserveLowestFreeSeat(BlackjackTableDefinition definition, UUID playerId) {
                return service.reserveLowestFreeSeat(definition, playerId).isPresent();
            }

            @Override
            public void mountReservedPlayer(Player player) {
                service.mountReservedPlayer(player);
            }

            @Override
            public void releaseSeat(UUID playerId) {
                service.releaseSeat(playerId);
            }

            @Override
            public Optional<Integer> getSeatNumber(UUID playerId) {
                return service.getSeatNumber(playerId);
            }
        };
    }

    interface TableAccess {
        Optional<BlackjackTableDefinition> getDefinition(String tableId);

        Optional<BlackjackSession> getSession(String tableId);
    }

    interface SeatAccess {
        boolean reserveLowestFreeSeat(BlackjackTableDefinition definition, UUID playerId);

        void mountReservedPlayer(Player player);

        void releaseSeat(UUID playerId);

        Optional<Integer> getSeatNumber(UUID playerId);
    }

    @FunctionalInterface
    interface TaskScheduler {
        BukkitTask schedule(Runnable action, long delayTicks);
    }
}

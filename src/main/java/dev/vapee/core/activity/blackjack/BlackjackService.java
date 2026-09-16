package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.ActivitySession;
import dev.vapee.core.activity.ActivitySessionCreationResult;
import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.location.ActivityArea;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.activity.location.ActivityVenue;
import dev.vapee.core.activity.player.ActivityParticipant;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.message.MessageService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

public final class BlackjackService {

    public static final long TURN_TIMEOUT_TICKS = 20L * 20L;
    public static final long RESULT_VIEW_TICKS = 3L * 20L;

    private final ActivityService activityService;
    private final Supplier<Optional<Location>> lobbySpawnSupplier;
    private final Function<UUID, Player> onlinePlayerLookup;
    private final BiConsumer<Player, String> messageSender;
    private final TaskScheduler taskScheduler;
    private final Supplier<BlackjackShoe> shoeSupplier;
    private final Logger logger;

    private Consumer<BlackjackSession> tableRefresher = ignored -> {
    };
    private int nextTableNumber = 1;

    public BlackjackService(
            JavaPlugin plugin,
            ActivityService activityService,
            LobbyService lobbyService,
            MessageService messageService
    ) {
        this(
                activityService,
                Objects.requireNonNull(lobbyService, "lobbyService")::getSpawnLocation,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                Objects.requireNonNull(messageService, "messageService")::send,
                (action, delayTicks) -> plugin.getServer().getScheduler().runTaskLater(
                        plugin,
                        action,
                        delayTicks
                ),
                () -> BlackjackShoe.sixDecks(RandomGenerator.getDefault()),
                plugin.getLogger()
        );
    }

    BlackjackService(
            ActivityService activityService,
            Supplier<Optional<Location>> lobbySpawnSupplier,
            Function<UUID, Player> onlinePlayerLookup,
            BiConsumer<Player, String> messageSender,
            TaskScheduler taskScheduler,
            Supplier<BlackjackShoe> shoeSupplier,
            Logger logger
    ) {
        this.activityService = Objects.requireNonNull(activityService, "activityService");
        this.lobbySpawnSupplier = Objects.requireNonNull(lobbySpawnSupplier, "lobbySpawnSupplier");
        this.onlinePlayerLookup = Objects.requireNonNull(onlinePlayerLookup, "onlinePlayerLookup");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.shoeSupplier = Objects.requireNonNull(shoeSupplier, "shoeSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void setTableRefresher(Consumer<BlackjackSession> tableRefresher) {
        this.tableRefresher = Objects.requireNonNull(tableRefresher, "tableRefresher");
    }

    public Optional<BlackjackSession> launchSolo(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Optional<BlackjackSession> existing = getSessionForPlayer(validatedPlayer);
        if (existing.isPresent()) {
            return existing;
        }
        if (activityService.isParticipating(validatedPlayer.getUniqueId())) {
            send(validatedPlayer, "<red>You are already participating in another activity.</red>");
            return Optional.empty();
        }

        Optional<Location> optionalSpawn = lobbySpawnSupplier.get();
        if (optionalSpawn.isEmpty()) {
            sendUnavailable(validatedPlayer);
            return Optional.empty();
        }
        Location spawn = optionalSpawn.get();
        cleanupForeignIdleTables(requireWorld(spawn).getName());
        Optional<BlackjackSession> optionalSession = findUnclaimedTable(spawn)
                .or(() -> createVirtualTable(spawn));
        if (optionalSession.isEmpty()) {
            sendUnavailable(validatedPlayer);
            return Optional.empty();
        }

        BlackjackSession session = optionalSession.get();
        session.claim(BlackjackMode.SOLO, validatedPlayer.getUniqueId());
        ActivityResult joinResult = activityService.joinSession(validatedPlayer, session.getSessionId());
        if (joinResult != ActivityResult.SUCCESS) {
            if (session.getParticipantCount() == 0) {
                session.release();
            }
            sendJoinFailure(validatedPlayer, joinResult);
            return Optional.empty();
        }
        refresh(session);
        return Optional.of(session);
    }

    public Optional<BlackjackSession> launchPublic(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Optional<BlackjackSession> existing = getSessionForPlayer(validatedPlayer);
        if (existing.isPresent()) {
            return existing;
        }
        if (activityService.isParticipating(validatedPlayer.getUniqueId())) {
            send(validatedPlayer, "<red>You are already participating in another activity.</red>");
            return Optional.empty();
        }

        Optional<Location> optionalSpawn = lobbySpawnSupplier.get();
        if (optionalSpawn.isEmpty()) {
            sendUnavailable(validatedPlayer);
            return Optional.empty();
        }
        Location spawn = optionalSpawn.get();
        String lobbyWorldName = requireWorld(spawn).getName();
        cleanupForeignIdleTables(lobbyWorldName);
        Optional<BlackjackSession> optionalSession = findAvailablePublicTable(lobbyWorldName);
        if (optionalSession.isEmpty()) {
            optionalSession = findUnclaimedTable(spawn).or(() -> createVirtualTable(spawn));
            optionalSession.ifPresent(session -> session.claim(BlackjackMode.PUBLIC, null));
        }
        if (optionalSession.isEmpty()) {
            sendUnavailable(validatedPlayer);
            return Optional.empty();
        }

        BlackjackSession session = optionalSession.get();
        ActivityResult joinResult = activityService.joinSession(validatedPlayer, session.getSessionId());
        if (joinResult != ActivityResult.SUCCESS) {
            if (session.getParticipantCount() == 0) {
                session.release();
            }
            sendJoinFailure(validatedPlayer, joinResult);
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

    public boolean canOpenModeMenu(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!activityService.isParticipating(validatedPlayer.getUniqueId())) {
            return true;
        }
        if (getSessionForPlayer(validatedPlayer).isPresent()) {
            return false;
        }
        send(validatedPlayer, "<red>You are already participating in another activity.</red>");
        return false;
    }

    public ActivityResult startRound(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Optional<BlackjackSession> optionalSession = getSessionForPlayer(validatedPlayer);
        if (optionalSession.isEmpty()) {
            return ActivityResult.NOT_PARTICIPANT;
        }
        BlackjackSession session = optionalSession.get();
        if (session.getMode() == BlackjackMode.UNCLAIMED
                || session.getState() != ActivityState.AVAILABLE) {
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
        for (ActivitySession session : new ArrayList<>(activityService.getSessions(BlackjackActivityType.KEY))) {
            activityService.closeSession(session.getSessionId(), ActivityLeaveReason.PLUGIN_DISABLE);
        }
        for (ActivityVenue venue : new ArrayList<>(activityService.getVenues(BlackjackActivityType.KEY))) {
            ActivityResult result = activityService.unregisterVenue(BlackjackActivityType.KEY, venue.id());
            if (result != ActivityResult.SUCCESS && result != ActivityResult.VENUE_NOT_FOUND) {
                logger.warning("Could not unregister blackjack venue '" + venue.id() + "': " + result);
            }
        }
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
            if (session.getParticipantCount() == 0 && session.getMode() != BlackjackMode.UNCLAIMED) {
                session.release();
            }
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
        if (session.getParticipantCount() == 0 && session.getMode() != BlackjackMode.UNCLAIMED) {
            session.release();
        }
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

    private Optional<BlackjackSession> findAvailablePublicTable(String worldName) {
        return blackjackSessions().stream()
                .filter(session -> session.getMode() == BlackjackMode.PUBLIC)
                .filter(session -> session.getState() == ActivityState.AVAILABLE)
                .filter(session -> session.getParticipantCount() < BlackjackActivityType.MAX_PARTICIPANTS)
                .filter(session -> session.getVenue().area().worldName().equals(worldName))
                .findFirst();
    }

    private Optional<BlackjackSession> findUnclaimedTable(Location spawn) {
        String worldName = requireWorld(spawn).getName();
        return blackjackSessions().stream()
                .filter(session -> session.getMode() == BlackjackMode.UNCLAIMED)
                .filter(session -> session.getState() == ActivityState.AVAILABLE)
                .filter(session -> session.getParticipantCount() == 0)
                .filter(session -> session.getVenue().area().worldName().equals(worldName))
                .findFirst();
    }

    private Optional<BlackjackSession> createVirtualTable(Location spawn) {
        World world = requireWorld(spawn);
        String venueId;
        do {
            venueId = "table-" + nextTableNumber++;
        } while (activityService.getVenue(BlackjackActivityType.KEY, venueId).isPresent());

        ActivityPosition anchor = new ActivityPosition(
                world.getName(),
                spawn.getX(),
                spawn.getY(),
                spawn.getZ(),
                spawn.getYaw(),
                spawn.getPitch()
        );
        ActivityVenue venue = new ActivityVenue(
                venueId,
                BlackjackActivityType.KEY,
                new ActivityArea(
                        world.getName(),
                        spawn.getX() - 8.0D,
                        spawn.getY() - 4.0D,
                        spawn.getZ() - 8.0D,
                        spawn.getX() + 8.0D,
                        spawn.getY() + 4.0D,
                        spawn.getZ() + 8.0D
                ),
                anchor
        );
        ActivityResult venueResult = activityService.registerVenue(venue);
        if (venueResult != ActivityResult.SUCCESS) {
            logger.warning("Could not register virtual blackjack venue '" + venueId + "': " + venueResult);
            return Optional.empty();
        }

        ActivitySessionCreationResult creationResult = activityService.createSession(
                BlackjackActivityType.KEY,
                venueId
        );
        if (!creationResult.isSuccess()) {
            activityService.unregisterVenue(BlackjackActivityType.KEY, venueId);
            logger.warning("Could not create virtual blackjack session for '" + venueId
                    + "': " + creationResult.result());
            return Optional.empty();
        }
        ActivitySession createdSession = creationResult.session().orElseThrow();
        if (!(createdSession instanceof BlackjackSession blackjackSession)) {
            activityService.closeSession(createdSession.getSessionId(), ActivityLeaveReason.ERROR);
            activityService.unregisterVenue(BlackjackActivityType.KEY, venueId);
            throw new IllegalStateException("The blackjack activity type produced an incompatible session");
        }
        return Optional.of(blackjackSession);
    }

    private void cleanupForeignIdleTables(String currentWorldName) {
        for (BlackjackSession session : new ArrayList<>(blackjackSessions())) {
            if (session.getState() != ActivityState.AVAILABLE
                    || session.getParticipantCount() != 0
                    || session.getVenue().area().worldName().equals(currentWorldName)) {
                continue;
            }
            String venueId = session.getVenue().id();
            activityService.closeSession(session.getSessionId(), ActivityLeaveReason.SESSION_CLOSED);
            ActivityResult result = activityService.unregisterVenue(BlackjackActivityType.KEY, venueId);
            if (result != ActivityResult.SUCCESS && result != ActivityResult.VENUE_NOT_FOUND) {
                logger.warning("Could not remove stale blackjack venue '" + venueId + "': " + result);
            }
        }
    }

    private List<BlackjackSession> blackjackSessions() {
        List<BlackjackSession> result = new ArrayList<>();
        for (ActivitySession session : activityService.getSessions(BlackjackActivityType.KEY)) {
            if (!(session instanceof BlackjackSession blackjackSession)) {
                throw new IllegalStateException("The blackjack activity type produced an incompatible session");
            }
            result.add(blackjackSession);
        }
        return List.copyOf(result);
    }

    private void refresh(BlackjackSession session) {
        try {
            tableRefresher.accept(session);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not refresh blackjack table " + session.getSessionId() + ".", exception);
        }
    }

    private void sendJoinFailure(Player player, ActivityResult result) {
        if (result == ActivityResult.PLAYER_ALREADY_IN_ACTIVITY) {
            send(player, "<red>You are already participating in another activity.</red>");
        } else {
            sendUnavailable(player);
        }
    }

    private void sendUnavailable(Player player) {
        send(player, "<red>Blackjack is currently unavailable.</red>");
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

    private static World requireWorld(Location location) {
        return Objects.requireNonNull(Objects.requireNonNull(location, "location").getWorld(), "location world");
    }

    @FunctionalInterface
    interface TaskScheduler {
        BukkitTask schedule(Runnable action, long delayTicks);
    }
}

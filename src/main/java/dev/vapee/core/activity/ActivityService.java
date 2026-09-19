package dev.vapee.core.activity;

import dev.vapee.core.activity.location.ActivityVenue;
import dev.vapee.core.activity.player.ActivityParticipant;
import dev.vapee.core.player.PlayerService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class ActivityService {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_-]+");

    private final PlayerService playerService;
    private final Logger logger;
    private final BooleanSupplier primaryThreadCheck;
    private final Predicate<String> loadedWorldCheck;
    private final Map<String, RegisteredActivityType> activityTypes = new LinkedHashMap<>();
    private final Map<VenueKey, ActivityVenue> venues = new LinkedHashMap<>();
    private final Map<UUID, ActivitySession> sessions = new LinkedHashMap<>();
    private final Map<VenueKey, UUID> venueReservations = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerMemberships = new LinkedHashMap<>();
    private final Set<UUID> closingSessions = new HashSet<>();

    public ActivityService(Server server, PlayerService playerService, Logger logger) {
        this(
                playerService,
                logger,
                Bukkit::isPrimaryThread,
                worldName -> Objects.requireNonNull(server, "server").getWorld(worldName) != null
        );
    }

    ActivityService(
            PlayerService playerService,
            Logger logger,
            BooleanSupplier primaryThreadCheck,
            Predicate<String> loadedWorldCheck
    ) {
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.primaryThreadCheck = Objects.requireNonNull(primaryThreadCheck, "primaryThreadCheck");
        this.loadedWorldCheck = Objects.requireNonNull(loadedWorldCheck, "loadedWorldCheck");
    }

    public ActivityResult registerActivityType(ActivityType type) {
        requirePrimaryThread();
        Objects.requireNonNull(type, "type");

        RegisteredActivityType registeredType;
        try {
            String key = type.getKey();
            int minParticipants = type.getMinParticipants();
            int maxParticipants = type.getMaxParticipants();
            if (!isValidKey(key) || minParticipants < 1 || maxParticipants < minParticipants) {
                return ActivityResult.INVALID_ACTIVITY_TYPE;
            }
            registeredType = new RegisteredActivityType(type, key, minParticipants, maxParticipants);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not validate an activity type during registration.", exception);
            return ActivityResult.INVALID_ACTIVITY_TYPE;
        }

        String lookupKey = lookupKey(registeredType.key());
        if (activityTypes.containsKey(lookupKey)) {
            return ActivityResult.ACTIVITY_TYPE_ALREADY_REGISTERED;
        }

        activityTypes.put(lookupKey, registeredType);
        return ActivityResult.SUCCESS;
    }

    public ActivityResult unregisterActivityType(String activityKey) {
        requirePrimaryThread();
        String lookupKey = lookupKey(activityKey);
        RegisteredActivityType registeredType = activityTypes.get(lookupKey);
        if (registeredType == null) {
            return ActivityResult.ACTIVITY_TYPE_NOT_FOUND;
        }

        boolean hasVenue = venues.keySet().stream().anyMatch(key -> key.activityKey().equals(registeredType.key()));
        boolean hasSession = sessions.values().stream()
                .anyMatch(session -> session.getActivityKey().equals(registeredType.key()));
        if (hasVenue || hasSession) {
            return ActivityResult.ACTIVITY_TYPE_IN_USE;
        }

        activityTypes.remove(lookupKey);
        return ActivityResult.SUCCESS;
    }

    public Optional<ActivityType> getActivityType(String activityKey) {
        RegisteredActivityType registeredType = activityTypes.get(lookupKey(activityKey));
        return registeredType == null ? Optional.empty() : Optional.of(registeredType.type());
    }

    public List<ActivityType> getActivityTypes() {
        return activityTypes.values().stream().map(RegisteredActivityType::type).toList();
    }

    public ActivityResult registerVenue(ActivityVenue venue) {
        requirePrimaryThread();
        ActivityVenue validatedVenue = Objects.requireNonNull(venue, "venue");
        if (!activityTypes.containsKey(lookupKey(validatedVenue.activityKey()))) {
            return ActivityResult.ACTIVITY_TYPE_NOT_FOUND;
        }

        VenueKey key = VenueKey.of(validatedVenue);
        if (venues.containsKey(key)) {
            return ActivityResult.VENUE_ALREADY_REGISTERED;
        }

        venues.put(key, validatedVenue);
        return ActivityResult.SUCCESS;
    }

    public ActivityResult unregisterVenue(String activityKey, String venueId) {
        requirePrimaryThread();
        VenueKey key = VenueKey.lookup(activityKey, venueId);
        if (!venues.containsKey(key)) {
            return ActivityResult.VENUE_NOT_FOUND;
        }
        if (venueReservations.containsKey(key)) {
            return ActivityResult.VENUE_IN_USE;
        }

        venues.remove(key);
        return ActivityResult.SUCCESS;
    }

    public Optional<ActivityVenue> getVenue(String activityKey, String venueId) {
        return Optional.ofNullable(venues.get(VenueKey.lookup(activityKey, venueId)));
    }

    public List<ActivityVenue> getVenues() {
        return List.copyOf(venues.values());
    }

    public List<ActivityVenue> getVenues(String activityKey) {
        String key = lookupKey(activityKey);
        return venues.entrySet().stream()
                .filter(entry -> entry.getKey().activityKey().equals(key))
                .map(Map.Entry::getValue)
                .toList();
    }

    public ActivitySessionCreationResult createSession(String activityKey, String venueId) {
        requirePrimaryThread();
        String typeKey = lookupKey(activityKey);
        RegisteredActivityType registeredType = activityTypes.get(typeKey);
        if (registeredType == null) {
            return ActivitySessionCreationResult.failure(ActivityResult.ACTIVITY_TYPE_NOT_FOUND);
        }

        VenueKey venueKey = VenueKey.lookup(typeKey, venueId);
        ActivityVenue venue = venues.get(venueKey);
        if (venue == null) {
            return ActivitySessionCreationResult.failure(ActivityResult.VENUE_NOT_FOUND);
        }
        if (venueReservations.containsKey(venueKey)) {
            return ActivitySessionCreationResult.failure(ActivityResult.VENUE_IN_USE);
        }
        if (!isWorldLoaded(venue.area().worldName())) {
            return ActivitySessionCreationResult.failure(ActivityResult.VENUE_UNAVAILABLE);
        }

        UUID sessionId;
        do {
            sessionId = UUID.randomUUID();
        } while (sessions.containsKey(sessionId));

        ActivitySession session;
        try {
            session = registeredType.type().createSession(sessionId, venue);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE,
                    "Activity session creation failed [activity=" + registeredType.key()
                            + ", venue=" + venue.id() + "].",
                    exception
            );
            return ActivitySessionCreationResult.failure(ActivityResult.INVALID_SESSION);
        }

        if (!isValidCreatedSession(session, sessionId, registeredType.key(), venue)) {
            discardInvalidSession(session);
            return ActivitySessionCreationResult.failure(ActivityResult.INVALID_SESSION);
        }

        sessions.put(sessionId, session);
        venueReservations.put(venueKey, sessionId);
        return ActivitySessionCreationResult.success(session);
    }

    public Optional<ActivitySession> getSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(sessionId, "sessionId")));
    }

    public Optional<ActivitySession> getSessionForPlayer(UUID playerId) {
        UUID sessionId = playerMemberships.get(Objects.requireNonNull(playerId, "playerId"));
        return sessionId == null ? Optional.empty() : Optional.ofNullable(sessions.get(sessionId));
    }

    public List<ActivitySession> getSessions() {
        return List.copyOf(sessions.values());
    }

    public List<ActivitySession> getSessions(String activityKey) {
        String key = lookupKey(activityKey);
        return sessions.values().stream()
                .filter(session -> session.getActivityKey().equals(key))
                .toList();
    }

    public Optional<ActivitySession> findAvailableSession(String activityKey) {
        RegisteredActivityType registeredType = activityTypes.get(lookupKey(activityKey));
        if (registeredType == null) {
            return Optional.empty();
        }

        return sessions.values().stream()
                .filter(session -> session.getActivityKey().equals(registeredType.key()))
                .filter(session -> session.getState() == ActivityState.AVAILABLE)
                .filter(session -> session.getParticipantCount() < registeredType.maxParticipants())
                .findFirst();
    }

    public boolean isParticipating(UUID playerId) {
        return playerMemberships.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    public ActivityResult joinSession(Player player, UUID sessionId) {
        requirePrimaryThread();
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        UUID validatedSessionId = Objects.requireNonNull(sessionId, "sessionId");
        UUID playerId = validatedPlayer.getUniqueId();

        if (!validatedPlayer.isOnline()) {
            return ActivityResult.PLAYER_OFFLINE;
        }
        if (!playerService.isLoaded(playerId)) {
            return ActivityResult.PLAYER_NOT_LOADED;
        }
        if (playerMemberships.containsKey(playerId)) {
            return ActivityResult.PLAYER_ALREADY_IN_ACTIVITY;
        }

        ActivitySession session = sessions.get(validatedSessionId);
        if (session == null) {
            return ActivityResult.SESSION_NOT_FOUND;
        }
        if (session.getState() != ActivityState.AVAILABLE) {
            return ActivityResult.SESSION_NOT_AVAILABLE;
        }
        if (!isWorldLoaded(session.getVenue().area().worldName())) {
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.VENUE_UNAVAILABLE;
        }

        RegisteredActivityType registeredType = activityTypes.get(session.getActivityKey());
        if (registeredType == null) {
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.INVALID_SESSION;
        }
        if (session.getParticipantCount() >= registeredType.maxParticipants()) {
            return ActivityResult.SESSION_FULL;
        }

        ActivityParticipant participant = new ActivityParticipant(playerId, Instant.now());
        if (!session.addParticipant(participant)) {
            return ActivityResult.PLAYER_ALREADY_IN_ACTIVITY;
        }
        playerMemberships.put(playerId, session.getSessionId());

        try {
            session.onParticipantJoined(participant);
        } catch (RuntimeException exception) {
            session.removeParticipant(playerId);
            playerMemberships.remove(playerId, session.getSessionId());
            logSessionFailure(session, "onParticipantJoined", exception);
            return ActivityResult.HOOK_FAILED;
        }

        return ActivityResult.SUCCESS;
    }

    public ActivityResult leaveCurrentSession(Player player, ActivityLeaveReason reason) {
        Objects.requireNonNull(player, "player");
        return leaveCurrentSession(player.getUniqueId(), reason);
    }

    public ActivityResult leaveCurrentSession(UUID playerId, ActivityLeaveReason reason) {
        requirePrimaryThread();
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        ActivityLeaveReason validatedReason = Objects.requireNonNull(reason, "reason");
        UUID sessionId = playerMemberships.get(validatedPlayerId);
        if (sessionId == null) {
            return ActivityResult.NOT_PARTICIPANT;
        }

        ActivitySession session = sessions.get(sessionId);
        if (session == null) {
            playerMemberships.remove(validatedPlayerId, sessionId);
            return ActivityResult.SESSION_NOT_FOUND;
        }
        if (!isWorldLoaded(session.getVenue().area().worldName())) {
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.VENUE_UNAVAILABLE;
        }

        ActivityParticipant participant = session.removeParticipant(validatedPlayerId);
        playerMemberships.remove(validatedPlayerId, sessionId);
        if (participant == null) {
            return ActivityResult.NOT_PARTICIPANT;
        }

        boolean hookFailed = false;
        try {
            session.onParticipantLeft(participant, validatedReason);
        } catch (RuntimeException exception) {
            hookFailed = true;
            logSessionFailure(session, "onParticipantLeft", exception);
        }

        RegisteredActivityType registeredType = activityTypes.get(session.getActivityKey());
        if (session.getState() == ActivityState.ACTIVE
                && (registeredType == null
                || session.getParticipantCount() < registeredType.minParticipants())) {
            ActivityResult resetResult = registeredType == null
                    ? closeInvalidSession(session)
                    : resetSessionInternal(session);
            if (resetResult != ActivityResult.SUCCESS) {
                return resetResult;
            }
        }

        return hookFailed ? ActivityResult.HOOK_FAILED : ActivityResult.SUCCESS;
    }

    public ActivityResult activateSession(UUID sessionId) {
        requirePrimaryThread();
        ActivitySession session = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (session == null) {
            return ActivityResult.SESSION_NOT_FOUND;
        }
        if (session.getState() != ActivityState.AVAILABLE) {
            return ActivityResult.INVALID_STATE;
        }
        if (!isWorldLoaded(session.getVenue().area().worldName())) {
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.VENUE_UNAVAILABLE;
        }

        RegisteredActivityType registeredType = activityTypes.get(session.getActivityKey());
        if (registeredType == null) {
            return closeInvalidSession(session);
        }
        if (session.getParticipantCount() < registeredType.minParticipants()) {
            return ActivityResult.NOT_ENOUGH_PARTICIPANTS;
        }

        session.transitionTo(ActivityState.ACTIVE);
        try {
            session.onActivated();
        } catch (RuntimeException exception) {
            logSessionFailure(session, "onActivated", exception);
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.HOOK_FAILED;
        }
        return ActivityResult.SUCCESS;
    }

    public ActivityResult resetSession(UUID sessionId) {
        requirePrimaryThread();
        ActivitySession session = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (session == null) {
            return ActivityResult.SESSION_NOT_FOUND;
        }
        return resetSessionInternal(session);
    }

    public ActivityResult closeSession(UUID sessionId, ActivityLeaveReason participantReason) {
        requirePrimaryThread();
        ActivitySession session = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (session == null) {
            return ActivityResult.SESSION_NOT_FOUND;
        }
        safeCloseSession(session, Objects.requireNonNull(participantReason, "participantReason"));
        return ActivityResult.SUCCESS;
    }

    public boolean isPlayerInsideVenue(UUID sessionId, Player player) {
        ActivitySession session = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        return session != null
                && session.getVenue().area().contains(Objects.requireNonNull(player, "player").getLocation());
    }

    public int getActivityTypeCount() {
        return activityTypes.size();
    }

    public int getVenueCount() {
        return venues.size();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public int getMembershipCount() {
        return playerMemberships.size();
    }

    void shutdown() {
        requirePrimaryThread();
        for (ActivitySession session : new ArrayList<>(sessions.values())) {
            try {
                safeCloseSession(session, ActivityLeaveReason.PLUGIN_DISABLE);
            } catch (RuntimeException exception) {
                logSessionFailure(session, "shutdown", exception);
            }
        }

        playerMemberships.clear();
        venueReservations.clear();
        sessions.clear();
        closingSessions.clear();
        venues.clear();
        activityTypes.clear();
    }

    private ActivityResult resetSessionInternal(ActivitySession session) {
        if (session.getState() != ActivityState.ACTIVE) {
            return ActivityResult.INVALID_STATE;
        }
        if (!isWorldLoaded(session.getVenue().area().worldName())) {
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.VENUE_UNAVAILABLE;
        }

        session.transitionTo(ActivityState.RESETTING);
        if (!cancelTasksForReset(session)) {
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.HOOK_FAILED;
        }

        try {
            session.onReset();
        } catch (RuntimeException exception) {
            logSessionFailure(session, "onReset", exception);
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.HOOK_FAILED;
        }

        if (!cancelTasksForReset(session)) {
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.HOOK_FAILED;
        }

        session.transitionTo(ActivityState.AVAILABLE);
        try {
            session.onResetCompleted();
        } catch (RuntimeException exception) {
            logSessionFailure(session, "onResetCompleted", exception);
            safeCloseSession(session, ActivityLeaveReason.ERROR);
            return ActivityResult.HOOK_FAILED;
        }
        return ActivityResult.SUCCESS;
    }

    private boolean cancelTasksForReset(ActivitySession session) {
        try {
            session.cancelTrackedTasks();
            return true;
        } catch (RuntimeException exception) {
            logSessionFailure(session, "cancelTrackedTasks", exception);
            return false;
        }
    }

    private ActivityResult closeInvalidSession(ActivitySession session) {
        safeCloseSession(session, ActivityLeaveReason.ERROR);
        return ActivityResult.INVALID_SESSION;
    }

    private void safeCloseSession(ActivitySession session, ActivityLeaveReason participantReason) {
        if (!closingSessions.add(session.getSessionId())) {
            return;
        }

        boolean alreadyClosed = session.getState() == ActivityState.CLOSED;
        try {
            try {
                session.cancelTrackedTasks();
            } catch (RuntimeException exception) {
                logSessionFailure(session, "cancelTrackedTasks", exception);
            }

            for (ActivityParticipant participant : new ArrayList<>(session.getParticipants())) {
                session.removeParticipant(participant.uniqueId());
                playerMemberships.remove(participant.uniqueId(), session.getSessionId());
                try {
                    session.onParticipantLeft(participant, participantReason);
                } catch (RuntimeException exception) {
                    logSessionFailure(session, "onParticipantLeft", exception);
                }
            }
            playerMemberships.entrySet().removeIf(entry -> entry.getValue().equals(session.getSessionId()));

            if (!alreadyClosed) {
                session.transitionTo(ActivityState.CLOSED);
                try {
                    session.onClosed();
                } catch (RuntimeException exception) {
                    logSessionFailure(session, "onClosed", exception);
                }
            }
        } finally {
            sessions.remove(session.getSessionId(), session);
            venueReservations.remove(VenueKey.of(session.getVenue()), session.getSessionId());
            closingSessions.remove(session.getSessionId());
        }
    }

    private void discardInvalidSession(ActivitySession session) {
        if (session == null) {
            return;
        }

        try {
            session.cancelTrackedTasks();
        } catch (RuntimeException exception) {
            logSessionFailure(session, "cancelTrackedTasks", exception);
        }
        for (ActivityParticipant participant : new ArrayList<>(session.getParticipants())) {
            session.removeParticipant(participant.uniqueId());
            try {
                session.onParticipantLeft(participant, ActivityLeaveReason.ERROR);
            } catch (RuntimeException exception) {
                logSessionFailure(session, "onParticipantLeft", exception);
            }
        }
        if (session.getState() != ActivityState.CLOSED) {
            try {
                session.transitionTo(ActivityState.CLOSED);
                session.onClosed();
            } catch (RuntimeException exception) {
                logSessionFailure(session, "discardInvalidSession", exception);
            }
        }
    }

    private boolean isValidCreatedSession(
            ActivitySession session,
            UUID expectedId,
            String expectedActivityKey,
            ActivityVenue expectedVenue
    ) {
        return session != null
                && expectedId.equals(session.getSessionId())
                && expectedActivityKey.equals(session.getActivityKey())
                && expectedVenue.equals(session.getVenue())
                && session.getState() == ActivityState.AVAILABLE
                && session.getParticipantCount() == 0;
    }

    private boolean isWorldLoaded(String worldName) {
        try {
            return loadedWorldCheck.test(worldName);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not determine whether world '" + worldName + "' is loaded.", exception);
            return false;
        }
    }

    private void requirePrimaryThread() {
        if (!primaryThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Activity runtime mutations must run on the primary server thread");
        }
    }

    private void logSessionFailure(ActivitySession session, String hook, RuntimeException exception) {
        logger.log(
                Level.SEVERE,
                "Activity session cleanup/hook failed [activity=" + session.getActivityKey()
                        + ", session=" + session.getSessionId()
                        + ", venue=" + session.getVenue().id()
                        + ", hook=" + hook + "].",
                exception
        );
    }

    private static boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    private static String lookupKey(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredActivityType(
            ActivityType type,
            String key,
            int minParticipants,
            int maxParticipants
    ) {
    }

    private record VenueKey(String activityKey, String venueId) {

        private static VenueKey of(ActivityVenue venue) {
            return new VenueKey(venue.activityKey(), venue.id());
        }

        private static VenueKey lookup(String activityKey, String venueId) {
            return new VenueKey(lookupKey(activityKey), lookupKey(venueId));
        }
    }
}

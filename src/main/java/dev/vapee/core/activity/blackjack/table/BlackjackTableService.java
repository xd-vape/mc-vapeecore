package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.ActivitySession;
import dev.vapee.core.activity.ActivitySessionCreationResult;
import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.BlackjackActivityType;
import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.location.ActivityVenue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BlackjackTableService {

    private final ActivityService activityService;
    private final BlackjackTableConfig tableConfig;
    private final Consumer<String> seatCleanup;
    private final Predicate<String> loadedWorldCheck;
    private final Logger logger;
    private final Map<String, BlackjackTableDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, BlackjackSession> sessions = new LinkedHashMap<>();
    private final Map<BlackjackBlockPosition, String> interactions = new LinkedHashMap<>();

    public BlackjackTableService(
            ActivityService activityService,
            BlackjackTableConfig tableConfig,
            BlackjackSeatService seatService,
            Predicate<String> loadedWorldCheck,
            Logger logger
    ) {
        this(activityService, tableConfig, seatService::cleanupTable, loadedWorldCheck, logger);
    }

    BlackjackTableService(
            ActivityService activityService,
            BlackjackTableConfig tableConfig,
            Consumer<String> seatCleanup,
            Predicate<String> loadedWorldCheck,
            Logger logger
    ) {
        this.activityService = Objects.requireNonNull(activityService, "activityService");
        this.tableConfig = Objects.requireNonNull(tableConfig, "tableConfig");
        this.seatCleanup = Objects.requireNonNull(seatCleanup, "seatCleanup");
        this.loadedWorldCheck = Objects.requireNonNull(loadedWorldCheck, "loadedWorldCheck");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void activateConfiguredTables() {
        for (BlackjackTableDraft draft : tableConfig.getDrafts()) {
            if (!draft.isEnabled()) {
                continue;
            }
            BlackjackTableOperationResult result = activateDraft(draft);
            if (!result.isSuccess()) {
                logger.warning("Skipping enabled blackjack table '" + draft.getId() + "': "
                        + describe(result) + ". The file was left unchanged.");
            }
        }
    }

    public BlackjackTableOperationResult enableTable(String tableId) {
        Optional<BlackjackTableDraft> optionalDraft = tableConfig.getDraft(tableId);
        if (optionalDraft.isEmpty()) {
            return BlackjackTableOperationResult.of(BlackjackTableOperationResult.Status.TABLE_NOT_FOUND);
        }
        BlackjackTableDraft draft = optionalDraft.get();
        if (definitions.containsKey(tableId)) {
            return BlackjackTableOperationResult.of(BlackjackTableOperationResult.Status.ALREADY_ENABLED);
        }

        BlackjackTableOperationResult activationResult = activateDraft(draft);
        if (!activationResult.isSuccess()) {
            return activationResult;
        }
        if (draft.isEnabled()) {
            return BlackjackTableOperationResult.success();
        }

        BlackjackTableDraft enabledDraft = draft.copy();
        enabledDraft.setEnabled(true);
        try {
            tableConfig.saveDraft(enabledDraft);
        } catch (RuntimeException exception) {
            try {
                deactivateRuntime(tableId, ActivityLeaveReason.ERROR);
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
        return BlackjackTableOperationResult.success();
    }

    public BlackjackTableOperationResult disableTable(String tableId) {
        Optional<BlackjackTableDraft> optionalDraft = tableConfig.getDraft(tableId);
        if (optionalDraft.isEmpty()) {
            return BlackjackTableOperationResult.of(BlackjackTableOperationResult.Status.TABLE_NOT_FOUND);
        }
        BlackjackTableDraft draft = optionalDraft.get();
        BlackjackSession session = sessions.get(tableId);
        if (session != null && (session.getState() != ActivityState.AVAILABLE
                || session.getParticipantCount() != 0)) {
            return BlackjackTableOperationResult.of(BlackjackTableOperationResult.Status.TABLE_IN_USE);
        }
        if (session == null && !draft.isEnabled()) {
            return BlackjackTableOperationResult.of(BlackjackTableOperationResult.Status.NOT_ENABLED);
        }

        BlackjackTableDefinition previousDefinition = definitions.get(tableId);
        if (session != null) {
            deactivateRuntime(tableId, ActivityLeaveReason.SESSION_CLOSED);
        }
        BlackjackTableDraft disabledDraft = draft.copy();
        disabledDraft.setEnabled(false);
        try {
            tableConfig.saveDraft(disabledDraft);
        } catch (RuntimeException exception) {
            if (previousDefinition != null) {
                BlackjackTableOperationResult rollback = activateDefinition(previousDefinition);
                if (!rollback.isSuccess()) {
                    exception.addSuppressed(new IllegalStateException(
                            "Could not restore blackjack table runtime: " + describe(rollback)
                    ));
                }
            }
            throw exception;
        }
        return BlackjackTableOperationResult.success();
    }

    public Optional<String> getTableAt(BlackjackBlockPosition interaction) {
        return Optional.ofNullable(interactions.get(Objects.requireNonNull(interaction, "interaction")));
    }

    public Optional<BlackjackTableDefinition> getDefinition(String tableId) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(tableId, "tableId")));
    }

    public Optional<BlackjackSession> getSession(String tableId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(tableId, "tableId")));
    }

    public List<BlackjackTableDefinition> getDefinitions() {
        return List.copyOf(definitions.values());
    }

    public boolean isRuntimeActive(String tableId) {
        return definitions.containsKey(Objects.requireNonNull(tableId, "tableId"));
    }

    public void shutdown() {
        for (String tableId : new ArrayList<>(definitions.keySet())) {
            try {
                deactivateRuntime(tableId, ActivityLeaveReason.PLUGIN_DISABLE);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Could not fully deactivate blackjack table '" + tableId + "'.", exception);
            }
        }
        interactions.clear();
        sessions.clear();
        definitions.clear();
    }

    private BlackjackTableOperationResult activateDraft(BlackjackTableDraft draft) {
        List<String> validationErrors = BlackjackTableDefinition.validate(draft);
        if (!validationErrors.isEmpty()) {
            return BlackjackTableOperationResult.invalid(validationErrors);
        }
        BlackjackTableDefinition definition = BlackjackTableDefinition.fromDraft(draft);
        if (!loadedWorldCheck.test(definition.area().worldName())) {
            return BlackjackTableOperationResult.of(BlackjackTableOperationResult.Status.WORLD_NOT_LOADED);
        }
        return activateDefinition(definition);
    }

    private BlackjackTableOperationResult activateDefinition(BlackjackTableDefinition definition) {
        String existingInteraction = interactions.get(definition.interaction());
        if (existingInteraction != null && !existingInteraction.equals(definition.id())) {
            return new BlackjackTableOperationResult(
                    BlackjackTableOperationResult.Status.INTERACTION_CONFLICT,
                    List.of("interaction block is already used by " + existingInteraction)
            );
        }

        ActivityVenue venue = new ActivityVenue(
                definition.id(),
                BlackjackActivityType.KEY,
                definition.area(),
                definition.dealer()
        );
        ActivityResult venueResult = activityService.registerVenue(venue);
        if (venueResult != ActivityResult.SUCCESS) {
            return new BlackjackTableOperationResult(
                    BlackjackTableOperationResult.Status.RUNTIME_FAILURE,
                    List.of("venue registration returned " + venueResult)
            );
        }

        ActivitySessionCreationResult creationResult = activityService.createSession(
                BlackjackActivityType.KEY,
                definition.id()
        );
        if (!creationResult.isSuccess()) {
            activityService.unregisterVenue(BlackjackActivityType.KEY, definition.id());
            BlackjackTableOperationResult.Status status = creationResult.result() == ActivityResult.VENUE_UNAVAILABLE
                    ? BlackjackTableOperationResult.Status.WORLD_NOT_LOADED
                    : BlackjackTableOperationResult.Status.RUNTIME_FAILURE;
            return new BlackjackTableOperationResult(
                    status,
                    List.of("session creation returned " + creationResult.result())
            );
        }

        ActivitySession created = creationResult.session().orElseThrow();
        if (!(created instanceof BlackjackSession blackjackSession)) {
            activityService.closeSession(created.getSessionId(), ActivityLeaveReason.ERROR);
            activityService.unregisterVenue(BlackjackActivityType.KEY, definition.id());
            return new BlackjackTableOperationResult(
                    BlackjackTableOperationResult.Status.RUNTIME_FAILURE,
                    List.of("activity type produced an incompatible session")
            );
        }

        definitions.put(definition.id(), definition);
        sessions.put(definition.id(), blackjackSession);
        interactions.put(definition.interaction(), definition.id());
        return BlackjackTableOperationResult.success();
    }

    private void deactivateRuntime(String tableId, ActivityLeaveReason reason) {
        BlackjackSession session = sessions.get(tableId);
        BlackjackTableDefinition definition = definitions.get(tableId);
        if (session == null || definition == null) {
            interactions.entrySet().removeIf(entry -> entry.getValue().equals(tableId));
            sessions.remove(tableId);
            definitions.remove(tableId);
            seatCleanup.accept(tableId);
            return;
        }

        ActivityResult closeResult = activityService.closeSession(session.getSessionId(), reason);
        if (closeResult != ActivityResult.SUCCESS && closeResult != ActivityResult.SESSION_NOT_FOUND) {
            throw new IllegalStateException("Could not close blackjack session: " + closeResult);
        }
        seatCleanup.accept(tableId);
        ActivityResult venueResult = activityService.unregisterVenue(BlackjackActivityType.KEY, tableId);
        if (venueResult != ActivityResult.SUCCESS && venueResult != ActivityResult.VENUE_NOT_FOUND) {
            throw new IllegalStateException("Could not unregister blackjack venue: " + venueResult);
        }
        interactions.remove(definition.interaction(), tableId);
        sessions.remove(tableId);
        definitions.remove(tableId);
    }

    private String describe(BlackjackTableOperationResult result) {
        if (result.details().isEmpty()) {
            return result.status().name();
        }
        return result.status().name() + " (" + String.join(", ", result.details()) + ")";
    }
}

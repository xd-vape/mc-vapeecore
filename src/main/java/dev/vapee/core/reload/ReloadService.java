package dev.vapee.core.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReloadService {

    private final Logger logger;
    private final List<ReloadParticipant> participants;

    private boolean running;

    public ReloadService(Logger logger, List<? extends ReloadParticipant> participants) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        if (this.participants.isEmpty()) {
            throw new IllegalArgumentException("At least one reload participant is required");
        }

        for (ReloadParticipant participant : this.participants) {
            requireParticipantName(participant);
        }
    }

    public ReloadResult reload() {
        long startedAt = System.nanoTime();
        if (running) {
            return result(ReloadResult.Status.ALREADY_RUNNING, "", 0, 0, startedAt);
        }

        running = true;
        try {
            return executeReload(startedAt);
        } finally {
            running = false;
        }
    }

    private ReloadResult executeReload(long startedAt) {
        logger.info("Preparing configuration reload.");
        List<PreparedReload> preparedReloads = new ArrayList<>(participants.size());

        for (ReloadParticipant participant : participants) {
            String name = requireParticipantName(participant);
            try {
                ReloadPlan plan = Objects.requireNonNull(
                        participant.prepareReload(),
                        "Reload plan for " + name
                );
                preparedReloads.add(new PreparedReload(name, plan));
                logger.info("Prepared " + name + ".");
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Could not prepare configuration reload for " + name + ".", exception);
                return result(
                        ReloadResult.Status.PREPARE_FAILED,
                        name,
                        preparedReloads.size(),
                        0,
                        startedAt
                );
            }
        }

        logger.info("Applying configuration reload.");
        List<PreparedReload> appliedReloads = new ArrayList<>(preparedReloads.size());
        for (PreparedReload preparedReload : preparedReloads) {
            try {
                preparedReload.plan().apply();
                appliedReloads.add(preparedReload);
            } catch (RuntimeException exception) {
                logger.log(
                        Level.SEVERE,
                        "Could not apply configuration reload for " + preparedReload.name() + ".",
                        exception
                );
                boolean rollbackComplete = rollback(preparedReload, appliedReloads);
                ReloadResult.Status status = rollbackComplete
                        ? ReloadResult.Status.APPLY_FAILED
                        : ReloadResult.Status.ROLLBACK_INCOMPLETE;
                if (!rollbackComplete) {
                    logger.severe("Configuration rollback was incomplete. A controlled server restart is recommended.");
                }
                return result(
                        status,
                        preparedReload.name(),
                        preparedReloads.size(),
                        appliedReloads.size(),
                        startedAt
                );
            }
        }

        ReloadResult successfulResult = result(
                ReloadResult.Status.SUCCESS,
                "",
                preparedReloads.size(),
                appliedReloads.size(),
                startedAt
        );
        logger.info("Configuration reload completed in " + successfulResult.durationMillis() + " ms.");
        return successfulResult;
    }

    private boolean rollback(PreparedReload failedReload, List<PreparedReload> appliedReloads) {
        boolean complete = rollback(failedReload);
        for (int index = appliedReloads.size() - 1; index >= 0; index--) {
            if (!rollback(appliedReloads.get(index))) {
                complete = false;
            }
        }
        return complete;
    }

    private boolean rollback(PreparedReload preparedReload) {
        try {
            preparedReload.plan().rollback();
            return true;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE,
                    "Could not roll back configuration reload for " + preparedReload.name() + ".",
                    exception
            );
            return false;
        }
    }

    private ReloadResult result(
            ReloadResult.Status status,
            String failedComponent,
            int preparedComponents,
            int appliedComponents,
            long startedAt
    ) {
        long durationNanos = Math.max(0L, System.nanoTime() - startedAt);
        return new ReloadResult(
                status,
                failedComponent,
                preparedComponents,
                appliedComponents,
                durationNanos / 1_000_000L
        );
    }

    private String requireParticipantName(ReloadParticipant participant) {
        ReloadParticipant validatedParticipant = Objects.requireNonNull(participant, "participant");
        String name = Objects.requireNonNull(validatedParticipant.getReloadName(), "reload participant name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Reload participant names must not be blank");
        }
        return name;
    }

    private record PreparedReload(String name, ReloadPlan plan) {
    }
}

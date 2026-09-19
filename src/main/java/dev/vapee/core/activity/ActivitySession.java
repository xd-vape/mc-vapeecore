package dev.vapee.core.activity;

import dev.vapee.core.activity.location.ActivityVenue;
import dev.vapee.core.activity.player.ActivityParticipant;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public abstract class ActivitySession {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_-]+");

    private final UUID sessionId;
    private final String activityKey;
    private final ActivityVenue venue;
    private final Instant createdAt;
    private final Map<UUID, ActivityParticipant> participants = new LinkedHashMap<>();
    private final Set<BukkitTask> trackedTasks = Collections.newSetFromMap(new IdentityHashMap<>());

    private ActivityState state = ActivityState.AVAILABLE;

    protected ActivitySession(UUID sessionId, String activityKey, ActivityVenue venue) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.activityKey = requireActivityKey(activityKey);
        this.venue = Objects.requireNonNull(venue, "venue");
        this.createdAt = Instant.now();

        if (!this.activityKey.equals(venue.activityKey())) {
            throw new IllegalArgumentException("Session and venue activity keys must match");
        }
    }

    public final UUID getSessionId() {
        return sessionId;
    }

    public final String getActivityKey() {
        return activityKey;
    }

    public final ActivityVenue getVenue() {
        return venue;
    }

    public final ActivityState getState() {
        return state;
    }

    public final Instant getCreatedAt() {
        return createdAt;
    }

    public final List<ActivityParticipant> getParticipants() {
        return List.copyOf(participants.values());
    }

    public final Optional<ActivityParticipant> getParticipant(UUID uniqueId) {
        return Optional.ofNullable(participants.get(Objects.requireNonNull(uniqueId, "uniqueId")));
    }

    public final int getParticipantCount() {
        return participants.size();
    }

    public final boolean hasParticipant(UUID uniqueId) {
        return participants.containsKey(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    protected final <T extends BukkitTask> T trackTask(T task) {
        T validatedTask = Objects.requireNonNull(task, "task");
        if (state == ActivityState.CLOSED) {
            validatedTask.cancel();
            throw new IllegalStateException("Closed activity sessions cannot track tasks");
        }
        trackedTasks.add(validatedTask);
        return validatedTask;
    }

    protected final boolean untrackTask(BukkitTask task) {
        return trackedTasks.remove(Objects.requireNonNull(task, "task"));
    }

    protected void onParticipantJoined(ActivityParticipant participant) {
    }

    protected void onParticipantLeft(ActivityParticipant participant, ActivityLeaveReason reason) {
    }

    protected void onActivated() {
    }

    protected void onReset() {
    }

    protected void onResetCompleted() {
    }

    protected void onClosed() {
    }

    final void transitionTo(ActivityState target) {
        ActivityState validatedTarget = Objects.requireNonNull(target, "target");
        if (!state.canTransitionTo(validatedTarget)) {
            throw new IllegalStateException("Invalid activity state transition: " + state + " -> " + target);
        }
        state = validatedTarget;
    }

    final boolean addParticipant(ActivityParticipant participant) {
        ActivityParticipant validatedParticipant = Objects.requireNonNull(participant, "participant");
        return participants.putIfAbsent(validatedParticipant.uniqueId(), validatedParticipant) == null;
    }

    final ActivityParticipant removeParticipant(UUID uniqueId) {
        return participants.remove(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    final int getTrackedTaskCount() {
        return trackedTasks.size();
    }

    final void cancelTrackedTasks() {
        List<BukkitTask> tasks = new ArrayList<>(trackedTasks);
        trackedTasks.clear();

        RuntimeException firstFailure = null;
        for (BukkitTask task : tasks) {
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private static String requireActivityKey(String activityKey) {
        String value = Objects.requireNonNull(activityKey, "activityKey");
        if (!KEY_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("activityKey must match [a-z0-9_-]+");
        }
        return value;
    }
}

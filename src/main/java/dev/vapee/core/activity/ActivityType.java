package dev.vapee.core.activity;

import dev.vapee.core.activity.location.ActivityVenue;

import java.util.UUID;

public interface ActivityType {

    String getKey();

    int getMinParticipants();

    int getMaxParticipants();

    ActivitySession createSession(UUID sessionId, ActivityVenue venue);
}

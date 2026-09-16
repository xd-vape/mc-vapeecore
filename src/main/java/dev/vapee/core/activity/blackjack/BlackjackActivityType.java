package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivitySession;
import dev.vapee.core.activity.ActivityType;
import dev.vapee.core.activity.location.ActivityVenue;

import java.util.Objects;
import java.util.UUID;

public final class BlackjackActivityType implements ActivityType {

    public static final String KEY = "blackjack";
    public static final int MIN_PARTICIPANTS = 1;
    public static final int MAX_PARTICIPANTS = 5;

    private final BlackjackService blackjackService;

    public BlackjackActivityType(BlackjackService blackjackService) {
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public int getMinParticipants() {
        return MIN_PARTICIPANTS;
    }

    @Override
    public int getMaxParticipants() {
        return MAX_PARTICIPANTS;
    }

    @Override
    public ActivitySession createSession(UUID sessionId, ActivityVenue venue) {
        return new BlackjackSession(sessionId, venue, blackjackService);
    }
}

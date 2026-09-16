package dev.vapee.core.activity;

import java.util.Objects;
import java.util.Optional;

public record ActivitySessionCreationResult(
        ActivityResult result,
        Optional<ActivitySession> session
) {

    public ActivitySessionCreationResult {
        result = Objects.requireNonNull(result, "result");
        session = Objects.requireNonNull(session, "session");

        if ((result == ActivityResult.SUCCESS) != session.isPresent()) {
            throw new IllegalArgumentException("A successful creation requires exactly one session");
        }
    }

    public static ActivitySessionCreationResult success(ActivitySession session) {
        return new ActivitySessionCreationResult(
                ActivityResult.SUCCESS,
                Optional.of(Objects.requireNonNull(session, "session"))
        );
    }

    public static ActivitySessionCreationResult failure(ActivityResult result) {
        if (Objects.requireNonNull(result, "result") == ActivityResult.SUCCESS) {
            throw new IllegalArgumentException("Use success(session) for successful creation");
        }
        return new ActivitySessionCreationResult(result, Optional.empty());
    }

    public boolean isSuccess() {
        return result == ActivityResult.SUCCESS;
    }
}

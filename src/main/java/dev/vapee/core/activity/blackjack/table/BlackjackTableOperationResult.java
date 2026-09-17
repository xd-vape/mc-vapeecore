package dev.vapee.core.activity.blackjack.table;

import java.util.List;
import java.util.Objects;

public record BlackjackTableOperationResult(Status status, List<String> details) {

    public BlackjackTableOperationResult {
        status = Objects.requireNonNull(status, "status");
        details = List.copyOf(Objects.requireNonNull(details, "details"));
    }

    public static BlackjackTableOperationResult success() {
        return new BlackjackTableOperationResult(Status.SUCCESS, List.of());
    }

    public static BlackjackTableOperationResult of(Status status) {
        return new BlackjackTableOperationResult(status, List.of());
    }

    public static BlackjackTableOperationResult invalid(List<String> details) {
        return new BlackjackTableOperationResult(Status.INVALID_DEFINITION, details);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        TABLE_NOT_FOUND,
        ALREADY_ENABLED,
        NOT_ENABLED,
        INVALID_DEFINITION,
        WORLD_NOT_LOADED,
        INTERACTION_CONFLICT,
        TABLE_IN_USE,
        RUNTIME_FAILURE
    }
}

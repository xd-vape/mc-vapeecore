package dev.vapee.core.reload;

import java.util.Objects;

public record ReloadResult(
        Status status,
        String failedComponent,
        int preparedComponents,
        int appliedComponents,
        long durationMillis
) {

    public ReloadResult {
        Objects.requireNonNull(status, "status");
        failedComponent = Objects.requireNonNullElse(failedComponent, "");
        if (preparedComponents < 0 || appliedComponents < 0 || durationMillis < 0L) {
            throw new IllegalArgumentException("Reload result counts and duration must not be negative");
        }
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        PREPARE_FAILED,
        APPLY_FAILED,
        ROLLBACK_INCOMPLETE,
        ALREADY_RUNNING
    }
}

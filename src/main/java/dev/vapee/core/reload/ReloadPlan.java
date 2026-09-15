package dev.vapee.core.reload;

import java.util.Objects;

public interface ReloadPlan {

    void apply();

    void rollback();

    static ReloadPlan of(Runnable applyAction, Runnable rollbackAction) {
        Runnable validatedApplyAction = Objects.requireNonNull(applyAction, "applyAction");
        Runnable validatedRollbackAction = Objects.requireNonNull(rollbackAction, "rollbackAction");
        return new ReloadPlan() {
            @Override
            public void apply() {
                validatedApplyAction.run();
            }

            @Override
            public void rollback() {
                validatedRollbackAction.run();
            }
        };
    }
}

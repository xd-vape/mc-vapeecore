package dev.vapee.core.command;

import dev.vapee.core.rank.staff.StaffHierarchyConfig;
import dev.vapee.core.rank.staff.StaffHierarchyService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/** Shared test seam: missing explicit entry means a loaded ordinary user, null means unavailable. */
public final class StaffTargetTestFixture {
    public final Map<UUID, String> groups = new HashMap<>();
    public StaffHierarchyConfig config = StaffHierarchyConfig.defaults();
    public int reads;
    public boolean fail;
    public final OnlineStaffTargetGuard guard;

    public StaffTargetTestFixture(Logger logger) {
        guard = new OnlineStaffTargetGuard(new StaffHierarchyService(() -> config, id -> {
            reads++;
            if (fail) throw new IllegalStateException("simulated loaded lookup failure");
            return Optional.ofNullable(groups.getOrDefault(id, "default"));
        }, id -> { throw new AssertionError("Online authorization must never load LP users asynchronously"); }), logger);
    }

    public StaffTargetTestFixture() { this(quietLogger()); }

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }
}

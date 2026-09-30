package dev.vapee.core.rank.staff;

import dev.vapee.core.permission.LuckPermsService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** Read-only authorization: no Bukkit, storage, grants, user saves or secondary staff cache. */
public final class StaffHierarchyService {
    private final Supplier<StaffHierarchyConfig> configuration;
    private final Function<UUID, Optional<String>> loadedGroups;
    private final Function<UUID, CompletableFuture<Optional<String>>> loadGroups;

    public StaffHierarchyService(Supplier<StaffHierarchyConfig> configuration, LuckPermsService luckPerms) {
        this(configuration, Objects.requireNonNull(luckPerms)::getPrimaryGroup, luckPerms::loadPrimaryGroup);
    }

    public StaffHierarchyService(Supplier<StaffHierarchyConfig> configuration,
                                 Function<UUID, Optional<String>> loadedGroups,
                                 Function<UUID, CompletableFuture<Optional<String>>> loadGroups) {
        this.configuration = Objects.requireNonNull(configuration);
        this.loadedGroups = Objects.requireNonNull(loadedGroups);
        this.loadGroups = Objects.requireNonNull(loadGroups);
    }

    public Optional<String> getLoadedPrimaryGroup(UUID id) { return loadedGroups.apply(Objects.requireNonNull(id)); }
    public CompletableFuture<Optional<String>> loadPrimaryGroup(UUID id) {
        return Objects.requireNonNull(loadGroups.apply(Objects.requireNonNull(id)), "primary group future");
    }

    /** Main-thread application call after an offline load; one immutable config for both levels. */
    public StaffTargetDecision decide(UUID actorId, Optional<String> targetGroup) {
        StaffHierarchyConfig snapshot = configuration.get();
        return decide(snapshot, getLoadedPrimaryGroup(actorId), targetGroup);
    }

    public StaffTargetDecision decideLoaded(UUID actorId, UUID targetId) {
        StaffHierarchyConfig snapshot = configuration.get();
        return decide(snapshot, getLoadedPrimaryGroup(actorId), getLoadedPrimaryGroup(targetId));
    }

    public static StaffTargetDecision decide(StaffHierarchyConfig snapshot, Optional<String> actorGroup,
                                             Optional<String> targetGroup) {
        Objects.requireNonNull(snapshot);
        Optional<String> target = normalized(targetGroup);
        if (target.isEmpty()) return StaffTargetDecision.UNAVAILABLE;
        int targetLevel = snapshot.level(target.get());
        if (targetLevel < 0) return StaffTargetDecision.ALLOW;
        Optional<String> actor = normalized(actorGroup);
        if (actor.isEmpty()) return StaffTargetDecision.UNAVAILABLE;
        int actorLevel = snapshot.level(actor.get());
        if (actorLevel < 0) return StaffTargetDecision.DENY_ACTOR_NOT_PROTECTED;
        return actorLevel > targetLevel ? StaffTargetDecision.ALLOW : StaffTargetDecision.DENY_SAME_OR_HIGHER;
    }

    private static Optional<String> normalized(Optional<String> group) {
        if (group == null || group.isEmpty()) return Optional.empty();
        try { return Optional.of(StaffHierarchyConfig.normalizeGroup(group.get())); }
        catch (IllegalArgumentException | NullPointerException exception) { return Optional.empty(); }
    }
}

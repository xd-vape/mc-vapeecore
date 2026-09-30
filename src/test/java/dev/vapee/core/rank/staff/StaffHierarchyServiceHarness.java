package dev.vapee.core.rank.staff;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class StaffHierarchyServiceHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        StaffHierarchyConfig defaults = StaffHierarchyConfig.defaults();
        check(defaults.protectedGroups().equals(List.of("builder", "moderator", "admin", "owner")), "default LOW to HIGH");
        var source = new ArrayList<>(List.of(" Builder ", "MODERATOR", "ADMIN", "owner"));
        StaffHierarchyConfig normalized = new StaffHierarchyConfig(source);
        source.clear();
        check(normalized.equals(defaults), "normalized immutable input copy");
        try { normalized.protectedGroups().add("vip"); throw new AssertionError("mutable config"); }
        catch (UnsupportedOperationException expected) { checks++; }
        for (List<String> invalid : List.of(List.<String>of(), List.of(""), List.of("   "), List.of("ad min"),
                List.of("admin\n"), List.of("a\u0000b"), List.of("admin", "ADMIN"), List.of("admin", " admin "))) {
            try { new StaffHierarchyConfig(invalid); throw new AssertionError("invalid config accepted: " + invalid); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        try { new StaffHierarchyConfig(null); throw new AssertionError("null list"); }
        catch (NullPointerException expected) { checks++; }
        try { new StaffHierarchyConfig(Arrays.asList("admin", null)); throw new AssertionError("null entry"); }
        catch (NullPointerException expected) { checks++; }
        List<String> groups = List.of("default", "vip", "custom", "builder", "moderator", "admin", "owner");
        for (String actor : groups) for (String target : groups) {
            int a = defaults.level(actor), t = defaults.level(target);
            StaffTargetDecision expected = t < 0 ? StaffTargetDecision.ALLOW
                    : a < 0 ? StaffTargetDecision.DENY_ACTOR_NOT_PROTECTED
                    : a > t ? StaffTargetDecision.ALLOW : StaffTargetDecision.DENY_SAME_OR_HIGHER;
            check(StaffHierarchyService.decide(defaults, Optional.of(actor), Optional.of(target)) == expected,
                    actor + " -> " + target);
        }
        check(StaffHierarchyService.decide(defaults, Optional.of(" OWNER "), Optional.of("ADMIN")) == StaffTargetDecision.ALLOW, "case and trim");
        for (Optional<String> unknown : List.of(Optional.<String>empty(), Optional.of(" "), Optional.of("admin\n"))) {
            check(StaffHierarchyService.decide(defaults, Optional.of("owner"), unknown) == StaffTargetDecision.UNAVAILABLE, "unknown target closed");
            check(StaffHierarchyService.decide(defaults, unknown, Optional.of("admin")) == StaffTargetDecision.UNAVAILABLE, "unknown actor protected closed");
        }
        check(StaffHierarchyService.decide(defaults, Optional.empty(), Optional.of("default")) == StaffTargetDecision.ALLOW,
                "known normal target needs only command permission");
        var custom = new StaffHierarchyConfig(List.of("helper", "admin"));
        check(StaffHierarchyService.decide(custom, Optional.of("admin"), Optional.of("helper")) == StaffTargetDecision.ALLOW, "explicit custom protection");
        check(StaffHierarchyService.decide(custom, Optional.of("helper"), Optional.of("helper")) == StaffTargetDecision.DENY_SAME_OR_HIGHER, "custom equal deny");
        UUID actor = new UUID(0, 1), target = new UUID(0, 2);
        var state = new AtomicReference<>(defaults);
        var reads = new AtomicInteger();
        var groupsById = new HashMap<UUID, String>();
        groupsById.put(actor, "admin"); groupsById.put(target, "moderator");
        var service = new StaffHierarchyService(() -> { reads.incrementAndGet(); return state.get(); },
                id -> Optional.ofNullable(groupsById.get(id)),
                id -> CompletableFuture.completedFuture(Optional.of("owner")));
        check(service.decideLoaded(actor, target) == StaffTargetDecision.ALLOW && reads.get() == 1, "one config snapshot per check");
        state.set(new StaffHierarchyConfig(List.of("admin", "moderator")));
        check(service.decideLoaded(actor, target) == StaffTargetDecision.DENY_SAME_OR_HIGHER && reads.get() == 2, "reload changes next decision");
        state.set(defaults);
        check(service.decideLoaded(actor, target) == StaffTargetDecision.ALLOW, "rollback restores semantics");
        groupsById.put(actor, "builder");
        check(service.decideLoaded(actor, target) == StaffTargetDecision.DENY_SAME_OR_HIGHER, "fresh actor group no cache");
        state.set(defaults);
        var mutating = new StaffHierarchyService(state::get, id -> {
            state.set(new StaffHierarchyConfig(List.of("admin", "moderator")));
            return Optional.of(id.equals(actor) ? "admin" : "moderator");
        }, id -> CompletableFuture.completedFuture(Optional.empty()));
        check(mutating.decideLoaded(actor, target) == StaffTargetDecision.ALLOW, "both levels retain captured config");
        check(mutating.decideLoaded(actor, target) == StaffTargetDecision.DENY_SAME_OR_HIGHER,
                "changed supplier only affects next check");
        var resolved = new AtomicReference<Optional<String>>();
        service.loadPrimaryGroup(target).thenAccept(resolved::set);
        check(resolved.get().equals(Optional.of("owner")), "async source delegated without wait");
        sourceBoundaries();
        System.out.println("StaffHierarchyServiceHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }

    private static void sourceBoundaries() throws Exception {
        var root = java.nio.file.Path.of("src/main/java/dev/vapee/core");
        String authorization = java.nio.file.Files.readString(root.resolve("rank/staff/StaffHierarchyService.java"));
        for (String forbidden : List.of("org.bukkit", "RankInfo", "getWeight", "getPrefix", "getSuffix", "getTrackGroups",
                "isOp", "PlayerIdentity", "PlayerService", "saveUser", "InheritanceNode", ".join()")) {
            check(!authorization.contains(forbidden), "authorization source independent: " + forbidden);
        }
        for (String path : List.of("permission/LuckPermsService.java", "moderation/command/AbstractModerationCommand.java")) {
            String source = java.nio.file.Files.readString(root.resolve(path));
            for (String forbidden : List.of(".join()", "Thread.sleep", "loadUser(uniqueId).get(", "loadPrimaryGroup(target.uniqueId()).get(",
                    "saveUser(", "isOp(", "runTaskTimer", "PlayerCommandPreprocessEvent")) {
                check(!source.contains(forbidden), "nonblocking read-only boundary: " + path + "/" + forbidden);
            }
        }
        String rank = java.nio.file.Files.readString(root.resolve("rank/RankModule.java"));
        check(rank.contains("getStaffHierarchyService()") && rank.contains("configService::getStaffHierarchyConfig")
                && !rank.contains("dev.vapee.core.moderation"), "Rank owns staff service without reverse dependency");
        String display = java.nio.file.Files.readString(root.resolve("rank/RankService.java"));
        check(!display.contains("StaffHierarchy") && !display.contains("loadPrimaryGroup("), "display rank remains separate and cached-only");
    }
}

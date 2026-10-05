package dev.vapee.core.activity.blackjack.presentation;

import dev.vapee.core.activity.blackjack.table.BlackjackDisplayAnchor;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDraft;
import dev.vapee.core.activity.location.ActivityPosition;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BlackjackPreviewHarness {

    private static int checks;

    private BlackjackPreviewHarness() { }

    public static void main(String[] args) {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> method.getName().equals("getName") ? "world" : null
        );
        RecordingGateway gateway = new RecordingGateway();
        RecordingScheduler scheduler = new RecordingScheduler();
        BlackjackPreviewService service = new BlackjackPreviewService(
                name -> name.equals("world") ? world : null,
                gateway,
                scheduler
        );
        UUID admin = UUID.fromString("9a930ba7-f59b-40f0-9d1f-19de04f7da71");
        BlackjackTableDraft draft = new BlackjackTableDraft("table-1");
        draft.setDisplayAnchor(new BlackjackDisplayAnchor("world", 10.5D, 65.0D, 20.5D, 90.0F));
        draft.setSeat(1, new ActivityPosition("world", 10.5D, 64.5D, 24.5D, 180.0F, 0.0F));

        BlackjackPreviewService.PreviewResult draftResult = service.show(admin, draft);
        check(draftResult.status() == BlackjackPreviewService.PreviewStatus.SHOWN,
                "disabled draft table can be previewed without a session");
        check(draftResult.markers().equals(List.of("CENTER", "STATUS", "SEAT 1")),
                "partial setup renders only available anchors plus center and status");
        check(draftResult.missingComponents().contains("Dealer")
                        && draftResult.missingComponents().contains("Seat 5"),
                "partial setup reports missing components without rejecting the preview");
        String owner = BlackjackPreviewService.owner(admin, "table-1");
        check(owner.equals("blackjack-preview:" + admin + ":table-1")
                        && gateway.createdOwners.stream().allMatch(owner::equals),
                "preview uses its deterministic admin/table owner");
        check(gateway.markers.stream().allMatch(marker -> marker.location().getY() >= 65.10D),
                "preview markers are lifted from the configured table surface");

        ActivityPosition dealer = new ActivityPosition("world", 10.5D, 64.5D, 16.5D, 0.0F, 0.0F);
        draft.setDealer(dealer);
        BlackjackPreviewService.PreviewMarker dealerMarker = BlackjackPreviewService.buildMarkers(world, draft)
                .stream()
                .filter(marker -> marker.id().equals("dealer-hand"))
                .findFirst().orElseThrow();
        org.bukkit.Location productionDealerLocation = BlackjackDisplayGeometry.dealerHandLocation(world, dealer);
        check(dealerMarker.text().equals("DEALER HAND")
                        && distanceSquared(dealerMarker.location(), productionDealerLocation) < 0.0000001D,
                "preview dealer marker uses the exact floating production-hand location");

        draft.setEnabled(true);
        gateway.clearCreated();
        BlackjackPreviewService.PreviewResult enabledResult = service.show(admin, draft);
        check(enabledResult.status() == BlackjackPreviewService.PreviewStatus.SHOWN,
                "enabled table can be previewed without consulting an activity session");
        check(gateway.removedOwners.contains(owner) && scheduler.tasks.getFirst().cancelled,
                "starting a new preview cleans up and cancels the previous preview");

        BlackjackTableDraft missingSurface = new BlackjackTableDraft("missing-surface");
        BlackjackPreviewService.PreviewResult missingResult = service.show(admin, missingSurface);
        check(missingResult.status() == BlackjackPreviewService.PreviewStatus.MISSING_SURFACE
                        && missingResult.missingComponents().contains("Table Surface"),
                "missing table surface returns a graceful result with a setup hint");

        gateway.clearCreated();
        draft.setEnabled(false);
        service.show(admin, draft);
        RecordingScheduler.TaskState timeoutTask = scheduler.tasks.getLast();
        timeoutTask.run();
        check(gateway.removedOwners.getLast().equals(owner),
                "preview owner is removed when its twelve-second task expires");

        service.show(admin, draft);
        service.shutdown();
        check(scheduler.tasks.getLast().cancelled && gateway.removedOwners.getLast().equals(owner),
                "module shutdown cancels tasks and removes every preview");
        check(gateway.removedOwners.stream().noneMatch(value -> value.equals("blackjack:table-1")),
                "preview cleanup never touches the production display owner");

        testCleanupFaults(world, draft);
        testFailedReplacementAndTimeout(world, draft);
        testFailedCreationCleanup(world, draft);
        System.out.println("BlackjackPreviewHarness passed " + checks + " checks.");
    }

    private static void testCleanupFaults(World world, BlackjackTableDraft draft) {
        for (int mode = 0; mode < 3; mode++) {
            for (int mask : new int[]{0, 1, 2, 4, 5, 7}) {
                RecordingGateway gateway = new RecordingGateway();
                RecordingScheduler scheduler = new RecordingScheduler();
                BlackjackPreviewService service = new BlackjackPreviewService(n -> world, gateway, scheduler);
                for (int i = 0; i < 3; i++) service.show(new UUID(0, i + 1), draft);
                gateway.activeOwners.add("blackjack:foreign");
                gateway.failMask = mode == 0 ? 0 : mask;
                scheduler.failMask = mode == 1 ? 0 : mask;
                RuntimeException failure = capture(service::shutdown);
                check(gateway.removedOwners.size() == 3 && scheduler.cancelAttempts == 3,
                        "preview shutdown attempts every task and owner despite failure");
                check((failure == null) == (mask == 0), "preview shutdown reports incomplete cleanup");
                if (failure != null) {
                    check(leafCount(failure) == Integer.bitCount(mask) * (mode == 2 ? 2 : 1),
                            "preview aggregate reports every original external failure");
                    String context = failureTree(failure);
                    check(context.contains("BlackjackPreview shutdown") && context.contains("admin=")
                                    && context.contains("owner=blackjack-preview:"),
                            "preview aggregate retains service owner identity operation context");
                    if (mode != 1) check(context.contains("cancel failed") && context.contains("injected task cancel"),
                            "preview cancel original cause remains visible");
                    if (mode != 0) check(context.contains("removeOwner failed") && context.contains("injected preview remove"),
                            "preview display original cause remains visible");
                }
                check(gateway.activeOwners.size() == 1 + (mode == 0 ? 0 : Integer.bitCount(mask)),
                        "preview failed display ownership remains physically represented");
                check(gateway.activeOwners.contains("blackjack:foreign"), "preview shutdown leaves production foreign owner untouched");
                gateway.failMask = scheduler.failMask = 0;
                int cancels = scheduler.cancelAttempts;
                int removes = gateway.removedOwners.size();
                service.shutdown();
                check(scheduler.cancelAttempts == cancels + (mode == 1 ? 0 : Integer.bitCount(mask)),
                        "preview retry cancels only unfinished tasks");
                check(gateway.removedOwners.size() == removes + (mode == 0 ? 0 : Integer.bitCount(mask)),
                        "preview retry removes only unfinished marker owners");
                check(gateway.activeOwners.equals(java.util.Set.of("blackjack:foreign")),
                        "preview retry completes only owned cleanup");
                cancels = scheduler.cancelAttempts;
                removes = gateway.removedOwners.size();
                service.shutdown();
                check(scheduler.cancelAttempts == cancels && gateway.removedOwners.size() == removes,
                        "preview repeated shutdown is idempotent");
            }
        }
    }

    private static void testFailedReplacementAndTimeout(World world, BlackjackTableDraft draft) {
        RecordingGateway gateway = new RecordingGateway();
        RecordingScheduler scheduler = new RecordingScheduler();
        BlackjackPreviewService service = new BlackjackPreviewService(n -> world, gateway, scheduler);
        UUID admin = UUID.randomUUID();
        service.show(admin, draft);
        Runnable oldTimeout = scheduler.tasks.getFirst().action;
        gateway.failMask = 1;
        int creates = gateway.createdOwners.size();
        check(capture(() -> service.show(admin, draft)) != null, "failed preview clear prevents replacement");
        check(gateway.createdOwners.size() == creates && scheduler.tasks.size() == 1,
                "failed replacement creates no duplicate markers or task");
        gateway.failMask = 0;
        service.show(admin, draft);
        int removes = gateway.removedOwners.size();
        oldTimeout.run();
        check(gateway.removedOwners.size() == removes, "stale timeout cannot remove newer preview with identical owner");
        gateway.failMask = 1 << gateway.removedOwners.size();
        check(capture(scheduler.tasks.getLast().action) != null, "timeout remove failure is reported");
        int cancels = scheduler.cancelAttempts;
        gateway.failMask = 0;
        service.clear(admin);
        check(gateway.activeOwners.isEmpty() && scheduler.cancelAttempts == cancels,
                "expired failed preview can retry displays without cancelling finished task");
        service.show(admin, draft);
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class},
                (p, m, a) -> m.getName().equals("getUniqueId") ? admin : null);
        service.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        check(gateway.activeOwners.isEmpty() && scheduler.tasks.getLast().cancelled, "quit clears owned markers and task");
        service.clear(admin);
    }

    private static void testFailedCreationCleanup(World world, BlackjackTableDraft draft) {
        for (boolean schedule : new boolean[]{false, true}) {
            RecordingGateway gateway = new RecordingGateway();
            RecordingScheduler scheduler = new RecordingScheduler();
            BlackjackPreviewService service = new BlackjackPreviewService(n -> world, gateway, scheduler);
            UUID admin = UUID.randomUUID();
            gateway.failCreate = !schedule;
            scheduler.failSchedule = schedule;
            gateway.failMask = 1;
            RuntimeException failure = capture(() -> service.show(admin, draft));
            check(failure != null && failure.getMessage().contains(schedule ? "schedule" : "create"),
                    "preview creation failure retains original cause");
            check(failure.getSuppressed().length == 1 && failureTree(failure).contains("removeOwner"),
                    "failed preview creation cleanup is attached with owner context");
            check(gateway.activeOwners.size() == 1, "partially created preview remains owned after cleanup failure");
            gateway.failMask = 0;
            service.shutdown();
            check(gateway.activeOwners.isEmpty() && gateway.removedOwners.size() == 2,
                    "shutdown retries retained partially created preview");
            service.shutdown();
            check(gateway.removedOwners.size() == 2, "partial creation cleanup remains idempotent");
        }
    }

    private static int leafCount(Throwable failure) {
        int count = 0;
        if (failure.getCause() != null) count += leafCount(failure.getCause());
        for (Throwable suppressed : failure.getSuppressed()) count += leafCount(suppressed);
        return count == 0 ? 1 : count;
    }

    private static RuntimeException capture(Runnable action) {
        try { action.run(); return null; } catch (RuntimeException failure) { return failure; }
    }

    private static String failureTree(Throwable failure) {
        String text = failure.getMessage();
        if (failure.getCause() != null) text += " " + failureTree(failure.getCause());
        for (Throwable suppressed : failure.getSuppressed()) text += " " + failureTree(suppressed);
        return text;
    }

    private static double distanceSquared(org.bukkit.Location first, org.bukkit.Location second) {
        double x = first.getX() - second.getX();
        double y = first.getY() - second.getY();
        double z = first.getZ() - second.getZ();
        return x * x + y * y + z * z;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGateway implements BlackjackPreviewService.PreviewDisplayGateway {
        private final List<String> createdOwners = new ArrayList<>();
        private final List<BlackjackPreviewService.PreviewMarker> markers = new ArrayList<>();
        private final List<String> removedOwners = new ArrayList<>();
        private final java.util.Set<String> activeOwners = new java.util.HashSet<>();
        private int failMask;
        private boolean failCreate;

        @Override
        public void create(String owner, BlackjackPreviewService.PreviewMarker marker) {
            createdOwners.add(owner);
            activeOwners.add(owner);
            if (failCreate) throw new IllegalStateException("injected preview create");
            markers.add(marker);
        }

        @Override
        public void removeOwner(String owner) {
            removedOwners.add(owner);
            if ((failMask & (1 << (removedOwners.size() - 1))) != 0) throw new IllegalStateException("injected preview remove");
            activeOwners.remove(owner);
        }

        private void clearCreated() {
            createdOwners.clear();
            markers.clear();
        }
    }

    private static final class RecordingScheduler implements BlackjackPreviewService.TaskScheduler {
        private final List<TaskState> tasks = new ArrayList<>();
        private int failMask;
        private int cancelAttempts;
        private boolean failSchedule;

        @Override
        public BukkitTask schedule(Runnable action, long delayTicks) {
            if (failSchedule) throw new IllegalStateException("injected preview schedule");
            check(delayTicks == BlackjackPreviewService.PREVIEW_TICKS,
                    "preview timeout is centrally fixed at twelve seconds");
            TaskState state = new TaskState(action);
            tasks.add(state);
            return (BukkitTask) Proxy.newProxyInstance(
                    BukkitTask.class.getClassLoader(),
                    new Class<?>[]{BukkitTask.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("cancel")) {
                            cancelAttempts++;
                            if ((failMask & (1 << (cancelAttempts - 1))) != 0) throw new IllegalStateException("injected task cancel");
                            state.cancelled = true;
                            return null;
                        }
                        if (method.getReturnType() == boolean.class) return state.cancelled;
                        if (method.getReturnType() == int.class) return 0;
                        return null;
                    }
            );
        }

        private static final class TaskState {
            private final Runnable action;
            private boolean cancelled;

            private TaskState(Runnable action) {
                this.action = action;
            }

            private void run() {
                if (!cancelled) action.run();
            }
        }
    }
}

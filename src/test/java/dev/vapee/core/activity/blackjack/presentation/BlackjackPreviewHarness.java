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

        System.out.println("BlackjackPreviewHarness passed " + checks + " checks.");
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

        @Override
        public void create(String owner, BlackjackPreviewService.PreviewMarker marker) {
            createdOwners.add(owner);
            markers.add(marker);
        }

        @Override
        public void removeOwner(String owner) {
            removedOwners.add(owner);
        }

        private void clearCreated() {
            createdOwners.clear();
            markers.clear();
        }
    }

    private static final class RecordingScheduler implements BlackjackPreviewService.TaskScheduler {
        private final List<TaskState> tasks = new ArrayList<>();

        @Override
        public BukkitTask schedule(Runnable action, long delayTicks) {
            check(delayTicks == BlackjackPreviewService.PREVIEW_TICKS,
                    "preview timeout is centrally fixed at twelve seconds");
            TaskState state = new TaskState(action);
            tasks.add(state);
            return (BukkitTask) Proxy.newProxyInstance(
                    BukkitTask.class.getClassLoader(),
                    new Class<?>[]{BukkitTask.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("cancel")) {
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

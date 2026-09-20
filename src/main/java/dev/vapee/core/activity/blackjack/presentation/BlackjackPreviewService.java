package dev.vapee.core.activity.blackjack.presentation;

import dev.vapee.core.activity.blackjack.table.BlackjackDisplayAnchor;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDraft;
import dev.vapee.core.worlddisplay.WorldDisplayKey;
import dev.vapee.core.worlddisplay.WorldDisplayService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class BlackjackPreviewService implements Listener {

    public static final int PREVIEW_SECONDS = 12;
    public static final long PREVIEW_TICKS = PREVIEW_SECONDS * 20L;
    public static final float PREVIEW_SCALE = 0.22F;
    public static final double PREVIEW_LIFT = 0.10D;

    private final Function<String, World> worldLookup;
    private final PreviewDisplayGateway displayGateway;
    private final TaskScheduler taskScheduler;
    private final Map<UUID, PreviewState> activePreviews = new HashMap<>();

    public BlackjackPreviewService(JavaPlugin plugin, WorldDisplayService displayService) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        WorldDisplayService validatedDisplays = Objects.requireNonNull(displayService, "displayService");
        this.worldLookup = validatedPlugin.getServer()::getWorld;
        this.displayGateway = new PreviewDisplayGateway() {
            @Override
            public void create(String owner, PreviewMarker marker) {
                validatedDisplays.createText(
                        new WorldDisplayKey(owner, marker.id()),
                        marker.location(),
                        Component.text(marker.text(), marker.color()).decoration(TextDecoration.BOLD, true),
                        display -> configure(display)
                );
            }

            @Override
            public void removeOwner(String owner) {
                validatedDisplays.removeOwner(owner);
            }
        };
        this.taskScheduler = (action, delayTicks) -> validatedPlugin.getServer().getScheduler()
                .runTaskLater(validatedPlugin, action, delayTicks);
    }

    BlackjackPreviewService(
            Function<String, World> worldLookup,
            PreviewDisplayGateway displayGateway,
            TaskScheduler taskScheduler
    ) {
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
        this.displayGateway = Objects.requireNonNull(displayGateway, "displayGateway");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
    }

    public PreviewResult show(UUID adminId, BlackjackTableDraft draft) {
        UUID validatedAdmin = Objects.requireNonNull(adminId, "adminId");
        BlackjackTableDraft validatedDraft = Objects.requireNonNull(draft, "draft").copy();
        clear(validatedAdmin);

        BlackjackDisplayAnchor anchor = validatedDraft.getDisplayAnchor().orElse(null);
        List<String> missing = missingComponents(validatedDraft);
        if (anchor == null) {
            return new PreviewResult(PreviewStatus.MISSING_SURFACE, List.of(), missing);
        }
        World world = worldLookup.apply(anchor.worldName());
        if (world == null) {
            return new PreviewResult(PreviewStatus.WORLD_UNAVAILABLE, List.of(), missing);
        }

        List<PreviewMarker> markers = buildMarkers(world, validatedDraft);
        String owner = owner(validatedAdmin, validatedDraft.getId());
        try {
            for (PreviewMarker marker : markers) {
                displayGateway.create(owner, marker);
            }
            BukkitTask task = taskScheduler.schedule(
                    () -> expire(validatedAdmin, owner),
                    PREVIEW_TICKS
            );
            activePreviews.put(validatedAdmin, new PreviewState(owner, task));
        } catch (RuntimeException exception) {
            displayGateway.removeOwner(owner);
            throw exception;
        }
        return new PreviewResult(
                PreviewStatus.SHOWN,
                markers.stream().map(PreviewMarker::text).toList(),
                missing
        );
    }

    public void clear(UUID adminId) {
        PreviewState state = activePreviews.remove(Objects.requireNonNull(adminId, "adminId"));
        if (state == null) return;
        state.task().cancel();
        displayGateway.removeOwner(state.owner());
    }

    public void shutdown() {
        for (UUID adminId : List.copyOf(activePreviews.keySet())) {
            clear(adminId);
        }
        activePreviews.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    static String owner(UUID adminId, String tableId) {
        return "blackjack-preview:" + Objects.requireNonNull(adminId, "adminId")
                + ":" + Objects.requireNonNull(tableId, "tableId");
    }

    static List<PreviewMarker> buildMarkers(World world, BlackjackTableDraft draft) {
        BlackjackDisplayAnchor anchor = Objects.requireNonNull(draft, "draft").getDisplayAnchor().orElseThrow();
        List<PreviewMarker> markers = new ArrayList<>();
        markers.add(marker("center", "CENTER", BlackjackDisplayGeometry.tableCenter(world, anchor),
                NamedTextColor.AQUA));
        if (draft.getDealer().isPresent()) {
            markers.add(markerAt("dealer-hand", "DEALER HAND", BlackjackDisplayGeometry.dealerHandLocation(
                    world, draft.getDealer().orElseThrow()), NamedTextColor.GOLD));
        }
        markers.add(marker("status", "STATUS", BlackjackDisplayGeometry.statusAnchor(world, anchor),
                NamedTextColor.YELLOW));
        draft.getSeatDefinitions().forEach((number, seat) -> markers.add(marker(
                "seat-" + number,
                "SEAT " + number,
                BlackjackDisplayGeometry.seatCardAnchor(world, anchor, seat.position()),
                NamedTextColor.GREEN
        )));
        return List.copyOf(markers);
    }

    static List<String> missingComponents(BlackjackTableDraft draft) {
        BlackjackTableDraft validated = Objects.requireNonNull(draft, "draft");
        List<String> missing = new ArrayList<>();
        if (validated.getDisplayAnchor().isEmpty()) missing.add("Table Surface");
        if (validated.getDealer().isEmpty()) missing.add("Dealer");
        for (int seat = 1; seat <= 5; seat++) {
            if (!validated.getSeatDefinitions().containsKey(seat)) missing.add("Seat " + seat);
        }
        return List.copyOf(missing);
    }

    private static PreviewMarker marker(
            String id,
            String text,
            Location location,
            NamedTextColor color
    ) {
        return new PreviewMarker(id, text, location.clone().add(0.0D, PREVIEW_LIFT, 0.0D), color);
    }

    private static PreviewMarker markerAt(
            String id,
            String text,
            Location location,
            NamedTextColor color
    ) {
        return new PreviewMarker(id, text, location, color);
    }

    private static void configure(org.bukkit.entity.TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(145, 18, 18, 18));
        display.setLineWidth(80);
        display.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(PREVIEW_SCALE), new Quaternionf()));
    }

    private void expire(UUID adminId, String expectedOwner) {
        PreviewState state = activePreviews.get(adminId);
        if (state == null || !state.owner().equals(expectedOwner)) return;
        activePreviews.remove(adminId);
        displayGateway.removeOwner(expectedOwner);
    }

    public enum PreviewStatus {
        SHOWN,
        MISSING_SURFACE,
        WORLD_UNAVAILABLE
    }

    public record PreviewResult(
            PreviewStatus status,
            List<String> markers,
            List<String> missingComponents
    ) {
        public PreviewResult {
            status = Objects.requireNonNull(status, "status");
            markers = List.copyOf(Objects.requireNonNull(markers, "markers"));
            missingComponents = List.copyOf(Objects.requireNonNull(missingComponents, "missingComponents"));
        }
    }

    record PreviewMarker(String id, String text, Location location, NamedTextColor color) {
        PreviewMarker {
            id = Objects.requireNonNull(id, "id");
            text = Objects.requireNonNull(text, "text");
            location = Objects.requireNonNull(location, "location").clone();
            color = Objects.requireNonNull(color, "color");
        }
    }

    interface PreviewDisplayGateway {
        void create(String owner, PreviewMarker marker);

        void removeOwner(String owner);
    }

    @FunctionalInterface
    interface TaskScheduler {
        BukkitTask schedule(Runnable action, long delayTicks);
    }

    private record PreviewState(String owner, BukkitTask task) {
        private PreviewState {
            owner = Objects.requireNonNull(owner, "owner");
            task = Objects.requireNonNull(task, "task");
        }
    }
}

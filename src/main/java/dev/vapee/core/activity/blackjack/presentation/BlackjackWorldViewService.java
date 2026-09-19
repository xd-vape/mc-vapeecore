package dev.vapee.core.activity.blackjack.presentation;

import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.BlackjackPlayerRound;
import dev.vapee.core.activity.blackjack.BlackjackRoundPhase;
import dev.vapee.core.activity.blackjack.BlackjackService;
import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import dev.vapee.core.worlddisplay.WorldDisplayKey;
import dev.vapee.core.worlddisplay.WorldDisplayService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class BlackjackWorldViewService implements BlackjackTableService.LifecycleCallbacks {

    private final JavaPlugin plugin;
    private final WorldDisplayService displayService;
    private final BlackjackTableService tableService;
    private final BlackjackService blackjackService;
    private final Map<String, Set<String>> activeKeys = new HashMap<>();

    public BlackjackWorldViewService(JavaPlugin plugin, WorldDisplayService displayService,
                                     BlackjackTableService tableService, BlackjackService blackjackService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.displayService = Objects.requireNonNull(displayService, "displayService");
        this.tableService = Objects.requireNonNull(tableService, "tableService");
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
    }

    public void refresh(BlackjackSession session) {
        BlackjackSession validated = Objects.requireNonNull(session, "session");
        tableService.getDefinition(validated.getVenue().id())
                .ifPresent(definition -> render(definition, validated));
    }

    @Override
    public void onEnabled(BlackjackTableDefinition definition, BlackjackSession session) {
        render(definition, session);
    }

    @Override
    public void onDisabled(String tableId) {
        activeKeys.remove(tableId);
        displayService.removeOwner(owner(tableId));
    }

    public void shutdown() {
        for (String tableId : Set.copyOf(activeKeys.keySet())) onDisabled(tableId);
        activeKeys.clear();
    }

    private void render(BlackjackTableDefinition definition, BlackjackSession session) {
        try {
            World world = plugin.getServer().getWorld(definition.area().worldName());
            if (world == null) return;
            Location center = BlackjackDisplayGeometry.tableCenter(world, definition);
            Map<String, DisplaySpec> desired = new LinkedHashMap<>();
            desired.put("status", new DisplaySpec(
                    BlackjackDisplayGeometry.dealerAnchor(world, definition).add(0, BlackjackDisplayGeometry.STATUS_HEIGHT, 0),
                    statusText(session), Kind.STATUS
            ));
            if (session.getState() == ActivityState.ACTIVE) {
                addDealer(desired, world, center, definition, session);
            }
            for (var participant : session.getParticipants()) {
                Integer seatNumber = blackjackService.getSeatNumber(participant.uniqueId()).orElse(null);
                if (seatNumber == null) continue;
                BlackjackSeat seat = definition.seats().stream()
                        .filter(value -> value.number() == seatNumber).findFirst().orElse(null);
                if (seat == null) continue;
                addPlayer(desired, world, center, definition, session, seat, participant.uniqueId());
            }
            apply(definition.id(), desired);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not refresh blackjack world view for '" + definition.id() + "'.", exception);
        }
    }

    private void addDealer(Map<String, DisplaySpec> desired, World world, Location center,
                           BlackjackTableDefinition definition, BlackjackSession session) {
        Location anchor = BlackjackDisplayGeometry.dealerAnchor(world, definition);
        boolean hidden = session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS;
        int value = hidden && session.getDealerHand().size() > 0
                ? new BlackjackHand(java.util.List.of(session.getDealerHand().getCards().getFirst())).getValue()
                : session.getDealerHand().getValue();
        desired.put("dealer-label", new DisplaySpec(anchor.clone().add(0, BlackjackDisplayGeometry.LABEL_HEIGHT, 0),
                Component.text("Dealer • " + value, NamedTextColor.GOLD), Kind.LABEL));
        var cards = session.getDealerHand().getCards();
        for (int index = 0; index < cards.size(); index++) {
            Component text = hidden && index == 1
                    ? Component.text("?", NamedTextColor.GRAY)
                    : cardText(cards.get(index));
            desired.put("dealer-card-" + index, new DisplaySpec(
                    BlackjackDisplayGeometry.card(anchor, center, index, cards.size()), text, Kind.CARD));
        }
    }

    private void addPlayer(Map<String, DisplaySpec> desired, World world, Location center,
                           BlackjackTableDefinition definition, BlackjackSession session,
                           BlackjackSeat seat, UUID playerId) {
        Location anchor = BlackjackDisplayGeometry.playerAnchor(world, definition, seat);
        BlackjackPlayerRound round = session.getPlayerRound(playerId).orElse(null);
        String detail = round == null ? "Ready" : Integer.toString(round.getHand().getValue());
        if (round != null && round.getOutcome().isPresent()) detail = round.getOutcome().orElseThrow().name();
        desired.put("seat-" + seat.number() + "-label", new DisplaySpec(
                anchor.clone().add(0, BlackjackDisplayGeometry.LABEL_HEIGHT, 0),
                Component.text(blackjackService.getPlayerName(playerId) + " • " + detail, NamedTextColor.AQUA),
                Kind.LABEL));
        if (round == null) return;
        var cards = round.getHand().getCards();
        for (int index = 0; index < cards.size(); index++) {
            desired.put("seat-" + seat.number() + "-card-" + index, new DisplaySpec(
                    BlackjackDisplayGeometry.card(anchor, center, index, cards.size()),
                    cardText(cards.get(index)), Kind.CARD));
        }
    }

    private void apply(String tableId, Map<String, DisplaySpec> desired) {
        String owner = owner(tableId);
        Set<String> previous = activeKeys.getOrDefault(tableId, Set.of());
        for (String obsolete : previous) {
            if (!desired.containsKey(obsolete)) displayService.remove(new WorldDisplayKey(owner, obsolete));
        }
        for (var entry : desired.entrySet()) {
            WorldDisplayKey key = new WorldDisplayKey(owner, entry.getKey());
            DisplaySpec spec = entry.getValue();
            if (displayService.getHandle(key).isPresent()) {
                boolean alive = displayService.updateText(key, spec.text());
                if (alive) alive = displayService.teleport(key, spec.location());
                if (alive) continue;
            }
            if (displayService.getHandle(key).isEmpty()) {
                displayService.createText(key, spec.location(), spec.text(), display -> configure(display, spec.kind()));
            }
        }
        activeKeys.put(tableId, new HashSet<>(desired.keySet()));
    }

    private void configure(TextDisplay display, Kind kind) {
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(kind == Kind.CARD ? 190 : 110, 20, 20, 20));
        display.setLineWidth(120);
        if (kind == Kind.CARD) {
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf().rotationX((float) Math.toRadians(-90.0D)),
                    new Vector3f(BlackjackDisplayGeometry.CARD_SCALE), new Quaternionf()));
        } else {
            display.setBillboard(Display.Billboard.CENTER);
            float scale = kind == Kind.STATUS ? 0.60F : 0.42F;
            display.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
        }
    }

    private Component statusText(BlackjackSession session) {
        String status = session.getState() == ActivityState.AVAILABLE
                ? "Blackjack • Ready • Right-click a seat"
                : "Blackjack • " + session.getRoundPhase().name().replace('_', ' ');
        return Component.text(status, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    private Component cardText(BlackjackCard card) {
        return Component.text(card.getDisplayText(), card.suit().isRed() ? NamedTextColor.RED : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true);
    }

    private String owner(String tableId) {
        return "blackjack:" + tableId;
    }

    private enum Kind { STATUS, LABEL, CARD }

    private record DisplaySpec(Location location, Component text, Kind kind) { }
}

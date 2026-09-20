package dev.vapee.core.activity.blackjack.presentation;

import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.BlackjackPlayerRound;
import dev.vapee.core.activity.blackjack.BlackjackOutcome;
import dev.vapee.core.activity.blackjack.BlackjackRoundPhase;
import dev.vapee.core.activity.blackjack.BlackjackService;
import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackDisplayAnchor;
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
    private final Map<String, Map<String, DisplayStyle>> activeStyles = new HashMap<>();

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
        activeStyles.remove(tableId);
        displayService.removeOwner(owner(tableId));
    }

    public void shutdown() {
        for (String tableId : Set.copyOf(activeStyles.keySet())) onDisabled(tableId);
        activeStyles.clear();
    }

    private void render(BlackjackTableDefinition definition, BlackjackSession session) {
        try {
            World world = plugin.getServer().getWorld(definition.area().worldName());
            if (world == null) return;
            BlackjackDisplayAnchor surface = BlackjackDisplayGeometry.surfaceAnchor(definition);
            Map<String, DisplaySpec> desired = new LinkedHashMap<>();
            desired.put("status", new DisplaySpec(
                    BlackjackDisplayGeometry.statusAnchor(world, surface),
                    statusText(session), DisplayStyle.STATUS, null
            ));
            if (session.getState() == ActivityState.ACTIVE) {
                addDealer(desired, world, surface, session);
            }
            for (var participant : session.getParticipants()) {
                Integer seatNumber = blackjackService.getSeatNumber(participant.uniqueId()).orElse(null);
                if (seatNumber == null) continue;
                BlackjackSeat seat = definition.seats().stream()
                        .filter(value -> value.number() == seatNumber).findFirst().orElse(null);
                if (seat == null) continue;
                addPlayer(desired, world, surface, session, seat, participant.uniqueId());
            }
            apply(definition.id(), desired);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not refresh blackjack world view for '" + definition.id() + "'.", exception);
        }
    }

    private void addDealer(Map<String, DisplaySpec> desired, World world, BlackjackDisplayAnchor surface,
                           BlackjackSession session) {
        boolean hidden = session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS;
        int value = hidden && session.getDealerHand().size() > 0
                ? new BlackjackHand(java.util.List.of(session.getDealerHand().getCards().getFirst())).getValue()
                : session.getDealerHand().getValue();
        desired.put("dealer-label", new DisplaySpec(
                BlackjackDisplayGeometry.dealerValueAnchor(world, surface),
                Component.text("Dealer " + value, NamedTextColor.GOLD), DisplayStyle.VALUE, null));
        var cards = session.getDealerHand().getCards();
        for (int index = 0; index < cards.size(); index++) {
            boolean hiddenCard = hidden && index == 1;
            desired.put("dealer-card-" + index, new DisplaySpec(
                    BlackjackDisplayGeometry.dealerCard(world, surface, index, cards.size()),
                    hiddenCard ? hiddenCardText() : cardText(cards.get(index)),
                    hiddenCard ? DisplayStyle.HIDDEN_CARD : DisplayStyle.OPEN_CARD,
                    BlackjackDisplayGeometry.dealerCardRotation(surface)));
        }
    }

    private void addPlayer(Map<String, DisplaySpec> desired, World world, BlackjackDisplayAnchor surface,
                           BlackjackSession session,
                           BlackjackSeat seat, UUID playerId) {
        BlackjackPlayerRound round = session.getPlayerRound(playerId).orElse(null);
        String detail = playerLabelText(round);
        if (detail == null) return;
        boolean settled = round.getOutcome().isPresent();
        desired.put("seat-" + seat.number() + "-label", new DisplaySpec(
                settled
                        ? BlackjackDisplayGeometry.resultAnchor(world, surface, seat.position())
                        : BlackjackDisplayGeometry.handValueAnchor(world, surface, seat.position()),
                settled ? resultText(round.getOutcome().orElseThrow())
                        : Component.text(detail, NamedTextColor.AQUA),
                settled ? DisplayStyle.RESULT : DisplayStyle.VALUE,
                BlackjackDisplayGeometry.seatFacingRotation(world, surface, seat.position())));
        var cards = round.getHand().getCards();
        for (int index = 0; index < cards.size(); index++) {
            desired.put("seat-" + seat.number() + "-card-" + index, new DisplaySpec(
                    BlackjackDisplayGeometry.seatCard(world, surface, seat.position(), index, cards.size()),
                    cardText(cards.get(index)), DisplayStyle.OPEN_CARD,
                    BlackjackDisplayGeometry.seatCardRotation(world, surface, seat.position())));
        }
    }

    private void apply(String tableId, Map<String, DisplaySpec> desired) {
        String owner = owner(tableId);
        Map<String, DisplayStyle> previous = activeStyles.getOrDefault(tableId, Map.of());
        for (String obsolete : previous.keySet()) {
            if (!desired.containsKey(obsolete)) displayService.remove(new WorldDisplayKey(owner, obsolete));
        }
        for (var entry : desired.entrySet()) {
            WorldDisplayKey key = new WorldDisplayKey(owner, entry.getKey());
            DisplaySpec spec = entry.getValue();
            if (displayService.getHandle(key).isPresent() && previous.get(entry.getKey()) != spec.style()) {
                displayService.remove(key);
            }
            if (displayService.getHandle(key).isPresent()) {
                boolean alive = displayService.updateText(key, spec.text());
                if (alive) alive = displayService.teleport(key, spec.location());
                if (alive) continue;
            }
            if (displayService.getHandle(key).isEmpty()) {
                displayService.createText(key, spec.location(), spec.text(), display -> configure(display, spec));
            }
        }
        Map<String, DisplayStyle> styles = new HashMap<>();
        desired.forEach((key, spec) -> styles.put(key, spec.style()));
        activeStyles.put(tableId, Map.copyOf(styles));
    }

    private void configure(TextDisplay display, DisplaySpec spec) {
        DisplayStyle style = spec.style();
        display.setSeeThrough(false);
        display.setShadowed(style != DisplayStyle.OPEN_CARD);
        display.setBackgroundColor(background(style));
        display.setLineWidth(style == DisplayStyle.STATUS ? 120 : 80);
        boolean fixed = style == DisplayStyle.OPEN_CARD || style == DisplayStyle.HIDDEN_CARD
                || spec.rotation() != null;
        display.setBillboard(fixed ? Display.Billboard.FIXED : Display.Billboard.CENTER);
        Quaternionf rotation = spec.rotation() == null ? new Quaternionf() : new Quaternionf(spec.rotation());
        display.setTransformation(new Transformation(
                new Vector3f(), rotation, new Vector3f(scale(style)), new Quaternionf()));
    }

    private Component statusText(BlackjackSession session) {
        String detail;
        if (session.getState() == ActivityState.AVAILABLE) {
            detail = idleStatusText(session.getParticipantCount(), blackjackService.getTableCapacity(session));
        } else if (session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS) {
            detail = session.getCurrentTurnPlayer()
                    .map(playerId -> blackjackService.getPlayerName(playerId) + "'s turn")
                    .orElse("Player turn");
        } else if (session.getRoundPhase() == BlackjackRoundPhase.DEALER_TURN) {
            detail = "Dealer";
        } else if (session.getRoundPhase() == BlackjackRoundPhase.SETTLED) {
            detail = "Round complete";
        } else {
            detail = "Table available";
        }
        String status = "Blackjack\n" + detail;
        return Component.text(status, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    private static String idleStatusText(int participants, int capacity) {
        return participants + " / " + capacity;
    }

    private static String playerLabelText(BlackjackPlayerRound round) {
        if (round == null) return null;
        return round.getOutcome().map(Enum::name)
                .orElse(Integer.toString(round.getHand().getValue()));
    }

    private Component cardText(BlackjackCard card) {
        return Component.text(card.getDisplayText(), card.suit().isRed() ? NamedTextColor.RED : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true);
    }

    private Component hiddenCardText() {
        return Component.text("◆", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true);
    }

    private Component resultText(BlackjackOutcome outcome) {
        NamedTextColor color = switch (outcome) {
            case BLACKJACK -> NamedTextColor.GOLD;
            case WIN -> NamedTextColor.GREEN;
            case PUSH -> NamedTextColor.YELLOW;
            case LOSS, BUST -> NamedTextColor.RED;
        };
        return Component.text(outcome.name(), color).decoration(TextDecoration.BOLD, true);
    }

    private static Color background(DisplayStyle style) {
        return switch (style) {
            case OPEN_CARD -> Color.fromARGB(232, 245, 245, 238);
            case HIDDEN_CARD -> Color.fromARGB(235, 38, 45, 60);
            case STATUS -> Color.fromARGB(92, 20, 20, 20);
            case RESULT -> Color.fromARGB(75, 20, 20, 20);
            case VALUE -> Color.fromARGB(55, 20, 20, 20);
        };
    }

    private static float scale(DisplayStyle style) {
        return switch (style) {
            case OPEN_CARD, HIDDEN_CARD -> BlackjackDisplayGeometry.CARD_SCALE;
            case STATUS -> BlackjackDisplayGeometry.STATUS_SCALE;
            case RESULT -> BlackjackDisplayGeometry.RESULT_SCALE;
            case VALUE -> BlackjackDisplayGeometry.HAND_VALUE_SCALE;
        };
    }

    private String owner(String tableId) {
        return "blackjack:" + tableId;
    }

    private enum DisplayStyle { OPEN_CARD, HIDDEN_CARD, STATUS, RESULT, VALUE }

    private record DisplaySpec(Location location, Component text, DisplayStyle style, Quaternionf rotation) { }
}

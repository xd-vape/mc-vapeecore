package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.presentation.BlackjackAction;
import dev.vapee.core.activity.blackjack.presentation.BlackjackDisplayGeometry;
import dev.vapee.core.activity.blackjack.presentation.BlackjackWorldViewService;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackRank;
import dev.vapee.core.activity.blackjack.card.BlackjackSuit;
import dev.vapee.core.activity.blackjack.table.BlackjackBlockPosition;
import dev.vapee.core.activity.blackjack.table.BlackjackDisplayAnchor;
import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
import dev.vapee.core.activity.blackjack.table.BlackjackTableInteractionMode;
import dev.vapee.core.activity.location.ActivityArea;
import dev.vapee.core.activity.location.ActivityPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

public final class BlackjackPresentationHarness {

    private static int checks;

    private BlackjackPresentationHarness() { }

    public static void main(String[] args) throws Exception {
        check(Arrays.equals(BlackjackAction.values(), new BlackjackAction[]{
                BlackjackAction.DEAL, BlackjackAction.HIT, BlackjackAction.STAND,
                BlackjackAction.DOUBLE, BlackjackAction.LEAVE
        }), "action contract is exact and ordered");
        for (BlackjackAction action : BlackjackAction.values()) {
            check(BlackjackAction.fromPersistentId(action.persistentId()).orElseThrow() == action,
                    "action PDC roundtrip for " + action);
        }
        check(BlackjackAction.fromPersistentId("unknown").isEmpty(), "unknown action is rejected");
        check(BlackjackDisplayGeometry.CARD_SCALE >= 0.45F
                && BlackjackDisplayGeometry.CARD_SCALE <= 0.60F, "card scale is readable but compact");
        check(BlackjackDisplayGeometry.CARD_SPACING >= 0.32D
                && BlackjackDisplayGeometry.CARD_SPACING <= 0.40D, "card spacing matches larger cards");
        check(BlackjackDisplayGeometry.CARD_HOVER >= 0.02D
                && BlackjackDisplayGeometry.CARD_HOVER <= 0.05D, "cards hover just above the table surface");
        check(BlackjackDisplayGeometry.HAND_VALUE_SCALE >= 0.18F
                && BlackjackDisplayGeometry.HAND_VALUE_SCALE <= 0.28F, "hand values stay visually quiet");
        check(BlackjackDisplayGeometry.RESULT_SCALE >= 0.22F
                && BlackjackDisplayGeometry.RESULT_SCALE <= 0.28F, "results use their own compact scale");
        check(BlackjackDisplayGeometry.STATUS_SCALE >= 0.32F
                && BlackjackDisplayGeometry.STATUS_SCALE <= 0.42F, "status stays compact");
        check(invokeText("idleStatusText", new Class<?>[]{int.class, int.class}, 1, 5)
                        .equals("1 / 5"),
                "idle table status is player-facing and contains no internal enum");
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRound.class}, (Object) null) == null,
                "idle seated player creates no Ready label");
        BlackjackPlayerRound activeRound = new BlackjackPlayerRound(java.util.UUID.randomUUID());
        activeRound.getHand().add(new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.SPADES));
        activeRound.getHand().add(new BlackjackCard(BlackjackRank.SEVEN, BlackjackSuit.HEARTS));
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRound.class}, activeRound)
                        .equals("17"),
                "active player label is a compact hand value");
        activeRound.settle(BlackjackOutcome.WIN);
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRound.class}, activeRound).equals("WIN"),
                "settled player label is the short outcome");
        check(new BlackjackCard(BlackjackRank.ACE, BlackjackSuit.SPADES).getDisplayText().equals("A♠")
                        && new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.HEARTS).getDisplayText().equals("10♥"),
                "card text is compact without rank/suit whitespace");
        assertDistinctCardStyles();

        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> method.getName().equals("getName") ? "world" : null);
        BlackjackSeat seat = new BlackjackSeat(1,
                new ActivityPosition("world", 5.5, 64.5, 9.5, 180, 0),
                new BlackjackBlockPosition("world", 5, 64, 9));
        BlackjackTableDefinition definition = new BlackjackTableDefinition(
                "table", new ActivityArea("world", 0, 64, 0, 10, 65, 10),
                new ActivityPosition("world", 8, 64.5, 5, 90, 0), null, List.of(seat),
                BlackjackTableInteractionMode.MODERN_SEAT_CLICK,
                new BlackjackDisplayAnchor("world", 5.5, 64.75, 5.5, 180.0F));
        Location center = BlackjackDisplayGeometry.tableCenter(world, definition);
        check(center.getX() == 5.5D && center.getY() == 64.75D && center.getZ() == 5.5D,
                "configured display anchor is the table geometry source of truth");
        Vector forward = BlackjackDisplayGeometry.tableForward(definition.displayAnchor());
        Vector right = BlackjackDisplayGeometry.tableRight(definition.displayAnchor());
        check(Math.abs(forward.length() - 1.0D) < 0.0001D
                        && Math.abs(right.length() - 1.0D) < 0.0001D
                        && Math.abs(forward.dot(right)) < 0.0001D,
                "table-local forward and right vectors are normalized and perpendicular");
        check(forward.getZ() < -0.999D && right.getX() > 0.999D,
                "display yaw determines stable table-local directions");
        Location anchor = BlackjackDisplayGeometry.seatCardAnchor(world, definition, seat);
        check(distanceSquared(anchor, center) < distanceSquared(new Location(world, 5.5, 64.5, 9.5), center),
                "player card anchor moves from the seat toward table center");
        Location first = BlackjackDisplayGeometry.seatCard(world, definition.displayAnchor(), seat.position(), 0, 2);
        Location second = BlackjackDisplayGeometry.seatCard(world, definition.displayAnchor(), seat.position(), 1, 2);
        check(Math.abs(Math.sqrt(distanceSquared(first, second)) - BlackjackDisplayGeometry.CARD_SPACING) < 0.0001D,
                "cards use deterministic geometry spacing");
        check(Math.abs(first.getY() - (64.75D + BlackjackDisplayGeometry.CARD_HOVER)) < 0.0001D,
                "player cards use display surface plus card hover");
        assertHorizontalOrientation(BlackjackDisplayGeometry.seatCardRotation(
                        world, definition.displayAnchor(), seat.position()), anchor, center,
                "player cards read from the seat toward table center");
        Location dealerAnchor = BlackjackDisplayGeometry.dealerCardAnchor(world, definition);
        check(Math.abs(dealerAnchor.getY() - (64.75D + BlackjackDisplayGeometry.CARD_HOVER)) < 0.0001D,
                "dealer cards use table surface height instead of dealer feet height");
        check(dealerAnchor.getZ() < center.getZ(), "dealer anchor uses the table-local forward side");
        assertHorizontalOrientation(BlackjackDisplayGeometry.dealerCardRotation(definition.displayAnchor()),
                center, dealerAnchor, "dealer cards read from the player/table side");
        Location resultAnchor = BlackjackDisplayGeometry.resultAnchor(
                world, definition.displayAnchor(), seat.position());
        check(Math.abs(resultAnchor.getY() - (64.75D + BlackjackDisplayGeometry.RESULT_HEIGHT)) < 0.0001D
                        && distanceSquared(resultAnchor, center) < distanceSquared(anchor, center),
                "result stays small and immediately behind the player's cards");
        Location statusAnchor = BlackjackDisplayGeometry.statusAnchor(world, definition.displayAnchor());
        check(Math.abs(statusAnchor.getY() - (64.75D + BlackjackDisplayGeometry.STATUS_HEIGHT)) < 0.0001D
                        && statusAnchor.getZ() < center.getZ(),
                "status stays close to the dealer side of the physical table");

        BlackjackTableDefinition legacy = new BlackjackTableDefinition(
                "legacy", definition.area(), definition.dealer(),
                new BlackjackBlockPosition("world", 5, 64, 5),
                List.of(new BlackjackSeat(1, seat.position())),
                BlackjackTableInteractionMode.LEGACY_INTERACTION);
        check(BlackjackDisplayGeometry.tableCenter(world, legacy).getY() == definition.dealer().y(),
                "missing display anchor retains legacy dealer-height fallback");
        System.out.println("BlackjackPresentationHarness passed " + checks + " checks.");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertDistinctCardStyles() throws Exception {
        Class<?> styleType = Arrays.stream(BlackjackWorldViewService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("DisplayStyle"))
                .findFirst().orElseThrow();
        Object open = Enum.valueOf((Class) styleType, "OPEN_CARD");
        Object hidden = Enum.valueOf((Class) styleType, "HIDDEN_CARD");
        var background = BlackjackWorldViewService.class.getDeclaredMethod("background", styleType);
        background.setAccessible(true);
        Color openColor = (Color) background.invoke(null, open);
        Color hiddenColor = (Color) background.invoke(null, hidden);
        check(!openColor.equals(hiddenColor) && openColor.getRed() > hiddenColor.getRed(),
                "open and hidden cards use visibly different styles");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static double distanceSquared(Location first, Location second) {
        double x = first.getX() - second.getX();
        double y = first.getY() - second.getY();
        double z = first.getZ() - second.getZ();
        return x * x + y * y + z * z;
    }

    private static void assertHorizontalOrientation(
            Quaternionf rotation,
            Location from,
            Location to,
            String message
    ) {
        Vector3f transformedUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
        Vector3f expected = new Vector3f(
                (float) (to.getX() - from.getX()),
                0.0F,
                (float) (to.getZ() - from.getZ())
        ).normalize();
        Vector3f transformedNormal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(rotation);
        check(transformedUp.dot(expected) > 0.999F && transformedNormal.y > 0.999F, message);
    }

    private static String invokeText(String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        var method = BlackjackWorldViewService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (String) method.invoke(null, arguments);
    }
}

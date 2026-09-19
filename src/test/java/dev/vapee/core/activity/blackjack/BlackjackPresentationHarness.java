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
        check(BlackjackDisplayGeometry.LABEL_SCALE >= 0.22F
                && BlackjackDisplayGeometry.LABEL_SCALE <= 0.28F, "labels stay visually quiet");
        check(BlackjackDisplayGeometry.STATUS_SCALE >= 0.32F
                && BlackjackDisplayGeometry.STATUS_SCALE <= 0.42F, "status stays compact");
        check(invokeText("idleStatusText", new Class<?>[]{int.class, int.class}, 1, 5)
                        .equals("1 / 5 seated"),
                "idle table status is player-facing and contains no internal enum");
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRound.class}, (Object) null) == null,
                "idle seated player creates no Ready label");
        BlackjackPlayerRound activeRound = new BlackjackPlayerRound(java.util.UUID.randomUUID());
        activeRound.getHand().add(new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.SPADES));
        activeRound.getHand().add(new BlackjackCard(BlackjackRank.SEVEN, BlackjackSuit.HEARTS));
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRound.class}, activeRound)
                        .equals("Hand: 17"),
                "active player label is a compact hand value");
        activeRound.settle(BlackjackOutcome.WIN);
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRound.class}, activeRound).equals("WIN"),
                "settled player label is the short outcome");

        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> method.getName().equals("getName") ? "world" : null);
        BlackjackSeat seat = new BlackjackSeat(1,
                new ActivityPosition("world", 1.5, 64.5, 5.5, -90, 0),
                new BlackjackBlockPosition("world", 1, 64, 5));
        BlackjackTableDefinition definition = new BlackjackTableDefinition(
                "table", new ActivityArea("world", 0, 64, 0, 10, 65, 10),
                new ActivityPosition("world", 8, 64.5, 5, 90, 0), null, List.of(seat),
                BlackjackTableInteractionMode.MODERN_SEAT_CLICK,
                new BlackjackDisplayAnchor("world", 5.5, 64.75, 5.5, 180.0F));
        Location center = BlackjackDisplayGeometry.tableCenter(world, definition);
        check(center.getX() == 5.5D && center.getY() == 64.75D && center.getZ() == 5.5D,
                "configured display anchor is the table geometry source of truth");
        Location anchor = BlackjackDisplayGeometry.playerAnchor(world, definition, seat);
        check(distanceSquared(anchor, center) < distanceSquared(new Location(world, 1.5, 64.5, 5.5), center),
                "player card anchor moves from the seat toward table center");
        Location first = BlackjackDisplayGeometry.card(anchor, center, 0, 2);
        Location second = BlackjackDisplayGeometry.card(anchor, center, 1, 2);
        check(Math.abs(Math.sqrt(distanceSquared(first, second)) - BlackjackDisplayGeometry.CARD_SPACING) < 0.0001D,
                "cards use deterministic geometry spacing");
        check(Math.abs(first.getY() - (64.75D + BlackjackDisplayGeometry.CARD_HOVER)) < 0.0001D,
                "player cards use display surface plus card hover");
        assertHorizontalOrientation(BlackjackDisplayGeometry.playerCardRotation(anchor, center), anchor, center,
                "player cards read from the seat toward table center");
        Location dealerAnchor = BlackjackDisplayGeometry.dealerAnchor(world, definition);
        assertHorizontalOrientation(BlackjackDisplayGeometry.dealerCardRotation(dealerAnchor, center),
                center, dealerAnchor, "dealer cards read from the player/table side");

        BlackjackTableDefinition legacy = new BlackjackTableDefinition(
                "legacy", definition.area(), definition.dealer(),
                new BlackjackBlockPosition("world", 5, 64, 5),
                List.of(new BlackjackSeat(1, seat.position())),
                BlackjackTableInteractionMode.LEGACY_INTERACTION);
        check(BlackjackDisplayGeometry.tableCenter(world, legacy).getY() == definition.dealer().y(),
                "missing display anchor retains legacy dealer-height fallback");
        System.out.println("BlackjackPresentationHarness passed " + checks + " checks.");
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

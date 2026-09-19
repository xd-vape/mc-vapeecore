package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.presentation.BlackjackAction;
import dev.vapee.core.activity.blackjack.presentation.BlackjackDisplayGeometry;
import dev.vapee.core.activity.blackjack.table.BlackjackBlockPosition;
import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
import dev.vapee.core.activity.blackjack.table.BlackjackTableInteractionMode;
import dev.vapee.core.activity.location.ActivityArea;
import dev.vapee.core.activity.location.ActivityPosition;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

public final class BlackjackPresentationHarness {

    private static int checks;

    private BlackjackPresentationHarness() { }

    public static void main(String[] args) {
        check(Arrays.equals(BlackjackAction.values(), new BlackjackAction[]{
                BlackjackAction.DEAL, BlackjackAction.HIT, BlackjackAction.STAND,
                BlackjackAction.DOUBLE, BlackjackAction.LEAVE
        }), "action contract is exact and ordered");
        for (BlackjackAction action : BlackjackAction.values()) {
            check(BlackjackAction.fromPersistentId(action.persistentId()).orElseThrow() == action,
                    "action PDC roundtrip for " + action);
        }
        check(BlackjackAction.fromPersistentId("unknown").isEmpty(), "unknown action is rejected");
        check(BlackjackDisplayGeometry.CARD_SCALE > 0.0F, "card scale is positive");
        check(BlackjackDisplayGeometry.CARD_SPACING >= 0.25D
                && BlackjackDisplayGeometry.CARD_SPACING <= 0.35D, "card spacing stays compact");

        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> method.getName().equals("getName") ? "world" : null);
        BlackjackSeat seat = new BlackjackSeat(1,
                new ActivityPosition("world", 1.5, 64.5, 5.5, -90, 0),
                new BlackjackBlockPosition("world", 1, 64, 5));
        BlackjackTableDefinition definition = new BlackjackTableDefinition(
                "table", new ActivityArea("world", 0, 64, 0, 10, 65, 10),
                new ActivityPosition("world", 8, 64.5, 5, 90, 0), null, List.of(seat),
                BlackjackTableInteractionMode.MODERN_SEAT_CLICK);
        Location center = BlackjackDisplayGeometry.tableCenter(world, definition);
        Location anchor = BlackjackDisplayGeometry.playerAnchor(world, definition, seat);
        check(distanceSquared(anchor, center) < distanceSquared(new Location(world, 1.5, 64.5, 5.5), center),
                "player card anchor moves from the seat toward table center");
        Location first = BlackjackDisplayGeometry.card(anchor, center, 0, 2);
        Location second = BlackjackDisplayGeometry.card(anchor, center, 1, 2);
        check(Math.abs(Math.sqrt(distanceSquared(first, second)) - BlackjackDisplayGeometry.CARD_SPACING) < 0.0001D,
                "cards use deterministic geometry spacing");
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
}

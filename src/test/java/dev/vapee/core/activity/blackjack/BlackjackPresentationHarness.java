package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.blackjack.presentation.BlackjackAction;
import dev.vapee.core.activity.blackjack.presentation.BlackjackDisplayGeometry;
import dev.vapee.core.activity.blackjack.presentation.BlackjackWorldViewService;
import dev.vapee.core.activity.blackjack.presentation.BlackjackInventoryService;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackHandView;
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
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class BlackjackPresentationHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private BlackjackPresentationHarness() { }

    public static void main(String[] args) throws Exception {
        testRejectedWorldViewTeleport();
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
        check(BlackjackDisplayGeometry.DEALER_HAND_SCALE >= 0.28F
                && BlackjackDisplayGeometry.DEALER_HAND_SCALE <= 0.34F,
                "floating dealer hand has its own readable compact scale");
        check(invokeText("idleStatusText", new Class<?>[]{int.class, int.class}, 1, 5)
                        .equals("1 / 5"),
                "idle table status is player-facing and contains no internal enum");
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRoundView.class}, (Object) null) == null,
                "idle seated player creates no Ready label");
        BlackjackPlayerRound activeRound = new BlackjackPlayerRound(java.util.UUID.randomUUID());
        activeRound.getHand().add(new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.SPADES));
        activeRound.getHand().add(new BlackjackCard(BlackjackRank.SEVEN, BlackjackSuit.HEARTS));
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRoundView.class}, activeRound.toView())
                        .equals("17"),
                "active player label is a compact hand value");
        activeRound.settle(BlackjackOutcome.WIN);
        check(invokeText("playerLabelText", new Class<?>[]{BlackjackPlayerRoundView.class}, activeRound.toView()).equals("WIN"),
                "settled player label is the short outcome");
        check(new BlackjackCard(BlackjackRank.ACE, BlackjackSuit.SPADES).getDisplayText().equals("A♠")
                        && new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.HEARTS).getDisplayText().equals("10♥"),
                "card text is compact without rank/suit whitespace");
        assertDealerPresentation();

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
        assertDealerLocation(world, 0.0F, 8.0D, 64.5D, 5.0D, 8.0D, 5.75D);
        assertDealerLocation(world, 90.0F, 8.0D, 64.5D, 5.0D, 7.25D, 5.0D);
        assertDealerLocation(world, 180.0F, 8.0D, 64.5D, 5.0D, 8.0D, 4.25D);
        assertDealerLocation(world, -90.0F, 8.0D, 64.5D, 5.0D, 8.75D, 5.0D);
        Location dealerHand = BlackjackDisplayGeometry.dealerHandLocation(world, definition);
        check(Math.abs(dealerHand.getY()
                        - (definition.dealer().y() + BlackjackDisplayGeometry.DEALER_HAND_VERTICAL_OFFSET)) < 0.0001D,
                "dealer hand Y depends on dealer position rather than table surface Y");
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
        assertInventoryOutput();
        assertWorldOutput();
        System.out.println("BlackjackPresentationHarness passed " + checks + " checks.");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertDealerPresentation() throws Exception {
        for (BlackjackRank rank : BlackjackRank.values()) {
            BlackjackCard card = new BlackjackCard(rank, BlackjackSuit.SPADES);
            BlackjackHand single = new BlackjackHand(List.of(card));
            BlackjackHand pair = new BlackjackHand(List.of(card,
                    new BlackjackCard(BlackjackRank.ACE, BlackjackSuit.HEARTS)));
            check(PLAIN.serialize(invokeDealerDisplays(pair, true).get("dealer-hand"))
                            .equals(card.getDisplayText() + "   ◆\nDealer • " + single.getValue()),
                    "hidden dealer value matches previous single-card scoring for " + rank);
        }
        check(PLAIN.serialize(invokeDealerDisplays(new BlackjackHand(), true).get("dealer-hand"))
                        .equals("\nDealer • 0"), "empty dealer snapshot retains zero value");
        BlackjackHand hand = new BlackjackHand(List.of(
                new BlackjackCard(BlackjackRank.KING, BlackjackSuit.DIAMONDS),
                new BlackjackCard(BlackjackRank.SEVEN, BlackjackSuit.SPADES)
        ));
        Map<String, Component> hidden = invokeDealerDisplays(hand, true);
        check(hidden.keySet().equals(java.util.Set.of("dealer-hand")),
                "dealer uses exactly one stable world display key");
        String hiddenText = PLAIN.serialize(hidden.get("dealer-hand"));
        check(hiddenText.equals("K♦   ◆\nDealer • 10")
                        && !hiddenText.contains("7♠") && !hiddenText.contains("17"),
                "player turn hides the hole card and exposes only the visible value");
        Map<String, Component> revealed = invokeDealerDisplays(hand, false);
        Component revealedComponent = revealed.get("dealer-hand");
        check(PLAIN.serialize(revealedComponent).equals("K♦   7♠\nDealer • 17"),
                "dealer turn reveals every card and the full value");
        check(containsTextWithColor(revealedComponent, "K♦", NamedTextColor.RED)
                        && containsTextWithColor(revealedComponent, "7♠", NamedTextColor.WHITE),
                "dealer component colors red and black suits independently for the dark background");
        check(revealedComponent.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE,
                "dealer component explicitly disables italic text");

        BlackjackHand longHand = new BlackjackHand(List.of(
                new BlackjackCard(BlackjackRank.KING, BlackjackSuit.DIAMONDS),
                new BlackjackCard(BlackjackRank.TWO, BlackjackSuit.SPADES),
                new BlackjackCard(BlackjackRank.THREE, BlackjackSuit.HEARTS),
                new BlackjackCard(BlackjackRank.ACE, BlackjackSuit.CLUBS),
                new BlackjackCard(BlackjackRank.FOUR, BlackjackSuit.DIAMONDS)
        ));
        Map<String, Component> longDisplay = invokeDealerDisplays(longHand, false);
        check(longDisplay.size() == 1 && PLAIN.serialize(longDisplay.get("dealer-hand"))
                        .equals("K♦   2♠   3♥   A♣\n4♦\nDealer • 20"),
                "long dealer hand wraps after four cards without creating another entity");

        Class<?> styleType = Arrays.stream(BlackjackWorldViewService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("DisplayStyle"))
                .findFirst().orElseThrow();
        Object open = Enum.valueOf((Class) styleType, "OPEN_CARD");
        Object dealer = Enum.valueOf((Class) styleType, "DEALER_HAND");
        var background = BlackjackWorldViewService.class.getDeclaredMethod("background", styleType);
        background.setAccessible(true);
        Color openColor = (Color) background.invoke(null, open);
        Color dealerColor = (Color) background.invoke(null, dealer);
        check(!openColor.equals(dealerColor) && openColor.getRed() > dealerColor.getRed(),
                "floating dealer hand uses a distinct semi-transparent dark style");
        var billboard = BlackjackWorldViewService.class.getDeclaredMethod("billboard", styleType, Quaternionf.class);
        billboard.setAccessible(true);
        check(billboard.invoke(null, dealer, null) == Display.Billboard.VERTICAL,
                "floating dealer hand uses a vertical billboard without card-plane rotation");
        var seatCardKey = BlackjackWorldViewService.class.getDeclaredMethod("seatCardKey", int.class, int.class);
        seatCardKey.setAccessible(true);
        check(seatCardKey.invoke(null, 3, 2).equals("seat-3-card-2"),
                "player card world display keys remain unchanged");
    }

    private static void assertInventoryOutput() throws Exception {
        BlackjackPresentationFixture fixture = new BlackjackPresentationFixture();
        BlackjackInventoryService inventory = fixture.inventory;
        inventory.refreshPlayer(fixture.player, fixture.session);
        check(occupiedSlots(fixture).isEmpty(), "non-owner refresh does not write inventory");
        check(inventory.takeOwnership(fixture.player) && inventory.isOwner(fixture.playerId)
                        && fixture.relinquishes == 1 && fixture.selectedSlot == 0,
                "ownership hands off lobby inventory and selects slot zero");
        int closes = fixture.closes;
        check(inventory.takeOwnership(fixture.player) && fixture.closes == closes && fixture.relinquishes == 1,
                "repeated ownership is idempotent");
        inventory.refreshSession(fixture.session);
        check(occupiedSlots(fixture).equals(Set.of(0, 4, 8)), "available slots remain 0/4/8");
        assertItem(fixture, 0, Material.EMERALD, "Deal", BlackjackAction.DEAL,
                List.of("Start a new free-play round.", "Right-click to use."));
        assertItem(fixture, 4, Material.PAPER, "Blackjack Status", null,
                List.of("Waiting for deal", "1 player(s) seated"));
        assertItem(fixture, 8, Material.BARRIER, "Leave Table", BlackjackAction.LEAVE,
                List.of("Leave blackjack and return your lobby items.", "Right-click to use."));
        fixture.activate();
        BlackjackPlayerRound round = fixture.session.requirePlayerRound(fixture.playerId);
        round.getHand().add(new BlackjackCard(BlackjackRank.FIVE, BlackjackSuit.SPADES));
        round.getHand().add(new BlackjackCard(BlackjackRank.SIX, BlackjackSuit.HEARTS));
        inventory.refreshPlayer(fixture.player, fixture.session);
        check(occupiedSlots(fixture).equals(Set.of(0, 1, 2, 4, 8)), "own legal two-card turn exposes all five slots");
        assertItem(fixture, 0, Material.IRON_SWORD, "Hit", BlackjackAction.HIT,
                List.of("Draw one card.", "Right-click to use."));
        assertItem(fixture, 1, Material.SHIELD, "Stand", BlackjackAction.STAND,
                List.of("Keep your current hand.", "Right-click to use."));
        assertItem(fixture, 2, Material.GOLD_INGOT, "Double", BlackjackAction.DOUBLE,
                List.of("Draw exactly one card, then stand.", "Right-click to use."));
        assertItem(fixture, 4, Material.PAPER, "Blackjack Status", null, List.of("Your turn", "Hand value: 11"));
        for (String condition : List.of("three-cards", "finished", "doubled", "natural", "missing", "foreign-turn")) {
            fixture.session.beginRound(dev.vapee.core.activity.blackjack.card.BlackjackShoe.sixDecks(new java.util.Random(3)));
            fixture.session.beginTurn(fixture.playerId);
            round = fixture.session.requirePlayerRound(fixture.playerId);
            round.getHand().add(new BlackjackCard(condition.equals("natural") ? BlackjackRank.ACE : BlackjackRank.FIVE, BlackjackSuit.SPADES));
            round.getHand().add(new BlackjackCard(condition.equals("natural") ? BlackjackRank.KING : BlackjackRank.SIX, BlackjackSuit.HEARTS));
            switch (condition) {
                case "three-cards" -> round.getHand().add(new BlackjackCard(BlackjackRank.TWO, BlackjackSuit.CLUBS));
                case "finished" -> round.finish();
                case "doubled" -> round.markDoubledDown();
                case "missing" -> fixture.session.removePlayerRound(fixture.playerId);
                case "foreign-turn" -> fixture.session.beginTurn(UUID.randomUUID());
                default -> { }
            }
            inventory.refreshPlayer(fixture.player, fixture.session);
            check(fixture.items[2] == null, "double hidden for " + condition);
            if (condition.equals("foreign-turn")) {
                check(occupiedSlots(fixture).equals(Set.of(4, 8)), "foreign turn retains status/leave only");
                assertItem(fixture, 4, Material.PAPER, "Blackjack Status", null,
                        List.of("Another player's turn", "Hand value: 11"));
            }
        }
        for (BlackjackRoundPhase phase : List.of(BlackjackRoundPhase.DEALER_TURN, BlackjackRoundPhase.SETTLED)) {
            fixture.session.setRoundPhase(phase);
            inventory.refreshPlayer(fixture.player, fixture.session);
            check(occupiedSlots(fixture).equals(Set.of(4, 8)), phase + " retains status/leave only");
            assertItem(fixture, 4, Material.PAPER, "Blackjack Status", null,
                    List.of(phase == BlackjackRoundPhase.DEALER_TURN ? "Dealer is playing" : "Round complete", "Hand value: 11"));
        }
        inventory.release(fixture.playerId, true);
        check(!inventory.isOwner(fixture.playerId) && occupiedSlots(fixture).isEmpty() && fixture.restores == 1,
                "normal release removes managed items and restores lobby once");
        fixture.lobby.enterBuildMode(fixture.player);
        check(!inventory.takeOwnership(fixture.player) && !inventory.isOwner(fixture.playerId), "BUILD ownership still rejected");
        fixture.lobby.enterNormalMode(fixture.player);
        inventory.takeOwnership(fixture.player);
        inventory.refreshPlayer(fixture.player, fixture.session);
        int restores = fixture.restores;
        inventory.shutdown();
        check(!inventory.isOwner(fixture.playerId) && occupiedSlots(fixture).isEmpty() && fixture.restores == restores,
                "shutdown releases without reapplying lobby items");
    }

    private static Set<Integer> occupiedSlots(BlackjackPresentationFixture fixture) {
        Set<Integer> slots = new java.util.HashSet<>();
        for (int index = 0; index < fixture.items.length; index++) if (fixture.items[index] != null) slots.add(index);
        return slots;
    }

    private static void assertItem(BlackjackPresentationFixture fixture, int slot, Material material,
                                   String name, BlackjackAction action, List<String> lore) {
        ItemStack item = fixture.items[slot];
        check(item != null && item.getType() == material && PLAIN.serialize(item.getItemMeta().displayName()).equals(name),
                "slot " + slot + " retains material and name " + name);
        check(fixture.inventory.isManagedItem(item) && fixture.inventory.getAction(item).orElse(null) == action,
                "slot " + slot + " retains managed/action PDC");
        check(item.getItemMeta().lore().stream().map(PLAIN::serialize).toList().equals(lore), "slot " + slot + " retains lore");
    }

    private static void assertWorldOutput() throws Exception {
        BlackjackPresentationFixture fixture = new BlackjackPresentationFixture();
        fixture.render();
        check(fixture.displays.getDisplayCount() == 1 && fixture.displayText("status").equals("Blackjack\n1 / 1"),
                "idle renders only the unchanged occupancy status");
        fixture.activate();
        BlackjackPlayerRound round = fixture.session.requirePlayerRound(fixture.playerId);
        round.getHand().add(new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.SPADES));
        round.getHand().add(new BlackjackCard(BlackjackRank.SEVEN, BlackjackSuit.HEARTS));
        fixture.session.getDealerHand().add(new BlackjackCard(BlackjackRank.KING, BlackjackSuit.DIAMONDS));
        fixture.session.getDealerHand().add(new BlackjackCard(BlackjackRank.SEVEN, BlackjackSuit.SPADES));
        fixture.render();
        check(fixture.displays.getDisplayCount() == 5 && fixture.creates == 5, "active view creates exactly five expected displays");
        check(fixture.displayText("seat-1-card-0").equals("10♠") && fixture.displayText("seat-1-card-1").equals("7♥")
                        && fixture.displayText("seat-1-label").equals("17"), "player card order and hand value remain unchanged");
        check(fixture.displayText("dealer-hand").equals("K♦   ◆\nDealer • 10")
                        && fixture.displayText("status").equals("Blackjack\nTester's turn"), "player turn keeps dealer hole hidden and names turn owner");
        Map<?, ?> ids = Map.copyOf(fixture.entities);
        fixture.render();
        check(fixture.creates == 5 && ids.equals(fixture.entities), "identical refresh updates existing entities without churn");
        fixture.session.setRoundPhase(BlackjackRoundPhase.DEALER_TURN);
        fixture.render();
        check(fixture.displayText("dealer-hand").equals("K♦   7♠\nDealer • 17")
                        && fixture.displayText("status").equals("Blackjack\nDealer"), "dealer turn reveals cards and retains status");
        fixture.session.getDealerHand().add(new BlackjackCard(BlackjackRank.THREE, BlackjackSuit.CLUBS));
        fixture.render();
        check(fixture.displayText("dealer-hand").equals("K♦   7♠   3♣\nDealer • 20") && fixture.creates == 5
                        && ids.equals(fixture.entities), "dealer card growth reuses the single dealer entity");
        fixture.session.setRoundPhase(BlackjackRoundPhase.SETTLED);
        for (BlackjackOutcome outcome : BlackjackOutcome.values()) {
            round.settle(outcome);
            fixture.render();
            check(fixture.displayText("seat-1-label").equals(outcome.name())
                            && fixture.displayText("status").equals("Blackjack\nRound complete"), "settled label and status for " + outcome);
            check(fixture.displays.getDisplayCount() == 5 && fixture.creates == 6 && fixture.removals == 1,
                    "only the existing VALUE-to-RESULT style transition replaces one entity: " + outcome);
        }
        fixture.worldView.shutdown();
        check(fixture.displays.getDisplayCount() == 0 && fixture.entities.isEmpty(), "world shutdown cleans the complete owner");
        check(fixture.warnings.isEmpty(), "actual presentation path has no swallowed render failure");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Component> invokeDealerDisplays(BlackjackHand hand, boolean hidden) throws Exception {
        var method = BlackjackWorldViewService.class.getDeclaredMethod(
                "dealerDisplays", BlackjackHandView.class, boolean.class);
        method.setAccessible(true);
        return (Map<String, Component>) method.invoke(null, BlackjackHandView.from(hand), hidden);
    }

    private static void assertDealerLocation(
            World world,
            float yaw,
            double x,
            double y,
            double z,
            double expectedX,
            double expectedZ
    ) {
        Location location = BlackjackDisplayGeometry.dealerHandLocation(
                world, new ActivityPosition("world", x, y, z, yaw, 0.0F));
        check(Math.abs(location.getX() - expectedX) < 0.0001D
                        && Math.abs(location.getZ() - expectedZ) < 0.0001D
                        && Math.abs(location.getY()
                        - (y + BlackjackDisplayGeometry.DEALER_HAND_VERTICAL_OFFSET)) < 0.0001D,
                "dealer yaw " + yaw + " places the floating hand in front of the dealer");
    }

    private static boolean containsTextWithColor(Component component, String text, NamedTextColor color) {
        if (component instanceof TextComponent value
                && value.content().equals(text)
                && color.equals(value.color())) {
            return true;
        }
        return component.children().stream().anyMatch(child -> containsTextWithColor(child, text, color));
    }

    private static void testRejectedWorldViewTeleport() throws Exception {
        BlackjackPresentationFixture f = new BlackjackPresentationFixture();
        f.activate();
        f.render();
        var key = new dev.vapee.core.worlddisplay.WorldDisplayKey("blackjack:read-parity", "dealer-hand");
        var original = f.displays.getHandle(key).orElseThrow();
        int count = f.creates;
        Set<UUID> originals = Set.copyOf(f.texts.keySet());
        f.rejectedTeleports.add(original.entityId());
        f.render();
        check(f.displays.getHandle(key).orElseThrow() == original,
                "Blackjack apply retains live rejected original handle");
        check(f.creates == count && f.texts.keySet().equals(originals),
                "Blackjack apply never duplicates a live rejected display");
        check(originals.stream().allMatch(id -> f.teleports.getOrDefault(id, 0) == 1),
                "Blackjack apply attempts each display once despite one rejected movement");
        f.rejectedTeleports.clear();
        f.render();
        check(f.creates == count && f.displays.getHandle(key).orElseThrow() == original,
                "later Blackjack refresh updates original handle without recreation");
        f.rejectedTeleports.add(original.entityId());
        f.render();
        f.worldView.onDisabled(f.definition.id());
        check(f.displays.getDisplayCount() == 0 && f.texts.isEmpty(),
                "Blackjack owner cleanup removes rejected original without orphan");

        f.render();
        UUID missing = f.displays.getHandle(key).orElseThrow().entityId();
        f.disappearOnTeleport.add(missing);
        count = f.creates;
        f.render();
        check(f.creates == count + 1 && !f.displays.getHandle(key).orElseThrow().entityId().equals(missing),
                "Blackjack apply recreates exactly one entity when it disappears during movement");
        check(!f.texts.containsKey(missing) && f.texts.size() == f.displays.getDisplayCount(),
                "Blackjack missing reconciliation has no duplicate physical resource");
        check(f.warnings.isEmpty(), "Blackjack false movement remains controlled without refresh exception");
        f.worldView.shutdown();
        f.worldView.shutdown();
        check(f.texts.isEmpty(), "Blackjack repeated cleanup removes recreated displays safely");
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

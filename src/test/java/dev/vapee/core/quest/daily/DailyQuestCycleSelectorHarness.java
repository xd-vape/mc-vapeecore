package dev.vapee.core.quest.daily;

import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestProgressKey;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class DailyQuestCycleSelectorHarness {

    private static int checks;

    private DailyQuestCycleSelectorHarness() {
    }

    public static void main(String[] args) {
        DailyQuestCycleResolver resolver = new DailyQuestCycleResolver();
        ZoneId utc = ZoneId.of("UTC");
        check(resolver.resolve(Instant.parse("2026-09-21T23:59:00Z"), utc, LocalTime.MIDNIGHT)
                        .toString().equals("2026-09-21"), "midnight before boundary uses prior date");
        check(resolver.resolve(Instant.parse("2026-09-22T00:00:00Z"), utc, LocalTime.MIDNIGHT)
                        .toString().equals("2026-09-22"), "midnight boundary begins the new cycle");
        check(resolver.resolve(Instant.parse("2026-09-22T12:00:00Z"), utc, LocalTime.MIDNIGHT)
                        .toString().equals("2026-09-22"), "midday remains in the same cycle");
        LocalTime four = LocalTime.of(4, 0);
        check(resolver.resolve(Instant.parse("2026-09-22T03:59:00Z"), utc, four)
                        .toString().equals("2026-09-21"), "04:00 reset waits through 03:59");
        check(resolver.resolve(Instant.parse("2026-09-22T04:00:00Z"), utc, four)
                        .toString().equals("2026-09-22"), "04:00 reset starts at the boundary");
        check(resolver.resolve(Instant.parse("2026-09-22T04:01:00Z"), utc, four)
                        .toString().equals("2026-09-22"), "04:01 remains in the new cycle");
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZoneId newYork = ZoneId.of("America/New_York");
        Instant sameInstant = Instant.parse("2026-09-22T02:00:00Z");
        check(resolver.resolve(sameInstant, berlin, four).toString().equals("2026-09-22")
                        && resolver.resolve(sameInstant, newYork, four).toString().equals("2026-09-21"),
                "the same instant follows each configured IANA timezone");
        LocalTime halfPastTwo = LocalTime.of(2, 30);
        check(resolver.resolve(Instant.parse("2026-03-29T01:29:00Z"), berlin, halfPastTwo)
                        .toString().equals("2026-03-28")
                        && resolver.resolve(Instant.parse("2026-03-29T01:30:00Z"), berlin, halfPastTwo)
                        .toString().equals("2026-03-29"),
                "DST spring gap resolves the nonexistent 02:30 reset at 03:30");
        check(resolver.resolve(Instant.parse("2026-10-25T00:29:00Z"), berlin, halfPastTwo)
                        .toString().equals("2026-10-24")
                        && resolver.resolve(Instant.parse("2026-10-25T00:30:00Z"), berlin, halfPastTwo)
                        .toString().equals("2026-10-25")
                        && resolver.resolve(Instant.parse("2026-10-25T01:15:00Z"), berlin, halfPastTwo)
                        .toString().equals("2026-10-25"),
                "DST fall overlap never reverts the cycle when the clock repeats 02:15");
        check(DailyQuestCycleId.parse("2026-09-22").date().equals(LocalDate.of(2026, 9, 22)),
                "cycle ID uses a canonical LocalDate value");

        DailyQuestSelector selector = new DailyQuestSelector();
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        DailyQuestCycleId firstCycle = DailyQuestCycleId.parse("2026-09-22");
        DailyQuestCycleId secondCycle = DailyQuestCycleId.parse("2026-09-23");
        List<QuestDefinition> catalog = definitions(10);
        List<String> selected = selector.select(firstPlayer, firstCycle, catalog, 4);
        check(selected.equals(List.of("quest_7", "quest_3", "quest_1", "quest_4")),
                "fixed SHA-256 fixture locks the reproducible selection contract");
        check(selected.equals(selector.select(firstPlayer, firstCycle, catalog, 4)),
                "same player, cycle, and catalog reproduce the selection");
        check(selected.equals(new DailyQuestSelector().select(firstPlayer, firstCycle, catalog, 4)),
                "fresh selector after a hard crash reproduces the same assignments");
        List<QuestDefinition> reversed = new ArrayList<>(catalog);
        java.util.Collections.reverse(reversed);
        check(selected.equals(selector.select(firstPlayer, firstCycle, reversed, 4)),
                "catalog input order does not change the selection");
        check(selected.size() == 4 && new HashSet<>(selected).size() == 4,
                "requested count has no duplicate quest IDs");
        check(selector.select(firstPlayer, firstCycle, catalog.subList(0, 2), 4).size() == 2,
                "fewer definitions fill only the available slots");
        check(selector.select(secondPlayer, firstCycle, catalog, 4)
                        .equals(List.of("quest_3", "quest_7", "quest_1", "quest_8")),
                "fixed second UUID has its own exact deterministic selection");
        check(selector.select(firstPlayer, secondCycle, catalog, 4)
                        .equals(List.of("quest_8", "quest_9", "quest_2", "quest_7")),
                "fixed next cycle has its own exact deterministic selection");

        System.out.println("DailyQuestCycleSelectorHarness passed " + checks + " checks.");
    }

    private static List<QuestDefinition> definitions(int count) {
        List<QuestDefinition> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String id = "quest_" + index;
            values.add(new QuestDefinition(id, "Quest " + index, "Test", QuestProgressKey.of("test:" + id),
                    1L, 1L));
        }
        return List.copyOf(values);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

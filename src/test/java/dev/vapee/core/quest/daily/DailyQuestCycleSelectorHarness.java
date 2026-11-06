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

        selection(selector, firstPlayer, firstCycle, catalog);

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

    private static void selection(DailyQuestSelector selector, UUID player, DailyQuestCycleId cycle, List<QuestDefinition> catalog) {
        var all = selector.select(player, cycle, catalog, 100);
        var rules = new java.util.HashMap<String, DailyQuestSelection.Quest>();
        rules.put("quest_7", new DailyQuestSelection.Quest(false, "movement", "walking"));
        var disabled = new DailyQuestSelection(rules, null, null);
        check(selector.select(player, cycle, catalog, 4, disabled).equals(all.stream().filter(id -> !id.equals("quest_7")).limit(4).toList()),
                "disabled highest-ranked ID is skipped using the original SHA ranks");
        for (var definition : catalog) rules.put(definition.id(), new DailyQuestSelection.Quest(false, null, null));
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, null, null)).isEmpty(), "all disabled gives an empty selection");
        check(!disabled.quest("quest_7").enabled() && disabled.quest("quest_3").enabled(), "selection metadata is an immutable snapshot of its input map");
        rules.clear();
        rules.put("quest_7", new DailyQuestSelection.Quest(true, "movement", "walking"));
        rules.put("quest_3", new DailyQuestSelection.Quest(true, "movement", "walking"));
        rules.put("quest_1", new DailyQuestSelection.Quest(true, "movement", "rotation"));
        rules.put("quest_4", new DailyQuestSelection.Quest(true, "playtime", null));
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, null, 1L))
                        .equals(all.stream().filter(id -> !id.equals("quest_3")).limit(4).toList()), "group limit skips a similar task and fills from later SHA ranks");
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, 1L, null))
                        .equals(all.stream().filter(id -> !id.equals("quest_3") && !id.equals("quest_1")).limit(4).toList()), "category limit spans multiple groups");
        String extraMovement = all.get(4);
        rules.put(extraMovement, new DailyQuestSelection.Quest(true, "movement", "sprinting"));
        var combined = new DailyQuestSelection(rules, 2L, 1L);
        var chosen = selector.select(player, cycle, catalog, 4, combined);
        check(chosen.equals(all.stream().filter(id -> !id.equals("quest_3") && !id.equals(extraMovement)).limit(4).toList()), "both limits independently reject candidates and count only selected explicit metadata");
        var reverse = new ArrayList<>(catalog); java.util.Collections.reverse(reverse);
        check(chosen.equals(selector.select(player, cycle, reverse, 4, combined)), "constrained selection is independent of YAML order");
        check(chosen.equals(new DailyQuestSelector().select(player, cycle, catalog, 4, combined)), "constrained selection survives selector recreation");
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(java.util.Map.of(), 1L, 1L)).size() == 4,
                "missing categories and groups do not collide in invented default buckets");
        rules.put("quest_3", new DailyQuestSelection.Quest(true, "other_category", "walking"));
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, 10L, 1L)).stream().noneMatch("quest_3"::equals),
                "a group limit is shared across categories with the same technical group ID");
        for (var definition : catalog) rules.put(definition.id(), new DailyQuestSelection.Quest(true, "custom", "shared"));
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, 2L, null)).equals(all.subList(0, 2)), "category shortages return fewer tasks instead of relaxing limits");
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, null, 1L)).equals(all.subList(0, 1)), "group shortages return fewer tasks instead of relaxing limits");
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, null, null)).equals(all.subList(0, 4)), "metadata alone never changes legacy selection");
        for (var definition : catalog) rules.put(definition.id(), new DailyQuestSelection.Quest(true, "custom", null));
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, 2L, 1L)).size() == 2, "missing groups do not collide while a category limit applies");
        for (var definition : catalog) rules.put(definition.id(), new DailyQuestSelection.Quest(true, null, "shared"));
        check(selector.select(player, cycle, catalog, 4, new DailyQuestSelection(rules, 1L, 2L)).size() == 2, "missing categories do not collide while a group limit applies");
        for (int p = 1; p <= 24; p++) for (int day = 1; day <= 3; day++) for (int count : List.of(1, 4, 12)) {
            UUID id = new UUID(0, p); var date = new DailyQuestCycleId(java.time.LocalDate.of(2026, 9, day));
            check(selector.select(id, date, catalog, count).equals(legacy(id, date, catalog, count)), "exact previous SHA-256 algorithm across UUID/cycle/slot fixtures");
        }
        try { selector.select(player, cycle, List.of(catalog.getFirst(), catalog.getFirst()), 4, new DailyQuestSelection(rules, 1L, 1L)); throw new AssertionError("duplicates accepted"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("Duplicate"), "duplicate IDs remain invalid before filtering"); }
    }

    // Frozen pre-08A.1 ordering oracle; metadata must not alter rank inputs or byte comparison.
    private static List<String> legacy(UUID player, DailyQuestCycleId cycle, List<QuestDefinition> definitions, int count) {
        return definitions.stream().map(QuestDefinition::id).sorted((a, b) -> {
            try {
                var hash = java.security.MessageDigest.getInstance("SHA-256");
                int rank = java.util.Arrays.compareUnsigned(hash.digest((player + "\n" + cycle + "\n" + a).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        hash.digest((player + "\n" + cycle + "\n" + b).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                return rank == 0 ? a.compareTo(b) : rank;
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }).limit(count).toList();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

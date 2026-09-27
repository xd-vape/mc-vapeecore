package dev.vapee.core.quest.daily;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class DailyQuestConfigHarness {

    private static int checks;

    private DailyQuestConfigHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-daily-config-");
        Path file = directory.resolve("daily-quests.yml");
        List<String> warnings = new ArrayList<>();
        Logger logger = Logger.getLogger("DailyQuestConfigHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        try {
            String defaults = "enabled: false\nquests-per-day: 4\nreset:\n  time: '00:00'\n"
                    + "  timezone: system\nquests: {}\n";
            Files.writeString(file, defaults);
            DailyQuestConfig config = new DailyQuestConfig(file, logger);
            config.initialize();
            DailyQuestConfig.State state = config.getState();
            check(!state.enabled() && state.questsPerDay() == 4
                            && state.resetTime().equals(LocalTime.MIDNIGHT)
                            && state.timezoneSetting().equals("system")
                            && state.zone().equals(ZoneId.systemDefault())
                            && state.definitions().isEmpty(),
                    "disabled empty resource-style configuration loads the internal defaults");
            check(warnings.isEmpty(), "disabled empty default catalog produces no warnings");

            Files.writeString(file, "quests: {}\n");
            DailyQuestConfig.State missingKeys = config.prepareReloadState();
            check(!missingKeys.enabled() && missingKeys.questsPerDay() == 4
                            && missingKeys.resetTime().equals(LocalTime.MIDNIGHT)
                            && missingKeys.timezoneSetting().equals("system"),
                    "missing keys use all four safe Java defaults");

            String valid = """
                    enabled: true
                    quests-per-day: 2
                    reset:
                      time: "04:30"
                      timezone: "Europe/Berlin"
                    quests:
                      first:
                        name: "First"
                        description: "First quest"
                        progress-key: "test:first"
                        target: 10
                        reward-coins: 100
                      second:
                        name: "Second"
                        description: "Second quest"
                        progress-key: "test:second"
                        target: 20
                        reward-coins: 200
                    """;
            Files.writeString(file, valid);
            DailyQuestConfig.State prepared = config.prepareReloadState();
            check(config.getState() == state, "prepare does not mutate active config");
            check(prepared.enabled() && prepared.questsPerDay() == 2
                            && prepared.resetTime().equals(LocalTime.of(4, 30))
                            && prepared.zone().equals(ZoneId.of("Europe/Berlin"))
                            && prepared.definitions().stream().map(definition -> definition.id()).toList()
                            .equals(List.of("first", "second")),
                    "multiple custom definitions and reset settings load atomically");
            config.applyState(prepared);
            check(config.getState() == prepared, "apply activates the prepared snapshot");

            for (String invalidField : List.of(
                    "target: 0", "reward-coins: 0", "progress-key: 'Invalid Key'"
            )) {
                String invalid = valid.replace(
                        invalidField.startsWith("target") ? "target: 10"
                                : invalidField.startsWith("reward") ? "reward-coins: 100"
                                : "progress-key: \"test:first\"",
                        invalidField
                );
                Files.writeString(file, invalid);
                boolean failed = false;
                try {
                    config.prepareReloadState();
                } catch (IllegalArgumentException exception) {
                    failed = exception.getMessage().contains("quests.first");
                }
                check(failed && config.getState() == prepared,
                        "invalid " + invalidField + " rejects the entire catalog without changing active state");
            }

            String invalidSettings = valid.replace("quests-per-day: 2", "quests-per-day: 0")
                    .replace("time: \"04:30\"", "time: \"24:00\"")
                    .replace("timezone: \"Europe/Berlin\"", "timezone: \"Not/AZone\"");
            Files.writeString(file, invalidSettings);
            warnings.clear();
            DailyQuestConfig.State fallback = config.prepareReloadState();
            check(fallback.questsPerDay() == 4 && fallback.resetTime().equals(LocalTime.MIDNIGHT)
                            && fallback.timezoneSetting().equals("system"),
                    "invalid slot count, reset time, and timezone use safe defaults");
            check(warnings.stream().anyMatch(message -> message.contains("quests-per-day"))
                            && warnings.stream().anyMatch(message -> message.contains("reset.time"))
                            && warnings.stream().anyMatch(message -> message.contains("reset.timezone"))
                            && Files.readString(file).equals(invalidSettings),
                    "invalid settings warn without rewriting the administrator file");
            check(config.getState() == prepared, "fallback preparation alone leaves active state unchanged");

            Files.writeString(file, "enabled: true\nquests: {}\n");
            warnings.clear();
            check(config.prepareReloadState().definitions().isEmpty()
                            && warnings.stream().anyMatch(message -> message.contains("catalog is empty")),
                    "enabled empty catalog is safe and warns once at load");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        System.out.println("DailyQuestConfigHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

package dev.vapee.core.quest;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public final class QuestDefinitionHarness {

    private static int checks;

    private QuestDefinitionHarness() {
    }

    public static void main(String[] args) {
        testProgressKeyValidation();
        testDefinitionValidationAndImmutability();
        testRegistrySnapshotsAndAtomicReplace();
        System.out.println("QuestDefinitionHarness passed " + checks + " checks.");
    }

    private static void testProgressKeyValidation() {
        QuestProgressKey key = QuestProgressKey.of("mine:block:diamond_ore");
        check(key.value().equals("mine:block:diamond_ore")
                        && key.toString().equals("mine:block:diamond_ore"),
                "valid generic progress key retains its exact technical value");
        check(QuestProgressKey.of("activity.complete-1_test").value()
                        .equals("activity.complete-1_test"),
                "progress key supports documented stable separators");
        expectIllegalArgument(() -> QuestProgressKey.of(""), "blank progress key is rejected");
        expectIllegalArgument(() -> QuestProgressKey.of("Mine:block:any"),
                "uppercase progress key is rejected");
        expectIllegalArgument(() -> QuestProgressKey.of("mine:block:*"),
                "wildcard progress key is rejected");
        expectIllegalArgument(() -> QuestProgressKey.of("-mine:block"),
                "progress key must begin with an alphanumeric character");
        expectIllegalArgument(() -> QuestProgressKey.of("a".repeat(129)),
                "progress key longer than 128 characters is rejected");
    }

    private static void testDefinitionValidationAndImmutability() {
        QuestDefinition definition = definition("daily_miner", "mine:block:any", 250L, 400L);
        check(definition.id().equals("daily_miner")
                        && definition.name().equals("Quest daily_miner")
                        && definition.description().equals("Complete daily_miner")
                        && definition.target() == 250L
                        && definition.rewardCoins() == 400L,
                "valid quest definition retains all domain fields");
        check(QuestDefinition.class.isRecord()
                        && List.of(QuestDefinition.class.getDeclaredFields()).stream()
                        .filter(field -> !field.isSynthetic())
                        .allMatch(field -> Modifier.isFinal(field.getModifiers())),
                "quest definition is an immutable record");
        expectIllegalArgument(
                () -> definition("", "mine:block:any", 1L, 1L),
                "blank quest ID is rejected"
        );
        expectIllegalArgument(
                () -> definition("Daily_Miner", "mine:block:any", 1L, 1L),
                "uppercase quest ID is rejected"
        );
        expectIllegalArgument(
                () -> definition("daily miner", "mine:block:any", 1L, 1L),
                "quest ID with whitespace is rejected"
        );
        expectIllegalArgument(
                () -> definition("a".repeat(65), "mine:block:any", 1L, 1L),
                "quest ID longer than 64 characters is rejected"
        );
        expectIllegalArgument(
                () -> new QuestDefinition("valid", " ", "Description", key("test:key"), 1L, 1L),
                "blank quest name is rejected"
        );
        expectIllegalArgument(
                () -> new QuestDefinition("valid", "Name", " ", key("test:key"), 1L, 1L),
                "blank quest description is rejected"
        );
        expectIllegalArgument(
                () -> definition("valid", "test:key", 0L, 1L),
                "zero quest target is rejected"
        );
        expectIllegalArgument(
                () -> definition("valid", "test:key", -1L, 1L),
                "negative quest target is rejected"
        );
        expectIllegalArgument(
                () -> definition("valid", "test:key", 1L, 0L),
                "zero coin reward is rejected"
        );
        expectIllegalArgument(
                () -> definition("valid", "test:key", 1L, -1L),
                "negative coin reward is rejected"
        );
    }

    private static void testRegistrySnapshotsAndAtomicReplace() {
        QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
        check(registry.size() == 0 && registry.snapshot().isEmpty(),
                "production-ready registry starts empty");

        QuestDefinition beta = definition("beta", "test:beta", 2L, 20L);
        QuestDefinition alpha = definition("alpha", "test:alpha", 1L, 10L);
        registry.replaceAll(List.of(beta, alpha));
        check(registry.snapshot().stream().map(QuestDefinition::id).toList()
                        .equals(List.of("alpha", "beta")),
                "registry snapshot iteration is deterministic by quest ID");
        check(registry.findById("alpha").orElseThrow().equals(alpha)
                        && registry.findById("missing").isEmpty(),
                "registry supports exact lookup by quest ID");
        expectUnsupported(
                () -> registry.snapshot().add(alpha),
                "definition list snapshot is immutable"
        );
        expectUnsupported(
                () -> registry.snapshotById().put("other", alpha),
                "definition map snapshot is immutable"
        );

        List<QuestDefinition> beforeFailure = registry.snapshot();
        expectIllegalArgument(
                () -> registry.replaceAll(List.of(alpha, alpha)),
                "duplicate definition IDs are rejected"
        );
        check(registry.snapshot().equals(beforeFailure),
                "failed duplicate replacement leaves the old registry untouched");
        expectNullPointer(
                () -> registry.replaceAll(java.util.Arrays.asList(alpha, null)),
                "null definition is rejected"
        );
        check(registry.snapshot().equals(beforeFailure),
                "failed null replacement is atomic and retains old definitions");

        registry.replaceAll(List.of());
        check(registry.snapshotById().equals(Map.of()),
                "valid empty replacement atomically clears the registry");
    }

    private static QuestDefinition definition(
            String id,
            String progressKey,
            long target,
            long rewardCoins
    ) {
        return new QuestDefinition(
                id,
                "Quest " + id,
                "Complete " + id,
                key(progressKey),
                target,
                rewardCoins
        );
    }

    private static QuestProgressKey key(String value) {
        return QuestProgressKey.of(value);
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        expect(IllegalArgumentException.class, action, message);
    }

    private static void expectNullPointer(Runnable action, String message) {
        expect(NullPointerException.class, action, message);
    }

    private static void expectUnsupported(Runnable action, String message) {
        expect(UnsupportedOperationException.class, action, message);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (RuntimeException exception) {
            if (!type.isInstance(exception)) {
                throw new AssertionError(message, exception);
            }
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

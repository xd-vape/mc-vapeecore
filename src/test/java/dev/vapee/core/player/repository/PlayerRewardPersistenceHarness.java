package dev.vapee.core.player.repository;

import dev.vapee.core.player.CorePlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class PlayerRewardPersistenceHarness {

    private static int checks;

    private PlayerRewardPersistenceHarness() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("vapeecore-player-reward-harness-");
        CapturingHandler handler = new CapturingHandler();
        Logger logger = Logger.getLogger("PlayerRewardPersistenceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        FilePlayerRepository repository = new FilePlayerRepository(directory, logger);
        repository.initialize();

        try {
            testLegacyAndRoundTrip(directory, repository);
            testInvalidOptionalState(directory, repository, handler);
        } finally {
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(directory);
        }

        System.out.println("PlayerRewardPersistenceHarness passed " + checks + " checks.");
    }

    private static void testLegacyAndRoundTrip(
            Path directory,
            FilePlayerRepository repository
    ) throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = directory.resolve(playerId + ".yml");
        Files.writeString(playerFile, basicPlayerYaml("Legacy"));

        CorePlayer legacy = repository.findByUniqueId(playerId).orElseThrow();
        check(!legacy.getOnlineRewardProgress().isInitialized(),
                "player file without rewards section loads as uninitialized");
        check(legacy.getWallet().getCoins() == 0L
                        && legacy.getSettings().isScoreboardEnabled()
                        && legacy.getSocial().getIgnoredPlayers().isEmpty(),
                "legacy economy, settings, and social defaults remain intact");

        repository.save(legacy);
        check(!Files.readString(playerFile).contains("rewards:"),
                "saving uninitialized progress does not invent a processed baseline");

        legacy.getOnlineRewardProgress().setProcessedPlaytimeTicks(123_456L);
        repository.save(legacy);
        String saved = Files.readString(playerFile);
        check(saved.contains("processed-playtime-ticks: 123456"),
                "initialized processed playtime is written to the player YAML");

        CorePlayer reloaded = repository.findByUniqueId(playerId).orElseThrow();
        check(reloaded.getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow() == 123_456L,
                "processed playtime survives repository reload");
    }

    private static void testInvalidOptionalState(
            Path directory,
            FilePlayerRepository repository,
            CapturingHandler handler
    ) throws IOException {
        UUID negativeId = UUID.randomUUID();
        Path negativeFile = directory.resolve(negativeId + ".yml");
        Files.writeString(
                negativeFile,
                basicPlayerYaml("Negative")
                        + "rewards:\n  online:\n    processed-playtime-ticks: -1\n"
        );
        handler.messages.clear();
        CorePlayer negative = repository.findByUniqueId(negativeId).orElseThrow();
        check(!negative.getOnlineRewardProgress().isInitialized(),
                "negative optional reward progress migrates to uninitialized");
        check(handler.messages.stream().anyMatch(message -> message.contains("must not be negative")),
                "negative optional reward progress emits one controlled warning");

        UUID wrongTypeId = UUID.randomUUID();
        Path wrongTypeFile = directory.resolve(wrongTypeId + ".yml");
        Files.writeString(
                wrongTypeFile,
                basicPlayerYaml("WrongType")
                        + "rewards:\n  online:\n    processed-playtime-ticks: nope\n"
        );
        handler.messages.clear();
        CorePlayer wrongType = repository.findByUniqueId(wrongTypeId).orElseThrow();
        check(!wrongType.getOnlineRewardProgress().isInitialized(),
                "wrong-type optional reward progress migrates to uninitialized");
        check(handler.messages.stream().anyMatch(message -> message.contains("non-negative integer")),
                "wrong-type optional reward progress emits one controlled warning");

        UUID invalidSectionId = UUID.randomUUID();
        Path invalidSectionFile = directory.resolve(invalidSectionId + ".yml");
        Files.writeString(invalidSectionFile, basicPlayerYaml("InvalidSection") + "rewards: invalid\n");
        handler.messages.clear();
        CorePlayer invalidSection = repository.findByUniqueId(invalidSectionId).orElseThrow();
        check(!invalidSection.getOnlineRewardProgress().isInitialized(),
                "invalid optional rewards section does not invalidate the whole player");
        check(handler.messages.stream().anyMatch(message -> message.contains("expected a YAML section")),
                "invalid optional rewards section emits a controlled warning");
    }

    private static String basicPlayerYaml(String name) {
        return "name: " + name + "\nfirst-join: 1000\nlast-join: 1000\n";
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class CapturingHandler extends Handler {

        private final List<String> messages = new ArrayList<>();

        private CapturingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}

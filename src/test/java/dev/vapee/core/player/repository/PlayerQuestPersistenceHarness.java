package dev.vapee.core.player.repository;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.onlinereward.OnlineRewardProgress;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import dev.vapee.core.quest.PlayerQuestProgress;
import dev.vapee.core.quest.PlayerQuestState;
import dev.vapee.core.quest.QuestStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class PlayerQuestPersistenceHarness {

    private static int checks;

    private PlayerQuestPersistenceHarness() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("vapeecore-player-quest-harness-");
        CapturingHandler handler = new CapturingHandler();
        Logger logger = Logger.getLogger("PlayerQuestPersistenceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        FilePlayerRepository repository = new FilePlayerRepository(directory, logger);
        repository.initialize();

        try {
            testLegacyAndRoundTrip(directory, repository);
            testInvalidIndividualEntries(directory, repository, handler);
            testInvalidSections(directory, repository, handler);
            testUnknownDefinitionAndRestartState(directory, repository);
        } finally {
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(directory);
        }

        System.out.println("PlayerQuestPersistenceHarness passed " + checks + " checks.");
    }

    private static void testLegacyAndRoundTrip(
            Path directory,
            FilePlayerRepository repository
    ) throws IOException {
        UUID legacyId = UUID.randomUUID();
        Path legacyFile = directory.resolve(legacyId + ".yml");
        Files.writeString(legacyFile, basicPlayerYaml("Legacy"));
        CorePlayer legacy = repository.findByUniqueId(legacyId).orElseThrow();
        check(legacy.getQuestState().isEmpty(),
                "legacy player without quests section loads an empty quest state");
        repository.save(legacy);
        check(!Files.readString(legacyFile).contains("quests:"),
                "empty quest state follows the existing convention and omits its YAML section");

        UUID playerId = UUID.randomUUID();
        PlayerQuestState state = PlayerQuestState.of(List.of(
                new PlayerQuestProgress("z_active", 137L, QuestStatus.ACTIVE),
                new PlayerQuestProgress("a_completed", 30L, QuestStatus.COMPLETED),
                new PlayerQuestProgress("m_pending", 250L, QuestStatus.REWARD_PENDING)
        ));
        repository.save(player(playerId, state));
        String saved = Files.readString(directory.resolve(playerId + ".yml"));
        check(saved.contains("quests:")
                        && saved.contains("active:")
                        && saved.contains("progress: 137")
                        && saved.contains("status: ACTIVE")
                        && saved.contains("status: COMPLETED")
                        && saved.contains("status: REWARD_PENDING"),
                "all three quest statuses and progress values are stored in player YAML");
        check(saved.indexOf("a_completed:") < saved.indexOf("m_pending:")
                        && saved.indexOf("m_pending:") < saved.indexOf("z_active:"),
                "quest persistence order is deterministic by quest ID");

        CorePlayer reloaded = repository.findByUniqueId(playerId).orElseThrow();
        check(reloaded.getQuestState().snapshot().equals(state.snapshot()),
                "multiple ACTIVE, COMPLETED, and REWARD_PENDING states round-trip exactly");
        expectUnsupported(
                () -> reloaded.getQuestState().snapshot().clear(),
                "loaded quest snapshot cannot mutate repository-owned player state"
        );
    }

    private static void testInvalidIndividualEntries(
            Path directory,
            FilePlayerRepository repository,
            CapturingHandler handler
    ) throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = directory.resolve(playerId + ".yml");
        Files.writeString(
                playerFile,
                basicPlayerYaml("PartiallyInvalid")
                        + "quests:\n"
                        + "  active:\n"
                        + "    valid_quest:\n"
                        + "      progress: 4\n"
                        + "      status: ACTIVE\n"
                        + "    negative_quest:\n"
                        + "      progress: -1\n"
                        + "      status: ACTIVE\n"
                        + "    bad_status:\n"
                        + "      progress: 3\n"
                        + "      status: FINISHED\n"
                        + "    InvalidId:\n"
                        + "      progress: 2\n"
                        + "      status: COMPLETED\n"
                        + "    wrong_type:\n"
                        + "      progress: nope\n"
                        + "      status: ACTIVE\n"
        );
        handler.messages.clear();
        CorePlayer loaded = repository.findByUniqueId(playerId).orElseThrow();
        check(loaded.getQuestState().snapshot().equals(java.util.Map.of(
                        "valid_quest",
                        new PlayerQuestProgress("valid_quest", 4L, QuestStatus.ACTIVE)
                )),
                "invalid individual quest entries are skipped while valid state still loads");
        check(handler.messages.stream().filter(message -> message.contains("quest state")).count() == 4L,
                "each corrupt optional quest entry emits one controlled warning");
        check(loaded.getName().equals("PartiallyInvalid")
                        && loaded.getWallet().getCoins() == 0L,
                "corrupt optional quest entries do not invalidate the core player profile");
    }

    private static void testInvalidSections(
            Path directory,
            FilePlayerRepository repository,
            CapturingHandler handler
    ) throws IOException {
        UUID questsId = UUID.randomUUID();
        Path questsFile = directory.resolve(questsId + ".yml");
        Files.writeString(questsFile, basicPlayerYaml("WrongQuests") + "quests: invalid\n");
        handler.messages.clear();
        CorePlayer wrongQuests = repository.findByUniqueId(questsId).orElseThrow();
        check(wrongQuests.getQuestState().isEmpty()
                        && handler.messages.stream().anyMatch(message -> message.contains("'quests'")),
                "wrong quests section type becomes empty state with a warning");

        UUID activeId = UUID.randomUUID();
        Path activeFile = directory.resolve(activeId + ".yml");
        Files.writeString(
                activeFile,
                basicPlayerYaml("WrongActive") + "quests:\n  active: invalid\n"
        );
        handler.messages.clear();
        CorePlayer wrongActive = repository.findByUniqueId(activeId).orElseThrow();
        check(wrongActive.getQuestState().isEmpty()
                        && handler.messages.stream().anyMatch(message -> message.contains("quests.active")),
                "wrong active section type becomes empty state with a warning");
    }

    private static void testUnknownDefinitionAndRestartState(
            Path directory,
            FilePlayerRepository repository
    ) throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = directory.resolve(playerId + ".yml");
        Files.writeString(
                playerFile,
                basicPlayerYaml("Restart")
                        + "quests:\n"
                        + "  active:\n"
                        + "    definition_not_loaded:\n"
                        + "      progress: 137\n"
                        + "      status: ACTIVE\n"
                        + "    pending_after_restart:\n"
                        + "      progress: 250\n"
                        + "      status: REWARD_PENDING\n"
                        + "    completed_after_restart:\n"
                        + "      progress: 30\n"
                        + "      status: COMPLETED\n"
        );
        CorePlayer loaded = repository.findByUniqueId(playerId).orElseThrow();
        check(loaded.getQuestState().snapshot().get("definition_not_loaded").progress() == 137L
                        && loaded.getQuestState().snapshot().get("definition_not_loaded").status()
                        == QuestStatus.ACTIVE,
                "restart restores exact active progress without needing a registered definition");
        check(loaded.getQuestState().snapshot().get("pending_after_restart").status()
                        == QuestStatus.REWARD_PENDING
                        && loaded.getQuestState().snapshot().get("completed_after_restart").status()
                        == QuestStatus.COMPLETED,
                "pending and completed duplicate-protection states survive restart");
        check(loaded.getWallet().getCoins() == 0L,
                "repository load alone never grants quest coins or performs completion");
    }

    private static CorePlayer player(UUID playerId, PlayerQuestState questState) {
        Instant now = Instant.now();
        return new CorePlayer(
                playerId,
                "Player",
                now,
                now,
                PlayerSettings.defaults(),
                CoinWallet.empty(),
                PlayerSocial.empty(),
                OnlineRewardProgress.uninitialized(),
                questState
        );
    }

    private static String basicPlayerYaml(String name) {
        return "name: " + name + "\nfirst-join: 1000\nlast-join: 1000\n";
    }

    private static void expectUnsupported(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
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

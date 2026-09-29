package dev.vapee.core.player.repository;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.settings.AddedVisiblePlayerResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class PlayerVisibilityPersistenceHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-visibility-persistence-");
        CapturingHandler handler = new CapturingHandler();
        Logger logger = Logger.getLogger("PlayerVisibilityPersistenceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        FilePlayerRepository repository = new FilePlayerRepository(directory, logger);
        repository.initialize();
        try {
            legacyAndRoundTrip(directory, repository);
            invalidOptionalSettings(directory, repository, handler);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("PlayerVisibilityPersistenceHarness passed " + checks + " checks.");
    }

    private static void legacyAndRoundTrip(Path directory, FilePlayerRepository repository) throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = directory.resolve(owner + ".yml");
        Files.writeString(file, base("Legacy") + "settings:\n  lobby-players-visible: false\n");
        CorePlayer legacy = repository.findByUniqueId(owner).orElseThrow();
        var visibility = legacy.getSettings().getVisibility();
        check(!visibility.isAllPlayersVisible(), "legacy master toggle is preserved");
        check(!visibility.isShowFriends() && !visibility.isShowStaff()
                && !visibility.isShowAddedUsers() && !visibility.isShowGameParticipants()
                && visibility.getAddedPlayers().isEmpty(), "missing visibility section uses filter defaults");

        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");
        visibility.setShowFriends(true);
        visibility.setShowStaff(true);
        visibility.setShowAddedUsers(true);
        visibility.setShowGameParticipants(true);
        check(visibility.addPlayer(owner, first) == AddedVisiblePlayerResult.SUCCESS
                && visibility.addPlayer(owner, second) == AddedVisiblePlayerResult.SUCCESS,
                "roundtrip fixture adds UUID targets");
        repository.save(legacy);
        String saved = Files.readString(file).replace("\r\n", "\n");
        check(saved.contains("show-friends: true") && saved.contains("show-staff: true")
                && saved.contains("show-added-users: true") && saved.contains("show-game-participants: true"),
                "all visibility booleans are persisted");
        check(saved.indexOf(second.toString()) < saved.indexOf(first.toString()),
                "added UUIDs save in deterministic lexical order");
        CorePlayer reloaded = repository.findByUniqueId(owner).orElseThrow();
        check(reloaded.getSettings().getVisibility().getAddedPlayers().equals(Set.of(first, second)),
                "added players roundtrip as UUIDs");
        check(reloaded.getSettings().getVisibility().isShowFriends()
                && reloaded.getSettings().getVisibility().isShowStaff()
                && reloaded.getSettings().getVisibility().isShowAddedUsers()
                && reloaded.getSettings().getVisibility().isShowGameParticipants(),
                "boolean filters roundtrip");
    }

    private static void invalidOptionalSettings(Path directory, FilePlayerRepository repository,
                                                CapturingHandler handler) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID valid = UUID.randomUUID();
        Path file = directory.resolve(owner + ".yml");
        Files.writeString(file, base("Invalid") + "settings:\n"
                + "  lobby-players-visible: true\n"
                + "  visibility:\n"
                + "    show-friends: abc\n"
                + "    show-staff: false\n"
                + "    show-added-users: false\n"
                + "    show-game-participants: false\n"
                + "    added-players:\n"
                + "      - '" + valid + "'\n"
                + "      - '" + valid + "'\n"
                + "      - '" + owner + "'\n"
                + "      - 'invalid'\n"
                + "      - 12\n");
        handler.messages.clear();
        CorePlayer loaded = repository.findByUniqueId(owner).orElseThrow();
        check(!loaded.getSettings().getVisibility().isShowFriends(),
                "invalid boolean falls back to false");
        check(loaded.getSettings().getVisibility().getAddedPlayers().equals(Set.of(valid)),
                "duplicates deduplicate while self and invalid entries are ignored");
        check(handler.messages.stream().anyMatch(message -> message.contains("show-friends"))
                && handler.messages.stream().anyMatch(message -> message.contains("own UUID"))
                && handler.messages.stream().anyMatch(message -> message.contains("invalid UUID"))
                && handler.messages.stream().anyMatch(message -> message.contains("UUID string entry")),
                "invalid optional values emit controlled warnings");

        UUID sectionOwner = UUID.randomUUID();
        Path sectionFile = directory.resolve(sectionOwner + ".yml");
        Files.writeString(sectionFile, base("Section") + "settings:\n"
                + "  lobby-players-visible: false\n  visibility: broken\n");
        handler.messages.clear();
        CorePlayer section = repository.findByUniqueId(sectionOwner).orElseThrow();
        check(!section.getSettings().isLobbyPlayersVisible()
                && section.getSettings().getVisibility().getAddedPlayers().isEmpty(),
                "invalid visibility section keeps valid master and defaults filters");
        check(handler.messages.stream().anyMatch(message -> message.contains("expected a YAML section")),
                "invalid visibility section warns without rejecting login");
    }

    private static String base(String name) {
        return "name: '" + name + "'\nfirst-join: 1000\nlast-join: 1000\n";
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();
        @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
        @Override public void flush() { }
        @Override public void close() { }
    }
}

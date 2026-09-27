package dev.vapee.core.friend;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class FriendPersistenceHarness {

    private static int checks;

    private FriendPersistenceHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-friends-");
        try {
            missingFile(directory);
            roundTrip(directory);
            atomicFallback(directory);
            failedSaveCleanup(directory);
            corruption(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("FriendPersistenceHarness passed " + checks + " checks.");
    }

    private static void missingFile(Path directory) {
        Path file = directory.resolve("missing-parent").resolve("friends.yml");
        FriendSnapshot snapshot = new FileFriendRepository(file, logger()).initialize();
        check(snapshot.equals(FriendSnapshot.empty()), "missing file loads empty state");
        check(Files.isDirectory(file.getParent()) && Files.notExists(file),
                "initialize creates parent but not an empty data file");
    }

    private static void roundTrip(Path directory) throws IOException {
        Path file = directory.resolve("roundtrip").resolve("friends.yml");
        UUID first = id(1);
        UUID second = id(2);
        UUID third = id(3);
        UUID fourth = id(4);
        Friendship laterPair = new Friendship(third, second, Instant.ofEpochMilli(2_000L));
        Friendship firstPair = new Friendship(second, first, Instant.ofEpochMilli(1_000L));
        FriendRequest laterRequest = new FriendRequest(fourth, first, Instant.ofEpochMilli(4_000L));
        FriendRequest firstRequest = new FriendRequest(first, third, Instant.ofEpochMilli(3_000L));
        FriendSnapshot snapshot = new FriendSnapshot(
                List.of(laterPair, firstPair),
                List.of(laterRequest, firstRequest)
        );

        FileFriendRepository repository = new FileFriendRepository(file, logger());
        repository.save(snapshot);
        String yaml = Files.readString(file);
        check(yaml.contains("schema-version: 1"), "save writes schema-version 1");
        check(yaml.contains("friendships:") && yaml.contains("requests:"),
                "save writes both central collections");
        check(!yaml.contains("name:") && !yaml.contains("rank:") && !yaml.contains("display-name:"),
                "save persists UUID data without identity duplication");
        check(yaml.indexOf(firstPair.playerA().toString()) < yaml.indexOf(laterPair.playerA().toString()),
                "friendships are written in canonical pair order");
        check(yaml.indexOf(firstRequest.sender().toString()) < yaml.indexOf(laterRequest.sender().toString()),
                "requests are written in sender/recipient order");

        FriendSnapshot reloaded = new FileFriendRepository(file, logger()).initialize();
        check(reloaded.equals(snapshot), "repository roundtrip reconstructs exact state");
        check(reloaded.friendships().getFirst().createdAt().equals(Instant.ofEpochMilli(1_000L))
                        && reloaded.requests().getFirst().createdAt().equals(Instant.ofEpochMilli(3_000L)),
                "roundtrip retains createdAt values");
        check(noTemporaryFiles(file.getParent()), "successful save leaves no temporary file");
    }

    private static void atomicFallback(Path directory) throws IOException {
        Path file = directory.resolve("fallback").resolve("friends.yml").toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        int[] moves = {0};
        boolean[] observedTemporaryFile = {false};
        FriendFileMover mover = (source, target, options) -> {
            observedTemporaryFile[0] |= Files.isRegularFile(source)
                    && source.getFileName().toString().endsWith(".tmp");
            moves[0]++;
            if (moves[0] == 1 && hasOption(options, StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "simulated");
            }
            Files.move(source, target, options);
        };
        FileFriendRepository repository = new FileFriendRepository(file, logger(), mover);
        repository.save(FriendSnapshot.empty());

        check(observedTemporaryFile[0], "save writes through a sibling temporary file");
        check(moves[0] == 2 && Files.isRegularFile(file),
                "atomic-move rejection uses controlled replace fallback");
        check(noTemporaryFiles(file.getParent()), "fallback success leaves no temporary file");
    }

    private static void failedSaveCleanup(Path directory) throws IOException {
        Path file = directory.resolve("failure").resolve("friends.yml").toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "original-data");
        FriendFileMover mover = (source, target, options) -> {
            throw new IOException("simulated move failure");
        };
        FileFriendRepository repository = new FileFriendRepository(file, logger(), mover);
        rejectRepository(() -> repository.save(FriendSnapshot.empty()),
                "move failure is reported as repository failure");
        check(Files.readString(file).equals("original-data"),
                "failed replacement preserves the previous target file");
        check(noTemporaryFiles(file.getParent()), "failed save cleans temporary file");
    }

    private static void corruption(Path directory) throws IOException {
        Path file = directory.resolve("corruption").resolve("friends.yml");
        Files.createDirectories(file.getParent());
        String first = id(1).toString();
        String second = id(2).toString();

        rejectYaml(file, "schema-version: 2\nfriendships: []\nrequests: []\n",
                "future schema is rejected");
        rejectYaml(file, "schema-version: one\nfriendships: []\nrequests: []\n",
                "invalid schema type is rejected");
        rejectYaml(file, base(friendship(first, first, "1"), "[]"),
                "self friendship is rejected");
        rejectYaml(file, base(
                "\n  - player-a: " + first + "\n    player-b: " + second + "\n    created-at: 1"
                        + "\n  - player-a: " + first + "\n    player-b: " + second + "\n    created-at: 2",
                "[]"
        ), "duplicate friendship is rejected");
        rejectYaml(file, base(
                "\n  - player-a: " + first + "\n    player-b: " + second + "\n    created-at: 1"
                        + "\n  - player-a: " + second + "\n    player-b: " + first + "\n    created-at: 2",
                "[]"
        ), "inverse duplicate friendship is rejected");
        rejectYaml(file, base("[]", request(first, first, "1")),
                "self request is rejected");
        rejectYaml(file, base("[]",
                "\n  - sender: " + first + "\n    recipient: " + second + "\n    created-at: 1"
                        + "\n  - sender: " + first + "\n    recipient: " + second + "\n    created-at: 2"
        ), "duplicate request is rejected");
        rejectYaml(file, base("[]",
                "\n  - sender: " + first + "\n    recipient: " + second + "\n    created-at: 1"
                        + "\n  - sender: " + second + "\n    recipient: " + first + "\n    created-at: 2"
        ), "opposing requests are rejected");
        rejectYaml(file, base(friendship(first, second, "1"), request(first, second, "2")),
                "request between friends is rejected");
        rejectYaml(file, base(friendship("not-a-uuid", second, "1"), "[]"),
                "invalid UUID is rejected");
        rejectYaml(file, base(friendship(first, second, "not-a-number"), "[]"),
                "invalid timestamp type is rejected");
        rejectYaml(file, "schema-version: 1\nfriendships: invalid\nrequests: []\n",
                "invalid collection type is rejected");
    }

    private static String base(String friendships, String requests) {
        return "schema-version: 1\nfriendships:" + friendships + "\nrequests:" + requests + "\n";
    }

    private static String friendship(String first, String second, String timestamp) {
        return "\n  - player-a: " + first
                + "\n    player-b: " + second
                + "\n    created-at: " + timestamp;
    }

    private static String request(String sender, String recipient, String timestamp) {
        return "\n  - sender: " + sender
                + "\n    recipient: " + recipient
                + "\n    created-at: " + timestamp;
    }

    private static void rejectYaml(Path file, String yaml, String message) throws IOException {
        Files.writeString(file, yaml);
        rejectRepository(() -> new FileFriendRepository(file, logger()).initialize(), message);
    }

    private static boolean hasOption(CopyOption[] options, CopyOption expected) {
        for (CopyOption option : options) {
            if (option == expected) {
                return true;
            }
        }
        return false;
    }

    private static boolean noTemporaryFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"));
        }
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("FriendPersistenceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void rejectRepository(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (FriendRepositoryException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

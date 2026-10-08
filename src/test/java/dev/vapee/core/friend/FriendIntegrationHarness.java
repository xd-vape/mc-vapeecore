package dev.vapee.core.friend;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.social.SocialService;
import dev.vapee.core.settings.SettingsMenu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class FriendIntegrationHarness {

    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-friend-integration-");
        try {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            UUID charlie = UUID.randomUUID();
            UUID invalid = UUID.randomUUID();
            Path playersDirectory = directory.resolve("players");
            Files.createDirectories(playersDirectory);
            Files.writeString(playersDirectory.resolve(alice + ".yml"), playerYaml("Alice", ""));
            Files.writeString(playersDirectory.resolve(bob + ".yml"), playerYaml("Bob",
                    "settings:\n  friend-requests: false\nsocial:\n  ignored:\n    - '" + alice + "'\n"));
            Files.writeString(playersDirectory.resolve(charlie + ".yml"), playerYaml("Charlie", ""));
            Path invalidFile = playersDirectory.resolve(invalid + ".yml");
            String invalidYaml = playerYaml("Invalid", "settings:\n  friend-requests: nope\n");
            Files.writeString(invalidFile, invalidYaml);
            CapturingHandler handler = new CapturingHandler();
            Logger logger = logger();
            logger.addHandler(handler);
            FilePlayerRepository fileRepository = new FilePlayerRepository(playersDirectory, logger);
            fileRepository.initialize();
            FailingRepository repository = new FailingRepository(fileRepository);
            PlayerService players = new PlayerService(repository, logger);
            PlayerSettingsService settings = new PlayerSettingsService(players);
            SocialService social = new SocialService(players, logger);

            check(settings.areKnownFriendRequestsEnabled(alice).orElseThrow(),
                    "legacy offline profile defaults to accepting friend requests");
            check(!settings.areKnownFriendRequestsEnabled(bob).orElseThrow(),
                    "known offline privacy reads persisted false");
            check(settings.areKnownFriendRequestsEnabled(UUID.randomUUID()).isEmpty(),
                    "unknown privacy returns empty");
            check(!players.isLoaded(alice) && !players.isLoaded(bob),
                    "known settings reads do not load players");
            handler.messages.clear();
            check(settings.areKnownFriendRequestsEnabled(invalid).orElseThrow()
                            && handler.messages.stream().anyMatch(message -> message.contains("friend-requests"))
                            && Files.readString(invalidFile).equals(invalidYaml),
                    "invalid setting warns, defaults true, and leaves YAML unchanged");

            players.loadPlayer(alice, "Alice");
            check(settings.areFriendRequestsEnabled(alice).orElseThrow(),
                    "loaded default privacy is true");
            repository.failNext = true;
            boolean failed = false;
            try {
                settings.setFriendRequestsEnabled(alice, false);
            } catch (IllegalStateException expected) {
                failed = true;
            }
            check(failed && settings.areFriendRequestsEnabled(alice).orElseThrow(),
                    "settings save failure rolls back loaded value");
            check(settings.setFriendRequestsEnabled(alice, false)
                            && !settings.areKnownFriendRequestsEnabled(alice).orElseThrow(),
                    "settings false saves and is immediately readable");
            check(Files.readString(playersDirectory.resolve(alice + ".yml"))
                            .contains("friend-requests: false"), "new setting uses expected player YAML key");
            check(settings.setFriendRequestsEnabled(alice, true), "settings true saves");
            players.unloadPlayer(alice);
            check(settings.areKnownFriendRequestsEnabled(alice).orElseThrow()
                            && !players.isLoaded(alice),
                    "offline privacy read uses saved true without loading");

            check(social.isKnownIgnoring(bob, alice), "known offline owner ignore is read from persistence");
            check(!social.isKnownIgnoring(charlie, alice), "known offline non-ignore is false");
            check(!social.isKnownIgnoring(UUID.randomUUID(), alice), "unknown owner does not ignore");
            check(!players.isLoaded(bob) && !players.isLoaded(charlie),
                    "offline ignore checks do not mutate loaded cache");
            players.loadPlayer(bob, "Bob");
            social.activatePlayer(bob);
            check(social.isKnownIgnoring(bob, alice), "loaded ignore snapshot is authoritative");
            check(!social.isKnownIgnoring(bob, charlie), "loaded non-ignore is false");
            players.unloadPlayer(bob);
            social.deactivatePlayer(bob);
            check(social.isKnownIgnoring(bob, alice), "persisted ignore remains after unload");

            FriendRequestPolicy policy = FriendModule.createPolicy(social, settings);
            check(policy.evaluate(alice, bob) == FriendRequestDecision.BLOCKED
                            && policy.evaluate(bob, alice) == FriendRequestDecision.BLOCKED,
                    "ignore blocks requests in either direction without revealing the side");
            check(policy.evaluate(alice, charlie) == FriendRequestDecision.ALLOW,
                    "known offline recipient with default privacy allows requests");
            Path friendsFile = directory.resolve("friends.yml");
            FriendService friends = new FriendService(new FileFriendRepository(friendsFile, logger),
                    FriendLimits::defaults, policy, Clock.systemUTC());
            check(!Files.exists(friendsFile), "absent friends.yml starts empty without eager data file");
            check(friends.sendRequest(alice, bob) == FriendResult.BLOCKED
                            && !Files.exists(friendsFile), "blocked request does not persist data");
            check(friends.sendRequest(alice, charlie) == FriendResult.SUCCESS
                            && Files.readString(friendsFile).contains("schema-version: 1"),
                    "allowed request persists central versioned friends data");
            players.loadPlayer(charlie, "Charlie");
            settings.setFriendRequestsEnabled(charlie, false);
            players.unloadPlayer(charlie);
            check(friends.acceptRequest(charlie, alice) == FriendResult.SUCCESS,
                    "existing request can be accepted after recipient disables new requests");
            check(friends.getRelation(alice, charlie) == FriendRelation.FRIENDS,
                    "privacy change does not destroy accepted friendship");
            check(friends.sendRequest(bob, charlie) == FriendResult.REQUESTS_DISABLED,
                    "disabled privacy blocks new request without persistence mutation");
            check(SettingsMenu.FRIEND_REQUESTS_SLOT >= 0
                            && SettingsMenu.FRIEND_REQUESTS_STATUS_SLOT == SettingsMenu.FRIEND_REQUESTS_SLOT + 9
                            && SettingsMenu.FRIEND_REQUESTS_STATUS_SLOT < SettingsMenu.INVENTORY_SIZE
                            && SettingsMenu.INVENTORY_SIZE == 54,
                    "friend request setting has feature and status below in redesigned settings GUI");
            String menuSource = Files.readString(Path.of(
                    "src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java"));
            String listenerSource = Files.readString(Path.of(
                    "src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java"));
            check(menuSource.contains("PlayerSettings::isFriendRequestsEnabled")
                            && listenerSource.contains("PlayerSettingsService::setFriendRequestsEnabled"),
                    "settings GUI displays and toggles persisted friend request privacy");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("FriendIntegrationHarness passed " + checks + " checks.");
    }

    private static String playerYaml(String name, String extra) {
        return "name: '" + name + "'\nfirst-join: 0\nlast-join: 0\n" + extra;
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("FriendIntegrationHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

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

    private static final class FailingRepository implements PlayerRepository {
        private final PlayerRepository delegate;
        private boolean failNext;

        private FailingRepository(PlayerRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return delegate.findByUniqueId(uniqueId);
        }

        @Override
        public void save(CorePlayer player) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated save failure");
            }
            delegate.save(player);
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return delegate.exists(uniqueId);
        }

        @Override
        public Set<UUID> findUniqueIdsByName(String name) {
            return delegate.findUniqueIdsByName(name);
        }
    }
}

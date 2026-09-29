package dev.vapee.core.visibility;

import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.friend.FriendRepository;
import dev.vapee.core.friend.FriendRequestPolicy;
import dev.vapee.core.friend.FriendResult;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.FriendSnapshot;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.player.settings.PlayerVisibilitySettings;
import dev.vapee.core.social.IgnoreResult;
import dev.vapee.core.social.SocialService;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

public final class VisibilityPolicyHarness {
    private static int checks;

    public static void main(String[] args) {
        UUID viewer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Map<UUID, PlayerVisibilitySettings> settings = new HashMap<>();
        PlayerVisibilitySettings choice = PlayerVisibilitySettings.defaults();
        settings.put(viewer, choice);
        MutablePair ignore = new MutablePair();
        MutablePair friends = new MutablePair();
        MutablePair game = new MutablePair();
        VisibilityPolicy policy = new VisibilityPolicy(settings::get, ignore, friends, game);

        check(policy.shouldShow(viewer, target, false), "default master shows target");
        check(policy.shouldShow(viewer, viewer, false), "self is never hidden");
        ignore.matches = true;
        check(!policy.shouldShow(viewer, target, false), "viewer-to-target ignore hides");
        ignore.matches = false;
        VisibilityPolicy reverseIgnore = new VisibilityPolicy(settings::get,
                (left, right) -> left.equals(target) && right.equals(viewer), friends, game);
        check(!reverseIgnore.shouldShow(viewer, target, false), "target-to-viewer ignore hides");

        choice.setAllPlayersVisible(false);
        check(!policy.shouldShow(viewer, target, false), "filtered mode without matches hides");
        friends.matches = true;
        check(!policy.shouldShow(viewer, target, false), "friend disabled remains hidden");
        choice.setShowFriends(true);
        check(policy.shouldShow(viewer, target, false), "enabled friend filter shows friend");
        ignore.matches = true;
        check(!policy.shouldShow(viewer, target, false), "ignore overrides friend");
        ignore.matches = false;
        friends.matches = false; // Also represents incoming/outgoing request: not FRIENDS.
        check(!policy.shouldShow(viewer, target, false), "friend request is not friendship");

        choice.setShowFriends(false);
        check(!policy.shouldShow(viewer, target, true), "staff disabled remains hidden");
        choice.setShowStaff(true);
        check(policy.shouldShow(viewer, target, true), "enabled staff filter shows marker target");
        ignore.matches = true;
        check(!policy.shouldShow(viewer, target, true), "ignore overrides staff");
        ignore.matches = false;

        choice.setShowStaff(false);
        choice.addPlayer(viewer, target);
        check(!policy.shouldShow(viewer, target, false), "added target disabled remains hidden");
        choice.setShowAddedUsers(true);
        check(policy.shouldShow(viewer, target, false), "enabled added-user filter shows target");
        ignore.matches = true;
        check(!policy.shouldShow(viewer, target, false), "ignore overrides added user");
        ignore.matches = false;

        choice.setShowAddedUsers(false);
        game.matches = true;
        check(!policy.shouldShow(viewer, target, false), "game filter disabled remains hidden");
        choice.setShowGameParticipants(true);
        check(policy.shouldShow(viewer, target, false), "game extension provider can show target");
        ignore.matches = true;
        check(!policy.shouldShow(viewer, target, false), "ignore overrides game participant");
        ignore.matches = false;

        choice.setShowFriends(true);
        friends.matches = true;
        check(policy.shouldShow(viewer, target, true), "multiple positive matches remain visible");
        choice.setAllPlayersVisible(true);
        ignore.matches = true;
        check(!policy.shouldShow(viewer, target, true), "ignore overrides master all");

        productionIntegrations();
        System.out.println("VisibilityPolicyHarness passed " + checks + " checks.");
    }

    private static void productionIntegrations() {
        UUID viewer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        MemoryPlayerRepository playersRepository = new MemoryPlayerRepository();
        PlayerService players = new PlayerService(playersRepository, Logger.getLogger("visibility-policy-test"));
        players.loadPlayer(viewer, "Viewer");
        players.loadPlayer(target, "Target");
        PlayerSettingsService settings = new PlayerSettingsService(players);
        check(settings.setLobbyPlayersVisible(viewer, false), "production setup enables filtered mode");
        check(settings.setLobbyFriendsVisible(viewer, true), "production setup enables friend filter");

        FriendService friends = new FriendService(
                new MemoryFriendRepository(),
                new FriendLimits(20, 20, 20),
                FriendRequestPolicy.allowAll(),
                Clock.systemUTC()
        );
        SocialService social = new SocialService(players, Logger.getLogger("visibility-policy-test"));
        VisibilityPolicy policy = new VisibilityPolicy(settings, social, friends, (left, right) -> false);

        check(friends.sendRequest(viewer, target) == FriendResult.SUCCESS,
                "production friend integration creates a pending request");
        check(!policy.shouldShow(viewer, target, false),
                "production FriendService does not treat a request as friendship");
        check(friends.acceptRequest(target, viewer) == FriendResult.SUCCESS,
                "production friend integration accepts the request");
        check(policy.shouldShow(viewer, target, false),
                "production FriendService friendship enables the filter");
        check(social.ignore(target, viewer) == IgnoreResult.SUCCESS,
                "production SocialService creates reverse ignore");
        check(!policy.shouldShow(viewer, target, false),
                "production SocialService reverse ignore overrides friendship");
    }

    private static final class MutablePair implements BiPredicate<UUID, UUID> {
        private boolean matches;
        @Override public boolean test(UUID first, UUID second) { return matches; }
    }

    private static final class MemoryPlayerRepository implements PlayerRepository {
        private final Map<UUID, CorePlayer> players = new HashMap<>();

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(players.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            players.put(player.getUniqueId(), player);
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return players.containsKey(uniqueId);
        }

        @Override
        public Set<UUID> findUniqueIdsByName(String name) {
            return Set.of();
        }
    }

    private static final class MemoryFriendRepository implements FriendRepository {
        private FriendSnapshot snapshot = FriendSnapshot.empty();

        @Override
        public FriendSnapshot initialize() {
            return snapshot;
        }

        @Override
        public void save(FriendSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

package dev.vapee.core.visibility;

import dev.vapee.core.friend.FriendRelation;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.player.settings.PlayerVisibilitySettings;
import dev.vapee.core.social.SocialService;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** One directional viewer→target decision, with Ignore as a hard privacy rule. */
public final class VisibilityPolicy {
    private final Function<UUID, PlayerVisibilitySettings> settings;
    private final BiPredicate<UUID, UUID> ignored;
    private final BiPredicate<UUID, UUID> friends;
    private final BiPredicate<UUID, UUID> sameGameParticipants;

    public VisibilityPolicy(PlayerSettingsService settings, SocialService social, FriendService friends,
                            BiPredicate<UUID, UUID> sameGameParticipants) {
        this(viewer -> settings.getSettings(viewer).map(value -> value.getVisibility())
                        .orElseGet(PlayerVisibilitySettings::defaults),
                social::isKnownIgnoring,
                (viewer, target) -> friends.getRelation(viewer, target) == FriendRelation.FRIENDS,
                sameGameParticipants);
    }

    public VisibilityPolicy(Function<UUID, PlayerVisibilitySettings> settings,
                            BiPredicate<UUID, UUID> ignored,
                            BiPredicate<UUID, UUID> friends,
                            BiPredicate<UUID, UUID> sameGameParticipants) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.ignored = Objects.requireNonNull(ignored, "ignored");
        this.friends = Objects.requireNonNull(friends, "friends");
        this.sameGameParticipants = Objects.requireNonNull(sameGameParticipants, "sameGameParticipants");
    }

    public boolean shouldShow(UUID viewer, UUID target, boolean targetIsStaff) {
        UUID checkedViewer = Objects.requireNonNull(viewer, "viewer");
        UUID checkedTarget = Objects.requireNonNull(target, "target");
        if (checkedViewer.equals(checkedTarget)) return true;
        if (ignored.test(checkedViewer, checkedTarget)
                || ignored.test(checkedTarget, checkedViewer)) return false;
        PlayerVisibilitySettings choice = Objects.requireNonNull(settings.apply(checkedViewer), "visibility settings");
        if (choice.isAllPlayersVisible()) return true;
        return (choice.isShowFriends() && friends.test(checkedViewer, checkedTarget))
                || (choice.isShowStaff() && targetIsStaff)
                || (choice.isShowAddedUsers() && choice.includesAddedPlayer(checkedTarget))
                || (choice.isShowGameParticipants() && sameGameParticipants.test(checkedViewer, checkedTarget));
    }
}

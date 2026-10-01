package dev.vapee.core.player.settings;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class PlayerSettingsServiceHarness {
    private static int checks;

    public static void main(String[] args) {
        MemoryRepository repository = new MemoryRepository();
        PlayerService players = new PlayerService(repository, logger());
        PlayerSettingsService settings = new PlayerSettingsService(players);
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        players.loadPlayer(owner, "Owner");
        int baselineSaves = repository.saves;

        check(!settings.areFriendPresenceNotificationsEnabled(owner).orElseThrow(),
                "friend presence notifications default to opt-in disabled");
        check(settings.areFriendPresenceNotificationsEnabled(UUID.randomUUID()).isEmpty(),
                "unloaded presence setting read is empty");
        check(settings.areLobbyPlayersVisible(owner).orElseThrow()
                && !settings.areLobbyFriendsVisible(owner).orElseThrow()
                && !settings.areLobbyStaffVisible(owner).orElseThrow()
                && !settings.areLobbyAddedUsersVisible(owner).orElseThrow()
                && !settings.areLobbyGameParticipantsVisible(owner).orElseThrow(),
                "loaded player defaults are readable");
        check(settings.areLobbyPlayersVisible(UUID.randomUUID()).isEmpty(),
                "unloaded owner read is empty");
        check(!settings.setLobbyPlayersVisible(UUID.randomUUID(), false)
                && settings.addLobbyVisiblePlayer(UUID.randomUUID(), target)
                == AddedVisiblePlayerResult.OWNER_NOT_LOADED,
                "unloaded owner mutations are controlled");

        check(settings.setFriendPresenceNotificationsEnabled(owner, true)
                && settings.setLobbyPlayersVisible(owner, false)
                && settings.setLobbyFriendsVisible(owner, true)
                && settings.setLobbyStaffVisible(owner, true)
                && settings.setLobbyAddedUsersVisible(owner, true)
                && settings.setLobbyGameParticipantsVisible(owner, true),
                "all boolean mutations save");
        check(repository.saves == baselineSaves + 6, "each changed boolean persists immediately");
        check(settings.addLobbyVisiblePlayer(owner, owner) == AddedVisiblePlayerResult.CANNOT_ADD_SELF,
                "service rejects self add");
        check(settings.addLobbyVisiblePlayer(owner, target) == AddedVisiblePlayerResult.SUCCESS
                && settings.addLobbyVisiblePlayer(owner, target) == AddedVisiblePlayerResult.ALREADY_ADDED,
                "service add and duplicate results are explicit");
        check(settings.getLobbyAddedVisiblePlayers(owner).orElseThrow().contains(target)
                && settings.isLobbyAddedVisiblePlayer(owner, target).orElseThrow(),
                "added UUID read APIs agree");
        check(settings.removeLobbyVisiblePlayer(owner, target) == AddedVisiblePlayerResult.SUCCESS
                && settings.removeLobbyVisiblePlayer(owner, target) == AddedVisiblePlayerResult.NOT_ADDED,
                "service remove and missing results are explicit");

        assertBooleanRollback(repository, settings, owner, "all",
                () -> settings.setLobbyPlayersVisible(owner, true),
                () -> !settings.areLobbyPlayersVisible(owner).orElseThrow());
        assertBooleanRollback(repository, settings, owner, "friends",
                () -> settings.setLobbyFriendsVisible(owner, false),
                () -> settings.areLobbyFriendsVisible(owner).orElseThrow());
        assertBooleanRollback(repository, settings, owner, "staff",
                () -> settings.setLobbyStaffVisible(owner, false),
                () -> settings.areLobbyStaffVisible(owner).orElseThrow());
        assertBooleanRollback(repository, settings, owner, "added-users",
                () -> settings.setLobbyAddedUsersVisible(owner, false),
                () -> settings.areLobbyAddedUsersVisible(owner).orElseThrow());
        assertBooleanRollback(repository, settings, owner, "game-participants",
                () -> settings.setLobbyGameParticipantsVisible(owner, false),
                () -> settings.areLobbyGameParticipantsVisible(owner).orElseThrow());
        assertBooleanRollback(repository, settings, owner, "friend-presence-notifications",
                () -> settings.setFriendPresenceNotificationsEnabled(owner, false),
                () -> settings.areFriendPresenceNotificationsEnabled(owner).orElseThrow());

        repository.failNext = true;
        expectFailure(() -> settings.addLobbyVisiblePlayer(owner, target));
        check(!settings.isLobbyAddedVisiblePlayer(owner, target).orElseThrow(),
                "failed add restores exact prior state");
        check(settings.addLobbyVisiblePlayer(owner, target) == AddedVisiblePlayerResult.SUCCESS,
                "add succeeds after failure");
        repository.failNext = true;
        expectFailure(() -> settings.removeLobbyVisiblePlayer(owner, target));
        check(settings.isLobbyAddedVisiblePlayer(owner, target).orElseThrow(),
                "failed remove restores exact prior state");
        System.out.println("PlayerSettingsServiceHarness passed " + checks + " checks.");
    }

    private static void assertBooleanRollback(MemoryRepository repository, PlayerSettingsService settings,
                                              UUID owner, String label, Runnable mutation,
                                              java.util.function.BooleanSupplier unchanged) {
        repository.failNext = true;
        expectFailure(mutation);
        check(unchanged.getAsBoolean(), "failed " + label + " mutation rolls back");
    }

    private static void expectFailure(Runnable action) {
        boolean failed = false;
        try { action.run(); } catch (RuntimeException expected) { failed = true; }
        check(failed, "simulated save failure propagates");
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class MemoryRepository implements PlayerRepository {
        private final Map<UUID, CorePlayer> data = new HashMap<>();
        private int saves;
        private boolean failNext;
        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
        @Override public void save(CorePlayer player) {
            if (failNext) { failNext = false; throw new IllegalStateException("simulated save failure"); }
            data.put(player.getUniqueId(), player);
            saves++;
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
    }
}

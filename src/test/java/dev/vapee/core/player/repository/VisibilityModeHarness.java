package dev.vapee.core.player.repository;

import dev.vapee.core.player.*;
import dev.vapee.core.player.settings.*;
import dev.vapee.core.visibility.VisibilityPolicy;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public final class VisibilityModeHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-mode-");
        var logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        var repository = new FilePlayerRepository(directory, logger); repository.initialize();
        try {
            for (boolean legacy : List.of(true, false)) {
                UUID id = UUID.randomUUID(); var file = directory.resolve(id + ".yml");
                String text = "# operator note\nname: Legacy\nfirst-join: 1\nlast-join: 2\nsettings:\n  lobby-players-visible: " + legacy + "\n  visibility:\n    extension: keep\n    show-friends: true\n";
                Files.writeString(file, text);
                var player = repository.findByUniqueId(id).orElseThrow();
                check(player.getSettings().getVisibility().getMode() == (legacy ? VisibilityMode.ALL : VisibilityMode.FILTERED), "legacy mapping " + legacy);
                check(Files.readString(file).equals(text), "load performs no eager rewrite");
                var visibility = player.getSettings().getVisibility();
                UUID target = UUID.randomUUID(); visibility.addPlayer(id, target);
                visibility.setShowStaff(true); visibility.setShowAddedUsers(true); visibility.setShowGameParticipants(true);
                var policy = new VisibilityPolicy(ignored -> visibility, (a,b) -> false, (a,b) -> true, (a,b) -> true);
                for (var mode : List.of(VisibilityMode.FILTERED, VisibilityMode.ALL, VisibilityMode.HIDDEN, VisibilityMode.FILTERED)) {
                    visibility.setMode(mode); repository.save(player);
                    var loaded = repository.findByUniqueId(id).orElseThrow().getSettings().getVisibility();
                    check(loaded.getMode() == mode && loaded.isShowFriends() && loaded.isShowStaff() && loaded.isShowAddedUsers()
                            && loaded.isShowGameParticipants() && loaded.getAddedPlayers().equals(Set.of(target)), "mode roundtrip preserves independent advanced preferences " + mode);
                    check(policy.shouldShow(id, target, true) == (mode != VisibilityMode.HIDDEN), "mode policy with all filters enabled " + mode);
                    check(Files.readString(file).contains("extension: keep"), "unknown persisted sibling retained");
                }
                visibility.setShowFriends(false); visibility.setShowStaff(false); visibility.setShowAddedUsers(false); visibility.setShowGameParticipants(false);
                visibility.setMode(VisibilityMode.FILTERED);
                check(!policy.shouldShow(id, target, true), "FILTERED respects disabled filters");
                visibility.setShowAddedUsers(true);
                check(policy.shouldShow(id, target, false), "FILTERED uses exact selected UUID authority");
                var privacy = new VisibilityPolicy(ignored -> visibility, (a,b) -> true, (a,b) -> true, (a,b) -> true);
                for (var mode : VisibilityMode.values()) { visibility.setMode(mode); check(!privacy.shouldShow(id, target, true), "ignore remains hard privacy rule " + mode); }
            }
            for (String raw : List.of("UNKNOWN", "true", "42", "[ALL]", "null", "all")) {
                UUID id = UUID.randomUUID(); Path file = directory.resolve(id + ".yml");
                String text = "name: Invalid\nfirst-join: 1\nlast-join: 2\nsettings:\n  lobby-players-visible: true\n  visibility:\n    mode: " + raw + "\n    show-friends: true\n";
                Files.writeString(file, text);
                check(repository.findByUniqueId(id).orElseThrow().getSettings().getVisibility().getMode() == VisibilityMode.HIDDEN, "malformed mode fails closed " + raw);
                check(Files.readString(file).equals(text), "malformed mode does not rewrite source");
            }
            Map<UUID,CorePlayer> values = new HashMap<>(); boolean[] fail = {false};
            var players = new PlayerService(new PlayerRepository() {
                public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(values.get(id)); }
                public void save(CorePlayer value) { if (fail[0]) throw new IllegalStateException("injected save failure"); values.put(value.getUniqueId(),value); }
                public boolean exists(UUID id) { return values.containsKey(id); }
            }, logger);
            UUID owner = UUID.randomUUID(); players.loadPlayer(owner, "Owner");
            var settings = new PlayerSettingsService(players); int[] notifications = {0};
            settings.addChangeListener(ignored -> notifications[0]++);
            settings.setLobbyFriendsVisible(owner, true); settings.addLobbyVisiblePlayer(owner, UUID.randomUUID());
            settings.setVisibilityMode(owner, VisibilityMode.HIDDEN); int committed = notifications[0];
            fail[0] = true;
            for (Runnable change : List.<Runnable>of(() -> settings.setVisibilityMode(owner, VisibilityMode.ALL), () -> settings.setLobbyPlayersVisible(owner, true), () -> settings.setLobbyPlayersVisible(owner, false))) {
                try { change.run(); throw new AssertionError("failure expected"); } catch (IllegalStateException expected) { }
                check(settings.getVisibilityMode(owner).orElseThrow() == VisibilityMode.HIDDEN, "failure restores exact HIDDEN mode, including compatibility boolean setter");
                check(notifications[0] == committed && settings.areLobbyFriendsVisible(owner).orElseThrow(), "failed mode emits no success and preserves advanced preferences");
            }
            check(!settings.setVisibilityMode(UUID.randomUUID(), VisibilityMode.ALL), "unloaded owner fails closed");
        } finally {
            try (var paths = Files.walk(directory)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        System.out.println("VisibilityModeHarness passed " + checks + " checks.");
    }
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
}

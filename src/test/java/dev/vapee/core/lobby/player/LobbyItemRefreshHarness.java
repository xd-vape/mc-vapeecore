package dev.vapee.core.lobby.player;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

/** Reload item reconciliation must respect existing lobby, BUILD and activity inventory ownership. */
public final class LobbyItemRefreshHarness {
    private static int checks;
    public static void main(String[] args) {
        int[] applies = {0}, clears = {0};
        UUID id = UUID.randomUUID();
        boolean[] online = {true}, lobby = {true}, eligible = {true}, primary = {true};
        var inventory = Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{org.bukkit.inventory.PlayerInventory.class},
                (ignored, method, values) -> { if (method.getName().equals("clear")) clears[0]++; return null; });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (ignored, method, values) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isOnline" -> online[0];
                    case "getInventory" -> inventory;
                    default -> null;
                });
        var service = new LobbyPlayerStateService(() -> primary[0], ignored -> lobby[0], () -> GameMode.ADVENTURE,
                ignored -> applies[0]++, ignored -> { }, () -> List.of(player));
        service.refreshLobbyItems();
        check(applies[0] == 0, "fail-closed until integration supplies eligibility");
        service.setItemRefreshEligibility(ignored -> eligible[0]); service.refreshLobbyItems();
        check(applies[0] == 1 && clears[0] == 0, "NORMAL eligible reload applies without clearing foreign inventory");
        eligible[0] = false; service.refreshLobbyItems();
        check(applies[0] == 1, "activity/incomplete profile guard prevents reconciliation");
        eligible[0] = true; online[0] = false; service.refreshLobbyItems();
        check(applies[0] == 1, "offline player skipped");
        online[0] = true; lobby[0] = false; service.refreshLobbyItems();
        check(applies[0] == 1, "foreign world skipped");
        lobby[0] = true; service.enterBuildMode(player); int afterBuild = clears[0]; service.refreshLobbyItems();
        check(applies[0] == 1 && clears[0] == afterBuild, "BUILD inventory not taken by reload");
        service.handleQuit(id); service.refreshLobbyItems();
        check(applies[0] == 2, "quit clears BUILD and eligible later session may reconcile");
        primary[0] = false;
        try { service.refreshLobbyItems(); throw new AssertionError("off-thread refresh accepted"); }
        catch (IllegalStateException expected) { checks++; }
        check(applies[0] == 2 && clears[0] == afterBuild, "thread rejection mutates no inventory");
        System.out.println("LobbyItemRefreshHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

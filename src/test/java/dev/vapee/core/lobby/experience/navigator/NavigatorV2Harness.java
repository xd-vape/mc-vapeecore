package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;

import dev.vapee.core.lobby.player.LobbyPlayerMode;
import org.bukkit.Location;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NavigatorV2Harness {
    private static int checks;
    private static final String BUILDER = "vapeecore.navigator.builder";
    public static void main(String[] args) throws Exception {
        navigation(); security(); destinations(); countsAndLanguage(); lifecycle();
        System.out.println("NavigatorV2Harness passed " + checks + " checks.");
    }
    private static NavigatorFixture fixture() throws Exception {
        var f = new NavigatorFixture(); f.destinations(2);
        f.configured.set(NavigatorConfig.parse(NavigatorConfigHarness.CONFIG));
        return f;
    }
    private static NavigatorInventoryHolder holder(NavigatorFixture.TestPlayer p) { return (NavigatorInventoryHolder) p.open.getHolder(); }
    private static void navigation() throws Exception {
        var f = fixture(); var p = f.player();
        check(f.menu.open(p.player) && p.open.getSize() == 54 && plain(f.title).equals("Navigator"), "configured root opens through existing menu");
        check(holder(p).menuId().equals("root") && holder(p).entryAt(20).orElseThrow().equals("spawn"), "trusted entry ID mapping");
        check(p.open.getItem(24) == null && holder(p).entryAt(24).isEmpty(), "Builder absent without permission");
        var root = p.open;
        f.click(p, root, 22, ClickType.RIGHT);
        check(holder(p).menuId().equals("games") && plain(f.title).equals("Activities"), "right click enters child");
        check(p.open.getItem(48) != null && holder(p).backAt(48), "child has trusted Back control");
        f.click(p, root, 20, ClickType.LEFT);
        check(p.teleports == 0 && holder(p).menuId().equals("games"), "old root cannot teleport");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(p, root)));
        check(f.menu.isActive(p.player, p.open, holder(p)), "late parent close retains child");
        f.click(p, p.open, 53, ClickType.LEFT);
        check(holder(p).getPage() == 1 && holder(p).entryAt(20).orElseThrow().equals("more"), "submenu pagination uses configured page groups");
        f.click(p, p.open, 20, ClickType.LEFT);
        check(holder(p).menuId().equals("nested") && text(p.open, 22).equals("No Destinations Available"), "nested empty menu opens");
        f.click(p, p.open, 48, ClickType.LEFT);
        check(holder(p).menuId().equals("games") && holder(p).getPage() == 1, "Back restores actual parent page");
        f.click(p, p.open, 45, ClickType.LEFT);
        check(holder(p).getPage() == 0, "previous page stays inside submenu");
        int teleports = p.teleports; f.click(p, p.open, 24, ClickType.LEFT);
        check(p.teleports == teleports && p.output().contains("currently unavailable"), "placeholder inert with clear feedback");
        f.click(p, p.open, 48, ClickType.RIGHT);
        check(holder(p).menuId().equals("root") && p.open.getItem(48) == null, "Back reaches root without phantom parent");
        f.click(p, p.open, 20, ClickType.LEFT);
        check(p.teleports == 1 && p.open == p.bottom && p.target.getX() == 0, "direct authoritative Warp teleport and close");
        f.configured.set(NavigatorConfig.legacy()); f.menu.open(p.player);
        check(plain(f.title).equals("Warp Navigator") && holder(p).configuration() == null && holder(p).getWarpId(0).isPresent(), "disabled V2 restores unchanged flat list");
        f.menu.shutdown();
    }
    private static void security() throws Exception {
        var f = fixture(); var p = f.player(); var other = f.player();
        f.warps.setWarp("secret_builder", new Location(f.lobby, 9, 70, 9));
        f.menu.open(p.player); var before = p.open;
        for (ClickType click : List.of(ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.MIDDLE, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.SWAP_OFFHAND)) {
            check(f.click(p, before, 20, click).isCancelled() && p.teleports == 0 && p.open == before, "unsafe configured click denied: " + click);
        }
        check(f.click(p, before, 54, ClickType.SHIFT_LEFT).isCancelled() && p.teleports == 0, "bottom transfer denied");
        check(f.drag(p, before, 20).isCancelled() && !f.drag(p, before, 54).isCancelled(), "configured drag boundaries");
        check(f.click(other, before, 20, ClickType.LEFT).isCancelled() && other.teleports == 0, "foreign owner denied");
        var forged = new NavigatorInventoryHolder(p.id, 0, "root", Map.of(20,"spawn"), f.configured.get(), List.of());
        var fake = inventory(forged, 54); forged.bindInventory(fake); p.open = fake;
        check(f.click(p, fake, 20, ClickType.LEFT).isCancelled() && p.teleports == 0, "forged bound V2 holder lacks active authority");
        f.menu.open(p.player); p.permissions.add(BUILDER); f.menu.open(p.player); var privileged = p.open;
        check(privileged.getItem(24) != null && holder(p).entryAt(24).isPresent(), "dedicated permission exposes Builder");
        p.permissions.remove(BUILDER); int received = p.received.size();
        f.click(p, privileged, 24, ClickType.LEFT);
        check(p.teleports == 0 && p.open.getItem(24) == null && holder(p).entryAt(24).isEmpty(), "permission revoked at click time removes entry");
        check(p.received.subList(received,p.received.size()).stream().noneMatch(c -> plain(c).contains("secret_builder") || plain(c).contains("Builder")), "denial does not leak gated destination");
        f.click(p, privileged, 24, ClickType.LEFT); check(p.teleports == 0, "stale privileged inventory remains inert");
        p.permissions.add(BUILDER); f.menu.open(p.player); f.click(p,p.open,24,ClickType.RIGHT);
        check(p.teleports == 1 && p.target.getX() == 9 && p.open == p.bottom, "permission recovery uses local Warp only");
        f.menu.open(p.player); var old = p.open;
        f.configured.set(NavigatorConfig.parse(NavigatorConfigHarness.CONFIG.replace("warp: custom_000", "warp: custom_001")));
        f.click(p,old,20,ClickType.LEFT);
        check(p.teleports == 1 && p.open != old && holder(p).configuration() == f.configured.get(), "changed active config invalidates old action before execution");
        f.click(p,p.open,20,ClickType.LEFT); check(p.teleports == 2 && p.target.getX() == 1, "new config action is authoritative");
        f.configured.set(NavigatorConfig.parse(NavigatorConfigHarness.CONFIG.replace("menu: games", "menu: games, permission: " + BUILDER)));
        f.menu.open(p.player); f.click(p,p.open,22,ClickType.LEFT); var child=p.open; p.permissions.clear();
        f.click(p,child,20,ClickType.LEFT);
        check(p.teleports == 2 && holder(p).menuId().equals("root") && p.open.getItem(22) == null, "revoked parent permission blocks child actions and Back route");
        f.configured.set(NavigatorConfig.unavailable()); f.menu.open(p.player);
        check(p.open.getItem(24) == null && holder(p).entryAt(24).isEmpty()
                        && holder(p).getWarpId(0).isEmpty() && text(p.open,22).equals("No Destinations Available"),
                "invalid V2 cannot leak gated Warp through legacy fallback");
        f.configured.set(NavigatorConfig.parse(NavigatorConfigHarness.CONFIG));
        for (String denied : List.of("offline","unloaded","build","activity","world")) {
            p.online=true; p.loaded=true; p.mode=LobbyPlayerMode.NORMAL; p.participating=false; p.currentWorld=f.lobby;
            f.menu.open(p.player); var top=p.open;
            switch(denied) { case "offline" -> p.online=false; case "unloaded" -> p.loaded=false;
                case "build" -> p.mode=LobbyPlayerMode.BUILD; case "activity" -> p.participating=true; case "world" -> p.currentWorld=f.outside; }
            f.click(p,top,20,ClickType.LEFT); check(p.teleports == 2 && p.open==p.bottom,"click state revalidated: "+denied);
        }
        f.menu.shutdown();
    }
    private static void destinations() throws Exception {
        var f=fixture();var p=f.player();f.menu.open(p.player);var root=p.open;
        f.warps.removeWarp("custom_000"); f.click(p,root,20,ClickType.LEFT);
        check(p.teleports==0 && p.output().contains("no longer available"),"removed Warp never teleports");
        f.warps.setWarp("custom_000",new Location(f.lobby,0,64,0));f.warps.setNavigatorVisible("custom_000",false);
        f.click(p,p.open,20,ClickType.LEFT);check(p.teleports==0,"hidden Warp never teleports");
        f.warps.setNavigatorVisible("custom_000",true);f.worlds.remove(f.lobby.getName());
        f.click(p,p.open,20,ClickType.LEFT);check(p.teleports==0 && p.output().contains("world is not loaded"),"missing world preserves safe error");
        f.worlds.put(f.lobby.getName(),f.lobby);p.teleportAccepted=false;
        f.click(p,p.open,20,ClickType.LEFT);check(p.teleports==1 && p.open!=p.bottom && p.output().contains("cancelled or failed"),"teleport cancellation preserves menu");
        p.teleportAccepted=true;f.menu.startUpdates();var top=p.open;var h=holder(p);
        f.warps.setWarp("custom_000",new Location(f.lobby,88,75,88));
        check(p.open==top && holder(p)==h,"Warp admin update refreshes existing inventory and holder");
        f.click(p,top,20,ClickType.LEFT);check(p.teleports==2 && p.target.getX()==88,"click uses latest authoritative coordinates");
        f.menu.shutdown();
    }
    private static void countsAndLanguage() throws Exception {
        var f=fixture();var p=f.player();
        String counted=NavigatorConfigHarness.CONFIG.replace("warp: custom_001", "warp: custom_001, count: {world: lobby-world, min: {x: 0, y: 60, z: 0}, max: {x: 10, y: 80, z: 10}, stack-amount: true}");
        f.configured.set(NavigatorConfig.parse(counted)); f.menu.startUpdates();f.menu.open(p.player);f.click(p,p.open,22,ClickType.LEFT);
        check(lore(p,20).contains("0 players in this area.") && p.open.getItem(20).getAmount()==1,"zero lore with visible valid item");
        var target=f.player();target.x=5;target.y=70;target.z=5;f.menu.refreshActive(p.id);
        check(lore(p,20).contains("1 player in this area.") && p.open.getItem(20).getAmount()==1,"singular count from actual region");
        for(int i=0;i<70;i++){var peer=f.player();peer.x=5;peer.y=70;peer.z=5;}
        f.menu.refreshActive(p.id);check(lore(p,20).stream().anyMatch(line->line.contains("71 players")) && p.open.getItem(20).getAmount()==64,"large count accurate, stack safely capped");
        p.hidden.add(target.id);target.vanished=true;f.menu.refreshActive(p.id);
        check(lore(p,20).stream().anyMatch(line->line.contains("70 players")),"count respects visibility and vanish");
        p.locale=Locale.GERMANY;f.messages.localization().localeChanged(p.player,Locale.GERMANY);
        check(lore(p,20).stream().anyMatch(line->line.contains("70 Spieler")) && text(p.open,48).equals("Zurück") && text(p.open,50).equals("Schließen"),"German counts, Back and Close localize in place");
        f.menu.open(p.player);check(plain(f.title).equals("Navigator") && text(p.open,22).equals("Aktivitäten"),"configured German names");
        f.click(p,p.open,22,ClickType.LEFT);check(plain(f.title).equals("Aktivitäten"),"configured German child title");
        f.configured.set(NavigatorConfig.parse(counted.replace("stack-amount: true","stack-amount: false")));
        f.menu.open(p.player);check(lore(p,20).stream().noneMatch(line->line.contains("Spieler")||line.contains("player")),"no configured region means no invented count");
        String literal=NavigatorConfigHarness.CONFIG.replace("name: Spawn", "name: '<red>Literal</red>'");
        f.configured.set(NavigatorConfig.parse(literal));f.menu.open(p.player);
        check(text(p.open,20).equals("<red>Literal</red>") && spec(p.open,20).name().clickEvent()==null,"configured text remains literal, no MiniMessage command authority");
        f.menu.shutdown();
    }
    private static List<String> lore(NavigatorFixture.TestPlayer p,int slot){return spec(p.open,slot).lore().stream().map(NavigatorFixture::plain).toList();}
    private static void lifecycle() throws Exception {
        var f=fixture();var p=f.player();f.menu.startUpdates();f.menu.open(p.player);var root=p.open;
        p.cancelNextOpen=true;f.click(p,root,22,ClickType.LEFT);
        check(p.open==root && f.menu.isActive(p.player,root,holder(p)),"cancelled submenu does not replace active root");
        p.denyDuringOpen=true;f.click(p,root,22,ClickType.LEFT);
        check(p.open==p.bottom,"post-open loaded guard closes newly ineligible view");p.denyDuringOpen=false;p.loaded=true;
        f.menu.open(p.player);var old=p.open;p.open=p.bottom;
        f.listener.onPlayerQuit(new PlayerQuitEvent(p.player,Component.empty(),PlayerQuitEvent.QuitReason.DISCONNECTED));p.open=old;
        check(!f.menu.isActive(p.player,old,holder(p)),"quit releases V2 active state");
        f.menu.open(p.player);p.currentWorld=f.outside;f.listener.onPlayerChangedWorld(new PlayerChangedWorldEvent(p.player,f.lobby));
        check(p.open==p.bottom,"world leave closes configured view");p.currentWorld=f.lobby;
        f.menu.open(p.player);old=p.open;f.menu.shutdown();
        check(p.open==p.bottom && !f.menu.isActive(p.player,old,(NavigatorInventoryHolder)old.getHolder()),"shutdown closes V2 and releases authority");
        f.menu.startUpdates();f.menu.open(p.player);check(holder(p).menuId().equals("root"),"existing menu lifecycle can re-enable");f.menu.shutdown();
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}

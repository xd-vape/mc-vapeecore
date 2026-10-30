package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;
import dev.vapee.core.lobby.experience.navigator.NavigatorEditorHarness.Fixture;
import java.nio.file.Files;
import java.util.*;
import org.bukkit.event.inventory.ClickType;

public final class NavigatorFinalPresentationHarness {
    private static int checks;
    public static void main(String[]args)throws Exception {
        presentation();commands();System.out.println("NavigatorFinalPresentationHarness passed "+checks+" checks.");
    }
    private static List<String> lore(NavigatorFixture.TestPlayer p,int slot){return spec(p.open,slot).lore().stream().map(NavigatorFixture::plain).toList();}
    private static void presentation()throws Exception {
        var f=new NavigatorFixture();f.destinations(2);var p=f.player();p.locale=Locale.GERMANY;
        String source=NavigatorConfigHarness.CONFIG.replace("warp: custom_001","warp: custom_001, lore: [{en: Custom lore, de: Eigene Beschreibung}], count: {world: lobby-world, min: {x: 0, y: 60, z: 0}, max: {x: 10, y: 80, z: 10}}");
        f.configured.set(NavigatorConfig.parse(source));check(f.configured.get().menus().get("games").entries().get("parkour").stackAmount(),"omitted stack option defaults to stack presentation");
        f.menu.open(p.player);check(lore(p,20).isEmpty()&&p.open.getItem(20).getAmount()==1,"Warp without region has no population or automatic instructions");
        f.click(p,p.open,22,ClickType.LEFT);check(lore(p,20).equals(List.of("Eigene Beschreibung","0 Spieler"))&&p.open.getItem(20).getAmount()==1,"zero remains visible with minimal German fallback and authored lore");
        check(p.actionBars.size()==1&&plain(p.actionBars.getFirst()).equals("Rechtsklick auf einen leeren Platz: zurück."),"Back discoverability is one localized ActionBar");
        f.click(p,p.open,26,ClickType.LEFT);f.click(p,p.open,18,ClickType.LEFT);check(p.actionBars.size()==1,"paging does not repeat Back hint");
        var peer=f.player();peer.x=5;peer.y=70;peer.z=5;f.menu.refreshActive(p.id); // No observer needed for an explicit refresh.
        check(p.open.getItem(20).getAmount()==1&&lore(p,20).equals(List.of("Eigene Beschreibung")),"one player uses legal amount 1 without automatic count lore");
        for(int i=0;i<4;i++){var target=f.player();target.x=5;target.y=70;target.z=5;}f.menu.refreshActive(p.id);
        check(p.open.getItem(20).getAmount()==5&&lore(p,20).equals(List.of("Eigene Beschreibung")),"multiple players appear as stack only");
        peer.vanished=true;p.hidden.add(peer.id);f.menu.refreshActive(p.id);check(p.open.getItem(20).getAmount()==4,"stack count preserves privacy and vanish filtering");
        for(int i=0;i<70;i++){var target=f.player();target.x=5;target.y=70;target.z=5;}f.menu.refreshActive(p.id);
        check(p.open.getItem(20).getAmount()==64&&lore(p,20).equals(List.of("Eigene Beschreibung","74 Spieler")),"overflow clamps and retains exact count unobtrusively");
        f.configured.set(NavigatorConfig.parse(source.replace("icon: FEATHER","icon: DIAMOND_PICKAXE")));f.menu.open(p.player);f.click(p,p.open,22,ClickType.LEFT);
        check(p.open.getItem(20).getAmount()==1&&lore(p,20).contains("74 Spieler"),"unstackable material is never assigned an illegal amount");
        f.configured.set(NavigatorConfig.parse(source.replace("max: {x: 10, y: 80, z: 10}","max: {x: 10, y: 80, z: 10}, stack-amount: false")));f.menu.open(p.player);f.click(p,p.open,22,ClickType.LEFT);
        check(p.open.getItem(20).getAmount()==1&&lore(p,20).contains("74 Spieler in diesem Bereich."),"explicit operator stack opt-out keeps compatible lore counts");
        f.warps.setNavigatorVisible("custom_001",false);f.menu.refreshActive(p.id);check(lore(p,20).contains("Momentan nicht verfügbar.")&&lore(p,20).stream().noneMatch(line->line.contains("74")),"unavailable destinations retain feedback without leaking population");
        var english=f.player();f.configured.set(NavigatorConfig.parse(source));f.warps.setNavigatorVisible("custom_001",true);f.menu.open(english.player);f.click(english,english.open,22,ClickType.LEFT);
        check(lore(english,20).contains("74 players")&&!lore(english,20).stream().anyMatch(line->line.contains("Click to teleport")||line.contains("Right-click")),"English overflow without repeated instructions");
        f.menu.shutdown();
    }
    private static void commands()throws Exception {
        try(var t=new Fixture()) {
            var p=t.admin();t.cmd(p,"edit");byte[] original=Files.readAllBytes(t.file);t.cmd(p,"add warp custom_000");
            check(p.open==p.bottom,"adding via command does not reopen editor");
            for(String command:List.of("menu select root","select custom_000","entry name all Test","entry lore all Authored lore","entry permission none","region pos1","region pos2","region info","region clear")) {
                int opens=p.opens;t.cmd(p,command);check(p.open==p.bottom&&p.opens==opens,"pure command opens no GUI: "+command);
            }
            check(Arrays.equals(original,Files.readAllBytes(t.file)),"pure edits remain draft only");
            t.cmd(p,"edit");check(p.open.getSize()==54,"explicit edit opens editor");t.cmd(p,"select custom_000");check(p.open==p.bottom,"selection closes stale owned view without reopening");
            t.cmd(p,"preview");check(p.open.getHolder() instanceof NavigatorInventoryHolder,"explicit preview opens player presentation");t.cmd(p,"edit");
            t.click(p,p.open,11,ClickType.LEFT);check(p.open.getSize()==27&&text(p.open,18).equals("Player-count region"),"GUI clicks still navigate focused action screens");
            t.click(p,p.open,18,ClickType.LEFT);t.click(p,p.open,10,ClickType.LEFT);check(p.open.getSize()==27&&t.s(p).first!=null,"GUI Pos1 may explicitly reopen its relevant screen");
            t.cmd(p,"region pos2");check(p.open==p.bottom&&t.entry(p).stackAmount(),"new region assignment enables stack count by default");
            t.cmd(p,"entry stack false");t.cmd(p,"region pos1");t.cmd(p,"region pos2");check(!t.entry(p).stackAmount(),"replacing an existing region preserves explicit false");
            t.cmd(p,"save");check(t.config.getState().enabled()&&!t.config.getState().menus().get("root").entries().get("custom_000").stackAmount(),"opt-out is persisted by unchanged normal Save");
        }
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

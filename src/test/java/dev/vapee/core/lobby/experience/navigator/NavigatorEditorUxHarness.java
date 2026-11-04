package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;

import dev.vapee.core.i18n.LocalizedText;
import dev.vapee.core.lobby.experience.navigator.NavigatorEditorHarness.Fixture;
import dev.vapee.core.lobby.experience.navigator.NavigatorFixture.TestPlayer;
import java.nio.file.Files;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import net.kyori.adventure.text.event.ClickEvent;

/** Real command/listener regressions for the observed Pos1 loss, contextual feedback and action selectors. */
public final class NavigatorEditorUxHarness {
    private static int checks;
    public static void main(String[] args)throws Exception {
        movement(); context(); diagnostics(); menuActions(); entryActions(); labelsAndSecurity();
        System.out.println("NavigatorEditorUxHarness passed "+checks+" checks.");
    }
    private static TestPlayer warp(Fixture t)throws Exception {
        var p=t.admin();t.cmd(p,"edit");t.cmd(p,"add warp custom_000");t.cmd(p,"edit");return p;
    }
    private static String last(TestPlayer p){return plain(p.received.getLast());}
    private static void close(Fixture t,TestPlayer p) {
        var top=p.open;p.player.closeInventory();t.editor.close(new InventoryCloseEvent(view(p,top)));
    }
    private static List<String> lore(TestPlayer p,int slot){return spec(p.open,slot).lore().stream().map(NavigatorFixture::plain).toList();}
    private static World world(String name,UUID id) {
        return proxy(World.class,(method,args)->switch(method){case "getName"->name;case "getUID"->id;case "getMinHeight"->-64;case "getMaxHeight"->320;default->null;});
    }
    private static void movement()throws Exception {
        for(String variant:List.of("movement","reopen","reselect-same-menu","empty-slot-navigation")) {
            try(var t=new Fixture()) {
                var p=warp(t);var originalWarp=t.f.warps.getWarp("custom_000").orElseThrow();close(t,p);
                p.x=10;p.y=80;p.z=10;int messages=p.received.size();t.cmd(p,"region pos1");var first=t.s(p).first;
                check(first!=null&&first.menu().equals("root")&&first.entry().equals("custom_000")&&first.worldId().equals(t.f.lobby.getUID()),variant+": Pos1 snapshots authoritative world UUID and selected entry");
                check(p.received.size()==messages+1&&last(p).startsWith("Position 1 saved."),variant+": one short Pos1 confirmation");
                check(p.open==p.bottom,variant+": command does not force inventory open while walking");
                if(variant.equals("reopen"))t.cmd(p,"edit");
                if(variant.equals("reselect-same-menu")){t.cmd(p,"menu select root");t.cmd(p,"select custom_000");}
                if(variant.equals("empty-slot-navigation")){
                    t.cmd(p,"edit");t.click(p,p.open,0,org.bukkit.event.inventory.ClickType.LEFT);t.click(p,p.open,18,org.bukkit.event.inventory.ClickType.LEFT);t.cmd(p,"select custom_000");
                }
                check(t.s(p).first==first,variant+": Pos1 survives movement, reopen and normal selection/navigation");
                close(t,p);p.x=0;p.y=60;p.z=0;t.cmd(p,"region pos2");
                check(t.s(p).first==null&&t.entry(p).region().minX()==0&&t.entry(p).region().maxY()==t.f.lobby.getMaxHeight()-1&&t.entry(p).region().world().equals("lobby-world"),variant+": exact same-world sequence completes inclusive region at default full world height");
                check(last(p).equals("Region selected. Save to apply.")&&p.open==p.bottom,variant+": distinct successful Pos2 feedback without opening GUI");
                int opens=p.opens;messages=p.received.size();t.cmd(p,"region info");
                check(p.received.size()==messages+1&&last(p).contains("Count: ON")&&last(p).contains("lobby-world")&&p.opens==opens,variant+": info reports region without mutating/reopening view");
                t.cmd(p,"save");check(t.s(p)==null&&NavigatorConfig.parse(Files.readString(t.file)).menus().get("root").entries().get("custom_000").region()!=null,variant+": save persists region and releases session");
                check(t.f.warps.getWarp("custom_000").orElseThrow().equals(originalWarp),variant+": selection never changes Warp coordinates");
            }
        }
        try(var t=new Fixture()) {
            var p=warp(t);t.cmd(p,"region pos1");UUID same=p.currentWorld.getUID();p.currentWorld=world("lobby-world",same);p.x=3;t.cmd(p,"region pos2");
            check(t.entry(p).region()!=null,"same Bukkit world UUID accepted across distinct World object instances");
            t.cmd(p,"region pos1");p.currentWorld=world("lobby-world",UUID.randomUUID());t.cmd(p,"region pos2");
            check(t.s(p).first!=null&&last(p).startsWith("Both positions must be in the same world."),"same world name with different UUID remains rejected");
        }
    }
    private static void context()throws Exception {
        try(var t=new Fixture()) {
            var p=warp(t);t.cmd(p,"add warp custom_001");t.cmd(p,"select custom_000");t.cmd(p,"region pos1");var pending=t.s(p).first;
            int n=p.received.size();t.cmd(p,"select custom_001");
            check(t.s(p).first==pending&&p.received.size()==n+1&&last(p).contains("Position 1 retained for root/custom_000"),"entry switch retains Pos1 with explicit owner notice");
            n=p.received.size();t.cmd(p,"menu select root");t.cmd(p,"select custom_001");
            check(p.received.size()==n,"repeated menu/selection navigation does not repeat pending notice");
            t.cmd(p,"region pos2");check(last(p).contains("belongs to root/custom_000")&&!last(p).contains("same world")&&t.entry(p).region()==null,"wrong selected Warp cannot consume Pos1 or emit false world mismatch");
            t.cmd(p,"region pos1");check(t.s(p).first==pending,"Pos1 on another Warp cannot overwrite unfinished selection");
            t.cmd(p,"region clear");check(t.s(p).first==pending,"clear on another Warp cannot discard the original pending state");
            byte[] before=Files.readAllBytes(t.file);t.cmd(p,"save");check(Arrays.equals(before,Files.readAllBytes(t.file))&&last(p).contains("Incomplete region for root/custom_000"),"Save remains blocked across entry changes with precise owner");
            t.cmd(p,"select custom_000");t.cmd(p,"entry remove");check(t.s(p).first==pending&&t.entry(p)!=null,"removing a Warp cannot silently destroy its pending region");
            t.cmd(p,"menu create child Child");t.cmd(p,"menu select child");t.cmd(p,"add warp custom_000");t.cmd(p,"region pos2");
            check(last(p).contains("belongs to root/custom_000")&&t.entry(p).region()==null,"same Warp ID in a different menu is a distinct entry context");
            t.cmd(p,"menu select root");t.cmd(p,"select custom_000");t.cmd(p,"region clear");
            check(t.s(p).first==null&&t.entry(p).region()==null,"return to original entry and clear explicitly cancels pending bounds");
            t.cmd(p,"region pos1");t.cmd(p,"entry move child 15");
            check(t.s(p).first.menu().equals("root"),"failed duplicate-ID move preserves original pending owner");
            t.cmd(p,"menu select child");t.cmd(p,"select custom_000");t.cmd(p,"entry remove");t.cmd(p,"menu select root");t.cmd(p,"select custom_000");t.cmd(p,"entry move child 15");
            check(t.s(p).menu.equals("child")&&t.s(p).first.menu().equals("child")&&t.s(p).first.entry().equals("custom_000"),"successful entry move preserves pending selection with updated menu identity");
            t.cmd(p,"menu delete child");check(t.s(p).draft.menus().containsKey("child")&&last(p).contains("Incomplete region"),"deleting owner menu requires completing or clearing region");
            p.x=2;p.z=2;t.cmd(p,"region pos2");check(t.s(p).first==null&&t.entry(p).region()!=null,"moved Warp can complete its original region");
        }
    }
    private static void diagnostics()throws Exception {
        try(var t=new Fixture()) {
            var p=t.admin();t.cmd(p,"region pos2");check(last(p).equals("No editor session. Open /navigator edit."),"missing session has its own error");
            t.cmd(p,"edit");t.cmd(p,"region pos2");check(last(p).startsWith("Select a Warp entry first."),"missing selected Warp has direct selection guidance");
            t.cmd(p,"add warp custom_000");t.cmd(p,"region pos2");check(last(p).startsWith("Position 1 is missing.")&&!last(p).contains("same world"),"missing Pos1 is not misreported as a world mismatch");
            t.cmd(p,"region pos1");var first=t.s(p).first;p.currentWorld=t.f.outside;t.cmd(p,"region pos2");
            check(last(p).contains("Pos1: lobby-world; Pos2: other-world")&&t.s(p).first==first,"actual world mismatch reports both worlds and retains Pos1");
            p.currentWorld=t.f.lobby;p.x=Double.NaN;t.cmd(p,"region pos2");check(last(p).startsWith("Invalid coordinates")&&t.s(p).first==first,"invalid coordinate rejected without losing selection");
            p.x=1;p.y=Double.POSITIVE_INFINITY;t.cmd(p,"region pos1");check(last(p).startsWith("Invalid coordinates")&&t.s(p).first==first,"invalid Pos1 cannot overwrite valid pending bounds");p.y=64;
            t.s(p).selected="stale";t.cmd(p,"region pos2");check(last(p).startsWith("Selected entry is stale or missing."),"stale entry state distinguished from missing Pos1/world mismatch");
            t.s(p).menu="stale-menu";t.cmd(p,"region pos2");check(last(p).startsWith("Invalid editor state."),"invalid menu state has explicit recovery instruction");t.s(p).menu="root";t.s(p).selected="custom_000";
            p.locale=Locale.GERMANY;t.cmd(p,"region pos2");check(last(p).equals("Region gewählt. Zum Übernehmen speichern."),"German success feedback");
            t.cmd(p,"region pos2");check(last(p).startsWith("Position 1 fehlt."),"German missing Pos1 feedback");
            t.cmd(p,"menu create test Test");t.cmd(p,"region pos1");check(last(p).startsWith("Wähle zuerst einen Warp-Eintrag."),"MENU entry is not a selected Warp");
            t.cmd(p,"menu select root");t.cmd(p,"select custom_000");
            for(String command:List.of("unknown","menu create","menu unknown","entry permission","entry unknown","region unknown","menu back not-a-number")) {
                int n=p.received.size();t.cmd(p,command);
                check(p.received.size()==n+1&&last(p).startsWith("Verwendung: /navigator"),"one relevant localized syntax line for "+command);
                check(!last(p).contains("edit | preview | save | discard"),"no full command-tree dump for "+command);
            }
            int n=p.received.size();t.cmd(p,"help");check(p.received.size()==n+5,"explicit /navigator help retains complete reference");
        }
    }
    private static void menuActions()throws Exception {
        try(var t=new Fixture()) {
            var p=t.admin();t.cmd(p,"edit");int n=p.received.size();t.click(p,p.open,46,ClickType.LEFT);
            check(p.open.getSize()==27&&text(p.open,10).equals("Create submenu")&&text(p.open,14).equals("Change menu icon")&&p.open.getItem(0)==null,"menu settings opens compact action selection, not icon picker");
            check(p.received.size()==n,"opening menu actions emits no help messages");
            for(int i=0;i<3;i++){t.click(p,p.open,22,ClickType.LEFT);t.click(p,p.open,46,ClickType.LEFT);}
            check(p.received.size()==n,"repeated menu navigation stays silent");
            t.click(p,p.open,11,ClickType.LEFT);check(text(p.open,0).startsWith("root —"),"select existing menu opens existing menu picker");
            t.click(p,p.open,0,ClickType.LEFT);t.click(p,p.open,46,ClickType.LEFT);t.click(p,p.open,12,ClickType.LEFT);
            check(p.open==p.bottom&&p.received.size()==n+1&&p.received.getLast().clickEvent().action()==ClickEvent.Action.SUGGEST_COMMAND&&p.received.getLast().clickEvent().value().equals("/navigator menu rename all "),"rename offers one contextual suggested command and retains session");
            t.cmd(p,"edit");t.click(p,p.open,46,ClickType.LEFT);t.click(p,p.open,14,ClickType.LEFT);
            check(text(p.open,0).equals("COMPASS"),"icon picker reached through specific Change menu icon action");
            t.click(p,p.open,4,ClickType.LEFT);check(t.s(p).draft.menus().get("root").icon()==Material.ENDER_PEARL,"menu icon picker applies through existing draft validation");
            t.click(p,p.open,46,ClickType.LEFT);t.click(p,p.open,15,ClickType.LEFT);
            check(List.of(10,12,14,16).stream().map(slot->text(p.open,slot)).toList().equals(List.of("27","36","45","54")),"menu size action offers supported sizes");
            t.click(p,p.open,12,ClickType.LEFT);check(t.s(p).draft.menus().get("root").inventorySize()==36,"size selection updates current menu draft only");
            t.click(p,p.open,46,ClickType.LEFT);t.click(p,p.open,16,ClickType.LEFT);check(text(p.open,4).equals("Disabled")&&text(p.open,11).equals("Disable Back icon"),"optional Back action has explicit state and controls");
            t.click(p,p.open,15,ClickType.LEFT);check(p.received.getLast().clickEvent().value().equals("/navigator menu back ")&&p.open==p.bottom,"Back slot action offers contextual existing command");
            t.cmd(p,"menu back 0");check(t.s(p).draft.menus().get("root").backSlot()==0,"existing Back command remains supported");t.cmd(p,"edit");
            t.click(p,p.open,46,ClickType.LEFT);t.click(p,p.open,16,ClickType.LEFT);t.click(p,p.open,11,ClickType.LEFT);check(t.s(p).draft.menus().get("root").backSlot()==-1,"Back disable uses existing validation");
            t.click(p,p.open,46,ClickType.LEFT);t.click(p,p.open,10,ClickType.LEFT);check(p.received.getLast().clickEvent().value().equals("/navigator menu create ")&&p.open==p.bottom,"create submenu action prompts only its own syntax");
            t.cmd(p,"menu create worlds Worlds");check(t.s(p).draft.menus().containsKey("worlds")&&t.entry(p).type()==NavigatorConfig.Type.MENU,"suggested create workflow places real submenu");
            check(!t.config.getState().enabled(),"all action selection remains provisional");
        }
    }
    private static void entryActions()throws Exception {
        try(var t=new Fixture()) {
            var p=warp(t);int n=p.received.size();t.click(p,p.open,11,ClickType.LEFT);
            check(p.open.getSize()==27&&text(p.open,10).equals("Name and lore")&&text(p.open,11).equals("Icon")&&text(p.open,12).equals("Permission")&&text(p.open,13).equals("Move")&&text(p.open,14).equals("Remove entry")&&text(p.open,18).equals("Player-count region"),"Warp entry exposes six compact action choices");
            check(p.received.size()==n&&p.open.getItem(19)==null,"entry action selection emits no help and does not expose unrelated Pos1 actions");
            t.click(p,p.open,10,ClickType.LEFT);check(text(p.open,11).equals("Edit name")&&text(p.open,15).equals("Edit lore"),"name/lore action opens focused choices");
            t.click(p,p.open,11,ClickType.LEFT);check(p.received.size()==n+1&&p.received.getLast().clickEvent().value().equals("/navigator entry name all "),"name editing uses one suggested command");
            t.cmd(p,"edit");t.click(p,p.open,11,ClickType.LEFT);t.click(p,p.open,10,ClickType.LEFT);t.click(p,p.open,15,ClickType.LEFT);
            check(p.received.getLast().clickEvent().value().equals("/navigator entry lore all "),"lore editing uses its own suggested command");
            t.cmd(p,"edit");t.click(p,p.open,11,ClickType.LEFT);t.click(p,p.open,12,ClickType.LEFT);check(p.received.getLast().clickEvent().value().equals("/navigator entry permission "),"permission action does not dump full help");
            t.cmd(p,"edit");t.click(p,p.open,11,ClickType.LEFT);t.click(p,p.open,13,ClickType.LEFT);check(p.received.getLast().clickEvent().value().equals("/navigator entry move "),"move action prompts only scoped move command");
            t.cmd(p,"edit");t.click(p,p.open,11,ClickType.LEFT);t.click(p,p.open,11,ClickType.LEFT);check(text(p.open,0).equals("COMPASS"),"Warp icon picker requires explicit icon action");
            t.cmd(p,"edit");t.click(p,p.open,11,ClickType.LEFT);t.click(p,p.open,18,ClickType.LEFT);
            check(text(p.open,10).equals("Position 1")&&text(p.open,12).equals("Position 2")&&lore(p,4).getFirst().contains("OFF"),"region action opens optional counter screen");
            t.click(p,p.open,10,ClickType.LEFT);check(t.s(p).first!=null&&lore(p,4).getFirst().contains("pending Pos1"),"GUI Pos1 uses the same scoped pending state");
            p.x=9;t.click(p,p.open,12,ClickType.LEFT);check(t.s(p).first==null&&t.entry(p).region()!=null&&lore(p,4).getFirst().contains("Count: ON"),"GUI Pos2 completes region through existing validation");
            t.click(p,p.open,14,ClickType.LEFT);check(t.entry(p).region()==null,"GUI clear removes optional region");
            t.cmd(p,"edit");t.click(p,p.open,11,ClickType.LEFT);t.click(p,p.open,14,ClickType.LEFT);check(t.s(p).draft.menus().get("root").entries().isEmpty(),"remove action deletes only selected draft entry");
            check(t.f.warps.getWarp("custom_000").isPresent(),"entry removal leaves authoritative Warp intact");
        }
    }
    private static void labelsAndSecurity()throws Exception {
        try(var t=new Fixture()) {
            var p=warp(t);check(text(p.open,49).equals("Save & Activate Navigator"),"inactive English Save label");p.locale=Locale.GERMANY;t.cmd(p,"edit");
            check(text(p.open,49).equals("Speichern & Navigator aktivieren"),"inactive German Save label");
            t.cmd(p,"region pos1");t.cmd(p,"edit");check(text(p.open,49).equals("Speichern gesperrt")&&lore(p,49).getFirst().contains("Unvollständige Region"),"incomplete draft shows precise localized blocker on Save");
            byte[] original=Files.readAllBytes(t.file);int n=p.received.size();t.click(p,p.open,49,ClickType.LEFT);check(p.received.size()==n+1&&Arrays.equals(original,Files.readAllBytes(t.file))&&last(p).contains("Unvollständige Region"),"blocked Save cannot persist and reports one precise reason");
            t.cmd(p,"region clear");t.cmd(p,"save");t.cmd(p,"edit");check(text(p.open,49).equals("Änderungen speichern"),"active German Save label");p.locale=Locale.US;t.cmd(p,"edit");check(text(p.open,49).equals("Save Changes"),"active English Save label");
            var root=t.s(p).draft.menus().get("root");var entries=new LinkedHashMap<>(root.entries());var old=entries.get("custom_000");
            entries.put(old.id(),new NavigatorConfig.Entry(old.id(),old.type(),old.page(),old.slot(),old.icon(),old.name(),old.lore(),old.target(),"invalid permission",null,false));
            t.s(p).draft=new NavigatorConfig.State(true,"root",Map.of("root",new NavigatorConfig.Menu("root",LocalizedText.literal("Root"),entries,27,Material.COMPASS,-1)));
            t.cmd(p,"edit");check(text(p.open,49).equals("Cannot save")&&lore(p,49).getFirst().contains("Invalid permission"),"full schema validation exposes exact invalid-draft reason");byte[] saved=Files.readAllBytes(t.file);t.click(p,p.open,49,ClickType.LEFT);check(Arrays.equals(saved,Files.readAllBytes(t.file)),"invalid draft is not saved even through GUI");
        }
        try(var t=new Fixture()) {
            var p=warp(t);byte[] before=Files.readAllBytes(t.file);var old=p.open;t.click(p,p.open,46,ClickType.LEFT);var selector=p.open;int n=p.received.size();
            for(var unsafe:List.of(ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP,ClickType.SWAP_OFFHAND))check(t.click(p,selector,14,unsafe).isCancelled()&&p.open==selector,"unsafe action-selector click inert: "+unsafe);
            check(p.received.size()==n&&t.drag(p,selector,14).isCancelled()&&!t.drag(p,selector,27).isCancelled(),"action selector protects top drag and stays silent on unsafe clicks");
            t.click(p,old,49,ClickType.LEFT);check(Arrays.equals(before,Files.readAllBytes(t.file))&&p.open==selector,"old editor root cannot Save behind action selector");
            var other=t.admin();t.click(other,selector,14,ClickType.LEFT);check(other.open==other.bottom,"foreign UUID cannot invoke action selector");
            var forged=new NavigatorEditor.Holder(t.s(p));forged.inventory=inventory(forged,27);forged.actions.put(14,actor->{throw new AssertionError("forged action ran");});p.open=forged.inventory;t.click(p,p.open,14,ClickType.LEFT);check(Arrays.equals(before,Files.readAllBytes(t.file)),"forged action-selector holder lacks active authority");
            t.cmd(p,"edit");t.click(p,p.open,46,ClickType.LEFT);p.permissions.clear();t.click(p,p.open,14,ClickType.LEFT);check(t.s(p)==null&&p.open==p.bottom,"permission revocation invalidates selector and releases session");
            p.permissions.add(NavigatorEditor.PERMISSION);t.cmd(p,"edit");t.s(p).revision++;n=p.received.size();t.click(p,p.open,46,ClickType.LEFT);check(p.received.size()==n+1&&last(p).contains("editor view expired"),"expired own current view has distinct one-line feedback");
            t.click(p,p.open,46,ClickType.LEFT);check(p.received.size()==n+1,"repeated expired events do not spam chat");
        }
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

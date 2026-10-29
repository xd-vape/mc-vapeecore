package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;
import java.util.LinkedHashMap;
import java.util.List;
import org.bukkit.event.inventory.ClickType;

public final class NavigatorCompactHarness {
    private static int checks;
    public static void main(String[]args)throws Exception {
        var f=new NavigatorFixture();var p=f.player();
        var state=NavigatorConfig.parse("enabled: true\nmenus:\n  root:\n    title: Navigator\n    entries: {}\n");
        f.configured.set(state);f.menu.open(p.player);
        check(p.open.getSize()==27,"unspecified empty menu defaults to 27 slots");
        check(state.menus().get("root").backSlot()==-1,"optional Back off by default");
        for(int slot=0;slot<27;slot++)check(p.open.getItem(slot)==null||slot==13,"root has only empty marker, no Close/Back/filler at "+slot);
        var menu=state.menus().get("root");var entries=new LinkedHashMap<String,NavigatorConfig.Entry>();
        for(int expected:List.of(11,13,15)) {
            menu=new NavigatorConfig.Menu("root",menu.title(),entries,27,menu.icon(),-1);
            int actual=NavigatorEditor.suggestedSlot(menu,0);check(actual==expected,"balanced suggested placement "+expected);
            String id="entry"+expected;entries.put(id,new NavigatorConfig.Entry(id,NavigatorConfig.Type.UNAVAILABLE,0,actual,org.bukkit.Material.GRAY_DYE,dev.vapee.core.i18n.LocalizedText.literal(id),List.of(),"","",null,false));
        }
        String old=NavigatorConfigHarness.CONFIG.replace("slot: 22","slot: 40");state=NavigatorConfig.parse(old);f.configured.set(state);f.menu.open(p.player);
        check(p.open.getSize()==45 && ((NavigatorInventoryHolder)p.open.getHolder()).entryAt(40).isPresent(),"old explicit high slot retained with compatible automatic expansion");
        state=NavigatorConfig.parse(NavigatorConfigHarness.CONFIG.replace("    title:","    size: 54\n    title:"));f.configured.set(state);f.menu.open(p.player);
        check(p.open.getSize()==54,"explicit larger root size");
        f.click(p,p.open,22,ClickType.RIGHT);check(p.open.getSize()==54 && p.open.getItem(48)==null && p.open.getItem(50)==null,"larger submenu still omits intrusive controls");
        f.click(p,p.open,53,ClickType.LEFT);f.click(p,p.open,20,ClickType.RIGHT);check(((NavigatorInventoryHolder)p.open.getHolder()).menuId().equals("nested"),"populated right-click preserves nested action");
        f.click(p,p.open,0,ClickType.LEFT);check(((NavigatorInventoryHolder)p.open.getHolder()).menuId().equals("nested"),"left-click empty does not navigate");
        f.click(p,p.open,0,ClickType.RIGHT);check(((NavigatorInventoryHolder)p.open.getHolder()).menuId().equals("games") && ((NavigatorInventoryHolder)p.open.getHolder()).getPage()==1,"empty right-click restores exact parent page");
        state=NavigatorConfig.parse(NavigatorConfigHarness.CONFIG.replace("    title:","    back-slot: 0\n    title:"));f.configured.set(state);f.menu.open(p.player);f.click(p,p.open,22,ClickType.LEFT);
        check(p.open.getItem(0)!=null && ((NavigatorInventoryHolder)p.open.getHolder()).backAt(0),"explicit optional Back icon supported");f.click(p,p.open,0,ClickType.LEFT);check(((NavigatorInventoryHolder)p.open.getHolder()).menuId().equals("root"),"optional Back uses trusted parent route");
        for(String bad:List.of(NavigatorConfigHarness.CONFIG.replace("    title:","    size: 28\n    title:"),NavigatorConfigHarness.CONFIG.replace("    title:","    size: 27\n    title:").replace("slot: 24","slot: 26"),NavigatorConfigHarness.CONFIG.replace("    title:","    back-slot: 20\n    title:"))) {
            try{NavigatorConfig.parse(bad);throw new AssertionError("invalid geometry accepted");}catch(IllegalArgumentException expected){check(true,"invalid explicit geometry/control collision rejected");}
        }
        var before=p.open;p.open=p.bottom;f.listener.onInventoryClose(new org.bukkit.event.inventory.InventoryCloseEvent(view(p,before)));
        check(p.open==p.bottom,"ESC/Close never reopens player inventory");f.menu.shutdown();
        System.out.println("NavigatorCompactHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

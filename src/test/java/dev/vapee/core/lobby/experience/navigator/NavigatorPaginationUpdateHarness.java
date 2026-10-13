package dev.vapee.core.lobby.experience.navigator;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.lobby.experience.navigator.NavigatorMenu.Action.*;
public final class NavigatorPaginationUpdateHarness{
    private static int checks;
    private static final MenuLayout<NavigatorMenu.Action> LAYOUT=MenuLayout.builder(27,NavigatorMenu.Action.class).content(List.of(10,12)).emptyState(22).button(PREVIOUS,3).button(PAGE_INFO,4).button(NEXT,5).button(CLOSE,8).build();
    public static void main(String[] args)throws Exception{
        for(int count:List.of(0,1,2,3,5))for(int page=0;page<Pagination.of(count,0,LAYOUT.pageSize()).pageCount();page++){
            var f=new NavigatorFixture(LAYOUT);var p=f.player();f.destinations(count);f.menu.open(p.player,page);var top=p.open;var h=(NavigatorInventoryHolder)top.getHolder();
            checks+=PaginationAssertions.verify(Pagination.of(count,page,LAYOUT.pageSize()),LAYOUT,top,h::actionAt,PREVIOUS,PAGE_INFO,NEXT,CLOSE,slot->f.click(p,top,slot,ClickType.LEFT),()->p.open);
        }
        var f=new NavigatorFixture(LAYOUT);var p=f.player();f.destinations(3);f.menu.startUpdates();f.menu.open(p.player,1);var top=p.open;var h=(NavigatorInventoryHolder)top.getHolder();
        f.warps.setNavigatorVisible("custom_002",false);
        check(p.open==top && h.getPage()==0 && h.actionAt(LAYOUT.slot(NEXT)).isEmpty(),"admin hide clamps navigator in place");
        check(h.getWarpId(LAYOUT.contentSlot(0)).orElseThrow().equals("custom_000"),"fresh mapping replaces hidden last-page warp");
        f.warps.setNavigatorOrder("custom_000",10);check(h.getWarpId(LAYOUT.contentSlot(0)).orElseThrow().equals("custom_001"),"admin reorder refreshes active mapping");
        f.click(p,top,LAYOUT.contentSlot(0),ClickType.LEFT);check(p.teleports==1 && p.open==p.bottom,"teleport remains bound to displayed current warp");
        f.menu.open(p.player);var current=p.open;p.loaded=false;f.warps.setNavigatorOrder("custom_001",11);check(p.open==p.bottom,"automatic refresh rechecks current access guard");
        f.menu.shutdown();check(NavigatorMenu.DEFAULT_LAYOUT.actions().keySet().stream().noneMatch(action -> action.name().equals("REFRESH")),"Navigator has no Refresh");
        System.out.println("NavigatorPaginationUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
}

package dev.vapee.core.settings.visibility;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.settings.visibility.VisiblePlayersMenu.Action.*;
public final class VisiblePlayersPaginationUpdateHarness{
    private static int checks;
    private static final MenuLayout<VisiblePlayersMenu.Action> LAYOUT=MenuLayout.builder(27,VisiblePlayersMenu.Action.class).content(List.of(10,12)).emptyState(22).button(PREVIOUS,3).button(PAGE_INFO,4).button(NEXT,5).button(CLOSE,8).build();
    public static void main(String[] args)throws Exception{
        for(int count:List.of(0,1,2,3,5))for(int page=0;page<Pagination.of(count,0,LAYOUT.pageSize()).pageCount();page++){
            var f=new VisibilityMenuFixture(MenuTestLayouts.VISIBILITY,LAYOUT);var owner=f.player("Owner",true);
            for(int i=0;i<count;i++){var t=f.player("Target%03d".formatted(i),false);f.settings.addLobbyVisiblePlayer(owner.id,t.id);}
            f.visiblePlayersMenu.open(owner.player,page);var top=owner.open;var h=VisibilityMenuFixture.visibleHolder(top);
            checks+=PaginationAssertions.verify(Pagination.of(count,page,LAYOUT.pageSize()),LAYOUT,top,h::actionAt,PREVIOUS,PAGE_INFO,NEXT,CLOSE,slot->f.visibleClick(owner,top,slot,ClickType.LEFT),()->owner.open);
        }
        var f=new VisibilityMenuFixture(MenuTestLayouts.VISIBILITY,LAYOUT);var owner=f.player("Owner",true);f.visiblePlayersMenu.startUpdates();
        var targets=new ArrayList<VisibilityMenuFixture.TestPlayer>();for(int i=0;i<3;i++){var t=f.player("Target%03d".formatted(i),true);targets.add(t);f.settings.addLobbyVisiblePlayer(owner.id,t.id);}
        f.visiblePlayersMenu.open(owner.player,1);var top=owner.open;var h=VisibilityMenuFixture.visibleHolder(top);UUID removed=h.getTarget(LAYOUT.contentSlot(0)).orElseThrow();
        f.settings.removeLobbyVisiblePlayer(owner.id,removed);
        check(owner.open==top && h.getPage()==0 && h.actionAt(LAYOUT.slot(NEXT)).isEmpty(),"external removal clamps in place");
        UUID actual=h.getTarget(LAYOUT.contentSlot(0)).orElseThrow();check(actual.equals(targets.getFirst().id),"fresh UUID map reflects changed page");
        f.visiblePlayersMenu.targetPresenceChanged(actual,false);
        check(VisibilityMenuFixture.visibleSpec(top,LAYOUT.contentSlot(0)).lore().stream().map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).anyMatch("Offline"::equals),"quit updates status even while API still reports online");
        var unrelated=f.player("Unrelated",true);var item=top.getItem(LAYOUT.contentSlot(0));f.visiblePlayersMenu.targetPresenceChanged(unrelated.id,false);check(top.getItem(LAYOUT.contentSlot(0))==item,"unrelated presence does not rerender viewer");
        f.visiblePlayersMenu.targetPresenceChanged(actual,true);check(VisibilityMenuFixture.visibleSpec(top,LAYOUT.contentSlot(0)).lore().stream().map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).anyMatch("Online"::equals),"join updates relevant status");
        f.visibleClick(owner,top,LAYOUT.contentSlot(0),ClickType.RIGHT);check(!f.settings.getLobbyAddedVisiblePlayers(owner.id).orElseThrow().contains(actual),"GUI removes fresh target without manual refresh");
        f.visiblePlayersMenu.shutdown();check(VisiblePlayersMenu.DEFAULT_LAYOUT.actionSlot(REFRESH).isEmpty(),"production has no manual Refresh");
        System.out.println("VisiblePlayersPaginationUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
}

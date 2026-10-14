package dev.vapee.core.friend.gui;
import dev.vapee.core.friend.*;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.friend.gui.FriendMenu.Action.*;
public final class FriendPaginationUpdateHarness {
    private static int checks;
    private static final MenuLayout<FriendMenu.Action> LAYOUT = MenuLayout.builder(27, FriendMenu.Action.class)
            .content(List.of(10, 12)).emptyState(22).button(PREVIOUS, 3).button(PAGE_INFO, 4).button(NEXT, 5).button(CLOSE, 8).build();
    public static void main(String[] args) throws Exception {
        for (int count : List.of(0, 1, 2, 3, 5)) for (int page = 0; page < Pagination.of(count, 0, LAYOUT.pageSize()).pageCount(); page++) {
            var f = new FriendMenuFixture(LAYOUT); var owner = f.player("Owner", true);
            for (int i=0;i<count;i++) { var t=f.player("Target%03d".formatted(i), false); f.friends.sendRequest(owner.id,t.id); f.friends.acceptRequest(t.id,owner.id); }
            f.menu.open(owner.player, FriendMenuView.FRIENDS, page); var top=owner.open; var h=(FriendMenuHolder)top.getHolder();
            checks+=PaginationAssertions.verify(Pagination.of(count,page,LAYOUT.pageSize()),LAYOUT,top,h::actionAt,PREVIOUS,PAGE_INFO,NEXT,CLOSE,slot->f.click(owner,top,slot,ClickType.LEFT),()->owner.open);
        }
        var f=new FriendMenuFixture(LAYOUT);var owner=f.player("Owner",true);f.menu.startUpdates();
        var targets=new ArrayList<FriendMenuFixture.TestPlayer>();
        for(int i=0;i<2;i++){var t=f.player("Target%03d".formatted(i),true);targets.add(t);f.friends.sendRequest(owner.id,t.id);f.friends.acceptRequest(t.id,owner.id);}
        f.menu.open(owner.player);var one=owner.open;check(((FriendMenuHolder)one.getHolder()).actionAt(LAYOUT.slot(NEXT)).isEmpty(),"single page has no Next");
        var third=f.player("Target002",true);f.friends.sendRequest(owner.id,third.id);f.friends.acceptRequest(third.id,owner.id);
        check(owner.open==one && ((FriendMenuHolder)one.getHolder()).actionAt(LAYOUT.slot(NEXT)).isPresent(),"external acceptance creates pagination in place");
        f.click(owner,one,LAYOUT.slot(NEXT),ClickType.LEFT);var last=owner.open;var h=(FriendMenuHolder)last.getHolder();
        check(h.getTarget(LAYOUT.contentSlot(0)).orElseThrow().equals(third.id),"last-page target exact");
        f.friends.removeFriend(third.id,owner.id);
        check(owner.open==last && h.getPage()==0 && h.actionAt(LAYOUT.slot(NEXT)).isEmpty() && h.actionAt(LAYOUT.slot(PAGE_INFO)).isEmpty(),"external removal clamps and hides pagination without Refresh");
        check(h.getTarget(LAYOUT.contentSlot(0)).orElseThrow().equals(targets.getFirst().id) && !h.getTarget(LAYOUT.contentSlot(0)).orElseThrow().equals(third.id),"fresh target map replaces stale last-page UUID");
        f.click(owner,last,LAYOUT.contentSlot(0),ClickType.SHIFT_RIGHT);
        check(f.friends.getRelation(owner.id,targets.getFirst().id)==FriendRelation.NONE && f.friends.getRelation(owner.id,targets.getLast().id)==FriendRelation.FRIENDS,"GUI mutation acts only on freshly mapped target");
        int count=f.friends.countFriends(owner.id);var item=last.getItem(LAYOUT.contentSlot(0));f.repository.failNext=true;
        try{f.friends.removeFriend(owner.id,targets.getLast().id);throw new AssertionError("save should fail");}catch(FriendRepositoryException expected){check(f.friends.countFriends(owner.id)==count && last.getItem(LAYOUT.contentSlot(0))==item,"failed persistence does not publish optimistic UI");}
        f.menu.shutdown();check(FriendMenu.DEFAULT_LAYOUT.actionSlot(REFRESH).isEmpty(),"production has no manual Refresh");
        System.out.println("FriendPaginationUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
}

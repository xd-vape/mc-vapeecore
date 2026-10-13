package dev.vapee.core.clan.gui;
import dev.vapee.core.clan.*;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.clan.gui.ClanMenu.Action.*;
public final class ClanPaginationUpdateHarness {
    private static int checks;
    private static final MenuLayout<ClanMenu.Action> LAYOUT=MenuLayout.builder(27,ClanMenu.Action.class).content(List.of(10,12)).emptyState(22).button(PREVIOUS,3).button(PAGE_INFO,4).button(NEXT,5).button(CLOSE,8).build();
    private static ClanMenu.Layout definition(){return new ClanMenu.Layout(LAYOUT,20,22,24);}
    public static void main(String[] args)throws Exception{
        for(int count:List.of(0,1,2,3,5))for(int page=0;page<Pagination.of(count,0,LAYOUT.pageSize()).pageCount();page++){
            var f=new ClanMenuFixture(ClanLimits.defaults(),definition());var owner=f.player("Owner",true);f.clans.createClan(owner.id,"Matrix Clan","MAT");
            for(int i=0;i<count;i++){var t=f.player("Target"+i,false);f.clans.inviteMember(owner.id,t.id);}
            f.menu.open(owner.player,ClanMenuView.INVITES,page);var top=owner.open;var h=(ClanMenuHolder)top.getHolder();
            checks+=PaginationAssertions.verify(Pagination.of(count,page,LAYOUT.pageSize()),LAYOUT,top,h::actionAt,PREVIOUS,PAGE_INFO,NEXT,CLOSE,slot->f.click(owner,top,slot,ClickType.LEFT),()->owner.open);
        }
        var f=new ClanMenuFixture(ClanLimits.defaults(),definition());var owner=f.player("Owner",true);f.clans.createClan(owner.id,"Update Clan","UPD");f.menu.startUpdates();
        var targets=new ArrayList<ClanMenuFixture.TestPlayer>();for(int i=0;i<3;i++){var t=f.player("Target"+i,true);targets.add(t);f.clans.inviteMember(owner.id,t.id);}
        f.menu.open(owner.player,ClanMenuView.INVITES,1);var top=owner.open;var h=(ClanMenuHolder)top.getHolder();var removed=h.target(LAYOUT.contentSlot(0)).orElseThrow();
        f.clans.cancelInvite(owner.id,removed);
        check(owner.open==top && h.page()==0 && h.actionAt(LAYOUT.slot(NEXT)).isEmpty(),"committed external cancel clamps in place");
        UUID actual=h.target(LAYOUT.contentSlot(0)).orElseThrow();check(!actual.equals(removed),"invite mappings rebuilt");
        f.click(owner,top,LAYOUT.contentSlot(0),ClickType.RIGHT);check(f.clans.getOutgoingInvites(f.clans.getClanOf(owner.id).orElseThrow().id()).stream().noneMatch(i->i.recipient().equals(actual)),"GUI cancel uses current mapping without Refresh");
        var prior=top.getItem(LAYOUT.contentSlot(0));f.repository.failNext=true;
        try{f.clans.cancelInvite(owner.id,h.target(LAYOUT.contentSlot(0)).orElseThrow());throw new AssertionError("save should fail");}catch(ClanRepositoryException expected){check(top.getItem(LAYOUT.contentSlot(0))==prior,"failed clan commit publishes no UI");}
        f.clans.renameClan(owner.id,"Renamed Clan");check(owner.open==top,"external rename does not reopen same view");
        f.menu.shutdown();check(ClanMenu.DEFAULT_LAYOUT.menu().actionSlot(REFRESH).isEmpty(),"production Clan has no Clock");
        var last=new ClanMenuFixture(ClanLimits.defaults(),definition());var inviter=last.player("Inviter",true);var recipient=last.player("Recipient",true);
        last.clans.createClan(inviter.id,"Last Invite Clan","END");last.clans.inviteMember(inviter.id,recipient.id);last.menu.startUpdates();
        last.menu.open(inviter.player,ClanMenuView.INVITES,0);last.menu.open(recipient.player,ClanMenuView.INVITES,0);
        var outgoing=inviter.open;var incoming=recipient.open;
        check(((ClanMenuHolder)incoming.getHolder()).target(LAYOUT.contentSlot(0)).isPresent(),"recipient initially sees incoming invitation");
        last.clans.renameClan(inviter.id,"Updated Invite Clan");
        check(recipient.open==incoming && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(ClanMenuFixture.spec(incoming,LAYOUT.contentSlot(0)).spec.name()).contains("Updated Invite Clan"),"source clan rename updates pending recipient display");
        last.clans.cancelInvite(inviter.id,recipient.id);
        check(inviter.open==outgoing && ((ClanMenuHolder)outgoing.getHolder()).target(LAYOUT.contentSlot(0)).isEmpty(),"removing final invite updates its owner without Refresh");
        check(recipient.open==incoming && ((ClanMenuHolder)incoming.getHolder()).target(LAYOUT.contentSlot(0)).isEmpty(),"removed invite notifies its recipient and clears stale mapping");
        last.menu.shutdown();
        System.out.println("ClanPaginationUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
}

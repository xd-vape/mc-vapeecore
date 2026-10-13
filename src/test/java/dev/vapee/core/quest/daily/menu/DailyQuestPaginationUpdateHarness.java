package dev.vapee.core.quest.daily.menu;
import dev.vapee.core.quest.*;
import dev.vapee.core.quest.daily.*;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.quest.daily.menu.DailyQuestMenu.Action.*;
public final class DailyQuestPaginationUpdateHarness{
    private static int checks;
    public static void main(String[] args)throws Exception{
        for(int count:List.of(0,1,2,3,4,5,6,7,11)){
            var f=new QuestMenuFixture(DailyQuestMenu.DEFAULT_LAYOUT);var p=f.player();var definitions=new ArrayList<QuestDefinition>();
            for(int i=0;i<count;i++)definitions.add(QuestCompletionFixture.definition("q%03d".formatted(i),"playtime:minute",100,10));
            f.domain.registry.replaceAll(definitions);for(var definition:definitions)f.domain.quests.assignQuest(p.id,definition.id());
            f.forcedResult=DailyQuestSyncResult.CURRENT;f.domain.quests.flushPlayer(p.id);
            for(int page=0;page<Pagination.of(count,0,f.menu.pageSize()).pageCount();page++){
                f.menu.open(p.player,page);var top=p.open;var h=(DailyQuestInventoryHolder)top.getHolder();var expected=Pagination.of(count,page,f.menu.pageSize());
                checks+=PaginationAssertions.verify(expected,f.menu.layout(),top,h::actionAt,PREVIOUS,PAGE_INFO,NEXT,CLOSE,slot->f.click(p,top,slot,ClickType.LEFT),()->p.open);
                var slots=f.menu.visibleContentSlots(expected.toIndex()-expected.fromIndex());
                for(int i=0;i<slots.size();i++)check(h.target(slots.get(i)).orElseThrow().equals(definitions.get(expected.fromIndex()+i).id())&&top.getItem(slots.get(i))!=null,"adaptive slot maps exactly to quest on this page");
                if(count==0)check(top.getItem(f.menu.layout().emptySlot())!=null,"empty state rendered");
                check(f.menu.pageSize()==5,"DailyQuest maximum page capacity is five");
            }
        }
        var f=new QuestMenuFixture(DailyQuestMenu.DEFAULT_LAYOUT);var p=f.player();
        f.domain.registry.replaceAll(List.of(QuestCompletionFixture.definition("progress","playtime:minute",100,10)));f.menu.open(p.player,0);f.menu.startUpdates();var top=p.open;int slot=f.menu.visibleContentSlots(1).getFirst();var item=top.getItem(slot);
        f.domain.quests.addProgress(p.id,QuestProgressKey.of("playtime:minute"),1);
        check(top.getItem(slot)==item,"dirty progress not shown optimistically before successful flush");
        f.domain.repository.fail=true;f.domain.quests.flushPlayer(p.id);check(top.getItem(slot)==item,"failed save emits no UI commit");
        f.domain.repository.fail=false;f.domain.quests.flushPlayer(p.id);
        check(p.open==top && QuestMenuFixture.lore(top,slot).contains("1 / 100"),"persisted progress rerenders same inventory without Refresh");
        check(((DailyQuestInventoryHolder)top.getHolder()).target(slot).orElseThrow().equals("progress"),"committed map remains bound to exact quest");
        f.now=f.now.plusSeconds(86400);f.daily.syncPlayer(p.id,f.now);f.domain.quests.flushPlayer(p.id);
        check(p.open==top && QuestMenuFixture.lore(top,slot).contains("0 / 100"),"persisted cycle rotation updates adaptive view");
        f.domain.quests.clearAssignments(p.id);f.domain.quests.flushPlayer(p.id);
        check(p.open==top && ((DailyQuestInventoryHolder)top.getHolder()).target(slot).isEmpty()&&top.getItem(f.menu.layout().emptySlot())!=null,"committed clear removes stale target and shows empty state");
        f.menu.shutdown();check(DailyQuestMenu.DEFAULT_LAYOUT.menu().actionSlot(REFRESH).isEmpty(),"production DailyQuest has no manual Clock");
        System.out.println("DailyQuestPaginationUpdateHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String reason){checks++;PaginationAssertions.require(value,reason);}
}

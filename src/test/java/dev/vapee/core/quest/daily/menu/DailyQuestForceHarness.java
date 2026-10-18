package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.quest.*;
import dev.vapee.core.quest.daily.*;
import dev.vapee.core.quest.daily.command.CoreQuestCommand;
import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.rank.staff.*;
import org.bukkit.command.*;
import net.kyori.adventure.text.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dev.vapee.core.quest.QuestCompletionFixture.*;

public final class DailyQuestForceHarness {
    private static int checks;
    public static void main(String[] args)throws Exception{
        selectionAndPersistence();failureAndPending();command();
        System.out.println("DailyQuestForceHarness passed "+checks+" checks.");
    }
    private static QuestMenuFixture fixture()throws Exception{
        var f=new QuestMenuFixture(DailyQuestMenu.DEFAULT_LAYOUT);var definitions=new ArrayList<QuestDefinition>();
        for(int i=0;i<7;i++)definitions.add(definition("q"+i,"test:q"+i,100,10));
        f.domain.registry.replaceAll(definitions);return f;
    }
    private static void selectionAndPersistence()throws Exception{
        var f=fixture();var p=f.player();f.perDay=2;f.menu.startUpdates();f.menu.open(p.player,0);var top=p.open;
        var selected=f.domain.quests.getActiveQuests(p.id).orElseThrow();
        f.domain.quests.addProgress(p.id,selected.getFirst().definition().progressKey(),100);
        f.domain.quests.addProgress(p.id,selected.getLast().definition().progressKey(),7);f.domain.quests.flushPlayer(p.id);
        var previous=f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot();
        check(previous.size()==2,"starts with actual existing two-quest selection");
        int[] events={0};f.domain.quests.addChangeListener(id->{
            check(f.domain.repository.persisted.get(id).equals(f.domain.players.getPlayer(id).orElseThrow().getQuestState().snapshot()),"force observer runs after successful persistence");events[0]++;
        });
        int saves=f.domain.repository.saves;
        for(int count:List.of(3,4,5,6,7)){
            f.perDay=count;check(f.daily.forcePlayer(p.id,f.now)==DailyQuestSyncResult.ROTATED,"force current config count "+count);
            var state=f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot();
            check(state.size()==count,"exact configured assignment count "+count);
            check(previous.entrySet().stream().allMatch(e->e.getValue().equals(state.get(e.getKey()))),"retained progress/completion preserved "+count);
            check(f.domain.economy.getCoins(p.id).orElseThrow()==10,"force grants no coins "+count);
            check(p.open==top,"force refreshes currently open exact inventory without reopening "+count);
            var holder=(DailyQuestInventoryHolder)top.getHolder();var slots=f.menu.visibleContentSlots(Math.min(5,count));
            check(slots.stream().allMatch(slot->holder.target(slot).filter(state::containsKey).isPresent()),"rendered mappings rebuilt for current forced selection "+count);
        }
        check(events[0]==5 && f.domain.repository.saves==saves+5,"one committed notification/save per force");
        f.menu.open(p.player,1);var last=p.open;f.perDay=3;f.daily.forcePlayer(p.id,f.now);
        var h=(DailyQuestInventoryHolder)last.getHolder();
        check(p.open==last && h.page()==0,"force smaller selection clamps existing final page in same inventory");
        check(f.menu.visibleContentSlots(3).stream().allMatch(slot->h.target(slot).isPresent()),"clamped view maps exactly three quests");
        f.perDay=1;check(f.daily.forcePlayer(p.id,f.now)==DailyQuestSyncResult.ROTATED && f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot().size()==1,"force can explicitly reduce selection");
        f.perDay=5;f.menu.open(p.player,0);
        check(f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot().size()==1,"ordinary same-cycle open/reload rule remains unchanged");
        f.perDay=100;f.daily.forcePlayer(p.id,f.now);
        check(f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot().size()==7,"small catalog caps forced count");
        var profile=f.domain.players.getPlayer(p.id).orElseThrow();
        check(profile.getDailyQuestState().cycleId().orElseThrow().equals(new DailyQuestCycleResolver().resolve(f.now,f.zone,f.resetTime)),"force commits actual configured cycle");
        f.menu.shutdown();
    }
    private static void failureAndPending()throws Exception{
        var f=fixture();var p=f.player();f.perDay=2;f.menu.startUpdates();f.menu.open(p.player,0);f.domain.quests.flushPlayer(p.id);
        var profile=f.domain.players.getPlayer(p.id).orElseThrow();var original=profile.getQuestState().snapshot();var cycle=profile.getDailyQuestState().cycleId();
        var persisted=f.domain.repository.persisted.get(p.id);var top=p.open;var item=top.getItem(f.menu.visibleContentSlots(2).getFirst());int saves=f.domain.repository.saves;
        f.perDay=5;f.domain.repository.fail=true;
        check(f.daily.forcePlayer(p.id,f.now.plusSeconds(86400))==DailyQuestSyncResult.ASSIGNMENT_FAILED,"failed force save reports failure");
        check(profile.getQuestState().snapshot().equals(original) && profile.getDailyQuestState().cycleId().equals(cycle),"failed force restores assignments, progress and prior cycle");
        check(f.domain.repository.persisted.get(p.id).equals(persisted) && f.domain.repository.saves==saves,"failed force changes no persisted data");
        check(p.open==top && top.getItem(f.menu.visibleContentSlots(2).getFirst())==item,"failed force emits no optimistic UI rerender");
        f.domain.repository.fail=false;var fresh=f.player();f.domain.repository.fail=true;check(f.daily.forcePlayer(fresh.id,f.now)==DailyQuestSyncResult.ASSIGNMENT_FAILED && f.domain.players.getPlayer(fresh.id).orElseThrow().getDailyQuestState().cycleId().isEmpty(),"failed initial force restores uninitialized cycle");
        f.domain.repository.fail=false;
        var first=f.domain.quests.getActiveQuests(p.id).orElseThrow().getFirst().definition();
        f.domain.quests.addProgress(p.id,first.progressKey(),1);var dirty=profile.getQuestState().snapshot();
        f.domain.repository.fail=true;f.daily.forcePlayer(p.id,f.now);f.domain.repository.fail=false;f.domain.quests.flushPlayer(p.id);
        check(f.domain.repository.persisted.get(p.id).equals(dirty),"failed force preserves prior dirty progress for normal retry");
        f.domain.economy.setCoins(p.id,Long.MAX_VALUE);f.domain.quests.addProgress(p.id,first.progressKey(),100);f.domain.quests.flushPlayer(p.id);
        var pending=profile.getQuestState().snapshot();saves=f.domain.repository.saves;
        check(f.daily.forcePlayer(p.id,f.now)==DailyQuestSyncResult.BLOCKED_PENDING_REWARD,"force cannot discard pending reward");
        check(profile.getQuestState().snapshot().equals(pending) && f.domain.repository.saves==saves && f.domain.economy.getCoins(p.id).orElseThrow()==Long.MAX_VALUE,"pending force refusal neither saves, rewards nor changes state");
        f.domain.registry.replaceAll(List.of());
        check(f.daily.forcePlayer(p.id,f.now)==DailyQuestSyncResult.NO_DEFINITIONS,"empty catalog does not wipe assignments");
        f.enabled=false;check(f.daily.forcePlayer(p.id,f.now)==DailyQuestSyncResult.DISABLED,"disabled daily config blocks force");
        f.enabled=true;check(f.daily.forcePlayer(UUID.randomUUID(),f.now)==DailyQuestSyncResult.PLAYER_NOT_LOADED,"unloaded force never loads an offline profile");
        var clean=fixture();var q=clean.player();
        check(clean.domain.quests.reconcileAssignments(q.id,List.of("q0","q0"))==QuestAssignmentResult.DUPLICATE_QUEST_ID,"duplicate desired IDs rejected before mutation");
        check(clean.domain.quests.reconcileAssignments(q.id,List.of("q0","missing"))==QuestAssignmentResult.DEFINITION_NOT_FOUND,"unknown desired IDs rejected before mutation");
        check(clean.domain.players.getPlayer(q.id).orElseThrow().getQuestState().isEmpty(),"invalid force arguments leave model unchanged");
        f.menu.shutdown();
    }
    private static void command()throws Exception{
        var f=fixture();var p=f.player();var other=f.player();other.name="Other";f.perDay=3;
        var groups=new HashMap<UUID,String>();int[] reads={0},loads={0},online={0};
        var hierarchy=new StaffHierarchyService(StaffHierarchyConfig::defaults,id->{reads[0]++;return Optional.ofNullable(groups.get(id));},id->{loads[0]++;return CompletableFuture.completedFuture(Optional.empty());});
        var guard=new OnlineStaffTargetGuard(hierarchy,f.domain.logger);
        var command=new CoreQuestCommand(f.daily,f.domain.players,f.messages,()->{online[0]++;return f.online.values().stream().map(v->v.player).toList();},()->f.now,guard);
        p.permission=false;int saves=f.domain.repository.saves;
        command.onCommand(p.player,null,"core",new String[]{"force","Other"});
        check(f.domain.repository.saves==saves && online[0]==0 && reads[0]==0 && p.received().contains("permission"),"permission denial precedes lookup, hierarchy and mutation");
        check(command.onTabComplete(p.player,null,"core",new String[]{"force",""}).isEmpty() && online[0]==0,"unauthorized completion reveals no targets");
        p.permission=true;
        for(String[] args:List.of(new String[]{},new String[]{"bad"},new String[]{"force","Other","extra"}))command.onCommand(p.player,null,"core",args);
        check(f.domain.repository.saves==saves,"invalid command arity/action cannot mutate");
        command.onCommand(p.player,null,"core",new String[]{"FoRcE"});
        check(f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot().size()==3 && p.received().contains("updated: 3"),"self force applies configured count and localized success");
        check(reads[0]==0,"self UUID force needs no LP lookup");
        p.locale=Locale.GERMANY;command.onCommand(p.player,null,"core",new String[]{"force"});
        check(p.received().contains("Tagesquests") && p.received().contains("aktualisiert: 3"),"German force feedback uses existing localization");
        groups.put(p.id,"admin");groups.put(other.id,"owner");saves=f.domain.repository.saves;
        command.onCommand(p.player,null,"core",new String[]{"force","Other"});
        check(f.domain.repository.saves==saves && f.domain.players.getPlayer(other.id).orElseThrow().getQuestState().isEmpty(),"admin capability never bypasses higher staff target protection");
        check(!command.onTabComplete(p.player,null,"core",new String[]{"force",""}).contains("Other"),"completion hides protected target");
        groups.put(other.id,"default");command.onCommand(p.player,null,"core",new String[]{"force","oThEr"});
        check(f.domain.players.getPlayer(other.id).orElseThrow().getQuestState().snapshot().size()==3,"exact case-insensitive online target supported");
        saves=f.domain.repository.saves;command.onCommand(p.player,null,"core",new String[]{"force","Oth"});
        check(f.domain.repository.saves==saves,"partial player match cannot force");
        other.online=false;command.onCommand(p.player,null,"core",new String[]{"force","Other"});
        check(f.domain.repository.saves==saves,"offline player cannot force");other.online=true;
        f.domain.players.unloadPlayer(other.id);saves=f.domain.repository.saves;command.onCommand(p.player,null,"core",new String[]{"force","Other"});
        check(f.domain.repository.saves==saves,"online target without loaded profile cannot force");
        var output=new ArrayList<Component>();var console=proxy(ConsoleCommandSender.class,(name,args)->switch(name){case "hasPermission"->true;case "sendMessage"->{for(Object a:args)if(a instanceof Component c)output.add(c);yield null;}default->DEFAULT;});
        command.onCommand(console,null,"core",new String[]{"force"});check(output.stream().map(QuestCompletionFixture::plain).anyMatch(v->v.contains("specify an online player")),"console requires explicit online target");
        command.onCommand(console,null,"core",new String[]{"force",p.name});check(output.stream().map(QuestCompletionFixture::plain).anyMatch(v->v.contains("updated: 3")),"console may force explicit online target");
        check(command.onTabComplete(p.player,null,"core",new String[]{"F"}).equals(List.of("force")),"force completion case insensitive");
        check(loads[0]==0,"force/typing never performs an asynchronous offline LP load");
        p.name="<click:run_command:'/op @s'>";p.locale=Locale.US;p.output.clear();command.onCommand(p.player,null,"core",new String[]{"force"});
        check(p.received().contains(p.name) && p.output.stream().noneMatch(QuestCompletionFixture::hasEvent),"dynamic target name remains safe literal Component");
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

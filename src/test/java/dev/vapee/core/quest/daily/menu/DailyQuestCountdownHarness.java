package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.quest.*;
import dev.vapee.core.quest.daily.*;
import dev.vapee.core.i18n.*;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import static dev.vapee.core.quest.daily.menu.DailyQuestMenu.Action.*;
import static dev.vapee.core.quest.daily.menu.QuestMenuFixture.*;

public final class DailyQuestCountdownHarness {
    private static int checks;
    public static void main(String[] args)throws Exception{
        if (args.length > 0 && args[0].equals("glint-only")) {
            completionGlint(); System.out.println("DailyQuestCountdownHarness passed "+checks+" checks."); return;
        }
        var layout=DailyQuestMenu.DEFAULT_LAYOUT;
        check(layout.menu().size()==36,"production inventory has four rows");
        check(layout.menu().slot(RESET_INFO)==34,"production reset clock is exactly slot 34");
        check(layout.menu().slot(CLOSE)==35,"production close is exactly slot 35");
        check(layout.menu().slot(PREVIOUS)==27 && layout.menu().slot(PAGE_INFO)==31 && layout.menu().slot(NEXT)==33,"pagination stays in local footer placements");
        check(layout.menu().actionSlot(REFRESH).isEmpty(),"no production manual refresh");
        check(layout.menu().contentSlots().stream().noneMatch(slot->layout.menu().actionAt(slot).isPresent()),"adaptive content/control placements never overlap");
        for(int count:List.of(0,1,2,3,4,5,6,7,11)){
            var f=fixture(count);var p=f.player();f.forcedResult=DailyQuestSyncResult.CURRENT;
            for(var def:f.domain.registry.snapshot())f.domain.quests.assignQuest(p.id,def.id());
            for(int page=0;page<Pagination.of(count,0,f.menu.pageSize()).pageCount();page++){
                f.menu.open(p.player,page);var top=p.open;var h=(DailyQuestInventoryHolder)top.getHolder();var pagination=Pagination.of(count,page,f.menu.pageSize());
                checks+=PaginationAssertions.verify(pagination,f.menu.layout(),top,h::actionAt,PREVIOUS,PAGE_INFO,NEXT,CLOSE,slot->f.click(p,top,slot,ClickType.LEFT),()->p.open);
                check(spec(top,34).material()==org.bukkit.Material.CLOCK && top.getItem(35)!=null,"clock/close permanent on every page and empty state");
                check(h.actionAt(34).orElseThrow()==RESET_INFO,"clock has a rendered information-only placement");
                var visible=f.menu.visibleContentSlots(pagination.toIndex()-pagination.fromIndex());
                for(int slot:visible)check(h.target(slot).isPresent() && top.getItem(slot)!=null,"adaptive actual targets preserved");
            }
            f.menu.shutdown();
        }
        var f=fixture(11);var p=f.player();var other=f.player();other.locale=Locale.GERMANY;
        f.resetTime=LocalTime.of(4,0);f.zone=ZoneId.of("Europe/Berlin");f.now=Instant.parse("2026-10-18T01:59:58Z");
        f.menu.startUpdates();f.menu.startUpdates();check(f.runningTimers()==0,"idle menu owns no timer");
        f.menu.open(p.player,1);var top=p.open;var holder=(DailyQuestInventoryHolder)top.getHolder();
        check(lore(top,34).contains("Resets in 00:00:02"),"configured schedule appears on first render");
        f.menu.open(other.player,0);check(f.runningTimers()==1 && f.timers.size()==1,"two viewers share exactly one countdown task");
        check(text(other.open,34).equals("Täglicher Reset") && lore(other.open,34).contains("Reset in 00:00:02"),"German viewer sees same duration in German");
        check(text(top,34).equals("Daily Reset"),"English viewer remains independent");
        int sync=f.syncCalls,saves=f.domain.repository.saves,opens=p.opens;
        var assignments=f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot();
        ItemSnapshot snapshot=new ItemSnapshot(top,34);var oldClock=top.getItem(34);
        f.tick();check(top.getItem(34)==oldClock,"unchanged second does not rebuild even clock unnecessarily");
        f.now=f.now.plusSeconds(1);f.tick();
        check(top.getItem(34)!=oldClock && lore(top,34).contains("00:00:01"),"controlled second updates actual clock");
        check(snapshot.sameExceptClock(top,34) && p.opens==opens && holder.page()==1,"clock tick preserves inventory, quest stacks, other controls and page");
        check(holder.target(layout.slots(5).getFirst()).isPresent() && holder.actionAt(f.menu.layout().slot(NEXT)).orElseThrow()==NEXT,"clock preserves actual target and runtime action maps");
        f.now=f.now.plusSeconds(1);f.tick();
        check(lore(top,34).contains("24:00:00"),"at boundary countdown advances to next day, not permanently zero");
        check(f.syncCalls==sync && f.domain.repository.saves==saves && f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot().equals(assignments),"ordinary/boundary ticks never synchronize or persist quests");
        for(ClickType type:List.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY))
            check(f.click(p,top,34,type).isCancelled() && p.open==top && f.syncCalls==sync && f.domain.repository.saves==saves && p.opens==opens,"clock click inert: "+type);
        var drag=new InventoryDragEvent(view(p,top),null,null,false,Map.of());
        f.listener.onInventoryDrag(drag);check(drag.isCancelled(),"drag remains protected");
        f.messages.localization().localeChanged(p.player,Locale.GERMANY);
        check(p.open==top && text(top,34).equals("Täglicher Reset"),"existing locale event immediately localizes clock without reopening");
        p.locale=Locale.GERMANY;
        Path directory=Files.createTempDirectory("vapeecore-clock-lang-");
        try{
            Files.writeString(directory.resolve("de.yml"),"daily-quests.reset-title: '<gray>Mein Reset</gray>'\ndaily-quests.reset-countdown: '<gray>Noch <aqua><time></aqua></gray>'\n");
            byte[] bytes=Files.readAllBytes(directory.resolve("de.yml"));var previous=f.messages.localization().snapshot();
            f.messages.localization().apply(f.messages.localization().prepare(directory));f.tick();
            check(text(top,34).equals("Mein Reset") && lore(top,34).contains("Noch 24:00:00"),"next clock render honors new live language override without restart");
            check(Arrays.equals(bytes,Files.readAllBytes(directory.resolve("de.yml"))),"rendering leaves operator language bytes unchanged");
            check(lore(top,34).contains("Jeden Tag"),"missing live description falls back to bundled German");
            f.messages.localization().apply(previous);
        }finally{Files.deleteIfExists(directory.resolve("de.yml"));Files.deleteIfExists(directory);}
        f.resetTime=LocalTime.of(5,0);f.tick();check(lore(top,34).contains("01:00:00"),"next tick reads changed service schedule");
        other.player.closeInventory();check(f.runningTimers()==1,"closing one viewer preserves remaining countdown");
        p.player.closeInventory();check(f.runningTimers()==0 && f.menu.activeCount()==0,"last close releases task and active inventory");
        f.menu.open(p.player,0);check(f.runningTimers()==1,"reopen owns exactly one task");
        f.menu.shutdown();check(f.runningTimers()==0 && f.menu.activeCount()==0,"shutdown cancels countdown and releases views");
        f.now=f.now.plusSeconds(20);f.tick();check(f.runningTimers()==0,"cancelled task cannot restart itself");
        staleCases(); completionGlint(); catalogChanges();
        System.out.println("DailyQuestCountdownHarness passed "+checks+" checks.");
    }
    private static void catalogChanges()throws Exception{
        var f=fixture(2);var p=f.player();f.perDay=2;f.menu.startUpdates();f.menu.open(p.player,0);
        var saved=f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot();
        check(saved.size()==2,"initial small catalog assigns two daily quests");
        var definitions=new ArrayList<>(f.domain.registry.snapshot());
        definitions.add(QuestCompletionFixture.definition("new_quest_2","playtime:minute",15,250));
        definitions.add(QuestCompletionFixture.definition("new_quest_3","blackjack:win",3,500));
        f.domain.registry.replaceAll(definitions);f.perDay=3;f.menu.open(p.player,0);
        check(f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot().equals(saved),"expanded catalog and count do not reroll existing same-cycle assignments");
        var holder=(DailyQuestInventoryHolder)p.open.getHolder();
        check(f.menu.visibleContentSlots(2).stream().allMatch(slot->holder.target(slot).isPresent()),"menu shows actual two assignments rather than all four catalog definitions");
        f.now=f.now.plusSeconds(86400);f.daily.syncPlayer(p.id,f.now);f.domain.quests.flushPlayer(p.id);
        check(f.domain.players.getPlayer(p.id).orElseThrow().getQuestState().snapshot().size()==3,"next normal cycle selects configured three from expanded four-entry catalog");
        var rotated=(DailyQuestInventoryHolder)p.open.getHolder();
        check(f.menu.visibleContentSlots(3).stream().allMatch(slot->rotated.target(slot).isPresent()),"committed next cycle updates visible three-quest arrangement automatically");
        f.menu.shutdown();
    }
    private static void completionGlint()throws Exception{
        var f=fixture(1);var p=f.player();f.menu.startUpdates();f.menu.open(p.player,0);
        var top=p.open;int slot=f.menu.visibleContentSlots(1).getFirst();
        check(!glints(top.getItem(slot)),"active quest has no completion glint");
        check(!glints(top.getItem(34)) && !glints(top.getItem(35)),"Clock and Close have no completion glint");
        f.domain.quests.addProgress(p.id,QuestProgressKey.of("playtime:minute"),99);f.domain.quests.flushPlayer(p.id);
        check(!glints(top.getItem(slot)),"partial committed progress stays unglinted");
        var partial=top.getItem(slot);
        f.domain.quests.addProgress(p.id,QuestProgressKey.of("playtime:minute"),1);
        check(top.getItem(slot)==partial && !glints(partial),"completion glint waits for committed UI refresh");
        f.domain.quests.flushPlayer(p.id);
        check(p.open==top && glints(top.getItem(slot)),"committed completion gains glint automatically without reopening");
        check(spec(top,slot).material()==org.bukkit.Material.LIME_DYE && lore(top,slot).contains("Completed"),"existing completed material and status retained");
        check(((DailyQuestInventoryHolder)top.getHolder()).target(slot).orElseThrow().equals("quest_0"),"glint preserves technical quest identity");
        var completed=top.getItem(slot);int saves=f.domain.repository.saves;
        f.now=f.now.plusSeconds(1);f.tick();
        check(top.getItem(slot)==completed && glints(completed) && f.domain.repository.saves==saves,"clock tick preserves glint and completed item without persistence");
        f.messages.localization().localeChanged(p.player,Locale.GERMANY);
        check(glints(top.getItem(slot)) && lore(top,slot).contains("Abgeschlossen"),"German locale rerender retains completion glint");
        f.menu.open(p.player,0);check(glints(p.open.getItem(slot)),"reopening retains completed glint");
        check(f.domain.economy.getCoins(p.id).orElseThrow()==10,"presentation adds no extra reward");
        f.menu.shutdown();

        var pending=fixture(1);var q=pending.player();pending.menu.startUpdates();pending.menu.open(q.player,0);
        pending.domain.economy.setCoins(q.id,Long.MAX_VALUE);
        pending.domain.quests.addProgress(q.id,QuestProgressKey.of("playtime:minute"),100);pending.domain.quests.flushPlayer(q.id);
        check(!glints(q.open.getItem(slot)) && spec(q.open,slot).material()==org.bukkit.Material.GOLD_INGOT && lore(q.open,slot).contains("Reward Pending"),"pending reward is distinct from completed and has no completion glint");
        pending.domain.economy.setCoins(q.id,0);pending.domain.quests.retryPendingRewards(q.id);pending.domain.quests.flushPlayer(q.id);
        check(glints(q.open.getItem(slot)) && pending.domain.economy.getCoins(q.id).orElseThrow()==10,"successful pending retry gains glint after commit and pays once");
        pending.menu.shutdown();
    }
    private static boolean glints(org.bukkit.inventory.ItemStack item){
        var meta=item.getItemMeta();return meta.hasEnchantmentGlintOverride() && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
    }
    private static QuestMenuFixture fixture(int count)throws Exception{
        var f=new QuestMenuFixture(DailyQuestMenu.DEFAULT_LAYOUT);var defs=new ArrayList<QuestDefinition>();
        for(int i=0;i<count;i++)defs.add(QuestCompletionFixture.definition("quest_"+i,"playtime:minute",100,10));
        f.domain.registry.replaceAll(defs);return f;
    }
    @SuppressWarnings("unchecked") private static void staleCases()throws Exception{
        for(String kind:List.of("closed","replaced","unbound","copied-holder","foreign-holder","wrong-owner","lost-permission","offline","unloaded","quit")){
            var f=fixture(1);var p=f.player();f.menu.startUpdates();f.menu.open(p.player,0);var old=p.open;
            var mapField=DailyQuestMenu.class.getDeclaredField("active");mapField.setAccessible(true);var active=(Map<UUID,Inventory>)mapField.get(f.menu);
            Inventory candidate=old;
            switch(kind){
                case "closed" -> p.player.closeInventory();
                case "replaced" -> p.open=p.bottom;
                case "unbound" -> {var h=new DailyQuestInventoryHolder(p.id,0);h.recordAction(34,RESET_INFO);h.finishRender();candidate=inventory(h,36);p.open=candidate;active.put(p.id,candidate);}
                case "copied-holder" -> {candidate=inventory(old.getHolder(),36);p.open=candidate;active.put(p.id,candidate);}
                case "foreign-holder" -> {candidate=inventory(null,36);p.open=candidate;active.put(p.id,candidate);}
                case "wrong-owner" -> {var h=new DailyQuestInventoryHolder(UUID.randomUUID(),0);candidate=inventory(h,36);h.bind(candidate);h.recordAction(34,RESET_INFO);h.finishRender();p.open=candidate;active.put(p.id,candidate);}
                case "lost-permission" -> p.permission=false;
                case "offline" -> p.online=false;
                case "unloaded" -> f.domain.players.unloadPlayer(p.id);
                case "quit" -> f.listener.onPlayerQuit(new PlayerQuitEvent(p.player,net.kyori.adventure.text.Component.empty(),PlayerQuitEvent.QuitReason.DISCONNECTED));
            }
            var before=candidate.getItem(34);f.now=f.now.plusSeconds(2);f.tick();
            check(candidate.getItem(34)==before,"invalid view receives no clock write: "+kind);
            check(f.runningTimers()==0 && f.menu.activeCount()==0,"invalid view/task pruned: "+kind);
            f.menu.shutdown();
        }
    }
    private record ItemSnapshot(List<org.bukkit.inventory.ItemStack> items){
        ItemSnapshot(Inventory top,int ignored){this(java.util.stream.IntStream.range(0,top.getSize()).mapToObj(top::getItem).collect(java.util.stream.Collectors.toList()));}
        boolean sameExceptClock(Inventory top,int clock){for(int i=0;i<items.size();i++)if(i!=clock && top.getItem(i)!=items.get(i))return false;return true;}
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

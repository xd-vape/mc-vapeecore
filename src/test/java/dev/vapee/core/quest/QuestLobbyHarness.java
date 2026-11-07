package dev.vapee.core.quest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import java.util.*;

public final class QuestLobbyHarness {
    private static int checks;
    public static void main(String[]args){actions();encounters();epochs();bounded();lifecycle();System.out.println("QuestLobbyHarness passed "+checks+" checks.");}
    private static void actions(){
        try(var f=new QuestLobbyTestSupport()){
            var a=f.actor();var b=f.actor();f.drain();
            check(f.queries==0,"empty bundled catalog performs no spatial work");
            for(String key:List.of("spawn_arrived","warp_visit","warp_unique","navigator_open","quests_open","settings_open","friends_open","visibility_open","coins_check","rank_check","ranks_check","ping_self","ping_other"))f.define(key,"lobby:"+key,3);
            f.assign(a,f.hud.domain.registry.snapshot().stream().map(QuestDefinition::id).toArray(String[]::new));
            check(QuestLobbyProducer.warpIdentity("x".repeat(10000)).length()==69
                    && !QuestLobbyProducer.warpIdentity("one").equals(QuestLobbyProducer.warpIdentity("two")),"technical Warp IDs use stable bounded digests even for long valid IDs");
            f.owners.warp(a.player,"one",false);check(f.value(a,"warp_unique")==1&&f.value(a,"warp_visit")==0,"ordinary warp has unique only");
            f.owners.warp(a.player,"one",true);f.owners.warp(a.player,"two",true);f.owners.warp(a.player,"three",true);
            check(f.value(a,"warp_unique")==3&&f.value(a,"warp_visit")==3,"navigator success and distinct canonical identity");
            for(String key:List.of("spawn_arrived","navigator_open","quests_open","settings_open","friends_open","visibility_open","coins_check","rank_check","ranks_check","ping_self","ping_other")){
                f.producer.action(a.player,"lobby:"+key);check(f.value(a,key)==1,"success key routed through central engine: "+key);
            }
            f.hud.pump();check(!a.bars.isEmpty()&&a.clientExp>0,"lobby signals reach generic HUD");
            f.available=false;f.producer.action(a.player,"lobby:coins_check");f.available=true;check(f.value(a,"coins_check")==1,"retired domain owner has no authority");
            a.online=false;f.producer.action(a.player,"lobby:coins_check");a.online=true;check(f.value(a,"coins_check")==1,"offline sender rejected");
            var old=a.player;var replacement=f.hud.actor(a.id);f.producer.seed(replacement.player);f.producer.action(old,"lobby:coins_check");check(f.value(replacement,"coins_check")==1,"stale player instance rejected");
            f.hud.domain.players.unloadPlayer(a.id);f.producer.action(replacement.player,"lobby:coins_check");check(true,"unloaded sender rejected without exception");
        }
    }
    private static void encounters(){
        try(var f=new QuestLobbyTestSupport()){
            var a=f.actor();var b=f.actor();var c=f.actor();f.define("meet","social:meet_unique",2);f.define("also","social:meet_unique",3);f.assign(a,"meet","also");f.drain();
            check(f.value(a,"meet")==2&&f.value(a,"also")==2,"two nearby visible players, same-key assignments independent");
            f.encounter(a);check(f.value(a,"also")==2,"repeated encounter cannot recount");
            f.producer.onQuit(new PlayerQuitEvent(b.player,(net.kyori.adventure.text.Component)null));f.hud.online.remove(b.id);
            var again=f.hud.actor(b.id);f.producer.seed(again.player);f.encounter(a);check(f.value(a,"also")==2,"rejoin with same UUID cannot recount");
            var d=f.actor();f.denied.add(d.id);f.encounter(a);check(f.value(a,"also")==2,"viewer-specific visibility denies new target");
            f.denied.remove(d.id);d.at=new Location(QuestHudTestSupport.WORLD,6.001,64,0);f.encounter(a);check(f.value(a,"also")==2,"outside six-block spherical radius rejected");
            d.at=new Location(QuestHudTestSupport.WORLD,6,64,0);d.context=false;f.encounter(a);check(f.value(a,"also")==2,"target BUILD/Activity eligibility excluded");
            d.context=true;a.context=false;f.encounter(a);check(f.value(a,"also")==2,"viewer BUILD/Activity eligibility excluded");a.context=true;
            f.encounter(a);check(f.value(a,"also")==3&&f.hud.domain.players.getPlayer(a.id).orElseThrow().getWallet().getCoins()==14,"inclusive radius; each configured completion pays once");
            f.assign(a,"also");f.encounter(a);check(f.value(a,"also")>0,"new assignment starts fresh encounter identities");
        }
    }
    private static void epochs(){
        try(var f=new QuestLobbyTestSupport()){
            var a=f.actor();var b=f.actor();f.define("meet","social:meet_unique",10);f.assign(a,"meet");
            var before=f.hud.domain.registry.snapshot();f.hud.domain.registry.replaceAll(before);f.drain();check(f.value(a,"meet")==0,"queued pre-reload context rejected");
            f.encounter(a);check(f.value(a,"meet")==1,"current catalog context accepted");
            f.hud.tick+=20;f.producer.afterStateChange(a.id);f.hud.domain.quests.replaceAssignments(a.id,List.of("meet"));f.drain();
            check(f.value(a,"meet")==1,"assignment listener replaces pending work with current context");
            var old=a;f.hud.tick+=20;f.producer.afterStateChange(a.id);var newSession=f.hud.actor(a.id);f.producer.seed(newSession.player);
            f.producer.onQuit(new PlayerQuitEvent(old.player,(net.kyori.adventure.text.Component)null));f.drain();check(f.value(newSession,"meet")==1,"stale quit preserves replacement session, persisted target still deduplicates");
            f.hud.tick+=20;f.producer.afterStateChange(newSession.id);f.available=false;f.drain();check(f.value(newSession,"meet")==1,"queued callback loses authority when owners unavailable");
        }
    }
    private static void bounded(){
        try(var f=new QuestLobbyTestSupport()){
            var a=f.actor();var b=f.actor();f.define("meet","social:meet_unique",100);f.assign(a,"meet");
            var dense=new DensePlayers(b.player);f.supplied=dense;f.drain();int before=f.queries;int visited=dense.visited;
            var move=new PlayerMoveEvent(a.player,a.at,a.at.clone().add(1,0,0));
            for(int i=0;i<10000;i++)f.producer.onMove(move);f.drain();check(f.queries==before,"movement burst within interval performs no spatial queries");
            f.encounter(a);check(dense.visited-visited==32,"dense collection validates at most 32 local candidates");check(f.queries==before+1&&f.value(a,"meet")==1,"one bounded spatial query after interval, dense duplicates never farm");
            f.schedulingFailure=true;f.hud.tick+=20;f.producer.afterStateChange(a.id);check(!f.hud.domain.logs.isEmpty(),"scheduler refusal is isolated and diagnosed");f.schedulingFailure=false;
        }
    }
    private static void lifecycle(){
        try(var f=new QuestLobbyTestSupport()){
            var a=f.actor();f.define("one","lobby:coins_check",4);f.assign(a,"one");int[]foreign={0};f.owners.coins.addSuccessListener(p->foreign[0]++);
            f.producer.close();check(f.producer.released()&&f.callbacks.isEmpty(),"close clears queued task and all leases");
            check(QuestLobbyTestSupport.Owners.listeners(f.owners.coins,"successListeners",java.util.function.Consumer.class).size()==1,"foreign observer retained");
            f.bind();f.producer.seed(a.player);f.producer.action(a.player,"lobby:coins_check");check(f.value(a,"one")==1,"rebind uses fresh online session");
            boolean duplicate=false;try{f.bind();}catch(IllegalStateException expected){duplicate=true;}check(duplicate,"duplicate acquisition rejected");
            boolean thread=false;f.primary=false;try{f.producer.action(a.player,"lobby:coins_check");}catch(IllegalStateException expected){thread=true;}f.primary=true;check(thread,"async callers cannot mutate quest state");
        }
    }
    private static final class DensePlayers extends AbstractList<Player> implements RandomAccess {
        final Player player;int visited;DensePlayers(Player player){this.player=player;}
        public int size(){return 10000;}public Player get(int index){visited++;return player;}
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

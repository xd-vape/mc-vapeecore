package dev.vapee.core.lobby.experience.glide;

import org.bukkit.*;
import org.bukkit.event.player.*;
import net.kyori.adventure.text.Component;
import java.util.*;

public final class LobbyGlideHarness {
    private static int checks;
    public static void main(String[] args)throws Exception {
        activationLanding();eligibility();foreignOwnership();staleWork();lifecycle();cleanupFailure();
        System.out.println("LobbyGlideHarness passed "+checks+" checks.");
    }
    private static void activationLanding()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Normal");String inventory=p.inventoryBytes();
        f.glide.reconcile(p.player);
        check(p.allow&&!p.flying&&f.glide.isOwned(p.id),"eligible grounded player armed without native flying");
        p.ground=false;var event=f.flight(p);
        check(event.isCancelled()&&!p.allow&&!p.flying&&p.launches==1,"owned toggle cancelled and one launch consumed");
        check(p.velocity.getY()>0.8&&p.velocity.getY()<1&&p.velocity.getZ()>0.8&&p.velocity.getZ()<1,"restrained stronger upward and forward impulse");
        check(!p.gliding,"glide starts after flight toggle/velocity tick");f.tick();
        check(p.gliding&&f.glide.isActive(p.id),"single deferred launch enters owned glide");
        check(f.stop(p).isCancelled()&&p.gliding,"only airborne owned no-equipment stop is suppressed");
        for(int i=0;i<8;i++)check(f.flight(p).isCancelled()&&p.launches==1&&!p.flying,"airborne spam cannot stack launches");
        int reads=f.profileReads;
        var turn=new PlayerMoveEvent(p.player,new Location(p.world,0,64,0),new Location(p.world,0,64,0,90,0));f.listener.onMove(turn);
        check(f.profileReads==reads,"rotation-only move does no eligibility work");
        p.ground=true;check(!f.stop(p).isCancelled(),"landing always allows gliding to stop");f.flush();
        check(!p.gliding&&p.allow&&!p.flying&&!f.glide.isActive(p.id),"natural landing rearms double jump");
        p.ground=false;f.flight(p);f.tick();check(p.launches==2&&p.gliding,"second grounded activation works");
        p.water=true;check(!f.stop(p).isCancelled(),"water terminates instead of forcing gliding");f.flush();
        check(!p.gliding&&!p.allow,"no airborne rearm in water");p.water=false;p.ground=true;f.glide.moved(p.player,true);
        check(p.allow&&!p.gliding,"grounded movement fallback rearms after interrupted glide");
        f.glide.release(p.player);check(!p.allow&&!f.glide.isOwned(p.id),"owned armed flag restored on release");
        check(p.inventoryBytes().equals(inventory)&&p.inventoryReads==0&&p.inventoryWrites==0,"exact armor/storage/offhand/cursor snapshot unchanged and no inventory access");
        check(p.messages==0,"successful movement is silent");
    }
    private static void eligibility()throws Exception {
        for(String reason:List.of("world","profile","offline","build","creative","spectator","fly")){
            var f=new LobbyGlideFixture();var p=f.user(reason);
            switch(reason){
                case "world"->p.world=f.other;
                case "profile"->f.players.unloadPlayer(p.id);
                case "offline"->p.onlineState=false;
                case "build"->{f.permitInventory=true;f.states.enterBuildMode(p.player);p.mode=GameMode.ADVENTURE;p.allow=false;f.flush();f.permitInventory=false;}
                case "creative"->{p.mode=GameMode.CREATIVE;p.allow=true;}
                case "spectator"->{p.mode=GameMode.SPECTATOR;p.allow=true;}
                case "fly"->{f.utility.toggleFlight(p.player);f.flush();}
            }
            boolean before=p.allow;f.glide.reconcile(p.player);
            check(!f.glide.isOwned(p.id)&&p.allow==before,"eligibility refuses "+reason);
            check(!f.flight(p).isCancelled()&&p.launches==0&&!p.gliding,"unowned toggle untouched "+reason);
        }
        var f=new LobbyGlideFixture();var p=f.user("Falling");p.ground=false;f.glide.reconcile(p.player);
        check(!p.allow&&f.glide.isOwned(p.id),"joining midair waits for ground without granting flight");
        f.glide.moved(p.player,true);p.ground=true;f.tick();check(p.allow,"deferred final movement observes post-event grounded status");
    }
    private static void foreignOwnership()throws Exception {
        for(String flag:List.of("allow","flying","gliding")){
            var f=new LobbyGlideFixture();var p=f.user("Foreign");
            p.allow=flag.equals("allow");p.flying=flag.equals("flying");p.gliding=flag.equals("gliding");
            f.glide.reconcile(p.player);f.glide.release(p.player);f.glide.shutdown();
            check(!f.glide.isOwned(p.id)&&p.allow==flag.equals("allow")&&p.flying==flag.equals("flying")&&p.gliding==flag.equals("gliding"),"pre-existing foreign "+flag+" never adopted or disabled");
        }
        var f=new LobbyGlideFixture();var p=f.user("NewOwner");f.active(p);p.allow=true;
        f.glide.release(p.player);check(p.allow&&!p.gliding,"foreign allowFlight established during glide is preserved");
    }
    private static void staleWork()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Generation");f.glide.reconcile(p.player);long old=f.glide.generation(p.id);
        f.glide.release(p.player);f.glide.reconcile(p.player);long current=f.glide.generation(p.id);
        f.glide.release(p.player,old);
        check(current!=old&&f.glide.isOwned(p.id)&&f.glide.generation(p.id)==current&&p.allow,"stale cleanup cannot release a newer ownership generation");
        p.ground=false;f.flight(p);var launch=f.tasks.getLast();f.glide.release(p.player);
        f.utility.toggleFlight(p.player);launch.action.run();f.flush();
        check(f.utility.hasManagedFlight(p.id)&&p.allow&&!p.gliding,"stale launch cannot overwrite new /fly owner");
        f.utility.toggleFlight(p.player);f.flush();p.ground=true;f.glide.reconcile(p.player);
        f.glide.deferReconcile(p.id);var delayed=f.tasks.getLast();f.glide.release(p.player);delayed.action.run();
        check(!f.glide.isOwned(p.id)&&!p.allow,"cancelled old reconciliation cannot reacquire ownership");
    }
    private static void lifecycle()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Lifecycle");String inventory=p.inventoryBytes();f.active(p);
        p.world=f.other;var world=new PlayerChangedWorldEvent(p.player,f.lobby);f.listener.beforeWorldChange(world);f.listener.afterWorldChange(world);f.flush();
        check(!f.glide.isOwned(p.id)&&!p.gliding&&!p.allow,"lobby world exit cleans active glide");
        p.world=f.lobby;world=new PlayerChangedWorldEvent(p.player,f.other);f.listener.afterWorldChange(world);p.ground=true;f.flush();check(p.allow,"return to lobby reconciles");
        f.configured=f.other;f.states.refreshNormalGameModes();f.flush();check(!f.glide.isOwned(p.id)&&!p.allow,"reload changing configured lobby world releases old world");f.configured=f.lobby;
        f.glide.reconcile(p.player);var mode=new PlayerGameModeChangeEvent(p.player,GameMode.CREATIVE);f.listener.onGameMode(mode);p.mode=GameMode.CREATIVE;p.allow=true;f.flush();
        check(p.allow&&!f.glide.isOwned(p.id)&&!f.flight(p).isCancelled(),"native mode change hands off before vanilla ownership");
        p.mode=GameMode.ADVENTURE;p.allow=false;f.listener.onGameMode(new PlayerGameModeChangeEvent(p.player,GameMode.ADVENTURE));f.flush();check(p.allow,"return from native mode rearms");
        f.glide.deferReconcile(p.id);var quit=new PlayerQuitEvent(p.player,Component.empty());f.listener.onQuit(quit);p.onlineState=false;f.listener.afterQuit(quit);f.flush();
        check(!f.glide.isOwned(p.id)&&f.glide.pendingCount()==0&&!p.allow,"quit clears owned and delayed state");
        check(inventory.equals(p.inventoryBytes())&&p.inventoryReads==0,"world/quit/mode cleanup never accesses armor or inventory");
        p.onlineState=true;f.listener.onJoin(new PlayerJoinEvent(p.player,Component.empty()));f.flush();check(p.allow,"rejoin starts fresh safe ownership");
        f.active(p);p.dead=true;
        var death=new org.bukkit.event.entity.PlayerDeathEvent(p.player,
                dev.vapee.core.quest.QuestCompletionFixture.proxy(org.bukkit.damage.DamageSource.class,(m,a)->dev.vapee.core.quest.QuestCompletionFixture.DEFAULT),
                new ArrayList<>(),0,Component.empty(),false);
        f.listener.onDeath(death);f.listener.afterDeath(death);f.flush();check(!p.gliding&&!p.allow&&!f.glide.isOwned(p.id),"death terminates and forgets active ownership");
        p.dead=false;p.ground=true;f.listener.onRespawn(new PlayerRespawnEvent(p.player,new Location(p.world,0,64,0),false));f.flush();
        check(p.allow&&!p.gliding,"respawn evaluates fresh state without restoring old glide");
        f.glide.shutdown();for(var task:List.copyOf(f.tasks))task.action.run();check(!p.allow&&f.glide.ownedCount()==0&&f.glide.pendingCount()==0,"disable cancels all pending work and releases states");
    }
    private static void cleanupFailure()throws Exception {
        var f=new LobbyGlideFixture();var bad=f.user("Bad");var good=f.user("Good");f.glide.reconcile(bad.player);f.glide.reconcile(good.player);bad.failRelease=true;
        f.glide.shutdown();check(!good.allow&&f.glide.ownedCount()==0&&f.glide.pendingCount()==0,"one failed cleanup does not prevent other players or registry cleanup");
        check(f.logs.stream().anyMatch(r->r.getMessage().contains(bad.id.toString())),"cleanup diagnostics include failing UUID");
    }
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}

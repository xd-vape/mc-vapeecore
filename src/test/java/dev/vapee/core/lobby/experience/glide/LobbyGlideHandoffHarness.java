package dev.vapee.core.lobby.experience.glide;

import dev.vapee.core.activity.*;
import dev.vapee.core.activity.location.*;
import dev.vapee.core.activity.player.ActivityParticipant;
import org.bukkit.GameMode;
import java.util.UUID;

/** Exercises real domain transitions, not direct calls to handoff callbacks. */
public final class LobbyGlideHandoffHarness {
    private static int checks;
    public static void main(String[] args)throws Exception {
        flight();build();activity(false);activity(true);activityClose();
        System.out.println("LobbyGlideHandoffHarness passed "+checks+" checks.");
    }
    private static void flight()throws Exception {
        for(boolean active:new boolean[]{false,true}){
            var f=new LobbyGlideFixture();var p=f.user("Fly");String inventory=p.inventoryBytes();
            if(active)f.active(p);else f.glide.reconcile(p.player);
            long old=f.glide.generation(p.id);
            check(f.utility.toggleFlight(p.player),"real Utility enables managed flight");
            check(!f.glide.isOwned(p.id)&&!p.gliding&&p.allow,"Glide releases before /fly establishes new flags");
            f.glide.release(p.player,old);f.flush();
            check(f.utility.hasManagedFlight(p.id)&&p.allow&&!p.gliding,"old cleanup cannot overwrite managed /fly");
            check(!f.flight(p).isCancelled(),"managed flight input is never intercepted");
            p.ground=true;check(!f.utility.toggleFlight(p.player),"real Utility disables managed flight");f.flush();
            check(p.allow&&!p.flying&&f.glide.isOwned(p.id),"disabling /fly rearms eligible grounded lobby");
            check(inventory.equals(p.inventoryBytes())&&p.inventoryReads==0,"/fly handoff introduces no equipment access");
        }
    }
    private static void build()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Build");f.active(p);
        f.states.addStateListener(new dev.vapee.core.lobby.player.LobbyPlayerStateListener(){
            public void beforeStateChange(org.bukkit.entity.Player player){
                check(!f.glide.isOwned(p.id)&&!p.gliding&&(p.mode==GameMode.CREATIVE||!p.allow),"release precedes BUILD's state and inventory mutations");
            }
            public void afterStateChange(UUID id){}
        });
        f.permitInventory=true;f.states.enterBuildMode(p.player);f.flush();
        check(f.states.isBuildMode(p.id)&&p.mode==GameMode.CREATIVE&&p.allow&&!f.glide.isOwned(p.id),"BUILD owns native flight and stays unarmed");
        check(!f.flight(p).isCancelled(),"BUILD native flight toggle untouched");
        // Domain's own existing inventory clear is permitted only around these transitions.
        p.ground=true;f.states.exitBuildMode(p.player);f.flush();f.permitInventory=false;
        check(!f.states.isBuildMode(p.id)&&p.mode==GameMode.ADVENTURE&&p.allow&&f.glide.isOwned(p.id),"BUILD exit reconciles NORMAL on ground");
    }
    private static ActivitySession session(LobbyGlideFixture f,LobbyGlideFixture.User p,boolean fail){
        check(f.activities.registerActivityType(new ActivityType(){
            public String getKey(){return "test";}public int getMinParticipants(){return 1;}public int getMaxParticipants(){return 4;}
            public ActivitySession createSession(UUID id,ActivityVenue venue){return new ActivitySession(id,"test",venue){
                protected void onParticipantJoined(ActivityParticipant participant){
                    check(!f.glide.isOwned(p.id)&&!p.allow&&!p.gliding,"generic Activity hook receives released movement ownership");
                    check(f.activities.isParticipating(p.id),"membership published before Activity hook");
                    if(fail)throw new IllegalStateException("synthetic failed join");
                }
            };}
        })==ActivityResult.SUCCESS,"generic synthetic activity registered");
        check(f.activities.registerVenue(new ActivityVenue("test","test",new ActivityArea("lobby",-5,0,-5,5,100,5),new ActivityPosition("lobby",0,64,0,0,0)))==ActivityResult.SUCCESS,"generic venue registered");
        return f.activities.createSession("test","test").session().orElseThrow();
    }
    private static void activity(boolean failedJoin)throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Activity");String inventory=p.inventoryBytes();f.active(p);
        var session=session(f,p,failedJoin);
        check(f.activities.joinSession(p.player,session.getSessionId())==(failedJoin?ActivityResult.HOOK_FAILED:ActivityResult.SUCCESS),"real Activity join result authoritative");
        p.ground=true;f.flush();
        if(failedJoin){check(!f.activities.isParticipating(p.id)&&p.allow&&f.glide.isOwned(p.id),"failed join rollback reevaluates actual NORMAL authority");}
        else {
            check(!f.glide.isOwned(p.id)&&!p.gliding&&!p.allow,"Activity membership prevents rearm");
            f.glide.reconcile(p.player);check(!f.flight(p).isCancelled(),"Activity toggle not hijacked");
            check(f.activities.activateSession(session.getSessionId())==ActivityResult.SUCCESS,"Activity activation succeeds without Glide");
            check(f.activities.leaveCurrentSession(p.id,ActivityLeaveReason.VOLUNTARY)==ActivityResult.SUCCESS,"actual Activity leave succeeds");f.flush();
            check(p.allow&&!p.gliding&&f.glide.isOwned(p.id),"Activity leave rearms eligible NORMAL owner");
        }
        check(inventory.equals(p.inventoryBytes())&&p.inventoryReads==0,"generic Activity handoff changes no equipment");
    }
    private static void activityClose()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Close");f.glide.reconcile(p.player);var session=session(f,p,false);
        f.activities.joinSession(p.player,session.getSessionId());f.flush();f.activities.closeSession(session.getSessionId(),ActivityLeaveReason.SESSION_CLOSED);f.flush();
        check(!f.activities.isParticipating(p.id)&&p.allow&&f.glide.isOwned(p.id),"session-wide close notifies returned participants");
        f.utility.removeFlightListener(f.glide);f.states.removeStateListener(f.glide);f.activities.removeMembershipListener(f.glide);f.glide.shutdown();
        f.utility.toggleFlight(p.player);check(p.allow&&f.glide.pendingCount()==0,"detached lifecycle hooks schedule no Glide work");
    }
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}



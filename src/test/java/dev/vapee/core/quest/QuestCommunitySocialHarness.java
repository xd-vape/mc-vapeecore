package dev.vapee.core.quest;

import dev.vapee.core.friend.FriendResult;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.privatemessage.PrivateMessageResult;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.BiConsumer;

/** Exercises the real service entry points, never a command-name-to-quest mapping. */
public final class QuestCommunitySocialHarness {
    private static int checks;
    public static void main(String[] args) {
        messages();friends();teleports();rewardsAndHud();
        System.out.println("QuestCommunitySocialHarness passed " + checks + " checks.");
    }
    private static void definitions(CommunityTestSupport f) {
        for(String key:List.of("pm_sent","pm_reply","friend_request","friend_accepted","tpa_visit","tpa_host"))f.define(key,"social:"+key,50,7);
    }
    private static void messages() {
        try(var f=new CommunityTestSupport()) {
            var a=f.actor("Alpha");var b=f.actor("Beta");definitions(f);f.assign(a,"pm_sent","pm_reply");f.assign(b,"pm_sent","pm_reply");
            check(f.pm.send(a.player,b.player,"Hello privately")==PrivateMessageResult.SUCCESS,"actual PM delivered");
            check(f.progress(a,"pm_sent")==1&&f.progress(a,"pm_reply")==0,"send key once, reply never inferred from text");
            check(f.pm.reply(b.player,"A genuine reply")==PrivateMessageResult.SUCCESS,"actual reply delivered");
            check(f.progress(b,"pm_sent")==1&&f.progress(b,"pm_reply")==1,"one reply earns each technical key once");
            check(!a.received.isEmpty()&&!b.received.isEmpty(),"existing PM delivery preserved");
            f.pm.addDeliveryListener((sender,recipient,reply)->{throw new IllegalStateException("observer refused");});
            check(f.pm.reply(a.player,"Reply despite observer failure")==PrivateMessageResult.SUCCESS,"observer cannot roll back delivery or conversation");
            check(f.progress(a,"pm_sent")==2&&f.progress(a,"pm_reply")==1&&!f.logs.isEmpty(),"failed observer diagnosed without replay");
            long sent=f.progress(a,"pm_sent"),reply=f.progress(a,"pm_reply");
            f.muted.add(a.id);check(f.pm.send(a.player,b.player,"Muted")==PrivateMessageResult.SENDER_MUTED,"mute domain guard");f.muted.clear();
            f.social.settings.setPrivateMessagesEnabled(b.id,false);check(f.pm.send(a.player,b.player,"Disabled")==PrivateMessageResult.RECIPIENT_DISABLED,"recipient setting guard");f.social.settings.setPrivateMessagesEnabled(b.id,true);
            b.online=false;check(f.pm.reply(a.player,"Offline")==PrivateMessageResult.TARGET_OFFLINE,"offline reply guard");b.online=true;
            check(f.pm.reply(a.player,"No partner")==PrivateMessageResult.NO_REPLY_TARGET,"offline cleanup keeps prior reply behavior");
            check(f.pm.send(a.player,a.player,"Self")==PrivateMessageResult.CANNOT_MESSAGE_SELF,"self guard");
            f.social.social.ignore(a.id,b.id);check(f.pm.send(a.player,b.player,"Ignore")==PrivateMessageResult.SENDER_IGNORES_RECIPIENT,"sender Ignore guard");f.social.social.unignore(a.id,b.id);
            f.social.social.ignore(b.id,a.id);check(f.pm.send(a.player,b.player,"Ignore")==PrivateMessageResult.RECIPIENT_IGNORES_SENDER,"recipient Ignore guard");f.social.social.unignore(b.id,a.id);
            boolean blank=false;try{f.pm.send(a.player,b.player,"   ");}catch(IllegalArgumentException expected){blank=true;}check(blank,"blank rejected at existing domain input guard");
            var previous=f.pm.getState();f.pm.applyState(new dev.vapee.core.privatemessage.PrivateMessageService.RuntimeState(false,previous.outgoingText(),previous.incomingText()));
            check(f.pm.send(a.player,b.player,"Disabled feature")==PrivateMessageResult.FEATURE_DISABLED,"disabled PM domain guard");f.pm.applyState(previous);
            Object delivery=ModuleRuntimeFixture.get(f.pm,"delivery");
            ModuleRuntimeFixture.set(f.pm,"delivery",(BiConsumer<Player,Component>)(player,text)->{if(player==b.player)throw new IllegalStateException("failed recipient delivery");});
            boolean failed=false;try{f.pm.send(a.player,b.player,"Fails on receipt");}catch(IllegalStateException expected){failed=true;}
            check(failed,"failed recipient delivery propagates existing failure");ModuleRuntimeFixture.set(f.pm,"delivery",delivery);
            check(f.progress(a,"pm_sent")==sent&&f.progress(a,"pm_reply")==reply,"all failure paths produce no progress");
            var profile=f.social.players.getPlayer(a.id).orElseThrow();f.social.players.unloadPlayer(a.id);
            check(f.pm.send(a.player,b.player,"Unloaded")==PrivateMessageResult.SENDER_NOT_LOADED,"unloaded sender guard");
            check(profile.getQuestState().snapshot().get("pm_sent").progress()==sent,"unloaded sender cannot earn progress");
        }
    }
    private static void friends() {
        try(var f=new CommunityTestSupport()) {
            var a=f.actor("Alpha");var b=f.actor("Beta");var c=f.actor("Gamma");definitions(f);
            for(var actor:List.of(a,b,c))f.assign(actor,"friend_request","friend_accepted");
            check(f.friends.sendRequest(a.id,b.id)==FriendResult.SUCCESS&&f.friendSaves==1,"request published after actual save");
            check(f.progress(a,"friend_request")==1&&f.progress(b,"friend_request")==0,"only outgoing requester credited");
            check(f.friends.sendRequest(a.id,b.id)==FriendResult.REQUEST_ALREADY_SENT&&f.progress(a,"friend_request")==1&&f.friendSaves==1,"duplicate request neither saves nor earns");
            check(f.friends.sendRequest(b.id,a.id)==FriendResult.AUTO_ACCEPTED,"counter-request uses existing auto accept");
            check(f.progress(a,"friend_accepted")==1&&f.progress(b,"friend_accepted")==1&&f.progress(b,"friend_request")==0,"auto friendship credits both, never a new request");
            check(f.friends.acceptRequest(b.id,a.id)==FriendResult.ALREADY_FRIENDS&&f.progress(b,"friend_accepted")==1,"duplicate accept inert");
            f.friends.removeFriend(a.id,b.id);check(f.progress(a,"friend_accepted")==1,"remove produces no positive success signal");
            f.friends.sendRequest(a.id,b.id);f.friends.acceptRequest(b.id,a.id);
            check(f.progress(a,"friend_accepted")==2&&f.progress(b,"friend_accepted")==2,"explicit accept credits both once");
            f.friends.sendRequest(a.id,c.id);f.friends.denyRequest(c.id,a.id);
            f.friends.sendRequest(c.id,b.id);f.friends.cancelRequest(c.id,b.id);
            check(f.progress(c,"friend_accepted")==0&&f.progress(b,"friend_accepted")==2,"deny and cancel never friendship progress");
            long requests=f.progress(c,"friend_request");f.friendSaveFailure=true;
            boolean failed=false;try{f.friends.sendRequest(c.id,a.id);}catch(IllegalStateException expected){failed=true;}
            check(failed&&f.progress(c,"friend_request")==requests&&f.friends.getOutgoingRequests(c.id).isEmpty(),"failed request persistence publishes nothing");
            f.friendSaveFailure=false;f.friends.sendRequest(c.id,a.id);f.friendSaveFailure=true;
            failed=false;try{f.friends.acceptRequest(a.id,c.id);}catch(IllegalStateException expected){failed=true;}
            check(failed&&f.progress(c,"friend_accepted")==0&&f.friends.getIncomingRequests(a.id).size()==1,"failed accept persistence keeps request, no credit");
            f.friendSaveFailure=false;f.friends.addSuccessListener((sender,recipient,friendship)->{throw new IllegalStateException("observer refused");});
            check(f.friends.acceptRequest(a.id,c.id)==FriendResult.SUCCESS&&f.progress(c,"friend_accepted")==1,"post-save observer failure cannot roll back friendship");
        }
    }
    private static void teleports() {
        for(boolean here:List.of(false,true))try(var f=new CommunityTestSupport()) {
            var a=f.actor("Alpha");var b=f.actor("Beta");definitions(f);
            f.assign(a,"tpa_visit","tpa_host");f.assign(b,"tpa_visit","tpa_host");
            f.social.command(a,here?"tpahere":"tpa","Beta");String nonce=b.accept();
            check(f.progress(a,"tpa_visit")==0&&f.progress(b,"tpa_host")==0,"send alone no credit "+here);
            f.social.requests.addSuccessListener((requester,recipient,direction)->{throw new IllegalStateException("observer refused");});
            f.social.command(b,"tpaccept",nonce);
            check((here?b.teleports==1&&a.teleports==0:a.teleports==1&&b.teleports==0),"actual stored request moves correct player "+here);
            check(f.progress(a,"tpa_visit")== (here?0:1)&&f.progress(b,"tpa_visit")==0,"visit limited to regular requesting visitor "+here);
            check(f.progress(b,"tpa_host")==1&&f.progress(a,"tpa_host")==0,"host credits explicit consenting request recipient "+here);
            check(b.last().contains("completed")||b.last().contains("success"),"success feedback survives observer failure "+here);
            f.social.command(b,"tpaccept",nonce);check(f.progress(b,"tpa_host")==1&&a.teleports+b.teleports==1,"consumed nonce never replays progress "+here);
        }
        for(String boundary:List.of("deny","expire","cancel","fail","throw","stale","ignore","build","unsafe","world","quit"))try(var f=new CommunityTestSupport()) {
            var a=f.actor("Alpha");var b=f.actor("Beta");definitions(f);f.assign(a,"tpa_visit");f.assign(b,"tpa_host");
            f.social.command(a,"tpa","Beta");String nonce=b.accept();
            switch(boundary){
                case "deny"->f.social.command(b,"tpdeny",nonce);
                case "expire"->f.seconds(60);
                case "cancel"->f.social.command(a,"tpcancel");
                case "fail"->a.teleportSuccess=false;
                case "throw"->a.throwTeleport=true;
                case "stale"->nonce=a.id+":"+UUID.randomUUID();
                case "ignore"->f.social.social.ignore(a.id,b.id);
                case "build"->f.social.states.enterBuildMode(a.player);
                case "unsafe"->b.location.setY(-100);
                case "world"->b.location.setWorld(f.social.other.world);
                case "quit"->{a.online=false;f.social.online.remove(a.id);}
            }
            f.social.command(b,"tpaccept",nonce);check(f.progress(a,"tpa_visit")==0&&f.progress(b,"tpa_host")==0,"negative TPA boundary "+boundary);
        }
    }
    private static void rewardsAndHud() {
        try(var f=new CommunityTestSupport();var hudActors=new QuestHudTestSupport.Fixture()) {
            var a=f.actor("Alpha");var b=f.actor("Beta");f.define("one","social:pm_sent",1,7);f.define("two","social:pm_sent",2,11);f.assign(a,"one","two");
            var actor=hudActors.actor(a.id);
            var hud=new QuestProgressHud(f.social.players,f.quests,f.definitions,f.social.messages,
                    id->id.equals(a.id)?actor.player:null,id->true,()->100,()->true,f.social.logger);
            f.quests.addProgressListener(hud);
            check(f.pm.send(a.player,b.player,"First actual PM")==PrivateMessageResult.SUCCESS,"PM triggers existing reporter path");hud.tick();
            check(actor.bar().contains("one")&&actor.bar().contains("100%")&&actor.clientExp==1F&&f.coins(a)==7,"central completion observer drives existing HUD and reward");
            f.pm.send(a.player,b.player,"Second actual PM");f.pm.send(a.player,b.player,"Third actual PM");
            check(f.progress(a,"one")==1&&f.progress(a,"two")==2&&f.coins(a)==18&&f.feedback.size()==2,"same-key quests cap independently and never double rewards");
            check(actor.mutations==0,"community integration does not mutate server XP");
            hud.close();f.quests.removeProgressListener(hud);
        }
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

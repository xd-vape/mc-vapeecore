package dev.vapee.core.quest;

import dev.vapee.core.chat.ChatModule;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.moderation.*;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.privatemessage.PrivateMessageModule;
import dev.vapee.core.utility.UtilityModule;
import dev.vapee.core.utility.tpa.TeleportRequestRegistration;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitScheduler;
import java.time.Clock;
import java.util.*;

/** Actual QuestModule acquisition, subscriptions, HandlerLists and retained cleanup/retry ownership. */
public final class QuestCommunityModuleHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        lifecycle();acquisitionFailures();cleanupFailures();partialCleanupRetry();yamlCompatibility();
        System.out.println("QuestCommunityModuleHarness passed " + checks + " checks.");
    }
    static final class RefusingSet<T> extends LinkedHashSet<T> {
        boolean addAfter, removeBefore;
        public boolean add(T value) { boolean changed=super.add(value);if(addAfter)throw new IllegalStateException("add after effect");return changed; }
        public boolean remove(Object value) { if(removeBefore)throw new IllegalStateException("remove before effect");return super.remove(value); }
    }
    static final class Owners {
        final ChatModule chat;
        final ModerationModule moderation;
        final PrivateMessageModule pm;
        final FriendModule friends;
        final UtilityModule utility;
        Owners(CommunityTestSupport f) {
            chat=ModuleRuntimeFixture.dependency(ChatModule.class,Map.of("chatService",f.chat));
            moderation=ModuleRuntimeFixture.dependency(ModerationModule.class,Map.of("projection",new ModerationMuteProjection(Clock.systemUTC())));
            pm=ModuleRuntimeFixture.dependency(PrivateMessageModule.class,Map.of("privateMessageService",f.pm));
            friends=ModuleRuntimeFixture.dependency(FriendModule.class,Map.of("friendService",f.friends,"active",true));
            var registration=ModuleRuntimeFixture.dependency(TeleportRequestRegistration.class,Map.of("requests",f.social.requests,"enabled",true));
            utility=ModuleRuntimeFixture.dependency(UtilityModule.class,Map.of("utilityService",f.social.utility,"teleportRequests",registration));
        }
        void bind(QuestModule module){module.startCommunity(chat,moderation,pm,friends,utility);}
    }
    static QuestCommunityProducer producer(QuestLifecycleFixture f) {
        return (QuestCommunityProducer)ModuleRuntimeFixture.get(ModuleRuntimeFixture.get(f.module,"runtime"),"community");
    }
    static long handlers(QuestLifecycleFixture f) {
        return HandlerList.getRegisteredListeners(f.runtime.plugin).stream().filter(h->h.getListener() instanceof QuestCommunityProducer).count();
    }
    static void define(QuestLifecycleFixture f,UUID id) {
        f.module.getDefinitionRegistry().replaceAll(List.of(new QuestDefinition("chat","Chat","Fixture",QuestProgressKey.of("social:chat_unique"),10,7)));
        f.module.getQuestService().replaceAssignments(id,List.of("chat"));
    }
    private static void lifecycle() throws Exception {
        try(var f=new QuestLifecycleFixture();var social=new CommunityTestSupport()) {
            var a=social.actor("Alpha");f.players.loadPlayer(a.id,"Alpha");f.runtime.online.add(a.player);
            Owners owners=new Owners(social);f.module.enable();define(f,a.id);
            int timers=f.tasks.size();owners.bind(f.module);var old=producer(f);
            check(old!=null&&handlers(f)==3&&f.tasks.size()==timers,"binding owns exactly three handlers and no recurring task");
            old.onSuccess(a.id,UUID.randomUUID(),false);check(f.players.getPlayer(a.id).orElseThrow().getQuestState().snapshot().get("chat").progress()==0,"unrelated key does not affect assigned chat quest");
            f.module.getDefinitionRegistry().replaceAll(List.of(new QuestDefinition("request","Request","Fixture",QuestProgressKey.of("social:friend_request"),10,7)));
            f.module.getQuestService().replaceAssignments(a.id,List.of("request"));
            social.friends.sendRequest(a.id,UUID.randomUUID());
            check(f.players.getPlayer(a.id).orElseThrow().getQuestState().snapshot().get("request").progress()==1,"actual module observer reaches module-owned quest service");
            ModuleRuntimeFixture.set(owners.friends,"active",false);social.friends.sendRequest(a.id,UUID.randomUUID());
            check(f.players.getPlayer(a.id).orElseThrow().getQuestState().snapshot().get("request").progress()==1,"retired source owner cannot retain producer authority");ModuleRuntimeFixture.set(owners.friends,"active",true);
            f.module.disable();check(handlers(f)==0&&old.released()&&old.trackedPlayers()==0,"disable removes owned observers and handlers");
            social.friends.sendRequest(a.id,UUID.randomUUID());check(f.players.getPlayer(a.id).orElseThrow().getQuestState().snapshot().get("request").progress()==1,"retained old service cannot earn after disable");
            f.module.enable();owners.bind(f.module);check(producer(f)!=old&&handlers(f)==3,"fresh module generation binds once");
            boolean duplicate=false;try{owners.bind(f.module);}catch(IllegalStateException expected){duplicate=true;}check(duplicate&&handlers(f)==3,"duplicate wiring rejected");f.module.disable();
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void acquisitionFailures() throws Exception {
        for(String stage:List.of("assignments","pm","friends","tpa","register-before","register-after","roster")) {
            try(var f=new QuestLifecycleFixture();var social=new CommunityTestSupport()) {
                Owners owners=new Owners(social);f.module.enable();
                Object target=switch(stage){case "assignments"->f.module.getQuestService();case "pm"->social.pm;case "friends"->social.friends;case "tpa"->social.social.requests;default->null;};
                String field=stage.equals("assignments")?"assignmentListeners":stage.equals("pm")?"deliveryListeners":"successListeners";
                RefusingSet set=null;
                if(target!=null){set=new RefusingSet();set.addAll((Collection)ModuleRuntimeFixture.get(target,field));ModuleRuntimeFixture.set(target,field,set);set.addAfter=true;}
                int before=set==null?0:set.size();
                f.refuseRegistrationBefore=stage.equals("register-before");f.refuseRegistrationAfter=stage.equals("register-after");f.refuseRoster=stage.equals("roster");
                boolean failed=false;try{owners.bind(f.module);}catch(RuntimeException expected){failed=true;}
                check(failed&&producer(f)==null&&handlers(f)==0,"partial acquisition released "+stage);
                if(set!=null){check(set.size()==before,"foreign subscriptions retained "+stage);set.addAfter=false;}
                f.refuseRegistrationBefore=false;f.refuseRegistrationAfter=false;f.refuseRoster=false;
                owners.bind(f.module);check(handlers(f)==3,"retry after failed partial start binds exactly once "+stage);f.module.disable();
            }
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void cleanupFailures() throws Exception {
        for(String stage:List.of("assignments","pm","friends","tpa","listener")) {
            try(var f=new QuestLifecycleFixture();var social=new CommunityTestSupport()) {
                Owners owners=new Owners(social);f.module.enable();owners.bind(f.module);var old=producer(f);
                RefusingSet set=null;QuestLifecycleFixture.RefusingHandlers refusing=null;
                if(stage.equals("listener")){refusing=new QuestLifecycleFixture.RefusingHandlers(old,f.runtime.plugin);f.refusingLists.add(refusing);}
                else {
                    Object target=stage.equals("assignments")?f.module.getQuestService():stage.equals("pm")?social.pm:stage.equals("friends")?social.friends:social.social.requests;
                    String field=stage.equals("assignments")?"assignmentListeners":stage.equals("pm")?"deliveryListeners":"successListeners";
                    set=new RefusingSet();set.addAll((Collection)ModuleRuntimeFixture.get(target,field));ModuleRuntimeFixture.set(target,field,set);set.removeBefore=true;
                }
                boolean failed=false;try{f.module.disable();}catch(IllegalStateException expected){failed=true;}
                check(failed&&old.trackedPlayers()==0,"cleanup failure visible and temporary state cleared "+stage);
                boolean blocked=false;try{f.module.enable();}catch(IllegalStateException expected){blocked=true;}check(blocked,"unconfirmed release blocks re-enable "+stage);
                if(set!=null)set.removeBefore=false;if(refusing!=null)refusing.refuse=false;
                f.module.disable();check(old.released()&&handlers(f)==0,"explicit cleanup retry releases original observer "+stage);
                f.module.enable();owners.bind(f.module);check(producer(f)!=old&&handlers(f)==3,"fresh enable after confirmed cleanup "+stage);f.module.disable();
            }
        }
    }
    private static void yamlCompatibility() throws Exception {
        var root=java.nio.file.Files.createTempDirectory("vapeecore-community-catalog-");
        try {
            String[] ids={"chat_hello","chat_thanks","chat_unique_3","chat_unique_6","private_message","private_reply","friend_request","friendship","tpa_visit","tpa_host"};
            String[] keys={"chat_hello","chat_thanks","chat_unique","chat_unique","pm_sent","pm_reply","friend_request","friend_accepted","tpa_visit","tpa_host"};
            var text=new StringBuilder("config-version: 1\nenabled: true\nquests-per-day: 10\nquests:\n");
            for(int i=0;i<ids.length;i++)text.append("  ").append(ids[i]).append(":\n    name: Fixture\n    description: YAML owned\n    progress-key: social:").append(keys[i]).append("\n    target: ").append(i+1).append("\n    reward-coins: ").append(i+2).append("\n");
            java.nio.file.Path file=root.resolve("daily-quests.yml");java.nio.file.Files.writeString(file,text);
            var type=dev.vapee.core.quest.daily.DailyQuestConfig.class;
            var config=new dev.vapee.core.quest.daily.DailyQuestConfig(file,java.util.logging.Logger.getAnonymousLogger());
            config.initialize();check(config.getState().definitions().size()==10,"all ten reference IDs load as YAML definitions");
            for(int i=0;i<ids.length;i++){final int index=i;var q=config.getState().definitions().stream().filter(d->d.id().equals(ids[index])).findFirst().orElseThrow();check(q.target()==i+1&&q.rewardCoins()==i+2&&q.progressKey().value().equals("social:"+keys[i]),"YAML remains authoritative "+ids[i]);}
            var defaults=new org.bukkit.configuration.file.YamlConfiguration();defaults.loadFromString(new String(Objects.requireNonNull(type.getResourceAsStream("/daily-quests.yml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            check(!defaults.getBoolean("enabled")&&defaults.getInt("quests-per-day")==4&&defaults.getConfigurationSection("quests").getKeys(false).isEmpty(),"bundled defaults remain disabled, four and catalog-free");
            new QuestDefinition("future","Future","Future",QuestProgressKey.of("social:meet_unique"),1,1);check(true,"unsupported future key remains syntactically legal and inert");
        } finally {
            try(var files=java.nio.file.Files.walk(root)){for(var path:files.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void partialCleanupRetry() throws Exception {
        try(var f=new QuestLifecycleFixture();var social=new CommunityTestSupport()) {
            Owners owners=new Owners(social);f.module.enable();
            RefusingSet set=new RefusingSet();set.addAll((Collection)ModuleRuntimeFixture.get(social.pm,"deliveryListeners"));
            ModuleRuntimeFixture.set(social.pm,"deliveryListeners",set);set.addAfter=true;set.removeBefore=true;
            boolean failed=false;try{owners.bind(f.module);}catch(IllegalStateException expected){failed=true;}
            check(failed&&producer(f)!=null&&handlers(f)==0,"failed partial bind retains refused observer release");
            boolean blocked=false;try{owners.bind(f.module);}catch(IllegalStateException expected){blocked=true;}check(blocked,"incomplete partial bind cannot acquire a second observer");
            failed=false;try{f.module.disable();}catch(IllegalStateException expected){failed=true;}check(failed,"normal module shutdown surfaces incomplete partial cleanup");
            blocked=false;try{f.module.enable();}catch(IllegalStateException expected){blocked=true;}check(blocked,"pending partial cleanup blocks a fresh module generation");
            set.addAfter=false;set.removeBefore=false;f.module.disable();check(set.size()==1,"explicit retry preserves foreign observer and removes only own failed acquisition");
            f.module.enable();owners.bind(f.module);check(handlers(f)==3,"fresh generation works after partial cleanup retry");f.module.disable();
        }
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

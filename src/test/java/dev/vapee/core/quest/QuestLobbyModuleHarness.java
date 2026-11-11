package dev.vapee.core.quest;

import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.lobby.warp.WarpModule;
import dev.vapee.core.lobby.experience.LobbyExperienceModule;
import dev.vapee.core.quest.daily.DailyQuestModule;
import dev.vapee.core.settings.SettingsModule;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.visibility.VisibilityModule;
import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.HandlerList;
import java.lang.reflect.*;
import java.util.*;

/** Actual module post-enable wiring, before/after-effect failures, cleanup retry and foreign leases. */
public final class QuestLobbyModuleHarness {
    private static int checks;
    public static void main(String[]args)throws Exception{normal();acquisition();cleanup();System.out.println("QuestLobbyModuleHarness passed "+checks+" checks.");}
    static final class RefusingSet<T> extends LinkedHashSet<T>{boolean addAfter,removeBefore;public boolean add(T v){boolean r=super.add(v);if(addAfter)throw new IllegalStateException("after add");return r;}public boolean remove(Object v){if(removeBefore)throw new IllegalStateException("before remove");return super.remove(v);}}
    static final class Owners{
        final QuestLobbyTestSupport.Owners sources=new QuestLobbyTestSupport.Owners();
        final LobbyPlayerStateService states=ModuleRuntimeFixture.dependency(LobbyPlayerStateService.class,Map.of("stateListeners",new LinkedHashSet<>()));
        final ActivityService activities=ModuleRuntimeFixture.dependency(ActivityService.class,Map.of("membershipListeners",new LinkedHashSet<>()));
        final LobbyModule lobby=ModuleRuntimeFixture.dependency(LobbyModule.class,Map.of("lobbyService",sources.lobby,"lobbyPlayerStateService",states));
        final WarpModule warps=ModuleRuntimeFixture.dependency(WarpModule.class,Map.of("warpService",sources.warps));
        final LobbyExperienceModule experience=ModuleRuntimeFixture.dependency(LobbyExperienceModule.class,Map.of("navigatorMenu",sources.navigator));
        final SettingsModule settings=ModuleRuntimeFixture.dependency(SettingsModule.class,Map.of("settingsMenu",sources.settings,"visibilitySettingsMenu",sources.visibilityMenu));
        final FriendModule friends=ModuleRuntimeFixture.dependency(FriendModule.class,Map.of("active",true,"friendMenu",sources.friends));
        final VisibilityModule visibility=ModuleRuntimeFixture.dependency(VisibilityModule.class,Map.of("service",sources.visibility));
        final DailyQuestModule daily;
        Owners(QuestLifecycleFixture f)throws Exception{
            Class<?> runtime=Arrays.stream(DailyQuestModule.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("RuntimeResources")).findFirst().orElseThrow();
            daily=ModuleRuntimeFixture.dependency(DailyQuestModule.class,Map.of("active",true,"runtime",ModuleRuntimeFixture.dependency(runtime,Map.of("menu",sources.daily))));
            var constructor=PluginCommand.class.getDeclaredConstructor(String.class,Plugin.class);constructor.setAccessible(true);
            Map<String,TabExecutor> executors=Map.of("coins",sources.coins,"ping",sources.ping);
            Map<String,PluginCommand> commands=new HashMap<>();for(var e:executors.entrySet()){var c=constructor.newInstance(e.getKey(),f.runtime.plugin);c.setExecutor(e.getValue());commands.put(e.getKey(),c);}
            Server original=f.runtime.plugin.getServer();
            Server server=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class<?>[]{Server.class},(p,m,a)->{
                if(m.getName().equals("getPluginCommand"))return commands.get(a[0]);
                if(m.getName().equals("isPrimaryThread"))return true;
                try{return m.invoke(original,a);}catch(InvocationTargetException e){throw e.getCause();}
            });ModuleRuntimeFixture.set(f.runtime.plugin,"server",server);
        }
        void bind(QuestModule module){module.startLobby(lobby,warps,experience,daily,settings,friends,visibility,activities);}
    }
    private static QuestLobbyProducer producer(QuestLifecycleFixture f){Object r=ModuleRuntimeFixture.get(f.module,"runtime");if(r==null)r=ModuleRuntimeFixture.get(f.module,"pendingCleanup");return r==null?null:(QuestLobbyProducer)ModuleRuntimeFixture.get(r,"lobby");}
    private static long handlers(QuestLifecycleFixture f){return HandlerList.getRegisteredListeners(f.runtime.plugin).stream().filter(h->h.getListener() instanceof QuestLobbyProducer).count();}
    private static void normal()throws Exception{
        try(var f=new QuestLifecycleFixture()){
            var owners=new Owners(f);int[] foreign={0};owners.sources.lobby.addSuccessListener(p->foreign[0]++);
            f.module.enable();owners.bind(f.module);var old=producer(f);check(handlers(f)==6,"six real Paper event handlers acquired");
            check(QuestLobbyTestSupport.Owners.listeners(owners.sources.lobby,"successListeners",java.util.function.Consumer.class).size()==2,"owned and foreign success listeners coexist");
            boolean duplicate=false;try{owners.bind(f.module);}catch(IllegalStateException e){duplicate=true;}check(duplicate,"module rejects duplicate bind");
            f.module.disable();check(old.released()&&handlers(f)==0,"disable removes all owned resources");
            check(QuestLobbyTestSupport.Owners.listeners(owners.sources.lobby,"successListeners",java.util.function.Consumer.class).size()==1,"disable retains foreign observer");
            f.module.enable();owners.bind(f.module);check(producer(f)!=old&&handlers(f)==6,"fresh re-enable");f.module.disable();
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static void acquisition()throws Exception{
        for(String stage:List.of("assignment","spawn","warp","menu","visibility","context","register-before","register-after","roster"))try(var f=new QuestLifecycleFixture()){
            var o=new Owners(f);f.module.enable();Object target=switch(stage){case "assignment"->f.module.getQuestService();case "spawn"->o.sources.lobby;case "warp"->o.sources.warps;case "menu"->o.sources.friends;case "visibility"->o.sources.visibility;case "context"->o.activities;default->null;};
            String field=stage.equals("assignment")?"assignmentListeners":stage.equals("visibility")?"refreshListeners":stage.equals("context")?"membershipListeners":"successListeners";
            RefusingSet set=null;if(target!=null){set=new RefusingSet();set.addAll((Collection)ModuleRuntimeFixture.get(target,field));ModuleRuntimeFixture.set(target,field,set);set.addAfter=true;}
            int before=set==null?0:set.size();f.refuseRegistrationBefore=stage.equals("register-before");f.refuseRegistrationAfter=stage.equals("register-after");f.refuseRoster=stage.equals("roster");
            boolean failed=false;try{o.bind(f.module);}catch(RuntimeException e){failed=true;}check(failed&&producer(f)==null&&handlers(f)==0,"partial start rolls back "+stage);
            if(set!=null){check(set.size()==before,"no lost foreign/no leaked owned subscription "+stage);set.addAfter=false;}
            f.refuseRegistrationBefore=false;f.refuseRegistrationAfter=false;f.refuseRoster=false;o.bind(f.module);check(handlers(f)==6,"retry succeeds "+stage);f.module.disable();
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static void cleanup()throws Exception{
        for(String stage:List.of("spawn","warp","menu","visibility","context","listener"))try(var f=new QuestLifecycleFixture()){
            var o=new Owners(f);f.module.enable();o.bind(f.module);var old=producer(f);RefusingSet set=null;QuestLifecycleFixture.RefusingHandlers refusing=null;
            if(stage.equals("listener")){refusing=new QuestLifecycleFixture.RefusingHandlers(old,f.runtime.plugin);f.refusingLists.add(refusing);}
            else{Object target=switch(stage){case "spawn"->o.sources.lobby;case "warp"->o.sources.warps;case "menu"->o.sources.friends;case "context"->o.activities;default->o.sources.visibility;};String field=stage.equals("context")?"membershipListeners":stage.equals("visibility")?"refreshListeners":"successListeners";set=new RefusingSet();set.addAll((Collection)ModuleRuntimeFixture.get(target,field));ModuleRuntimeFixture.set(target,field,set);set.removeBefore=true;}
            boolean failed=false;try{f.module.disable();}catch(RuntimeException e){failed=true;}check(failed,"cleanup failure visible "+stage);
            boolean blocked=false;try{f.module.enable();}catch(IllegalStateException e){blocked=true;}check(blocked,"unconfirmed release blocks enable "+stage);
            if(set!=null)set.removeBefore=false;if(refusing!=null)refusing.refuse=false;f.module.disable();check(old.released()&&handlers(f)==0,"independent cleanup/retry completes "+stage);
            f.module.enable();o.bind(f.module);check(handlers(f)==6,"fresh owner after retry "+stage);f.module.disable();
        }
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

package dev.vapee.core.moderation;

import static dev.vapee.core.moderation.ModerationTestSupport.*;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Real async events/projection and module publication; no persistence or Player lookup on chat threads. */
public final class ModerationMuteFeedbackHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        concurrencyAndInterval(); recordAndSessionFences(); failuresAndShutdown(); modulePublication(); sanctionNotifications();
        System.out.println("ModerationMuteFeedbackHarness passed " + checks + " checks.");
    }
    private static void concurrencyAndInterval() throws Exception {
        Fixture f=new Fixture(); Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();AtomicLong time=new AtomicLong();
        Thread main=Thread.currentThread();AtomicInteger lookups=new AtomicInteger();
        var listener=new ModerationMuteChatListener(f.projection,tasks::add,id->{
            if(Thread.currentThread()!=main)throw new AssertionError("async online lookup");lookups.incrementAndGet();return (Player)f.target.sender;
        },f.messages,f.logger,time::get);
        f.service.issueMute(TARGET,ModerationActor.console(),ATTACK,Optional.of(NOW.plusSeconds(60)));
        int writes=f.repository.saves;f.failIdentity=true;
        CountDownLatch start=new CountDownLatch(1);List<Thread> threads=new ArrayList<>();Queue<Throwable> errors=new ConcurrentLinkedQueue<>();
        Queue<AsyncChatEvent> events=new ConcurrentLinkedQueue<>();
        for(int i=0;i<24;i++){Thread t=new Thread(()->{try{start.await();var e=event((Player)f.target.sender);listener.onAsyncChat(e);events.add(e);}catch(Throwable failure){errors.add(failure);}},"mute-chat-"+i);threads.add(t);t.start();}
        start.countDown();for(Thread t:threads)t.join(10000);
        check(threads.stream().noneMatch(Thread::isAlive)&&errors.isEmpty()&&events.size()==24,"concurrent real chat-thread calls complete");
        for(var event:events)check(event.isCancelled(),"every concurrent muted message remains blocked");
        check(tasks.size()==1&&lookups.get()==0&&f.target.output.isEmpty(),"one atomic feedback claim, no async Bukkit lookup/message");
        tasks.remove().run();check(f.target.output.size()==1&&lookups.get()==1,"one main-thread compact reminder");
        check(!plain(f.target.output.getLast()).contains("\n")&&!plain(f.target.output.getLast()).contains(ATTACK)
                &&plain(f.target.output.getLast()).contains("UTC"),"repeat reminder retains expiry without repeating reason");
        time.set(ModerationMuteChatListener.FEEDBACK_INTERVAL_NANOS-1);
        check(async(listener,(Player)f.target.sender).isCancelled()&&tasks.isEmpty(),"silently blocked immediately before ten seconds");
        time.incrementAndGet();check(async(listener,(Player)f.target.sender).isCancelled()&&tasks.size()==1,"new reminder allowed at exact ten-second boundary");tasks.remove().run();
        check(f.target.output.size()==2&&f.repository.saves==writes,"throttle never persists or alters enforcement");
        var pre=event((Player)f.target.sender);pre.setCancelled(true);listener.onAsyncChat(pre);check(tasks.isEmpty(),"pre-cancelled events do not claim feedback");
        f.clock.now=NOW.plusSeconds(60);check(!async(listener,(Player)f.target.sender).isCancelled()&&size(listener)==0,"expiry allows chat and lazily releases feedback without publication");
    }
    private static void recordAndSessionFences() throws Exception {
        Fixture f=new Fixture();Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();AtomicLong time=new AtomicLong();
        var listener=listener(f,tasks,time);f.service.issueMute(TARGET,ModerationActor.console(),"old reason",Optional.empty());
        async(listener,(Player)f.target.sender);f.service.revokeMute(TARGET,ModerationActor.console(),Optional.empty());
        f.service.issueMute(TARGET,ModerationActor.console(),"new reason",Optional.of(NOW.plusSeconds(30)));
        async(listener,(Player)f.target.sender);check(tasks.size()==2,"new record gets immediate feedback claim despite old interval");
        tasks.remove().run();check(f.target.output.isEmpty(),"queued obsolete mute notice suppressed");tasks.remove().run();
        check(f.target.output.size()==1&&plain(f.target.output.getLast()).contains("UTC")&&!plain(f.target.output.getLast()).contains("Permanent"),"replacement record supplies current expiry");
        time.addAndGet(ModerationMuteChatListener.FEEDBACK_INTERVAL_NANOS);async(listener,(Player)f.target.sender);
        ModerationRecord current=f.projection.getActiveMute(TARGET).orElseThrow();
        ModerationRecord updated=new ModerationRecord(current.id(),current.action(),current.targetId(),current.actor(),"updated same ID",current.createdAt(),Optional.of(NOW.plusSeconds(50)),Optional.empty());
        f.projection.publish(new ModerationSnapshot(List.of(updated)));async(listener,(Player)f.target.sender);
        check(tasks.size()==2,"changed immutable record with same UUID receives new claim");tasks.remove().run();
        check(f.target.output.size()==1,"queued pre-update reminder cannot display stale expiry");tasks.remove().run();
        check(f.target.output.size()==2&&plain(f.target.output.getLast()).contains(ModerationComponents.time(NOW.plusSeconds(50))),"updated record feedback uses exact current immutable value");
        f.service.revokeMute(TARGET,ModerationActor.console(),Optional.empty());listener.reconcileFeedback();check(size(listener)==0,"unmute releases old feedback state");
        f.service.issueMute(TARGET,ModerationActor.console(),"session",Optional.empty());async(listener,(Player)f.target.sender);
        listener.onQuit(new PlayerQuitEvent((Player)f.target.sender,Component.empty()));check(size(listener)==0,"quit clears exact session stamp");
        Sender fresh=new Sender(Player.class,TARGET,"Rejoined",f.events);f.online.put(TARGET,fresh);async(listener,(Player)fresh.sender);
        listener.onQuit(new PlayerQuitEvent((Player)f.target.sender,Component.empty()));check(size(listener)==1,"old quit preserves replacement session stamp");
        tasks.remove().run();check(fresh.output.isEmpty(),"old session callback cannot notify replacement");tasks.remove().run();
        check(fresh.output.size()==1&&plain(fresh.output.getFirst()).contains("Permanent"),"new session receives its own eligible reminder");
        time.addAndGet(ModerationMuteChatListener.FEEDBACK_INTERVAL_NANOS);async(listener,(Player)fresh.sender);
        f.service.revokeMute(TARGET,ModerationActor.console(),Optional.empty());tasks.remove().run();
        check(fresh.output.size()==1&&size(listener)==0,"unmute between claim and callback suppresses stale output");
        f.service.issueMute(TARGET,ModerationActor.console(),"expires",Optional.of(NOW.plusSeconds(1)));async(listener,(Player)fresh.sender);
        f.clock.now=NOW.plusSeconds(1);tasks.remove().run();check(fresh.output.size()==1&&size(listener)==0,"expiry before callback suppresses stale output");
    }
    private static void failuresAndShutdown() throws Exception {
        Fixture f=new Fixture();Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();AtomicLong time=new AtomicLong();
        f.service.issueMute(TARGET,ModerationActor.console(),"reason",Optional.empty());AtomicBoolean reject=new AtomicBoolean(true);
        var listener=new ModerationMuteChatListener(f.projection,task->{if(reject.getAndSet(false))throw new IllegalStateException("scheduler closed");tasks.add(task);},id->(Player)f.target.sender,f.messages,f.logger,time::get);
        check(async(listener,(Player)f.target.sender).isCancelled()&&size(listener)==0&&f.severe()==1,"scheduler failure releases claim and still blocks");
        check(async(listener,(Player)f.target.sender).isCancelled()&&tasks.size()==1,"failed scheduling may retry without waiting interval");
        listener.deactivate();check(size(listener)==0,"shutdown releases all feedback stamps");tasks.remove().run();check(f.target.output.isEmpty(),"queued callback inert after shutdown");
        var next=listener(f,tasks,time);check(async(next,(Player)f.target.sender).isCancelled()&&tasks.size()==1,"new runtime does not inherit stale throttle");tasks.remove().run();
        check(f.target.output.size()==1,"fresh listener can remind while same committed mute remains");
    }
    private static void modulePublication() throws Exception {
        Fixture f=new Fixture();Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();List<Listener> listeners=new ArrayList<>();AtomicLong ids=new AtomicLong(100);
        var module=new ModerationModule(()->f.repository,f.clock,()->new UUID(0,ids.incrementAndGet()),f::context,new ModerationModule.Hooks(){
            public void install(String name,org.bukkit.command.TabExecutor executor){}
            public void register(Listener listener){listeners.add(listener);}
            public void unregister(Listener listener){listeners.remove(listener);}
            public void clearCommands(){}
        },tasks::add);
        module.enable();var listener=listeners.stream().filter(ModerationMuteChatListener.class::isInstance).map(ModerationMuteChatListener.class::cast).findFirst().orElseThrow();
        module.getModerationService().issueMute(TARGET,ModerationActor.console(),ATTACK,Optional.empty());async(listener,(Player)f.target.sender);
        check(size(listener)==1,"real enabled module owns feedback stamp");
        module.getModerationService().revokeMute(TARGET,ModerationActor.console(),Optional.empty());check(size(listener)==0,"committed unmute publisher immediately releases feedback state");tasks.remove().run();
        check(f.target.output.isEmpty(),"committed unmute cancels queued reminder");
        module.getModerationService().issueMute(TARGET,ModerationActor.console(),"new",Optional.empty());async(listener,(Player)f.target.sender);
        module.disable();check(size(listener)==0&&listeners.isEmpty(),"module disable clears throttle and unregisters listener");tasks.remove().run();check(f.target.output.isEmpty(),"old module task fenced");
        module.enable();var fresh=listeners.stream().filter(ModerationMuteChatListener.class::isInstance).map(ModerationMuteChatListener.class::cast).findFirst().orElseThrow();
        check(fresh!=listener&&async(fresh,(Player)f.target.sender).isCancelled()&&tasks.size()==1,"actual module re-enable enforces persisted mute with new feedback state");tasks.remove().run();module.disable();
    }
    private static void sanctionNotifications() throws Exception {
        Fixture f=new Fixture();Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();AtomicLong time=new AtomicLong();var listener=listener(f,tasks,time);
        new dev.vapee.core.moderation.command.MuteCommand(f.context).onCommand(f.staff.sender,null,"mute",new String[]{"Alex","permanent",ATTACK});
        check(f.target.output.size()==1&&plain(f.target.output.getLast()).contains(ATTACK),"staff-issued initial mute retains full reason");
        async(listener,(Player)f.target.sender);tasks.remove().run();int outputs=f.target.output.size();
        new dev.vapee.core.moderation.command.WarnCommand(f.context).onCommand(f.staff.sender,null,"warn",new String[]{"Alex","staff warning"});
        check(f.target.output.size()==outputs+1&&plain(f.target.output.getLast()).contains("staff warning"),"staff warning not suppressed by blocked-chat interval");
        new dev.vapee.core.moderation.command.UnmuteCommand(f.context).onCommand(f.staff.sender,null,"unmute",new String[]{"Alex","staff release"});
        check(plain(f.target.output.getLast()).contains("staff release"),"staff-issued unmute not suppressed by feedback interval");
        new dev.vapee.core.moderation.command.MuteCommand(f.context).onCommand(f.staff.sender,null,"mute",new String[]{"Alex","1m","new full sanction"});
        check(plain(f.target.output.getLast()).contains("new full sanction")&&plain(f.target.output.getLast()).contains("UTC"),"new staff mute always delivers full updated reason/expiry immediately");
    }
    private static ModerationMuteChatListener listener(Fixture f,Queue<Runnable> tasks,AtomicLong time){return new ModerationMuteChatListener(f.projection,tasks::add,id->f.online.containsKey(id)?(Player)f.online.get(id).sender:null,f.messages,f.logger,time::get);}
    private static int size(ModerationMuteChatListener listener)throws Exception{var field=ModerationMuteChatListener.class.getDeclaredField("feedback");field.setAccessible(true);return ((Map<?,?>)field.get(listener)).size();}
    private static AsyncChatEvent event(Player player){Component message=Component.text("chat");return new AsyncChatEvent(true,player,new HashSet<>(),ChatRenderer.defaultRenderer(),message,message,SignedMessage.system("chat",message));}
    private static AsyncChatEvent async(ModerationMuteChatListener listener,Player player)throws Exception{var event=event(player);AtomicReference<Throwable> failure=new AtomicReference<>();Thread thread=new Thread(()->{try{listener.onAsyncChat(event);}catch(Throwable error){failure.set(error);}},"paper-chat-feedback-test");thread.start();thread.join(10000);check(!thread.isAlive()&&failure.get()==null,"async boundary succeeds without persistence/main-thread access");return event;}
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
}

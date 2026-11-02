package dev.vapee.core.utility;

import dev.vapee.core.utility.tpa.*;
import java.util.*;
import java.util.logging.*;
import org.bukkit.command.*;
import org.bukkit.event.Listener;

/** Executes real registration and Utility startup/rollback/shutdown, including replaced ownership. */
public final class UtilityTpaLifecycleHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        registration(); rollback(); module();
        System.out.println("UtilityTpaLifecycleHarness passed " + checks + " checks.");
    }
    private static void registration() throws Exception {
        try(var f=new TpaRegistrationTestSupport()) {
            var a=f.feature.user("Alpha"); var b=f.feature.user("Beta"); f.registration.enable();
            check(f.commands.stream().allMatch(c->c.getExecutor()==f.feature.requests && c.getTabCompleter()==f.feature.requests),"all five real command pairs installed");
            check(f.listener&&f.observer,"listener and observer acquired before enabling requests");
            f.feature.command(a,"tpa","Beta");String old=b.accept();
            CommandExecutor foreign=(s,c,l,args)->false; TabCompleter completer=(s,c,l,args)->List.of("foreign");
            f.commands.get(1).setExecutor(foreign); f.commands.get(2).setTabCompleter(completer); f.registration.disable();
            check(!f.listener&&!f.observer,"listeners and observers removed");
            check(f.commands.get(1).getExecutor()==foreign && f.commands.get(2).getTabCompleter()==completer,"foreign replacement ownership retained independently");
            check(f.commands.getFirst().getExecutor()==f.originalExecutor && f.commands.getFirst().getTabCompleter()==f.originalCompleter,"previous compatible pair restored");
            f.feature.command(b,"tpaccept",old); check(a.teleports==0,"old executor is inert after disable");
            f.registration.enable();f.feature.command(b,"tpaccept",old);check(a.teleports==0,"re-enable cannot resurrect consent");
            f.feature.command(a,"tpa","Beta");f.feature.command(b,"tpaccept");check(a.teleports==1,"same lifecycle can re-enable for new consent");
            try {f.registration.enable();throw new AssertionError("double enable");}catch(IllegalStateException expected){check(true,"double enable rejected");}
            f.registration.disable();check(f.commands.get(1).getExecutor()==foreign&&f.commands.get(2).getTabCompleter()==completer,"re-enable restores intervening previous ownership");
        }
    }
    private static void rollback() throws Exception {
        for(String stage:List.of("missing-root","namespaced-collision","root-collision","foreign-owner","register","subscribe")) {
            try(var f=new TpaRegistrationTestSupport()) {
                if(stage.equals("missing-root"))f.routes.remove("tpdeny");
                if(stage.equals("namespaced-collision"))f.routes.put("vapeecore:tpdeny",f.paper.command);
                if(stage.equals("root-collision"))f.routes.put("tpaccept",f.paper.command);
                f.foreignOwner=stage.equals("foreign-owner");f.fail=stage;
                try{f.registration.enable();throw new AssertionError("failure not raised "+stage);}catch(RuntimeException expected){check(true,"startup rejects "+stage);}
                check(f.original()&&!f.listener&&!f.observer,"all owned state rolled back "+stage);
                if(!List.of("register","subscribe").contains(stage))check(f.events.isEmpty(),"all route preflight before first mutation "+stage);
                var a=f.feature.user("Alpha");var b=f.feature.user("Beta");f.feature.command(a,"tpa","Beta");check(b.received.isEmpty(),"partial startup never active "+stage);
            }
        }
        for(String stage:List.of("unregister","unsubscribe")) {
            try(var f=new TpaRegistrationTestSupport()) {
                f.registration.enable();f.fail=stage;
                try{f.registration.disable();throw new AssertionError("cleanup error lost");}catch(RuntimeException expected){check(expected==f.fault,"cleanup diagnostic retained "+stage);}
                check(f.original()&&!f.listener&&!f.observer,"later independent cleanup still runs "+stage);
                f.fail="";f.registration.enable();f.registration.disable();check(f.original(),"re-enable after cleanup diagnostic "+stage);
            }
        }
    }
    private static void module() throws Exception {
        for(String failure:List.of("","register","subscribe","log")) {
            try(var f=new TpaRegistrationTestSupport()) {
                Set<Listener> listeners=new HashSet<>();Map<String,CommandExecutor> executors=new HashMap<>();Map<String,TabCompleter> completers=new HashMap<>();
                boolean[] failLog={failure.equals("log")};Logger logger=Logger.getAnonymousLogger();logger.setUseParentHandlers(false);
                logger.addHandler(new Handler(){public void publish(LogRecord r){if(failLog[0])throw f.fault;}public void flush(){}public void close(){}});
                UtilityModule module=new UtilityModule(()->f.feature.utility,
                        service->new UtilityListener(service,task->{},()->true),
                        ()->new InvseeService(()->true,id->null,(holder,size,title)->{throw new AssertionError("unused");}),
                        (service,invsee,register)->register.accept("legacy",new TabExecutor(){
                            public boolean onCommand(CommandSender s,Command c,String l,String[] a){return true;}
                            public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return List.of();}
                        }),new UtilityModule.Hooks(){
                            public UtilityModule.CommandHook command(String name){return new UtilityModule.CommandHook(value->{if(value==null)executors.remove(name);else executors.put(name,value);},value->{if(value==null)completers.remove(name);else completers.put(name,value);});}
                            public void register(Listener l){listeners.add(l);}public void unregister(Listener l){listeners.remove(l);}
                        },logger,service->f.registration);
                f.fail=failure;
                if(failure.isEmpty())module.enable();
                else {
                    try{module.enable();throw new AssertionError("startup fault ignored");}catch(RuntimeException expected){check(expected==f.fault,"Utility retains original TPA startup failure "+failure);}
                    check(f.original()&&!f.listener&&!f.observer&&listeners.isEmpty()&&executors.isEmpty()&&completers.isEmpty(),"Utility rollback releases both old and new resources "+failure);
                    try{module.getUtilityService();throw new AssertionError("premature publication");}catch(IllegalStateException expected){check(true,"failed Utility publishes no service "+failure);}
                    f.fail="";failLog[0]=false;module.enable();
                }
                check(module.getUtilityService()==f.feature.utility && f.listener&&f.observer,"Utility publishes only complete acquisition "+failure);
                var a=f.feature.user("Alpha");var b=f.feature.user("Beta");f.feature.command(a,"tpa","Beta");String selector=b.accept();
                module.disable();check(f.original()&&!f.listener&&!f.observer&&listeners.isEmpty()&&executors.isEmpty()&&completers.isEmpty(),"Utility shutdown owns complete TPA cleanup "+failure);
                module.enable();f.feature.command(b,"tpaccept",selector);check(a.teleports==0,"actual Utility re-enable fences old consent "+failure);module.disable();
                module.disable();check(f.original(),"repeat Utility disable retains original pair "+failure);
            }
        }
    }
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
}

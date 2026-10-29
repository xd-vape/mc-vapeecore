package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.persistence.SafeFileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Real temp-file writes, external edits and injected OS/application failures at the commit boundary. */
public final class NavigatorStoreHarness {
    private static int checks;
    private static final Logger LOG=Logger.getAnonymousLogger();
    public static void main(String[] args)throws Exception {
        LOG.setUseParentHandlers(false);
        for(String fault:new String[]{"write","replace","fallback","application","rollback","external-before-replace","none"}) transaction(fault);
        conflicts();
        System.out.println("NavigatorStoreHarness passed "+checks+" checks.");
    }
    private static void transaction(String fault)throws Exception {
        Path dir=Files.createTempDirectory(Path.of("target/harness-temp"),"navigator-store-");
        try {
            Path file=dir.resolve("navigator.yml");
            // CRLF, comments and inactive composition must survive rollback/backup byte-for-byte.
            String original="# protected original\r\n"+NavigatorConfigHarness.CONFIG.replace("enabled: true","enabled: false").replace("\n","\r\n");
            Files.writeString(file,original);byte[] before=Files.readAllBytes(file);
            var config=new NavigatorConfig(file,LOG);config.initialize();var old=config.getState();
            var moves=new AtomicInteger();var applications=new AtomicInteger();
            var access=new SafeFileWriter.FileAccess(){
                @Override public Writer openWriter(Path path)throws IOException {
                    if(fault.equals("write"))throw new IOException("injected write");
                    Writer delegate=SafeFileWriter.FileAccess.super.openWriter(path);
                    if(!fault.equals("external-before-replace"))return delegate;
                    return new java.io.FilterWriter(delegate){@Override public void close()throws IOException{super.close();Files.writeString(file,original+"# concurrent operator\n");}};
                }
                @Override public void move(Path source,Path target,CopyOption...options)throws IOException {
                    int call=moves.incrementAndGet();
                    if(fault.equals("replace"))throw new IOException("injected replace");
                    if(fault.equals("fallback") && call==1)throw new AtomicMoveNotSupportedException(source.toString(),target.toString(),"injected unsupported");
                    if(fault.equals("fallback") && call==2)throw new IOException("injected fallback replace");
                    if(fault.equals("rollback") && call>=2)throw new IOException("injected rollback");
                    SafeFileWriter.FileAccess.super.move(source,target,options);
                }
            };
            var store=new NavigatorStore(config,new SafeFileWriter(LOG,access),next->{applications.incrementAndGet();if(fault.equals("application")||fault.equals("rollback"))throw new IllegalStateException("injected apply after pointer publication");});
            var snapshot=store.begin();var draft=new NavigatorConfig.State(true,snapshot.state().root(),snapshot.state().menus());
            check(snapshot.state().menus().size()==3 && !snapshot.state().enabled(),fault+": inactive composition retained for editing");
            Throwable caught=null;Path backup=null;
            try{backup=store.save(snapshot,draft);}catch(IOException|RuntimeException failure){caught=failure;}
            if(fault.equals("none")) {
                check(caught==null && config.getState().enabled() && applications.get()==1,"successful Save applies once");
                check(NavigatorConfig.parse(Files.readString(file)).equals(config.getState()),"committed disk and runtime agree");
                check(Arrays.equals(before,Files.readAllBytes(backup)),"protected original backup exact");
                reject(()->store.save(snapshot,draft),"same session cannot Save twice");
            } else {
                check(caught!=null,fault+": negative control actually triggers failure");
                check(config.getState()==(fault.equals("rollback")?NavigatorConfig.unavailable():old),fault+": runtime restored or fails closed if OS refuses rollback");
                if(fault.equals("rollback")) {
                    check(NavigatorConfig.parse(Files.readString(file)).enabled() && caught.getSuppressed().length>0,"failed disk rollback explicitly retains diagnostic and committed candidate");
                    reject(store::begin,"unresolved disk rollback blocks subsequent editor sessions");
                    var cold=new NavigatorConfig(file,LOG);cold.initialize();check(cold.getState()==NavigatorConfig.unavailable(),"recovery marker prevents silent cold activation after failed rollback");
                } else if(fault.equals("external-before-replace")) {
                    check(Files.readString(file).endsWith("# concurrent operator\n") && applications.get()==0,"late external edit protected by before-replace byte check");
                } else {
                    check(Arrays.equals(before,Files.readAllBytes(file)),fault+": original disk bytes restored or never replaced");
                    check(applications.get()==(fault.equals("application")?1:0),fault+": application only follows durable file commit");
                    check(store.begin().revision()==snapshot.revision(),fault+": unsuccessful Save does not publish revision");
                }
                try(var backups=Files.list(dir.resolve("navigator-backups"))) {
                    var paths=backups.toList();check(paths.size()==1 && Arrays.equals(before,Files.readAllBytes(paths.getFirst())),fault+": original protected backup survives failure");
                }
            }
            try(var paths=Files.list(dir)){check(paths.noneMatch(p->p.getFileName().toString().endsWith(".tmp")),fault+": temporary candidate cleaned");}
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    private static void conflicts()throws Exception {
        Path dir=Files.createTempDirectory(Path.of("target/harness-temp"),"navigator-store-conflict-");
        try {
            Path file=dir.resolve("navigator.yml");Files.writeString(file,NavigatorConfigHarness.CONFIG);
            var config=new NavigatorConfig(file,LOG);config.initialize();var store=new NavigatorStore(config,LOG,next->{});
            var a=store.begin();var b=store.begin();byte[] original=a.bytes();
            byte[] exposed=a.bytes();exposed[0]=0;check(Arrays.equals(a.bytes(),original),"snapshot bytes cannot be mutated through accessor");
            store.save(a,a.state());Files.write(file,original);
            reject(()->store.save(b,b.state()),"revision detects competing Save even if external bytes return to original (ABA)");
            var fresh=store.begin();Files.writeString(file,Files.readString(file)+"# external\n");
            reject(()->store.save(fresh,fresh.state()),"external whitespace/comment changes conflict");
            var base=store.begin();var previous=config.getState();
            reject(()->store.save(base,NavigatorConfig.legacy()),"disabled candidate cannot be silently committed by editor");
            check(config.getState()==previous,"invalid candidate does not apply");
            Files.delete(file);reject(store::begin,"missing file never replaced implicitly");
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    @FunctionalInterface private interface Action{Object run()throws Exception;}
    private static void reject(Action action,String reason)throws Exception{try{action.run();throw new AssertionError(reason);}catch(IOException|IllegalArgumentException|IllegalStateException expected){check(true,reason);}}
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

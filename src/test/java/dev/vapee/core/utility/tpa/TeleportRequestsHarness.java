package dev.vapee.core.utility.tpa;

import static dev.vapee.core.utility.tpa.TpaTestSupport.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.player.*;
import dev.vapee.core.activity.ActivityLeaveReason;

public final class TeleportRequestsHarness {
    private static int checks;
    public static void main(String[] args) {
        directions(); ownershipAndReplay(); expiryAndLimits(); policies(); lifecycle(); destination(); presentation();
        System.out.println("TeleportRequestsHarness passed " + checks + " checks.");
    }
    private static void directions() {
        for (boolean here : List.of(false, true)) {
            Fixture f = new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta"); var c=f.user("Other");
            a.location.setX(5.5); b.location.setX(10.5);
            f.command(a, here ? "tpahere" : "tpa", "bEtA");
            check(a.teleports == 0 && b.teleports == 0, "request never forces either player " + here);
            check(b.last().contains(here ? "wants you to teleport to them" : "wants to teleport to you"), "direction explained " + here);
            String selector=b.accept();
            f.command(c,"tpaccept",selector); f.command(a,"tpaccept",selector);
            check(a.teleports == 0 && b.teleports == 0, "only actual recipient may consent " + here);
            f.command(b,"tpaccept",selector);
            check((here ? b.teleports == 1 && a.teleports == 0 : a.teleports == 1 && b.teleports == 0), "correct consenting mover " + here);
            check(a.location.getX() == b.location.getX(), "teleport reaches destination " + here);
            f.command(b,"tpaccept",selector);
            check(a.teleports+b.teleports == 1, "accepted request consumed once " + here);
        }
    }
    private static void ownershipAndReplay() {
        Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta"); var c=f.user("Gamma");
        f.command(a,"tpa","Alpha"); check(a.last().contains("unavailable"),"self request rejected");
        f.command(a,"tpa","Bet"); check(a.last().contains("unavailable"),"partial names rejected");
        f.command(a,"tpa","Beta"); String old=b.accept(); int deliveries=b.received.size();
        f.command(a,"tpahere","Gamma"); check(a.last().contains("already") && b.received.size()==deliveries && c.received.isEmpty(),"one outgoing, no implicit replacement");
        f.command(a,"tpa","Beta"); check(a.last().contains("already"),"duplicate request rejected");
        f.command(c,"tpcancel"); check(c.last().contains("no outgoing"),"cannot cancel another sender");
        f.command(a,"tpcancel"); check(b.last().contains("cancelled"),"cancel informs original recipient");
        f.command(b,"tpaccept",old); check(a.teleports==0,"cancelled chat action inert");
        f.command(a,"tpa","Beta"); check(a.last().contains("15 seconds"),"cancellation retains cooldown");
        f.seconds(15); f.command(a,"tpa","Beta"); String fresh=b.accept();
        check(!fresh.equals(old),"replacement gets fresh unpredictable request identity");
        f.command(b,"tpaccept",old); f.command(b,"tpdeny",old); check(a.teleports==0,"old accept and deny cannot affect fresh request");
        f.command(b,"tpaccept",fresh); check(a.teleports==1,"fresh request survives old buttons");
        f.seconds(15); f.command(a,"tpa","Beta"); f.command(c,"tpahere","Beta");
        f.command(b,"tpaccept"); check(b.last().contains("several") && a.teleports==1 && b.teleports==0,"ambiguous no-arg acceptance never guesses");
        f.command(b,"tpdeny"); check(b.last().contains("several"),"ambiguous denial never guesses");
        check(f.tab(b,"tpaccept","").equals(List.of("Alpha","Gamma")),"incoming sender completion exact and sorted");
        f.command(b,"tpdeny","gAmMa"); check(c.last().contains("denied"),"explicit exact name selects sender");
        f.command(b,"tpaccept"); check(a.teleports==2,"no-arg only when exactly one remains");
        f.seconds(15); f.command(a,"tpa","Beta"); f.command(b,"tpdeny",a.id.toString());
        check(a.last().contains("denied"),"manual canonical UUID selects current sender");
        for (String malformed:List.of("bad:token",a.id+":",a.id+":"+UUID.randomUUID(),a.id+":"+UUID.randomUUID()+":x")) {
            f.command(b,"tpaccept",malformed); check(b.last().contains("no longer"),"malformed or forged nonce denied");
        }
    }
    private static void expiryAndLimits() {
        Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta");
        f.command(a,"tpa","Beta"); String selector=b.accept(); f.seconds(59); f.command(b,"tpaccept",selector);
        check(a.teleports==1,"eligible just before sixty-second deadline");
        f.command(a,"tpa","Beta"); selector=b.accept(); f.seconds(60); f.command(b,"tpaccept",selector);
        check(a.teleports==1 && a.last().contains("expired"),"deadline exact, lazy expiry not renewed by acceptance");
        check(b.received.stream().map(TpaTestSupport::plain).anyMatch(s->s.contains("expired")),"expiry informs recipient when pruned");
        f.command(a,"tpa","Beta"); check(a.last().contains("sent"),"expired outgoing replaced deterministically");
        f.command(b,"tpdeny"); f.seconds(14); f.command(a,"tpa","Beta"); check(a.last().contains("15 seconds"),"cooldown before boundary");
        f.seconds(1); f.command(a,"tpa","Beta"); check(a.last().contains("sent"),"cooldown permits exact fifteen-second boundary");
        Fixture limit=new Fixture(); var recipient=limit.user("Recipient"); List<User> senders=new ArrayList<>();
        for(int i=0;i<6;i++) { var s=limit.user("Sender"+i); senders.add(s); limit.command(s,"tpa","Recipient"); check(s.last().contains(i<5?"sent":"unavailable"),"bounded inbox slot "+i); }
        limit.command(senders.getFirst(),"tpcancel"); limit.command(senders.getLast(),"tpa","Recipient");
        check(senders.getLast().last().contains("sent"),"failed full-inbox send incurs no cooldown and free slot reused");
    }
    private static void policies() {
        for(String boundary:List.of("permission-sender","permission-recipient","ignore-sender","ignore-recipient","hidden-target","hidden-sender","build-sender","build-recipient","activity-sender","activity-recipient","unloaded-sender","unloaded-recipient","dead-sender","dead-recipient","vehicle","passengers")) {
            Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta"); block(f,a,b,boundary);
            f.command(a,"tpa","Beta"); check(boundary.equals("permission-sender")
                    ? a.last().equals("You do not have permission to use this command.") : a.last().contains("unavailable"),
                    "send denied at actual boundary "+boundary);
            if (boundary.equals("permission-sender")) check(a.received.size() == 1
                    && dev.vapee.core.message.MessageColorAssertions.hasTextColor(a.received.getFirst(),
                    "You do not have permission to use this command.", dev.vapee.core.message.MessageStyle.ERROR),
                    "ordinary TPA permission denial is one raw semantic error line");
            check(b.clicks().isEmpty() && a.teleports+b.teleports==0,"no private detail or actionable invitation "+boundary);
            check(f.tab(a,"tpa","").isEmpty(),"completion respects policy "+boundary);
        }
        for(String boundary:List.of("permission-sender","permission-recipient","ignore-sender","ignore-recipient","hidden-target","hidden-sender","build-sender","build-recipient","activity-sender","activity-recipient","unloaded-sender","unloaded-recipient","dead-sender","dead-recipient","vehicle","passengers")) {
            Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta"); f.command(a,"tpa","Beta"); String selector=b.accept(); block(f,a,b,boundary);
            f.command(b,"tpaccept",selector); check(a.teleports+b.teleports==0,"revalidate outstanding consent "+boundary);
        }
        Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta");
        f.settings.setPrivateMessagesEnabled(a.id,false); f.settings.setFriendRequestsEnabled(b.id,false); f.settings.setFriendPresenceNotificationsEnabled(b.id,false);
        f.command(a,"tpa","Beta"); f.command(b,"tpaccept"); check(a.teleports==1,"unrelated PM/friend settings do not become teleport policy");
        for(boolean throwing:List.of(false,true)) {
            Fixture cancel=new Fixture(); var x=cancel.user("Alpha"); var y=cancel.user("Beta"); x.teleportSuccess=false; x.throwTeleport=throwing;
            cancel.command(x,"tpa","Beta"); String selector=y.accept(); cancel.command(y,"tpaccept",selector); cancel.command(y,"tpaccept",selector);
            check(x.teleports==1 && x.location.getX()==.5 && y.teleports==0,"failed/cancelled/throwing teleport consumed once "+throwing);
            check(x.last().contains("failed"),"concise failure to sender "+throwing);
        }
        Fixture reentrant=new Fixture(); var x=reentrant.user("Alpha"); var y=reentrant.user("Beta"); reentrant.command(x,"tpa","Beta"); String selector=y.accept();
        x.teleportHook=()-> { reentrant.command(y,"tpaccept",selector); reentrant.command(y,"tpahere","Alpha"); };
        reentrant.command(y,"tpaccept",selector); check(x.teleports==1 && y.teleports==0,"teleport callback cannot replay consent or issue reentrant request");
    }
    private static void block(Fixture f,User a,User b,String boundary) {
        switch(boundary) {
            case "permission-sender" -> a.permissions.clear(); case "permission-recipient" -> b.permissions.clear();
            case "ignore-sender" -> f.social.ignore(a.id,b.id); case "ignore-recipient" -> f.social.ignore(b.id,a.id);
            case "hidden-target" -> a.hidden.add(b.id); case "hidden-sender" -> b.hidden.add(a.id);
            case "build-sender", "build-recipient" -> { var user=boundary.endsWith("sender")?a:b; user.permissions.add("vapeecore.utility.build"); f.states.enterBuildMode(user.player); }
            case "activity-sender", "activity-recipient" -> f.activity(boundary.endsWith("sender")?a:b);
            case "unloaded-sender", "unloaded-recipient" -> f.players.unloadPlayer((boundary.endsWith("sender")?a:b).id);
            case "dead-sender" -> a.dead=true; case "dead-recipient" -> b.dead=true;
            case "vehicle" -> a.vehicle=true; case "passengers" -> b.passengers=true;
            default -> throw new AssertionError(boundary);
        }
    }
    private static void lifecycle() {
        for(boolean sender:List.of(false,true)) {
            Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta"); f.command(a,"tpa","Beta"); String selector=b.accept(); var old=sender?a:b;
            f.requests.onQuit(new PlayerQuitEvent(old.player,Component.empty())); old.online=false; var fresh=f.user(old.id,old.name);
            f.command(sender?b:fresh,"tpaccept",selector); check(a.teleports+b.teleports+fresh.teleports==0,"reconnect with same UUID never revives consent "+sender);
            f.seconds(15); f.command(sender?fresh:a,"tpa","Beta");
            f.requests.onQuit(new PlayerQuitEvent(old.player,Component.empty()));
            f.command(sender?b:fresh,"tpaccept"); check((sender?fresh:a).teleports==1,"stale old quit does not remove new session request "+sender);
        }
        Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta"); f.command(a,"tpa","Beta"); String old=b.accept();
        f.requests.disable(); int deliveries=a.received.size()+b.received.size(); f.command(b,"tpaccept",old);
        check(a.teleports==0 && deliveries==a.received.size()+b.received.size(),"disabled command instance inert");
        f.requests.enable(); f.command(b,"tpaccept",old); check(a.teleports==0,"re-enable contains no old requests");
        f.command(a,"tpa","Beta"); check(a.last().contains("sent"),"re-enable clears old cooldowns");
        f.requests.onWorldChange(new PlayerChangedWorldEvent(a.player,f.ground.world)); f.command(b,"tpaccept"); check(a.teleports==0,"world lifecycle invalidates pending request");
        f.seconds(15); f.command(a,"tpa","Beta"); old=b.accept(); a.location.setWorld(f.other.world); f.command(b,"tpaccept",old);
        check(a.teleports==0,"world identity change denied even without event hook"); a.location.setWorld(f.ground.world);
        f.seconds(15); f.command(a,"tpa","Beta"); old=b.accept(); UUID activity=f.activity(a); f.activities.leaveCurrentSession(a.id,ActivityLeaveReason.VOLUNTARY); f.command(b,"tpaccept",old);
        check(a.teleports==0,"activity start then leave cannot restore old consent");
        f.seconds(15); f.command(a,"tpa","Beta"); old=b.accept(); a.permissions.add("vapeecore.utility.build"); f.states.enterBuildMode(a.player); f.states.exitBuildMode(a.player); f.command(b,"tpaccept",old);
        check(a.teleports==0,"BUILD enter then exit cannot restore old consent");
        f.seconds(15); f.command(a,"tpa","Beta"); old=b.accept(); f.social.ignore(a.id,b.id); f.social.unignore(a.id,b.id); f.command(b,"tpaccept",old);
        check(a.teleports==0,"ignore then unignore cannot revive old consent");
        f.primaryThread=false; try { f.command(a,"tpa","Beta"); throw new AssertionError("async accepted"); } catch(IllegalStateException expected) { check(true,"off-thread commands rejected before state access"); }
    }
    private static void destination() {
        Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta");
        check(f.requests.safeDestination(b.location),"normal supported standing position");
        for(double x:List.of(Double.NaN,Double.POSITIVE_INFINITY,30_000_000d)) { var loc=b.location.clone();loc.setX(x);check(!f.requests.safeDestination(loc),"nonfinite/outside absolute horizontal bounds"); }
        for(double y:List.of(-64d,319d,320d,Double.NaN)) { var loc=b.location.clone();loc.setY(y);check(!f.requests.safeDestination(loc),"height bounds include body and floor"); }
        f.ground.loaded=false; check(!f.requests.safeDestination(b.location) && f.ground.unloadedReads==0,"never reads blocks or generates unloaded chunk"); f.ground.loaded=true;
        f.ground.inside=false; check(!f.requests.safeDestination(b.location),"world border enforced"); f.ground.inside=true;
        f.worlds.remove(f.ground.id); check(!f.requests.safeDestination(b.location),"unloaded world identity denied"); f.worlds.put(f.ground.id,f.ground.world);
        for(Material material:List.of(Material.LAVA,Material.WATER,Material.FIRE,Material.POWDER_SNOW,Material.NETHER_PORTAL,Material.STONE)) {
            f.ground.blocks.put("0,64,0",material); check(!f.requests.safeDestination(b.location),"unsafe body block denied "+material);
        }
        f.ground.blocks.clear(); f.ground.blocks.put("0,63,0",Material.MAGMA_BLOCK); check(!f.requests.safeDestination(b.location),"hazardous support denied"); f.ground.blocks.clear();
        b.location.setX(0);b.location.setZ(0);f.ground.blocks.put("-1,65,-1",Material.STONE);
        check(!f.requests.safeDestination(b.location),"body-overlap neighbor blocks checked across negative chunk boundary"); f.ground.blocks.clear();
        for(int[] height:List.of(new int[]{0,256},new int[]{-64,320},new int[]{-2032,2032})) {
            f.ground.min=height[0];f.ground.max=height[1];f.ground.floor=height[0]; b.location.setY(height[0]+1);
            check(f.requests.safeDestination(b.location),"world-specific minimum support "+height[0]);
        }
        f.ground.min=-64;f.ground.max=320;f.ground.floor=63;b.location=new Location(f.ground.world,10.5,64,.5);
        f.command(a,"tpa","Beta"); b.location.setX(20.5); f.command(b,"tpaccept"); check(a.location.getX()==20.5,"destination captured at acceptance, not request time");
    }
    private static void presentation() {
        Fixture f=new Fixture(); var a=f.user("Alpha"); var b=f.user("Beta");b.locale=Locale.GERMANY;
        f.command(a,"tpa","Beta"); check(!b.last().contains("[TPA]") && b.last().contains("möchte sich zu dir") && !b.last().contains("OPERATOR"),"compact localized prefix-free raw TPA delivery");
        check(b.clicks().size()==1 && b.clicks().stream().allMatch(c->c.action()==ClickEvent.Action.RUN_COMMAND),"only the Accept RUN_COMMAND is displayed; manual deny stays covered");
        check(b.clicks().stream().allMatch(c->c.value().contains(a.id+":")),"clicks select UUID plus one-time request id");
        check(containsColor(b.received.getLast(),"Alpha",NamedTextColor.GOLD),"current RankService color on literal name");
        check(!b.last().contains("<player>") && !b.last().contains("[missing:"),"placeholders fully localized");
        f.command(b,"tpdeny"); f.seconds(15); f.rankColor="red"; f.command(a,"tpahere","Beta");
        check(b.last().contains("dass du dich zu ihm") && containsColor(b.received.getLast(),"Alpha",NamedTextColor.RED),"here localization and refreshed rank color");
        a.permissions.clear();check(f.tab(a,"tpa","").isEmpty(),"completion requires current player permission");
        List<Component> console=new ArrayList<>(); var sender=proxy(ConsoleCommandSender.class,(method,args)-> {if(method.equals("sendMessage")){for(Object arg:args)if(arg instanceof Component c)console.add(c);return null;}throw new AssertionError(method);});
        f.requests.onCommand(sender,command("tpa"),"tpa",new String[]{"Beta"});check(console.size()==1 && plain(console.getFirst()).contains("only"),"console cannot send requests");
        int beforeUsage = b.received.size();
        f.requests.onCommand(b.player,command("tpaccept"),"tpaccept",new String[]{"Alpha","extra"});
        check(b.received.size() == beforeUsage + 1 && b.last().equals("Verwendung: /tpaccept [player]")
                && clicks(b.received.getLast()).equals(List.of(ClickEvent.suggestCommand("/tpaccept ")))
                && !b.last().contains("OPERATOR"), "invalid usage is one localized raw line with a safe editable suggestion");
    }
    private static boolean containsColor(Component component,String text,NamedTextColor color) {
        return plain(component).equals(text) && color.equals(component.color()) || component.children().stream().anyMatch(c->containsColor(c,text,color));
    }
    private static void check(boolean condition,String label) { checks++; if(!condition) throw new AssertionError(label); }
}

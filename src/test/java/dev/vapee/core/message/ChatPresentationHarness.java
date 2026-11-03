package dev.vapee.core.message;

import static dev.vapee.core.i18n.Translations.tr;
import dev.vapee.core.command.help.*;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.moderation.*;
import dev.vapee.core.moderation.command.*;
import dev.vapee.core.staff.*;
import dev.vapee.core.utility.tpa.TpaTestSupport;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Actual command/panel/TPA components, localized in both languages, with a deliberately visible global prefix. */
public final class ChatPresentationHarness {
    private static int checks;
    public static void main(String[] args)throws Exception{
        for(Locale locale:List.of(Locale.US,Locale.GERMANY)){channels(locale);moderation(locale);tpa(locale);staff(locale);help(locale);}
        System.out.println("ChatPresentationHarness passed "+checks+" checks.");
    }
    private static void channels(Locale locale)throws Exception{
        var f=new ModerationTestSupport.Fixture();f.target.locale=locale;var messages=messages(f);
        SocialChannel.STAFF.send(messages,f.target.sender,Component.text("Body"));Component staff=f.target.output.getLast();String prefix=prefix(locale);
        check(plain(staff).equals(prefix+" Body"),"exact localized Staff heading without operator wrapper "+locale);
        check(color(staff,prefix,NamedTextColor.DARK_GRAY)&&color(staff,"Body",MessageStyle.BODY),"subtle Staff heading and gray body");
        SocialChannel.FRIENDS.send(messages,f.target.sender,Component.text("friends"));
        check(plain(f.target.output.getLast()).startsWith("[Operator] "+(locale.equals(Locale.US)?"[Friends]":"[Freunde]")),"Friends channel retains established operator-prefix convention");
        SocialChannel.CLAN.send(messages,f.target.sender,Component.text("clan"));check(plain(f.target.output.getLast()).startsWith("[Operator] [Clan]"),"Clan channel identity retained");
        messages.send(f.target.sender,"<gold><value></gold>",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value",ModerationTestSupport.ATTACK));
        check(plain(f.target.output.getLast()).contains(ModerationTestSupport.ATTACK)&&events(f.target.output.getLast()).isEmpty(),"independent operator-authored formatting and literal dynamic input preserved");
    }
    private static void moderation(Locale locale)throws Exception{
        var f=new ModerationTestSupport.Fixture();f.staff.locale=locale;f.target.locale=locale;var c=context(f,messages(f));String prefix=prefix(locale);
        new MuteCommand(c).onCommand(f.staff.sender,null,"mute",new String[]{"Alex","1m",ModerationTestSupport.ATTACK});
        Component result=f.staff.output.getLast(),notice=f.target.output.getLast();
        check(plain(result).stripLeading().startsWith(prefix+" ")&&count(plain(result),prefix)==1&&!plain(result).contains("Operator"),"staff-issued mute result has exactly one Staff heading");
        check(color(result,"Alex",MessageStyle.HIGHLIGHT)&&color(result,"1m",MessageStyle.HIGHLIGHT),"target and duration values retain selective accent");
        check(plain(result).contains(ModerationTestSupport.ATTACK)&&framed(result)&&events(result).isEmpty(),"staff result preserves literal reason in two padded indented lines");
        check(!plain(notice).contains(prefix)&&!plain(notice).contains("Operator")&&plain(notice).contains(ModerationTestSupport.ATTACK)
                &&plain(notice).contains("UTC")&&framed(notice),"initial victim notice remains complete, raw, with padded indented lines");
        new UnmuteCommand(c).onCommand(f.staff.sender,null,"unmute",new String[]{"Alex","release <red>literal"});
        check(plain(f.target.output.getLast()).contains("release <red>literal")&&events(f.target.output.getLast()).isEmpty(),"unmute reason literal and victim-facing without Staff prefix");
        new WarnCommand(c).onCommand(f.staff.sender,null,"warn",new String[]{"Alex",ModerationTestSupport.ATTACK});
        check(plain(f.target.output.getLast()).contains(ModerationTestSupport.ATTACK)&&!plain(f.target.output.getLast()).contains(prefix),"warning victim is never framed as staff feedback");
        f.staff.output.clear();new HistoryCommand(c).onCommand(f.staff.sender,null,"history",new String[]{"Alex"});
        String history=f.staff.output.stream().map(ChatPresentationHarness::plain).reduce("",(a,b)->a+"\n"+b);
        check(count(history,prefix)==1&&!history.contains("Operator")&&history.contains(ModerationTestSupport.ATTACK),"history group receives one heading, no per-entry global prefix");
        check(f.staff.output.subList(1,f.staff.output.size()).stream().allMatch(component->plain(component).lines().count()==2),"history records compact without losing reason or expiry");
        check(f.staff.output.stream().skip(1).allMatch(component->component.hoverEvent()!=null)&&history.contains("UTC"),"full record IDs stay available in hover; timestamps visible");
        f.staff.output.clear();new MuteCommand(c).onCommand(f.staff.sender,null,"mute",new String[]{"Alex"});Component usage=f.staff.output.getLast();
        check(plain(usage).lines().count()==1&&plain(usage).contains("/mute <player|uuid> <duration|permanent> <reason...>"),"missing duration/reason gives one relevant syntax line");
        check(color(usage,"/mute",MessageStyle.HIGHLIGHT)&&color(usage,"<player|uuid>",MessageStyle.BODY),"syntax command accented, arguments neutral");
        int saves=f.repository.saves;f.staff.allowAll=false;f.staff.permissions.clear();new WarnCommand(c).onCommand(f.staff.sender,null,"warn",new String[]{"Alex","denied"});
        check(f.repository.saves==saves&&plain(f.staff.output.getLast()).startsWith(prefix+" "),"revoked permission keeps actual command boundary and Staff-scoped denial");
        f.staff.permissions.add("vapeecore.moderation.warn");new WarnCommand(c).onCommand(f.staff.sender,null,"warn",new String[]{"Alex","regranted"});check(f.repository.saves==saves+1,"regrant restores original action without presentation bypass");
        for(var command:List.<org.bukkit.command.TabExecutor>of(new KickCommand(c),new BanCommand(c),new UnbanCommand(c))){f.staff.allowAll=true;String name=command instanceof KickCommand?"kick":command instanceof BanCommand?"ban":"unban";String[] args=name.equals("ban")?new String[]{"Alex","permanent",ModerationTestSupport.ATTACK}:name.equals("kick")?new String[]{"Alex",ModerationTestSupport.ATTACK}:new String[]{"Alex"};command.onCommand(f.staff.sender,null,name,args);check(plain(f.staff.output.getLast()).startsWith(prefix+" "),"remaining staff action result scoped "+name);}
        check(f.target.kicks.stream().allMatch(component->plain(component).contains(ModerationTestSupport.ATTACK)&&!plain(component).contains(prefix)&&events(component).isEmpty()),"ban/kick screens preserve literal mandatory reasons without staff wrappers");
    }
    private static void tpa(Locale locale){
        var f=new TpaTestSupport.Fixture();var a=f.user("Alpha");var b=f.user("Beta");a.locale=locale;b.locale=locale;
        f.command(a,"tpa","Beta");Component incoming=b.received.getLast();List<ClickEvent> actions=events(incoming);
        check(actions.size()==1&&actions.stream().allMatch(event->event.action()==ClickEvent.Action.RUN_COMMAND&&event.value().contains(a.id+":")),"TPA retains exact UUID/request UUID RUN_COMMAND selectors");
        check(color(incoming,"Alpha",NamedTextColor.GOLD),"TPA keeps existing primary rank name color");
        f.command(b,"tpdeny",b.accept());f.command(a,"tpa","Beta");f.seconds(15);f.command(a,"tpahere","Beta");f.command(a,"tpcancel");
        check(java.util.stream.Stream.concat(a.received.stream(),b.received.stream()).allMatch(component->!plain(component).contains("[TPA]")&&!plain(component).contains("OPERATOR")&&!plain(component).contains("[Staff]")),"all observed TPA paths use prefix-free raw delivery");
        check(java.util.stream.Stream.concat(a.received.stream(),b.received.stream()).allMatch(component->plain(component).lines().count()==1),"request, denial, cooldown and cancellation remain one compact line");
    }
    private static void staff(Locale locale){
        var f=new StaffPanelTestSupport.Fixture();var viewer=f.player("Viewer","admin",StaffPanel.PERMISSION,"vapeecore.moderation.warn");var target=f.player("Target","default");viewer.locale=locale;target.locale=locale;
        f.open(viewer);f.select(viewer,target);f.click(viewer,StaffPanel.TARGET_LAYOUT.slot(StaffPanel.Action.WARN));f.drain();Component suggestion=viewer.messages.getLast();
        check(plain(suggestion).startsWith(prefix(locale)+" ")&&events(suggestion).stream().anyMatch(event->event.action()==ClickEvent.Action.SUGGEST_COMMAND&&event.value().equals("/warn "+target.id+" ")),"Staff suggestion heading and original UUID SUGGEST_COMMAND preserved");
        check(events(suggestion).stream().noneMatch(event->event.action()==ClickEvent.Action.RUN_COMMAND),"staff text input does not run a sanction");
        f.list(target);check(plain(target.messages.getLast()).startsWith(prefix(locale)+" ")&&plain(target.messages.getLast()).contains("Viewer"),"public roster uses Staff heading while retaining scope");
    }
    private static void help(Locale locale)throws Exception{
        var f=new ModerationTestSupport.Fixture();f.staff.locale=locale;f.staff.allowAll=false;f.staff.permissions.clear();var renderer=new CommandHelpRenderer(messages(f));
        var page=new CommandHelpPage("Help",List.of(new CommandHelpSection("Commands",List.of(new CommandHelpEntry("/one <name>","Literal <red>body</red>.",null,"Hint"),new CommandHelpEntry("/secret","Hidden","test.secret")))));
        renderer.send(f.staff.sender,page);Component result=f.staff.output.getLast();String text=plain(result);
        check(text.lines().count()==3&&text.contains("/one <name> — Literal <red>body</red>. (Hint)")&&!text.contains("/secret"),"one line per entry, hint inline, permission filtering retained");
        check(!text.contains("─")&&!bold(result)&&count(text,"[Operator]")==1,"no decorative separator, bold wall or duplicate prefix");
        check(color(result,"/one",MessageStyle.HIGHLIGHT)&&color(result,"<name>",MessageStyle.BODY),"help arguments neutral while command remains identifiable");
        check(events(result).stream().anyMatch(event->event.action()==ClickEvent.Action.SUGGEST_COMMAND&&event.value().equals("/one <name>")),"entire literal syntax still inserted by SUGGEST_COMMAND");
        f.staff.permissions.add("test.secret");renderer.send(f.staff.sender,page);check(plain(f.staff.output.getLast()).contains("/secret"),"help responds to permission regrant");
    }
    private static MessageService messages(ModerationTestSupport.Fixture f){return new MessageService(()->"<gold>[Operator]</gold> ",LocalizationService.bundled(f.logger));}
    private static ModerationCommandContext context(ModerationTestSupport.Fixture f,MessageService messages){var old=f.context;return new ModerationCommandContext(old.service(),old.identities(),messages,old.onlinePlayer(),old.onlinePlayers(),old.clock(),old.logger(),old.hierarchy(),old.mainThread(),old.active());}
    private static String prefix(Locale locale){return locale.equals(Locale.US)?"[Staff]":"[Team]";}
    private static boolean color(Component component,String text,net.kyori.adventure.text.format.TextColor color){return MessageColorAssertions.hasTextColor(component,text,color);}
    private static String plain(Component component){return ModerationTestSupport.plain(component);}
    private static boolean framed(Component component){String[] lines=plain(component).split("\n",-1);return lines.length==4&&lines[0].isEmpty()&&lines[3].isEmpty()&&lines[1].startsWith("  ")&&lines[2].startsWith("  ");}
    private static List<ClickEvent> events(Component component){List<ClickEvent> result=new ArrayList<>();if(component.clickEvent()!=null)result.add(component.clickEvent());component.children().forEach(child->result.addAll(events(child)));return result;}
    private static boolean bold(Component component){return component.decoration(TextDecoration.BOLD)==TextDecoration.State.TRUE||component.children().stream().anyMatch(ChatPresentationHarness::bold);}
    private static int count(String text,String value){return (text.length()-text.replace(value,"").length())/value.length();}
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
}

package dev.vapee.core.config;

import dev.vapee.core.i18n.*;
import dev.vapee.core.message.*;
import dev.vapee.core.reload.*;
import dev.vapee.core.utility.tpa.TpaTestSupport;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Actual config snapshots, rollback, resource evolution and rendered components; no operator files. */
public final class MessageAccentHarness {
    private static int checks;
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("vapeecore-message-accent-");Logger logger=Logger.getAnonymousLogger();logger.setUseParentHandlers(false);
        List<LogRecord> logs=new ArrayList<>();logger.addHandler(new Handler(){public void publish(LogRecord row){logs.add(row);}public void flush(){}public void close(){}});
        Path file=root.resolve("config.yml");ConfigService config=new ConfigService(file,logger);
        write(file,"messages: {}\n");config.load();check(config.getMessageAccentColor().value()==0xE5BF75&&MessageStyle.HIGHLIGHT.value()==0xE5BF75,"missing color and live palette default to restrained gold");
        Map<String,Integer> named=Map.ofEntries(Map.entry("black",0x000000),Map.entry("dark_blue",0x0000AA),Map.entry("dark_green",0x00AA00),Map.entry("dark_aqua",0x00AAAA),Map.entry("dark_red",0xAA0000),Map.entry("dark_purple",0xAA00AA),Map.entry("gold",0xFFAA00),Map.entry("gray",0xAAAAAA),Map.entry("dark_gray",0x555555),Map.entry("blue",0x5555FF),Map.entry("green",0x55FF55),Map.entry("aqua",0x55FFFF),Map.entry("red",0xFF5555),Map.entry("light_purple",0xFF55FF),Map.entry("yellow",0xFFFF55),Map.entry("white",0xFFFFFF));
        for(var entry:named.entrySet()){color(file,"  "+entry.getKey().toUpperCase(Locale.ROOT)+"  ");config.load();check(config.getMessageAccentColor().value()==entry.getValue()&&MessageStyle.HIGHLIGHT.value()==entry.getValue(),"named Minecraft color "+entry.getKey());}
        for(String hex:List.of("#E5BF75","#00fF7a","#000000","#FFFFFF")){color(file,hex);config.load();check(config.getMessageAccentColor().value()==Integer.parseInt(hex.substring(1),16),"six-digit hex "+hex);}
        for(String invalid:List.of("[]","42","true","''","'#fff'","'#GGGGGG'","'reset'","'bold'","'<red>'","'not-a-color'")){
            String source="messages:\n  accent-color: "+invalid+"\n";write(file,source);logs.clear();config.load();
            check(config.getMessageAccentColor().value()==0xE5BF75&&MessageStyle.HIGHLIGHT.value()==0xE5BF75,"invalid color falls back "+invalid);
            check(logs.stream().anyMatch(row->row.getLevel()==Level.WARNING&&row.getMessage().contains("messages.accent-color")),"invalid color has contextual warning");
            check(Files.readString(file).equals(source),"config load never rewrites invalid operator input");
        }
        color(file,"#E5BF75");config.load();Component old=MessageStyle.value("old");color(file,"#123456");var plan=config.prepareReload();
        check(config.getMessageAccentColor().value()==0xE5BF75&&MessageStyle.HIGHLIGHT.value()==0xE5BF75,"prepare is side-effect free for snapshot and palette");plan.apply();
        check(config.getMessageAccentColor().value()==0x123456&&MessageStyle.value("new").color().value()==0x123456,"apply activates new color for newly built dynamic values");
        check(old.color().value()==0xE5BF75,"already-built immutable components retain their original style");plan.rollback();
        check(config.getMessageAccentColor().value()==0xE5BF75&&MessageStyle.HIGHLIGHT.value()==0xE5BF75,"rollback restores previous snapshot and palette");
        color(file,"blue");ReloadParticipant fault=new ReloadParticipant(){public String getReloadName(){return "injected";}public ReloadPlan prepareReload(){return ReloadPlan.of(()->{check(MessageStyle.HIGHLIGHT.value()==0x5555FF,"real reload applied config before later participant");throw new IllegalStateException("injected apply failure");},()->{});}};
        check(new ReloadService(logger,List.of(config,fault)).reload().status()==ReloadResult.Status.APPLY_FAILED,"later failure uses actual rollback transaction");
        check(config.getMessageAccentColor().value()==0xE5BF75&&MessageStyle.HIGHLIGHT.value()==0xE5BF75,"later participant failure restores live palette too");
        write(file,"messages: [broken\n");try{config.prepareReload();throw new AssertionError("invalid YAML accepted");}catch(IllegalStateException expected){check(MessageStyle.HIGHLIGHT.value()==0xE5BF75,"invalid YAML preparation leaves active palette untouched");}
        color(file,"#123456");config.load();rendering(config,logger);evolution(root.resolve("evolution"),logger);
        color(file,"#E5BF75");config.load();
        System.out.println("MessageAccentHarness passed "+checks+" checks.");
    }
    private static void rendering(ConfigService config,Logger logger){
        var messages=new MessageService(config,LocalizationService.bundled(logger));
        Component literal=messages.deserialize("<accent>System</accent> <aqua>Template</aqua> <red>Error</red> <green>Success</green>");
        check(has(literal,"System",TextColor.color(0x123456))&&has(literal,"Template",NamedTextColor.AQUA),"only explicit accent tag changes; operator aqua remains literal");
        check(has(literal,"Error",NamedTextColor.RED)&&has(literal,"Success",NamedTextColor.GREEN),"success/error colors do not inherit palette");
        for(Locale locale:List.of(Locale.US,Locale.GERMANY)){
            Component usage=messages.localization().component(locale,"utility.build.usage");check(has(usage,"/build",TextColor.color(0x123456)),"localized syntax uses configured accent "+locale);
            Component friends=messages.localization().render(locale,SocialChannel.FRIENDS.message(Component.text("body")));
            Component clan=messages.localization().render(locale,SocialChannel.CLAN.message(Component.text("body")));
            check(has(friends,locale.equals(Locale.US)?"[Friends]":"[Freunde]",NamedTextColor.GOLD)&&has(clan,"[Clan]",NamedTextColor.DARK_PURPLE),"social identities remain independent "+locale);
        }
        check(has(MessageStyle.syntax("/ping [player]"),"/ping",TextColor.color(0x123456))&&has(MessageStyle.syntax("/ping [player]"),"[player]",MessageStyle.BODY),"help/syntax root follows palette; arguments remain neutral");
        var fixture=new TpaTestSupport.Fixture();var a=fixture.user("Alpha");var b=fixture.user("Beta");fixture.command(a,"tpa","Beta");
        check(b.clicks().size()==1&&b.clicks().getFirst().action()==ClickEvent.Action.RUN_COMMAND&&b.clicks().getFirst().value().startsWith("/vapeecore:tpaccept "+a.id+":"),"only Accept appears with original UUID/request-ID selector");
        check(has(b.received.getLast(),"Alpha",NamedTextColor.GOLD),"LuckPerms rank color remains independent of custom system accent");
        fixture.command(b,"tpdeny",a.id.toString());check(a.last().contains("denied"),"manual tpdeny remains fully supported");
        fixture.seconds(15);fixture.rankColor="aqua";fixture.command(a,"tpahere","Beta");check(TpaTestSupport.clicks(b.received.getLast()).size()==1&&b.clicks().getLast().value().startsWith("/vapeecore:tpaccept "),"TPAHere also supplies exactly one Accept action");
        check(has(b.received.getLast(),"Alpha",NamedTextColor.AQUA),"explicit LuckPerms aqua is not remapped by the system palette");
        String last=PlainTextComponentSerializer.plainText().serialize(b.received.getLast());check(!last.contains("[Deny]")&&!last.contains("[Ablehnen]")&&!last.contains("[TPA]"),"no deny button or wrapper in either request direction");
    }
    private static void evolution(Path root,Logger logger)throws Exception{
        Files.createDirectories(root);Path file=root.resolve("config.yml");String old="config-version: 1\nmessages:\n  prefix: '<aqua>[Custom]</aqua> '\noperator-extra: retained\n";write(file,old);
        var evolution=new ConfigEvolution(root,logger,name->MessageAccentHarness.class.getResourceAsStream("/"+name));check(evolution.evolve("config.yml"),"existing startup evolution upgrades v1 to resource v2");
        var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());check(yaml.getInt("config-version")==2&&yaml.getString("messages.accent-color").equals("#E5BF75"),"existing additive upgrade inserts gold default");
        check(yaml.getString("messages.prefix").equals("<aqua>[Custom]</aqua> ")&&yaml.getString("operator-extra").equals("retained"),"operator prefix/unknown values survive standard evolution");
        try(var backups=Files.list(root.resolve("backups/config"))){List<Path> paths=backups.toList();check(paths.size()==1&&Arrays.equals(Files.readAllBytes(paths.getFirst()),old.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"standard evolution preserves exact original backup");}
        byte[] after=Files.readAllBytes(file);check(!evolution.evolve("config.yml")&&Arrays.equals(after,Files.readAllBytes(file)),"supported v2 remains byte-idempotent");
        Path existing=root.resolve("existing");Files.createDirectories(existing);write(existing.resolve("config.yml"),"config-version: 1\nmessages:\n  accent-color: '#998877'\n  prefix: 'kept'\n");
        check(new ConfigEvolution(existing,logger,name->MessageAccentHarness.class.getResourceAsStream("/"+name)).evolve("config.yml"),"legacy config with existing custom accent evolves normally");
        var custom=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(existing.resolve("config.yml").toFile());check(custom.getString("messages.accent-color").equals("#998877")&&custom.getString("messages.prefix").equals("kept"),"additive defaults never overwrite existing custom accent or prefix");
    }
    private static void color(Path file,String value)throws Exception{write(file,"messages:\n  accent-color: '"+value+"'\n");}
    private static void write(Path file,String value)throws Exception{Files.writeString(file,value);}
    private static boolean has(Component component,String text,TextColor color){return MessageColorAssertions.hasTextColor(component,text,color);}
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

package dev.vapee.core.i18n;

import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LocalizationHarness {
    private static int checks;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    public static void main(String[] args) throws Exception {
        for (String tag : List.of("de-DE", "de-AT", "de-CH", "de", "DE-xy"))
            check(LanguageResolver.resolve(Locale.forLanguageTag(tag)) == Language.GERMAN, "German language variants");
        for (Locale locale : List.of(Locale.US, Locale.UK, Locale.FRANCE, Locale.JAPAN, Locale.forLanguageTag("pl-PL"), Locale.ROOT))
            check(LanguageResolver.resolve(locale) == Language.ENGLISH, "English fallback for unsupported language");
        check(LanguageResolver.resolve(null) == Language.ENGLISH, "unknown locale fallback");
        var en = Map.of("test.notice", "Notice for <player>", "test.only-en", "English only");
        var de = Map.of("test.notice", "Hinweis für <player>");
        var bundled = Map.of(Language.ENGLISH, en, Language.GERMAN, de);
        var logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        var diagnostics = new ArrayList<LogRecord>(); logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { diagnostics.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        var service = new LocalizationService(logger, new LocalizationService.Snapshot(bundled, Map.of()));
        check(service.lookup(Language.GERMAN,"test.only-en").equals("English only"), "A missing German fixture falls back to English");
        var directory = Files.createTempDirectory("vapeecore-i18n-");
        try {
            Files.writeString(directory.resolve("en.yml"), "test.notice: 'Override for <player>'\n", StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("de.yml"), "{}\n", StandardCharsets.UTF_8);
            var candidate = service.prepare(directory); service.apply(candidate);
            check(service.lookup(Language.GERMAN,"test.notice").equals("Hinweis für <player>"), "B missing live DE uses bundled DE before live EN");
            check(service.lookup(Language.ENGLISH,"test.notice").equals("Override for <player>"), "live EN overrides bundled EN");
            Files.writeString(directory.resolve("de.yml"), "test.notice: 'Eigener Hinweis für <player>'\n");
            var before = service.snapshot(); var complete = service.prepare(directory);
            check(service.snapshot() == before, "prepare never partially publishes"); service.apply(complete);
            check(service.lookup(Language.GERMAN,"test.notice").startsWith("Eigener"), "live DE overrides bundled DE");
            service.apply(before); check(service.lookup(Language.GERMAN,"test.notice").equals("Hinweis für <player>"), "rollback restores exact previous snapshot");
            Files.writeString(directory.resolve("de.yml"), "test: [broken\n");
            fails(() -> service.prepare(directory), "C malformed reload rejected"); check(service.snapshot()==before,"malformed reload retains active snapshot");
            Files.writeString(directory.resolve("de.yml"), "test.notice: 'Fehlt'\n");
            fails(() -> service.prepare(directory), "D missing required placeholder rejected");
            for (String invalid : List.of("bad: null", "bad: 7", "bad: ''", "bad: [text]", "bad: one\nbad: two"))
                fails(() -> TranslationCatalog.read(new StringReader(invalid)),"wrong/null/blank/duplicate catalog value rejected");
            var builtins = LocalizationService.bundledSnapshot();
            check(builtins.bundled().get(Language.ENGLISH).keySet().equals(builtins.bundled().get(Language.GERMAN).keySet()),"complete bundled key parity");
            builtins.bundled().get(Language.ENGLISH).forEach((key,value)->check(TranslationCatalog.placeholders(value).equals(TranslationCatalog.placeholders(builtins.bundled().get(Language.GERMAN).get(key))),"bundled placeholder parity: "+key));
            String injected="<red>HACK <click:run_command:'/op me'>CLICK";
            var rendered=service.component(Locale.GERMAN,"test.notice",Placeholder.component("player",Component.text(injected)));
            check(PLAIN.serialize(rendered).contains(injected),"E dynamic MiniMessage remains literal");
            check(!hasClick(rendered),"injected click tag never becomes click event");
            check(!hasColor(rendered),"injected color tag never becomes text style");
            var template=Translations.tr("test.notice",Map.of("player",Component.text(injected)));
            check(PLAIN.serialize(service.render(Locale.GERMAN,template)).contains(injected),"locale-independent named argument also stays literal");
            var german=new Viewer(Locale.GERMANY);var english=new Viewer(Locale.US);var messages=new MessageService(()->"",service);
            messages.send(german.player,Translations.tr("test.notice",Map.of("player",Component.text("Alex"))));
            messages.send(english.player,Translations.tr("test.notice",Map.of("player",Component.text("Alex"))));
            check(german.last().equals("Hinweis für Alex"),"recipient-specific German message");
            check(english.last().equals("Override for Alex"),"recipient-specific English message");
            english.locale=Locale.GERMANY;messages.send(english.player,template);
            check(english.last().startsWith("Hinweis"),"future sends read changed current player locale");
            english.locale=Locale.US;service.addLocaleListener(player->messages.send(player,Translations.tr("test.notice",Map.of("player",Component.text("Alex")))));
            service.localeChanged(english.player,Locale.GERMANY);check(english.last().equals("Hinweis für Alex"),"locale event renders using explicit new locale even before Player state changes");
            messages.send(english.player,Translations.tr("test.notice",Map.of("player",Component.text("Alex"))));
            check(english.last().equals("Override for Alex"),"event scope does not cache language");
            check(PLAIN.serialize(service.english("test.notice",Placeholder.unparsed("player","Console"))).equals("Override for Console"),"non-player English presentation");
            check(PLAIN.serialize(service.component(Locale.JAPAN,"test.notice",Placeholder.unparsed("player","Alex"))).equals("Override for Alex"),"F unsupported locale English");
            check(PLAIN.serialize(service.english("unknown.key")).equals("[missing:unknown.key]"),"unknown key safe visible fallback");
            check(diagnostics.stream().anyMatch(record->record.getMessage().contains("unknown.key")),"unknown key diagnostic");
            check(LocalizedText.parse(Map.of("en","Title","de","Titel")).resolve(Locale.GERMANY).equals("Titel"),"localized config map");
            check(LocalizedText.literal("<red>Custom").resolve(Locale.GERMANY).equals("<red>Custom"),"operator scalar stays exact universal literal");
        } finally {
            Files.deleteIfExists(directory.resolve("en.yml"));Files.deleteIfExists(directory.resolve("de.yml"));Files.deleteIfExists(directory);
        }
        System.out.println("LocalizationHarness passed "+checks+" checks.");
    }
    private static boolean hasClick(Component value){return value.clickEvent()!=null||value.children().stream().anyMatch(LocalizationHarness::hasClick);}
    private static boolean hasColor(Component value){return value.color()!=null||value.children().stream().anyMatch(LocalizationHarness::hasColor);}
    private static void fails(Runnable action,String reason){try{action.run();throw new AssertionError(reason);}catch(IllegalArgumentException expected){checks++;}}
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static final class Viewer {
        Locale locale;final UUID id=UUID.randomUUID();final List<Component> received=new ArrayList<>();final Player player;
        Viewer(Locale locale){this.locale=locale;player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "locale"->this.locale;case "getUniqueId"->id;case "sendMessage"->{if(args!=null)for(Object arg:args)if(arg instanceof Component c)received.add(c);yield null;}
            case "toString"->id.toString();case "hashCode"->id.hashCode();case "equals"->proxy==args[0];default->null;});}
        String last(){return PLAIN.serialize(received.getLast());}
    }
}

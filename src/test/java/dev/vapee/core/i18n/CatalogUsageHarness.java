package dev.vapee.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public final class CatalogUsageHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        var service = LocalizationService.bundled(logger);
        var english = service.snapshot().bundled().get(Language.ENGLISH);
        var plain = PlainTextComponentSerializer.plainText();
        for (var entry : english.entrySet()) {
            var values = new HashMap<String, Component>();
            TranslationCatalog.placeholders(entry.getValue()).forEach(name -> values.put(name, Component.text("literal<red> " + name)));
            Component template = Translations.tr(entry.getKey(), values);
            for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
                String rendered = plain.serialize(service.render(locale, template));
                check(!rendered.contains("[missing:") && !rendered.contains("vapeecore.i18n."), "complete rendered catalog " + entry.getKey());
                for (String name : values.keySet()) check(rendered.contains("literal<red> " + name), "safe placeholder consumed in " + entry.getKey());
            }
        }
        Pattern references = Pattern.compile("\\btr\\(\\s*\"([^\"]+)\"");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = references.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    check(english.containsKey(key) || key.endsWith(".") && english.keySet().stream().anyMatch(value -> value.startsWith(key)), "production catalog reference " + file + ": " + key);
                }
            }
        }
        Pattern resourceReference = Pattern.compile("<i18n:([a-zA-Z0-9_.-]+)>");
        try (var paths = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                var matcher = resourceReference.matcher(Files.readString(file));
                while (matcher.find()) check(english.containsKey(matcher.group(1)), "resource translation reference " + file);
            }
        }
        for (var action : dev.vapee.core.moderation.ModerationAction.values()) check(english.containsKey("moderation.action." + action.name()), "all moderation action captions");
        for (var mode : dev.vapee.core.player.settings.VisibilityMode.values()) check(english.containsKey("visibility.mode." + mode.name()), "all persisted quick visibility mode captions");
        System.out.println("CatalogUsageHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}

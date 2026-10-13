package dev.vapee.core.i18n;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Resolves presentation only. Delivery, prefixes and domain authorization stay with their owners. */
public final class LocalizationService {
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Set<Consumer<Player>> localeListeners = new LinkedHashSet<>();
    private volatile Snapshot snapshot;
    // Only the synchronous locale-change event has an explicit locale scope; never persisted/cached.
    private UUID eventPlayer;
    private Locale eventLocale;

    public LocalizationService(Logger logger, Snapshot snapshot) {
        this.logger = Objects.requireNonNull(logger); this.snapshot = Objects.requireNonNull(snapshot);
    }
    public static LocalizationService bundled(Logger logger) { return new LocalizationService(logger, bundledSnapshot()); }
    public static Snapshot bundledSnapshot() {
        var catalogs = new EnumMap<Language, Map<String, String>>(Language.class);
        for (Language language : Language.values()) {
            var stream = LocalizationService.class.getResourceAsStream("/lang/" + language.code() + ".yml");
            if (stream == null) throw new IllegalStateException("Missing bundled language: " + language.code());
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                catalogs.put(language, TranslationCatalog.read(reader));
            } catch (java.io.IOException exception) { throw new IllegalStateException("Could not read bundled catalog", exception); }
        }
        catalogs.forEach((language, values) -> TranslationCatalog.validateParity(catalogs.get(Language.ENGLISH), values, true));
        return new Snapshot(catalogs, Map.of());
    }
    public Snapshot prepare(Path directory) {
        var live = new EnumMap<Language, Map<String, String>>(Language.class);
        for (Language language : Language.values()) {
            Path file = directory.resolve(language.code() + ".yml");
            if (!Files.exists(file)) { live.put(language, Map.of()); continue; }
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, String> values = TranslationCatalog.read(reader);
                TranslationCatalog.validateParity(snapshot.bundled().get(Language.ENGLISH), values, false);
                live.put(language, values);
            } catch (java.io.IOException exception) { throw new IllegalStateException("Could not read " + file, exception); }
        }
        return new Snapshot(snapshot.bundled(), live);
    }
    public Snapshot snapshot() { return snapshot; }
    public void apply(Snapshot candidate) { snapshot = Objects.requireNonNull(candidate); }
    public Locale locale(Audience audience) {
        if (!(audience instanceof Player player)) return Locale.ENGLISH;
        if (eventPlayer != null && eventPlayer.equals(player.getUniqueId())) return eventLocale;
        return player.locale();
    }
    public Component component(Audience audience, String key, TagResolver... placeholders) {
        return component(locale(audience), key, placeholders);
    }
    public Component english(String key, TagResolver... placeholders) { return component(Locale.ENGLISH, key, placeholders); }
    public Component component(Locale locale, String key, TagResolver... placeholders) {
        String value = lookup(LanguageResolver.resolve(locale), key);
        if (value == null) { logger.severe("Missing translation key: " + key); return Component.text("[missing:" + key + "]"); }
        try { return miniMessage.deserialize(value, TagResolver.resolver(placeholders)); }
        catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not render translation: " + key, exception);
            return Component.text("[missing:" + key + "]");
        }
    }
    public String lookup(Language language, String key) {
        Snapshot current = snapshot;
        for (Language candidate : language == Language.ENGLISH ? List.of(Language.ENGLISH) : List.of(language, Language.ENGLISH)) {
            String value = current.live().getOrDefault(candidate, Map.of()).get(key);
            if (value != null) return value;
            value = current.bundled().getOrDefault(candidate, Map.of()).get(key);
            if (value != null) return value;
        }
        return null;
    }
    public Component render(Audience audience, Component template) { return render(locale(audience), template); }
    public Component render(Locale locale, Component template) {
        Component result;
        if (template instanceof TranslatableComponent translated && translated.key().startsWith(Translations.PREFIX)) {
            var resolvers = new ArrayList<TagResolver>();
            for (var argument : translated.arguments()) {
                if (!(argument.value() instanceof TranslatableComponent named) || !named.key().startsWith(Translations.ARGUMENT)
                        || named.arguments().size() != 1 || !(named.arguments().getFirst().value() instanceof Component value))
                    throw new IllegalArgumentException("Invalid localization argument");
                resolvers.add(Placeholder.component(named.key().substring(Translations.ARGUMENT.length()), render(locale, value)));
            }
            result = component(locale, translated.key().substring(Translations.PREFIX.length()), resolvers.toArray(TagResolver[]::new))
                    .applyFallbackStyle(template.style());
            for (Component child : template.children()) result = result.append(render(locale, child));
        } else {
            result = template.children(template.children().stream().map(child -> render(locale, child)).toList());
        }
        var hover = result.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT && hover.value() instanceof Component text)
            result = result.hoverEvent(HoverEvent.showText(render(locale, text)));
        return result;
    }
    public void addLocaleListener(Consumer<Player> listener) { localeListeners.add(Objects.requireNonNull(listener)); }
    public void removeLocaleListener(Consumer<Player> listener) { localeListeners.remove(listener); }
    public void localeChanged(Player player, Locale locale) {
        UUID previousPlayer = eventPlayer; Locale previousLocale = eventLocale;
        try {
            eventPlayer = player.getUniqueId(); eventLocale = locale;
            for (var listener : List.copyOf(localeListeners)) {
                try { listener.accept(player); }
                catch (RuntimeException exception) { logger.log(Level.WARNING, "Locale view update failed for " + eventPlayer, exception); }
            }
        } finally { eventPlayer = previousPlayer; eventLocale = previousLocale; }
    }
    public record Snapshot(Map<Language, Map<String, String>> bundled, Map<Language, Map<String, String>> live) {
        public Snapshot { bundled = immutable(bundled); live = immutable(live); }
        private static Map<Language, Map<String, String>> immutable(Map<Language, Map<String, String>> source) {
            var copy = new EnumMap<Language, Map<String, String>>(Language.class);
            source.forEach((language, values) -> copy.put(language, Map.copyOf(values)));
            return Collections.unmodifiableMap(copy);
        }
    }
}

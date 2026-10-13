package dev.vapee.core.i18n;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class TranslationCatalog {
    private static final Pattern TAG = Pattern.compile("(?<!\\\\)<([a-zA-Z][a-zA-Z0-9_-]*)(?=[:>])");
    private TranslationCatalog() { }
    public static Map<String, String> read(Reader reader) {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        Object root;
        try { root = new Yaml(new SafeConstructor(options)).load(reader); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid language catalog", exception); }
        var values = new LinkedHashMap<String, String>();
        if (root == null) return Map.of();
        if (!(root instanceof Map<?, ?> map)) throw new IllegalArgumentException("Language catalog must be a section");
        flatten("", map, values);
        return Map.copyOf(values);
    }
    private static void flatten(String prefix, Map<?, ?> source, Map<String, String> destination) {
        source.forEach((rawKey, value) -> {
            if (!(rawKey instanceof String key) || key.isBlank()) throw new IllegalArgumentException("Invalid translation key");
            String path = prefix + key;
            if (value instanceof Map<?, ?> nested) { flatten(path + ".", nested, destination); return; }
            if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Translation must be a nonblank string: " + path);
            if (destination.putIfAbsent(path, text) != null) throw new IllegalArgumentException("Duplicate translation key: " + path);
        });
    }
    public static Set<String> placeholders(String value) {
        var names = new TreeSet<String>(); var matcher = TAG.matcher(value);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            if (!TagResolver.standard().has(name)) names.add(name);
        }
        return Set.copyOf(names);
    }
    public static void validateParity(Map<String, String> english, Map<String, String> other, boolean complete) {
        if (complete && !english.keySet().equals(other.keySet()))
            throw new IllegalArgumentException("Bundled language key sets differ");
        other.forEach((key, value) -> {
            String canonical = english.get(key);
            if (canonical != null && !placeholders(canonical).equals(placeholders(value)))
                throw new IllegalArgumentException("Translation placeholder mismatch: " + key);
        });
    }
}

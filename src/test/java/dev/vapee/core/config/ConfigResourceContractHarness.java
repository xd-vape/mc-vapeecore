package dev.vapee.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Frozen independent operator inventory; it does not infer expected versions from production. */
public final class ConfigResourceContractHarness {
    private static final Map<String, Integer> EXPECTED =
            Map.of(
                    "config.yml",
                    1,
                    "lobby.yml",
                    2,
                    "chat.yml",
                    1,
                    "private-messages.yml",
                    1,
                    "presentation.yml",
                    1,
                    "daily-quests.yml",
                    1);
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (var expected : EXPECTED.entrySet()) {
            String name = expected.getKey();
            Integer supported = ConfigEvolution.MANAGED_CONFIGS.get(name);
            check(supported != null, "managed resource registered: " + name);
            try (InputStream resource =
                    ConfigResourceContractHarness.class.getResourceAsStream("/" + name)) {
                check(resource != null, "managed resource exists: " + name);
                var document = read(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                Object version = document.get("config-version");
                check(
                        version instanceof Integer && (int) version > 0,
                        "resource has positive integer version: " + name);
                check(version.equals(supported), "resource/registry version agrees: " + name);
                check(
                        version.equals(expected.getValue()),
                        "resource version remains independently frozen: " + name);
                if (name.equals("config.yml")) {
                    check(
                            ((Map<?, ?>) document.get("ranks")).get("track").equals("ranks"),
                            "bundled ranks.track default remains independently frozen");
                }
            }
        }
        check(
                ConfigEvolution.MANAGED_CONFIGS.equals(EXPECTED),
                "exact frozen operator allowlist and versions");
        for (String excluded :
                List.of(
                        "players/a.yml",
                        "friends.yml",
                        "clans.yml",
                        "warps.yml",
                        "moderation.yml",
                        "blackjack.yml",
                        "lang/en.yml",
                        "lang/de.yml")) {
            check(
                    !ConfigEvolution.MANAGED_CONFIGS.containsKey(excluded),
                    "data/localization excluded: " + excluded);
        }
        try {
            ConfigEvolution.MANAGED_CONFIGS.put("extra.yml", 1);
            throw new AssertionError("mutable public allowlist");
        } catch (UnsupportedOperationException expected) {
            check(true, "public allowlist immutable");
        }
        for (String invalid :
                List.of(
                        "config-version: [broken\n",
                        "- list-root\n",
                        "scalar-root\n",
                        "",
                        "config-version: 1\nconfig-version: 2\n",
                        "config-version: 1\nsection: {value: 1, value: 2}\n")) {
            reject(invalid);
        }
        System.out.println("ConfigResourceContractHarness passed " + checks + " checks.");
    }

    private static Map<?, ?> read(String source) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object root = new Yaml(new SafeConstructor(options)).load(source);
        if (!(root instanceof Map<?, ?> mapping))
            throw new IllegalArgumentException("operator root must be a mapping");
        return mapping;
    }

    private static void reject(String invalid) {
        try {
            read(invalid);
            throw new AssertionError("invalid/duplicate resource accepted: " + invalid);
        } catch (RuntimeException expected) {
            check(true, "strict resource parser rejects malformed/duplicate/root input");
        }
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}

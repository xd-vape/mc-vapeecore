package dev.vapee.core.config;

import dev.vapee.core.chat.config.ChatConfig;
import dev.vapee.core.presentation.config.PresentationConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class DefaultConsistencyHarness {

    private static int checks;

    private DefaultConsistencyHarness() {
    }

    public static void main(String[] args) throws Exception {
        YamlConfiguration chat = resource("chat.yml");
        ChatConfig.State chatDefaults = defaults(ChatConfig.State.class);
        check(chatDefaults.enabled() == chat.getBoolean("enabled"),
                "chat enabled fallback matches the resource");
        check(chatDefaults.format().equals(chat.getString("format"))
                        && chatDefaults.format().equals(ChatConfig.DEFAULT_FORMAT),
                "chat format fallback exactly matches the rank-colored resource default");
        check(chatDefaults.metaFormat() == ChatConfig.MetaFormat.LEGACY_AMPERSAND
                        && chat.getString("luckperms-meta.format").equals("legacy-ampersand"),
                "chat LuckPerms meta fallback matches the resource");

        YamlConfiguration presentation = resource("presentation.yml");
        PresentationConfig.State presentationDefaults = defaults(PresentationConfig.State.class);
        check(presentationDefaults.enabled() == presentation.getBoolean("enabled")
                        && presentationDefaults.updateIntervalTicks() == presentation.getLong("update-interval-ticks")
                        && presentationDefaults.metaFormat() == PresentationConfig.MetaFormat.LEGACY_AMPERSAND
                        && presentation.getString("luckperms-meta.format").equals("legacy-ampersand"),
                "presentation general fallbacks match the resource");
        check(presentationDefaults.scoreboardEnabled() == presentation.getBoolean("scoreboard.enabled")
                        && presentationDefaults.scoreboardLobbyOnly() == presentation.getBoolean("scoreboard.lobby-only")
                        && presentationDefaults.scoreboardTitle().equals(presentation.getString("scoreboard.title")),
                "presentation scoreboard settings match the resource");
        List<String> scoreboardLines = presentation.getStringList("scoreboard.lines");
        check(presentationDefaults.scoreboardLines().equals(scoreboardLines)
                        && scoreboardLines.contains("<rank>")
                        && scoreboardLines.stream().anyMatch(line -> line.contains("<playtime>"))
                        && scoreboardLines.stream().noneMatch(line -> line.contains("<group>")
                                || line.contains("<white><rank>")),
                "scoreboard fallback matches the resource with colored rank and playtime");
        check(presentationDefaults.tablistEnabled() == presentation.getBoolean("tablist.enabled")
                        && presentationDefaults.tablistNameFormat().equals(
                                presentation.getString("tablist.name-format")
                        )
                        && presentationDefaults.tablistNameFormat().equals(
                                PresentationConfig.DEFAULT_TABLIST_NAME_FORMAT
                        ),
                "tablist name fallback matches the rank-colored resource default");
        check(presentationDefaults.tablistHeader().equals(presentation.getStringList("tablist.header"))
                        && presentationDefaults.tablistFooter().equals(presentation.getStringList("tablist.footer")),
                "tablist header and footer fallbacks match the resource");

        System.out.println("DefaultConsistencyHarness passed " + checks + " checks.");
    }

    private static YamlConfiguration resource(String name) throws Exception {
        try (InputStream stream = DefaultConsistencyHarness.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new AssertionError("Missing resource " + name);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T defaults(Class<T> stateClass) throws Exception {
        Method method = stateClass.getDeclaredMethod("defaults");
        method.setAccessible(true);
        return (T) method.invoke(null);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

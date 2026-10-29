package dev.vapee.core.lobby.experience.navigator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Logger;

public final class NavigatorConfigHarness {
    private static int checks;
    static final String CONFIG = """
            config-version: 1
            enabled: true
            root: root
            menus:
              root:
                title: {en: Navigator, de: Navigator}
                entries:
                  spawn: {type: WARP, slot: 20, icon: COMPASS, name: Spawn, warp: custom_000}
                  games: {type: MENU, slot: 22, icon: NETHER_STAR, name: {en: Activities, de: Aktivitäten}, menu: games}
                  builder: {type: WARP, slot: 24, icon: DIAMOND_PICKAXE, name: Builder, warp: secret_builder, permission: vapeecore.navigator.builder}
              games:
                title: {en: Activities, de: Aktivitäten}
                entries:
                  parkour: {type: WARP, slot: 20, icon: FEATHER, name: {en: Parkour, de: Parkour}, warp: custom_001}
                  later: {type: UNAVAILABLE, slot: 24, icon: GRAY_DYE, name: {en: Future, de: Demnächst}}
                  more: {type: MENU, page: 1, slot: 20, icon: NETHER_STAR, name: More, menu: nested}
              nested:
                title: Nested
                entries: {}
            """;

    public static void main(String[] args) throws Exception {
        var state = NavigatorConfig.parse(CONFIG);
        check(state.enabled() && state.root().equals("root") && state.menus().size() == 3, "valid root and nested graph");
        check(state.menus().get("root").entries().get("games").name().resolve(Locale.GERMANY).equals("Aktivitäten"), "existing localized text semantics");
        check(NavigatorConfig.parse(CONFIG.replace("\n", "\r\n")).equals(state), "CRLF and LF parse identically");
        check(!NavigatorConfig.parse("enabled: false\n").enabled(), "not configured retains legacy");
        try { state.menus().clear(); throw new AssertionError("mutable menus"); }
        catch (UnsupportedOperationException expected) { check(true, "immutable menu snapshot"); }
        try { state.menus().get("root").entries().clear(); throw new AssertionError("mutable entries"); }
        catch (UnsupportedOperationException expected) { check(true, "immutable entries"); }
        for (String invalid : new String[]{
                CONFIG.replace("enabled: true", "enabled: yesplease"),
                CONFIG.replace("config-version: 1", "config-version: 2"),
                CONFIG.replace("root: root", "root: absent"),
                CONFIG.replace("menu: games", "menu: absent"),
                CONFIG.replace("menu: nested", "menu: root"),
                CONFIG.replace("menu: games", "menu: root"),
                CONFIG.replace("type: WARP", "type: COMMAND"),
                CONFIG.replace("slot: 20, ", ""),
                CONFIG.replace("slot: 22", "slot: 20"),
                CONFIG.replace("slot: 22", "slot: 48"),
                CONFIG.replace("slot: 22", "slot: -1"),
                CONFIG.replace("slot: 22", "slot: 54"),
                CONFIG.replace("slot: 22", "slot: 1.25"),
                CONFIG.replace("page: 1", "page: -1"),
                CONFIG.replace("page: 1", "page: 1000"),
                CONFIG.replace("icon: FEATHER", "icon: AIR"),
                CONFIG.replace("icon: FEATHER", "icon: NOT_AN_ITEM"),
                CONFIG.replace("vapeecore.navigator.builder", "admin or owner"),
                CONFIG.replace("warp: custom_000", "warp: Uppercase"),
                CONFIG.replace("name: Spawn", "name: {de: Ohne Englisch}"),
                CONFIG.replace("title: Nested", "title: ''"),
                CONFIG.replace("type: UNAVAILABLE", "type: UNAVAILABLE, warp: forbidden"),
                CONFIG.replace("name: Spawn", "name: Spawn, x: 500"),
                CONFIG.replace("slot: 20, icon: COMPASS", "slot: 20, slot: 21, icon: COMPASS"),
                "enabled: true\nmenus: [invalid]\n", "[broken"
        }) {
            if (invalid.equals(CONFIG)) continue;
            reject(invalid);
        }
        check(NavigatorConfig.parse("enabled: false\nmenus: ignored\n") == NavigatorConfig.legacy(),
                "explicit disabled V2 retains legacy without interpreting inactive composition");
        reject(CONFIG.replace("    entries:\n", "    entries:\n      duplicate: {type: UNAVAILABLE, slot: 1, icon: STONE, name: First}\n      duplicate: {type: UNAVAILABLE, slot: 2, icon: STONE, name: Second}\n"));
        String counted = CONFIG.replace("warp: custom_001", "warp: custom_001, count: {world: lobby-world, min: {x: 10, y: 80, z: 10}, max: {x: 0, y: 60, z: 0}, stack-amount: true}");
        var entry = NavigatorConfig.parse(counted).menus().get("games").entries().get("parkour");
        check(entry.region().minX() == 0 && entry.region().maxY() == 80 && entry.stackAmount(), "explicit count bounds normalize");
        reject(counted.replace("x: 10", "x: .nan"));
        reject(counted.replace("world: lobby-world", "world: ''"));
        reject(counted.replace("stack-amount: true", "stack-amount: bad"));
        Path directory = Files.createTempDirectory("navigator-config-");
        var logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        try {
            Path file = directory.resolve("navigator.yml");
            var config = new NavigatorConfig(file, logger); config.initialize();
            check(!config.getState().enabled() && Files.exists(file), "safe bundled default created only when absent");
            check(NavigatorConfig.parse(Files.readString(file).replace("enabled: false", "enabled: true")).enabled(), "bundled opt-in example is valid");
            Files.writeString(file, CONFIG); byte[] valid = Files.readAllBytes(file); config.initialize();
            check(config.getState().enabled() && Arrays.equals(valid, Files.readAllBytes(file)), "existing operator file never rewritten");
            Files.writeString(file, "enabled: true\nmenus: broken\n"); byte[] invalid = Files.readAllBytes(file); config.initialize();
            check(config.getState() == NavigatorConfig.unavailable() && Arrays.equals(invalid, Files.readAllBytes(file)),
                    "invalid config fails closed without exposing legacy destinations or writing");
            check(!dev.vapee.core.config.ConfigEvolution.MANAGED_CONFIGS.containsKey("navigator.yml"), "outside existing six-file evolution");
        } finally {
            try (var paths = Files.walk(directory)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        System.out.println("NavigatorConfigHarness passed " + checks + " checks.");
    }
    private static void reject(String source) {
        try { NavigatorConfig.parse(source); throw new AssertionError("Invalid Navigator accepted: " + source); }
        catch (IllegalArgumentException | org.yaml.snakeyaml.error.YAMLException expected) { check(true, "invalid config rejected"); }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

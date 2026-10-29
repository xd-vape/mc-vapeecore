package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.i18n.LocalizedText;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only the Navigator v1 YAML representation; coordinates here belong solely to optional counters. */
final class NavigatorDocument {
    private NavigatorDocument() { }
    static String write(NavigatorConfig.State state) {
        var root = map("config-version", 1, "enabled", state.enabled(), "root", state.root());
        var menus = new LinkedHashMap<String, Object>();
        for (var menu : state.menus().values()) {
            var data = map("title", text(menu.title()), "icon", menu.icon().name());
            if (menu.size() != 0) data.put("size", menu.size());
            if (menu.backSlot() >= 0) data.put("back-slot", menu.backSlot());
            var entries = new LinkedHashMap<String, Object>();
            for (var entry : menu.entries().values()) {
                var item = map("type", entry.type().name(), "page", entry.page(), "slot", entry.slot(),
                        "icon", entry.icon().name(), "name", text(entry.name()));
                if (!entry.lore().isEmpty()) item.put("lore", entry.lore().stream().map(NavigatorDocument::text).toList());
                if (!entry.permission().isEmpty()) item.put("permission", entry.permission());
                if (entry.type() == NavigatorConfig.Type.WARP) item.put("warp", entry.target());
                if (entry.type() == NavigatorConfig.Type.MENU) item.put("menu", entry.target());
                var region = entry.region();
                if (region != null) item.put("count", map("world", region.world(),
                        "min", map("x", region.minX(), "y", region.minY(), "z", region.minZ()),
                        "max", map("x", region.maxX(), "y", region.maxY(), "z", region.maxZ()),
                        "stack-amount", entry.stackAmount()));
                entries.put(entry.id(), item);
            }
            data.put("entries", entries); menus.put(menu.id(), data);
        }
        root.put("menus", menus);
        var options = new DumperOptions(); options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2); options.setSplitLines(false);
        return new Yaml(options).dump(root);
    }
    private static Object text(LocalizedText text) { return new java.util.TreeMap<>(text.values()); }
    private static Map<String, Object> map(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}

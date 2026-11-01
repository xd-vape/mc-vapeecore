package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;

import dev.vapee.core.lobby.experience.navigator.NavigatorEditorHarness.Fixture;
import dev.vapee.core.lobby.experience.navigator.NavigatorFixture.TestPlayer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.event.inventory.ClickType;

/** Executable GUI, persistence and player-block boundary regressions for the two height modes. */
public final class NavigatorRegionHeightHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        newRegions(); existingRegions(); modeOwnership(); countedBlocks();
        System.out.println("NavigatorRegionHeightHarness passed " + checks + " checks.");
    }
    private static void screen(Fixture t, TestPlayer p) throws Exception {
        int slot = t.entry(p).slot();
        t.cmd(p, "edit"); t.click(p, p.open, slot, ClickType.LEFT); t.click(p, p.open, 18, ClickType.LEFT);
    }
    private static TestPlayer warp(Fixture t) {
        var p = t.admin(); t.cmd(p, "edit"); t.cmd(p, "add warp custom_000"); return p;
    }
    private static void newRegions() throws Exception {
        for (int[] limits : List.of(new int[]{-64, 320}, new int[]{0, 256}, new int[]{-128, 512}, new int[]{64, 192})) {
            try (var t = new Fixture()) {
                var p = warp(t); var world = world("custom-height-" + limits[0], limits[0], limits[1]);
                t.f.worlds.put(world.getName(), world); p.currentWorld = world;
                byte[] before = Files.readAllBytes(t.file); var initial = t.s(p).draft;
                screen(t, p);
                check(text(p.open, 6).equals("Height: Full world height") && t.s(p).draft == initial && t.entry(p).region() == null,
                        "new region defaults to full height without creating or mutating bounds");
                p.x = -3.8; p.y = limits[0] + 11.9; p.z = -8.1; t.cmd(p, "region pos1");
                check(t.s(p).first.x() == -4 && t.s(p).first.y() == limits[0] + 11 && t.s(p).first.z() == -9,
                        "fallback command floors all marked block coordinates, including negative XZ");
                p.x = 2.75; p.y = limits[0] + 21.2; p.z = -2.01; t.cmd(p, "region pos2");
                var full = t.entry(p).region();
                check(full.minY() == limits[0] && full.maxY() == limits[1] - 1, "new region uses the selected world's inclusive height");
                check(full.minX() == -4 && full.maxX() == 2 && full.minZ() == -9 && full.maxZ() == -3, "XZ identify exactly the selected corner blocks");
                screen(t, p); t.click(p, p.open, 6, ClickType.LEFT);
                var manual = t.entry(p).region();
                check(manual.minY() == limits[0] + 11 && manual.maxY() == limits[0] + 21,
                        "switch to manual restores Y from the two original marks");
                check(manual.minX() == full.minX() && manual.maxX() == full.maxX() && manual.minZ() == full.minZ() && manual.maxZ() == full.maxZ(),
                        "manual toggle changes only Y");
                p.locale = Locale.GERMANY; screen(t, p);
                check(text(p.open, 6).equals("Höhe: Manuelle Höhe") && spec(p.open, 6).lore().stream().map(NavigatorFixture::plain).anyMatch(s -> s.contains("Gesamte Welthöhe")),
                        "German control explains selected and next mode");
                t.click(p, p.open, 6, ClickType.LEFT);
                check(text(p.open, 6).equals("Höhe: Gesamte Welthöhe") && t.entry(p).region().equals(full), "full-height toggle is reversible without XZ drift");
                check(Arrays.equals(before, Files.readAllBytes(t.file)) && !t.config.getState().enabled(), "both modes remain unsaved and unpublished");
                t.cmd(p, "save"); t.cmd(p, "edit"); t.cmd(p, "select custom_000"); screen(t, p);
                check(t.entry(p).region().equals(full) && text(p.open, 6).equals("Höhe: Gesamte Welthöhe"),
                        "Save/reopen infers a full-height region from concrete bounds, without a new config key");
                check(NavigatorConfig.parse(Files.readString(t.file)).equals(t.config.getState()), "existing config round trip persists exact Y bounds");
            }
        }
    }
    private static void existingRegions() throws Exception {
        try (var t = new Fixture()) {
            var p = warp(t); screen(t, p); t.click(p, p.open, 6, ClickType.LEFT);
            p.x = -8; p.y = 60; p.z = -9; t.cmd(p, "region pos1"); p.x = -2; p.y = 80; p.z = -3; t.cmd(p, "region pos2");
            t.cmd(p, "entry stack false"); t.cmd(p, "entry lore all Authored region lore"); t.cmd(p, "save");
            var existing = t.config.getState().menus().get("root").entries().get("custom_000"); byte[] saved = Files.readAllBytes(t.file);
            t.cmd(p, "edit"); t.cmd(p, "select custom_000"); var originalDraft = t.s(p).draft; screen(t, p);
            check(t.entry(p).equals(existing) && t.s(p).draft == originalDraft && text(p.open, 6).equals("Height: Manual height"),
                    "existing region is unchanged on opening, with its manual height shown");
            p.currentWorld = world("administrator-elsewhere", 0, 128); t.click(p, p.open, 6, ClickType.LEFT);
            check(t.entry(p).region().minY() == -64 && t.entry(p).region().maxY() == 319, "existing expansion uses region world, never administrator world");
            check(!t.entry(p).stackAmount() && t.entry(p).lore().equals(existing.lore()) && t.entry(p).name().equals(existing.name()),
                    "height changes preserve operator stack opt-out and authored styling");
            t.click(p, p.open, 6, ClickType.LEFT);
            check(t.entry(p).equals(existing) && Arrays.equals(saved, Files.readAllBytes(t.file)), "switching back restores the exact previous region without file writes");
            t.f.worlds.remove("lobby-world"); var before = t.s(p).draft; t.click(p, p.open, 6, ClickType.LEFT);
            check(t.s(p).draft == before && t.entry(p).equals(existing) && p.output().contains("world is not loaded"),
                    "unloaded region world refuses expansion instead of inventing world bounds");
            t.f.worlds.put("lobby-world", t.f.lobby); t.cmd(p, "save");
            check(t.config.getState().menus().get("root").entries().get("custom_000").equals(existing), "ordinary Save never automatically expands an existing region");
        }
    }
    private static void modeOwnership() throws Exception {
        try (var t = new Fixture()) {
            var p = warp(t); screen(t, p); t.click(p, p.open, 6, ClickType.LEFT);
            t.cmd(p, "add warp custom_001"); screen(t, p);
            check(text(p.open, 6).equals("Height: Full world height"), "height mode is scoped to one entry");
            t.cmd(p, "select custom_000"); screen(t, p);
            check(text(p.open, 6).equals("Height: Manual height"), "changing selection retains original entry's manual choice");
            t.cmd(p, "menu create child Child"); t.cmd(p, "select custom_000"); t.cmd(p, "entry move child 11"); screen(t, p);
            check(text(p.open, 6).equals("Height: Manual height"), "entry move carries its draft height choice");
            var stale = p.open; t.click(p, p.open, 6, ClickType.LEFT); var before = t.s(p).draft;
            t.click(p, stale, 6, ClickType.LEFT);
            check(t.s(p).draft == before && text(p.open, 6).equals("Height: Full world height"), "stale height screen cannot toggle current mode");
            var other = t.admin(); t.click(other, p.open, 6, ClickType.LEFT);
            check(t.s(p).draft == before && other.open == other.bottom, "foreign UUID cannot toggle owner's region height");
            t.cmd(p, "region pos1"); var first = t.s(p).first; screen(t, p); t.click(p, p.open, 6, ClickType.LEFT);
            check(t.s(p).first == first, "height toggle preserves scoped unfinished Pos1");
            t.cmd(p, "region clear"); screen(t, p);
            check(text(p.open, 6).equals("Height: Full world height"), "clear resets only that entry to new-region default");
            p.permissions.clear(); t.click(p, p.open, 6, ClickType.LEFT);
            check(t.s(p) == null && p.open == p.bottom && !t.config.getState().enabled(), "revocation rejects the new action and releases the session without Save");
        }
    }
    private static void countedBlocks() throws Exception {
        var f = new NavigatorFixture(); var viewer = f.player(); var target = f.player();
        var r = new NavigatorRegion("lobby-world", -4, -64, -9, 2, 319, -3);
        double[] mins = {-4, -64, -9}, maxs = {2, 319, -3};
        for (int axis = 0; axis < 3; axis++) {
            double[] values = {Math.nextDown(mins[axis]), mins[axis], mins[axis] + 0.5, maxs[axis], Math.nextDown(maxs[axis] + 1), maxs[axis] + 1};
            for (int i = 0; i < values.length; i++) {
                target.x = -1.5; target.y = 70.5; target.z = -5.5;
                if (axis == 0) target.x = values[i]; else if (axis == 1) target.y = values[i]; else target.z = values[i];
                boolean inside = i > 0 && i < values.length - 1;
                check(r.contains(target.player.getLocation()) == inside, "inclusive entire block membership axis=" + axis + " position=" + values[i]);
                check(r.count(viewer.player, List.of(target.player)) == (inside ? 1 : 0), "actual player count follows block membership axis=" + axis + " position=" + values[i]);
            }
        }
        var fractional = new NavigatorRegion("lobby-world", -2.2, -7.3, -5.1, -0.1, -2.01, -1.1);
        check(fractional.contains(new Location(f.lobby, -2.99, -7.99, -5.99)) && fractional.contains(new Location(f.lobby, -0.001, -2.001, -1.001)),
                "existing fractional endpoints include their entire containing blocks on negative axes");
        check(!fractional.contains(new Location(f.lobby, 0, -3, -2)) && !fractional.contains(new Location(f.lobby, -3.001, -3, -2)),
                "negative floor boundaries reject both adjacent outside blocks");
        check(fractional.minX() == -2.2 && fractional.maxY() == -2.01, "membership correction never rewrites stored existing bounds");
        check(!r.contains(new Location(f.outside, 0, 70, -5)) && !r.contains(new Location(f.lobby, Double.NaN, 70, -5)) && !r.contains(null),
                "wrong world, nonfinite and missing player locations remain excluded");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

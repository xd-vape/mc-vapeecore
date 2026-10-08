package dev.vapee.core.ui;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Source checks protect authoring dependencies, not visual positions or domain behavior. */
public final class GuiAuthoringAuditHarness {
    private static final String MENUS = "(?:SettingsMenu|VisibilitySettingsMenu|VisiblePlayersMenu|FriendMenu|ClanMenu|NavigatorMenu|DailyQuestMenu)";
    private static final Pattern LEGACY = Pattern.compile("\\b" + MENUS + "\\.(?:\\w+_SLOT|INVENTORY_SIZE|CONTENT_SIZE|SIZE|PREVIOUS|NEXT|CLOSE|REFRESH|PAGE_INFO)\\b|import\\s+static\\s+[\\w.]+" + MENUS + "\\.\\*");
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var paths = Files.walk(Path.of("src/test/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                check(!LEGACY.matcher(Files.readString(path)).find(), "no normal-menu compile alias dependency: " + path);
            }
        }
        // Negative control A: the same audit rejects a reintroduced compile-time reference.
        String dependency = "int slot = " + "SettingsMenu." + "REFRESH_SLOT;";
        check(LEGACY.matcher(dependency).find(), "audit detects restored test compile coupling");
        check(MenuLayout.builder(27, Probe.class).content(List.of(10)).build().emptySlot() == -1, "empty marker is optional presentation");
        var one = MenuLayout.builder(27, Probe.class).setting(Probe.FEATURE, 10).build();
        check(one.actionSlot(Probe.REFRESH).isEmpty() && one.actionSlot(Probe.FEATURE, 1).isEmpty(), "missing button and status are optional");
        check(one.placement(Probe.REFRESH).isEmpty() && one.actionAt(19).isEmpty(), "no phantom mapping");
        try { MenuLayout.builder(27, Probe.class).button(Probe.REFRESH, 3).button(Probe.REFRESH, 4); throw new AssertionError("duplicate action accepted"); }
        catch (IllegalArgumentException expected) { check(true, "builder rejects duplicate action placement"); }
        System.out.println("GuiAuthoringAuditHarness passed " + checks + " checks.");
    }
    private enum Probe { FEATURE, REFRESH }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
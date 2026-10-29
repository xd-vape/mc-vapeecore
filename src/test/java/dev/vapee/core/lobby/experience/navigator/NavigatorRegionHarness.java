package dev.vapee.core.lobby.experience.navigator;

public final class NavigatorRegionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var f = new NavigatorFixture(); var viewer = f.player();
        var region = new NavigatorRegion("lobby-world", 10, 80, 10, 0, 60, 0);
        check(region.count(viewer.player, f.players.values().stream().map(p -> p.player).toList()) == 0, "zero is measured, owner outside region");
        var target = f.player(); target.x = 0; target.y = 60; target.z = 0;
        check(count(region, f, viewer) == 1, "inclusive minimum boundary");
        target.x = 10; target.y = 80; target.z = 10;
        check(count(region, f, viewer) == 1, "inclusive maximum boundary");
        for (int axis = 0; axis < 3; axis++) {
            target.x = 5; target.y = 70; target.z = 5;
            if (axis == 0) target.x = -0.001; else if (axis == 1) target.y = 59.999; else target.z = 10.001;
            check(count(region, f, viewer) == 0, "outside axis excluded " + axis);
        }
        target.x = 5; target.y = 70; target.z = 5;
        target.currentWorld = f.outside; check(count(region, f, viewer) == 0, "wrong world excluded"); target.currentWorld = f.lobby;
        target.online = false; check(count(region, f, viewer) == 0, "offline excluded"); target.online = true;
        viewer.hidden.add(target.id); check(count(region, f, viewer) == 0, "viewer-specific Paper hide excluded"); viewer.hidden.clear();
        target.vanished = true; check(count(region, f, viewer) == 0, "vanished excluded even if viewer can see staff"); target.vanished = false;
        target.invisible = true; check(count(region, f, viewer) == 0, "invisible excluded conservatively"); target.invisible = false;
        check(region.count(viewer.player, java.util.List.of(target.player, target.player)) == 1, "duplicate online UUID cannot inflate count");
        for (int i = 0; i < 70; i++) { var p = f.player(); p.x = 5; p.y = 70; p.z = 5; }
        check(count(region, f, viewer) == 71, "many players counted accurately without cap");
        for (long n : new long[]{0, 1, 2, 64, 65, Integer.MAX_VALUE, Long.MAX_VALUE}) {
            check(NavigatorRegion.stackAmount(n, 64) == Math.max(1, Math.min(64, n)), "valid stack amount " + n);
            check(NavigatorRegion.stackAmount(n, 1) == 1, "unstackable material limit " + n);
        }
        try { new NavigatorRegion("world", Double.NaN, 0, 0, 1, 1, 1); throw new AssertionError("nonfinite bounds"); }
        catch (IllegalArgumentException expected) { check(true, "nonfinite bounds rejected"); }
        System.out.println("NavigatorRegionHarness passed " + checks + " checks.");
    }
    private static long count(NavigatorRegion region, NavigatorFixture f, NavigatorFixture.TestPlayer viewer) {
        return region.count(viewer.player, f.players.values().stream().map(p -> p.player).toList());
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

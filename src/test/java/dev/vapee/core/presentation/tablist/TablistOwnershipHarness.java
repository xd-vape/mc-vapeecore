package dev.vapee.core.presentation.tablist;

import dev.vapee.core.presentation.PresentationFixture;
import dev.vapee.core.presentation.PresentationRenderer.RenderedPresentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TablistOwnershipHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var f = new PresentationFixture()) {
            TablistService service = new TablistService(f.presentationConfig);
            var player = f.player("Vanilla");
            Component oldName = Component.text("ForeignOld", NamedTextColor.GOLD);
            Component oldHeader = Component.text("HeaderOld", NamedTextColor.BLUE);
            Component oldFooter = Component.text("FooterOld", NamedTextColor.GREEN);
            for (int mask = 0; mask < 8; mask++) {
                player.name = oldName; player.header = oldHeader; player.footer = oldFooter;
                service.updatePlayer(player.player, rendered("A"));
                service.updatePlayer(player.player, rendered("B"));
                check(player.name.equals(Component.text("B-name")), "successful refresh applied");
                Component foreign = Component.text("foreign", NamedTextColor.RED);
                if ((mask & 1) != 0) player.name = foreign;
                if ((mask & 2) != 0) player.header = foreign;
                if ((mask & 4) != 0) player.footer = foreign;
                service.removePlayer(player.player);
                check(player.name.equals((mask & 1) != 0 ? foreign : oldName), "foreign-current name preserved / original restored: " + mask);
                check(player.header.equals((mask & 2) != 0 ? foreign : oldHeader), "foreign-current header preserved / original restored: " + mask);
                check(player.footer.equals((mask & 4) != 0 ? foreign : oldFooter), "foreign-current footer preserved / original restored: " + mask);
                int writes = player.writes;
                service.removePlayer(player.player);
                check(writes == player.writes, "repeated cleanup inert");
                check(((Map<?, ?>) PresentationFixture.get(service, "modifiedPlayers")).isEmpty(), "cycle metadata released");
            }
            // Same text with different style is a foreign Component, not the same field value.
            service.updatePlayer(player.player, rendered("style"));
            player.name = Component.text("style-name", NamedTextColor.RED);
            service.removePlayer(player.player);
            check(player.name.equals(Component.text("style-name", NamedTextColor.RED)), "Component style differences retained");
            player.header = null; player.footer = null;
            Component vanilla = player.name;
            service.updatePlayer(player.player, rendered("nulls"));
            service.removePlayer(player.player);
            check(player.header == null && player.footer == null && player.name.equals(vanilla), "nullable Paper fields and effective name restored");

            player.name = oldName; player.header = oldHeader; player.footer = oldFooter;
            service.updatePlayer(player.player, rendered("enabled"));
            player.footer = Component.text("foreign after write");
            Files.writeString(f.directory.resolve("presentation.yml"), "tablist:\n  enabled: false\n");
            f.presentationConfig.applyState(f.presentationConfig.prepareReloadState());
            service.updatePlayer(player.player, rendered("disabled"));
            check(player.name.equals(oldName) && player.header.equals(oldHeader)
                    && player.footer.equals(Component.text("foreign after write")), "tablist enabled-disabled guarded cleanup");
            Component nextBaseline = Component.text("new cycle"); player.name = nextBaseline;
            Files.writeString(f.directory.resolve("presentation.yml"), "tablist:\n  enabled: true\n");
            f.presentationConfig.applyState(f.presentationConfig.prepareReloadState());
            service.updatePlayer(player.player, rendered("reenabled"));
            service.removePlayer(player.player);
            check(player.name.equals(nextBaseline), "re-enable captures new baseline");

            service.updatePlayer(player.player, rendered("leaving"));
            service.removePlayer(player.player);
            var reconnect = f.new TestPlayer(player.id, "Reconnect");
            service.updatePlayer(reconnect.player, rendered("joined"));
            service.removePlayer(reconnect.player);
            check(reconnect.name.equals(Component.text("Reconnect")), "same UUID reconnect has no stale snapshot");
            service.updatePlayer(player.player, rendered("offline"));
            service.clearTrackedPlayers();
            service.removePlayer(player.player);
            check(player.name.equals(Component.text("offline-name")), "final registry clear forgets offline metadata");

            // Active updates remain the configured writer; interleaving does not rebase the cycle.
            player.name = oldName;
            service.updatePlayer(player.player, rendered("one"));
            player.name = Component.text("interleaved");
            service.updatePlayer(player.player, rendered("two"));
            service.removePlayer(player.player);
            check(Objects.equals(player.name, oldName), "refresh retains first baseline after an intervening writer");

            player.normalizeName = true;
            service.updatePlayer(player.player, new RenderedPresentation(Component.empty(), List.of(),
                    Component.empty().append(Component.text("normalized")), Component.empty(), Component.empty()));
            service.removePlayer(player.player);
            check(player.name.equals(oldName), "ownership compares the actual Paper-readable normalized value");
            player.normalizeName = false;
            player.header = oldHeader; player.footer = oldFooter; player.failHeaderWrite = true;
            try { service.updatePlayer(player.player, rendered("partial")); throw new AssertionError("failure missing"); }
            catch (IllegalStateException expected) { check(expected.getMessage().contains("injected"), "external failure propagated"); }
            player.failHeaderWrite = false;
            service.removePlayer(player.player);
            check(player.name.equals(oldName) && player.header.equals(oldHeader) && player.footer.equals(oldFooter),
                    "partial update tracks only successful field writes");
        }
        System.out.println("TablistOwnershipHarness passed " + checks + " checks.");
    }
    private static RenderedPresentation rendered(String value) {
        return new RenderedPresentation(Component.empty(), List.of(), Component.text(value + "-name"),
                Component.text(value + "-header"), Component.text(value + "-footer"));
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

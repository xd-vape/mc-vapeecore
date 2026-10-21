package dev.vapee.core.settings;

import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItemSpec;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Independent definition/layout and render-scope contracts, without frozen production geometry. */
public final class SettingsDefinitionContractHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        definitions();
        invalidCompositions();
        renderingScope();
        System.out.println("SettingsDefinitionContractHarness passed " + checks + " checks.");
    }

    private static void definitions() throws Exception {
        var fixture = new SettingsMenuFixture(SettingsMenu.DEFAULT_LAYOUT);
        var expectedMaterials =
                List.of(
                        Material.MAP,
                        Material.NOTE_BLOCK,
                        Material.WRITABLE_BOOK,
                        Material.PLAYER_HEAD,
                        Material.BELL);
        var entries = SettingsMenuEntries.defaults();
        check(
                entries.stream()
                        .map(e -> e.icon().apply(PlayerSettings.defaults()).material())
                        .toList()
                        .equals(expectedMaterials),
                "stable declared feature order, independent of hash iteration");
        for (var action :
                List.of(
                        SettingsMenu.Action.SCOREBOARD,
                        SettingsMenu.Action.SOUNDS,
                        SettingsMenu.Action.PRIVATE_MESSAGES,
                        SettingsMenu.Action.FRIEND_REQUESTS,
                        SettingsMenu.Action.FRIEND_PRESENCE)) {
            var layout = fixture.menu.layout();
            var entry = fixture.menu.entryAt(layout.slot(action)).orElseThrow();
            check(entry.slot() == layout.slot(action), "main resolves correct entry " + action);
            check(
                    entry.statusSlot() == layout.slot(action, 1),
                    "status follows owning action " + action);
            check(
                    fixture.menu.entryAt(entry.statusSlot()).orElseThrow() == entry,
                    "render/click share exact entry " + action);
        }
        check(
                entries.size() == 5
                        && SettingsMenu.DEFAULT_LAYOUT
                                .placement(SettingsMenu.Action.VISIBILITY)
                                .isEmpty(),
                "supported optional visibility stays unplaced");
        expectUnsupported(entries::clear);
        var iconOnly =
                MenuLayout.builder(9, SettingsMenu.Action.class)
                        .setting(SettingsMenu.Action.FRIEND_PRESENCE, 2)
                        .build();
        var entry = SettingsMenuEntries.defaults(iconOnly).getFirst();
        check(
                entry.statusSlot() == -1 && !entry.matches(3),
                "absent optional status has no click authority");
        var fixtureOnly = new SettingsMenuFixture(iconOnly);
        check(
                fixtureOnly.menu.open(fixtureOnly.player("IconOnly").player),
                "icon-only menu remains valid");
        var empty =
                new SettingsMenuFixture(MenuLayout.builder(9, SettingsMenu.Action.class).build());
        check(
                empty.menu.open(empty.player("Empty").player),
                "all supported definitions may be unplaced");
    }

    private static void invalidCompositions() throws Exception {
        var f = new SettingsMenuFixture();
        var layout =
                MenuLayout.builder(27, SettingsMenu.Action.class)
                        .setting(SettingsMenu.Action.SOUNDS, 2, 11)
                        .button(SettingsMenu.Action.CLOSE, 26)
                        .build();
        var correct = SettingsMenuEntries.defaults(layout).getFirst();
        reject(() -> menu(f, List.of(), layout), "placed feature without entry rejected");
        reject(
                () -> menu(f, List.of(at(correct, 2, -1)), layout),
                "placed status may not disappear");
        reject(() -> menu(f, List.of(at(correct, 11, 2)), layout), "icon/status reversal rejected");
        reject(
                () -> menu(f, List.of(at(correct, 2, 12)), layout),
                "incorrect status mapping rejected");
        reject(() -> menu(f, List.of(correct, correct), layout), "duplicate slots rejected");
        reject(
                () -> menu(f, List.of(correct, at(correct, 26, -1)), layout),
                "reserved control rejected");
        reject(
                () -> menu(f, List.of(correct, at(correct, 27, -1)), layout),
                "outside icon rejected");
        reject(
                () -> menu(f, List.of(correct, at(correct, 3, 27)), layout),
                "outside status rejected");
        reject(
                () ->
                        MenuLayout.builder(27, SettingsMenu.Action.class)
                                .setting(SettingsMenu.Action.SOUNDS, 2, 11)
                                .button(SettingsMenu.Action.BACK, 11)
                                .build(),
                "layout itself rejects status/control collision");
        var iconOnly =
                MenuLayout.builder(27, SettingsMenu.Action.class)
                        .setting(SettingsMenu.Action.SOUNDS, 2)
                        .build();
        reject(
                () -> menu(f, List.of(correct), iconOnly),
                "unplaced extra status cannot become invisible authority");
        var mutable = new ArrayList<>(List.of(correct));
        var menu = menu(f, mutable, layout);
        mutable.clear();
        check(
                menu.entryAt(2).orElseThrow() == correct,
                "constructor defensively copies entry list");
    }

    private static void renderingScope() throws Exception {
        var f = new SettingsMenuFixture(SettingsMenu.DEFAULT_LAYOUT);
        var de = f.player("RenderGerman");
        var en = f.player("RenderEnglish");
        de.locale = Locale.GERMANY;
        boolean[] fail = {true};
        var menu =
                new SettingsMenu(
                        f.settings,
                        f.messages,
                        id -> f.online.get(id).player,
                        (holder, size, title) -> SettingsMenuFixture.inventory(holder, size),
                        spec -> {
                            if (fail[0])
                                throw new IllegalStateException("injected renderer failure");
                            return new SpecItem(spec);
                        },
                        f.logger,
                        SettingsMenu.DEFAULT_LAYOUT);
        try {
            menu.open(de.player);
            throw new AssertionError("renderer should fail");
        } catch (IllegalStateException expected) {
            check(menu.activeCount() == 0, "failed initial render publishes no active view");
        }
        var context = SettingsMenu.class.getDeclaredField("renderingFor");
        context.setAccessible(true);
        check(context.get(menu) == null, "failed render releases viewer context");
        fail[0] = false;
        check(menu.open(en.player), "next English render succeeds");
        int close = menu.layout().slot(SettingsMenu.Action.CLOSE);
        check(
                SettingsMenuFixture.plain(((SpecItem) en.open.getItem(close)).spec.name())
                        .equals("Close"),
                "failed German render cannot leak locale to next viewer");
        menu.startUpdates();
        int saves = f.repository.saves;
        f.messages.localization().localeChanged(en.player, Locale.GERMANY);
        check(
                SettingsMenuFixture.plain(((SpecItem) en.open.getItem(close)).spec.name())
                        .equals("Schließen"),
                "active language change uses current recipient locale");
        check(
                f.repository.saves == saves && context.get(menu) == null,
                "locale rendering saves no preference and retains no viewer");
        fail[0] = true;
        menu.refreshActive(en.id);
        check(
                menu.activeCount() == 0 && en.open == en.bottom && context.get(menu) == null,
                "observer render failure releases view and render context");
        menu.shutdown();
    }

    private static SettingsMenu menu(
            SettingsMenuFixture f,
            List<SettingsMenuEntry> entries,
            MenuLayout<SettingsMenu.Action> layout) {
        return new SettingsMenu(
                f.settings,
                f.messages,
                id -> null,
                (holder, size, title) -> SettingsMenuFixture.inventory(holder, size),
                SpecItem::new,
                f.logger,
                entries,
                layout);
    }

    private static SettingsMenuEntry at(SettingsMenuEntry entry, int icon, int status) {
        return new SettingsMenuEntry(icon, status, entry.icon(), entry.status(), entry.action());
    }

    private static void reject(Runnable action, String reason) {
        try {
            action.run();
            throw new AssertionError(reason);
        } catch (IllegalArgumentException expected) {
            check(true, reason);
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("mutable defaults");
        } catch (UnsupportedOperationException expected) {
            check(true, "default entries immutable");
        }
    }

    private static final class SpecItem extends org.bukkit.inventory.ItemStack {
        private final UiItemSpec spec;

        private SpecItem(UiItemSpec spec) {
            this.spec = spec;
        }
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}

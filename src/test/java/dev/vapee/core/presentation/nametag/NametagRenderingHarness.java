package dev.vapee.core.presentation.nametag;

import dev.vapee.core.clan.*;
import dev.vapee.core.presentation.*;
import dev.vapee.core.presentation.tablist.TablistService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Files;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.ArrayList;
import java.util.List;

public final class NametagRenderingHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var f = new PresentationFixture()) {
            var a = f.player("Alice"); var b = f.player("Bob");
            f.prefixes.put(a.id, "&c[Admin] "); f.groups.put(a.id, "custom_group"); f.groupColors.put("custom_group", "#123456");
            MemoryClans repository = new MemoryClans();
            ClanService clans = new ClanService(repository, ClanLimits.defaults(), Clock.systemUTC(), UUID::randomUUID);
            check(clans.createClan(a.id, "Canonical Clan", "ABC") == ClanResult.SUCCESS, "real canonical clan setup");
            AtomicReference<Optional<String>> override = new AtomicReference<>();
            boolean[] overrideActive = {false}; boolean[] failClan = {false};
            var renderer = f.renderer(id -> {
                if (failClan[0]) throw new IllegalStateException("injected clan provider failure");
                return overrideActive[0] ? override.get() : clans.getClanOf(id).map(Clan::tag);
            });
            Files.writeString(f.directory.resolve("presentation.yml"), "scoreboard:\n  enabled: false\nnametag:\n  lobby-only: false\n");
            f.presentationConfig.applyState(f.presentationConfig.prepareReloadState());
            renderer.applyState(renderer.prepareState(f.presentationConfig.getState()));
            var boards = f.scoreboards();
            var service = new PresentationService(f.plugin, f.presentationConfig, renderer, boards, new TablistService(f.presentationConfig));
            service.updateAll();
            var board = f.boards.stream().filter(it -> it.board == b.board).findFirst().orElseThrow();
            var own = board.teams.get(NametagService.teamName(a.id));
            check(own.entries.equals(java.util.Set.of("Alice")), "actual Vanilla player name is the team entry");
            check(plain(own.prefix).equals("[Admin] ") && plain(own.suffix).equals(" [ABC]"), "rank prefix and clan suffix behind name");
            check(own.prefix.equals(renderer.renderMeta("&c[Admin] ")), "styled trusted prefix uses existing Component path");
            check(plain(a.name).equals("[Admin] Alice [ABC]"), "complete default tablist output");
            check(plain(b.name).equals("Bob") && renderer.render(b.player).nametagSuffix().equals(Component.empty()), "no-clan output has no wrapper");
            check(containsColor(own.suffix, NamedTextColor.GRAY), "default clan wrapper renders configured color");
            int saves = repository.saves;
            f.prefixes.put(a.id, "&a[Changed] "); f.groupColors.put("custom_group", "red"); service.updateAll();
            check(plain(a.name).equals("[Changed] Alice [ABC]") && plain(own.prefix).equals("[Changed] "), "rank metadata change refreshes both surfaces");
            check(own.color == null, "RGB/named rank color does not mutate team color");
            board.normalizeTeamComponents = true; f.prefixes.put(a.id, "&6[Changed] "); service.updateAll();
            int normalizedWrites = own.mutations; service.updateAll(); service.updateAll();
            check(own.mutations == normalizedWrites && !own.removed && plain(own.prefix).equals("[Changed] "),
                    "normalized readable Components retain ownership and unchanged refresh has zero writes");
            board.normalizeTeamComponents = false;
            check(clans.changeTag(a.id, "NEW") == ClanResult.SUCCESS, "canonical tag change"); service.updateAll();
            check(plain(a.name).equals("[Changed] Alice [NEW]") && plain(own.suffix).equals(" [NEW]"), "tag change refreshes tab and overhead");
            UUID clanId = clans.getClanOf(a.id).orElseThrow().id();
            check(clans.inviteMember(a.id, b.id) == ClanResult.SUCCESS && clans.acceptInvite(b.id, clanId) == ClanResult.SUCCESS, "real membership joins clan");
            service.updateAll(); check(plain(b.name).equals("Bob [NEW]"), "new membership refreshes conditional display");
            check(clans.leaveClan(b.id) == ClanResult.SUCCESS, "real member leave"); service.updateAll();
            check(plain(b.name).equals("Bob"), "leave removes wrapper entirely");
            f.prefixes.remove(a.id); service.updateAll();
            check(plain(a.name).equals("Alice [NEW]") && own.prefix.equals(Component.empty()), "rank without prefix remains correct name");
            f.groups.remove(a.id); service.updateAll();
            check(plain(a.name).equals("Alice [NEW]"), "missing rank has neutral presentation");
            f.groups.put(a.id, "unloaded_unknown_group"); service.updateAll();
            check(plain(a.name).equals("Alice [NEW]"), "unknown rank uses existing fallback without authority lookup");
            for (Optional<String> invalid : Arrays.asList(null, Optional.<String>empty(), Optional.of(""), Optional.of(" "),
                    Optional.of("bad tag"), Optional.of("control\n"), Optional.of("\u00a0"))) {
                overrideActive[0] = true; override.set(invalid); service.updateAll();
                check(plain(a.name).equals("Alice") && own.suffix.equals(Component.empty()), "invalid/missing clan read produces no empty brackets");
            }
            String hostile = "<red>ABC&c<click:run_command:/op>";
            override.set(Optional.of(hostile)); service.updateAll();
            check(plain(a.name).equals("Alice [" + hostile + "]") && plain(own.suffix).equals(" [" + hostile + "]"), "MiniMessage-looking clan is literal on both surfaces");
            check(!hasClick(a.name) && !hasClick(own.suffix), "clan cannot inject click events");
            check(!containsColor(own.suffix, NamedTextColor.RED), "clan cannot inject red formatting");
            check(repository.saves >= saves, "domain writes come from mutations");
            int afterMutations = repository.saves; service.updateAll();
            check(repository.saves == afterMutations, "presentation performs no clan persistence");
            List<LogRecord> logs = new ArrayList<>(); f.logger.addHandler(new Handler() {
                public void publish(LogRecord record) { logs.add(record); }
                public void flush() { }
                public void close() { }
            });
            Component previousName = a.name; a.hidden.add(b.id); failClan[0] = true; service.updatePlayer(a.player);
            check(a.name.equals(previousName) && logs.stream().anyMatch(l -> l.getThrown() != null
                    && l.getThrown().getMessage().contains("clan provider")), "clan provider failure logs and preserves prior player output");
            check(f.boards.stream().filter(it -> it.board == a.board).findFirst().orElseThrow().teams.isEmpty(),
                    "provider failure still reconciles hidden or unavailable target relations");
            failClan[0] = false; f.failRankRead = true; service.updatePlayer(a.player);
            check(a.name.equals(previousName) && logs.stream().anyMatch(l -> l.getThrown() != null
                    && l.getThrown().getMessage().contains("rank provider")), "rank provider failure follows existing logged-update contract");
            f.failRankRead = false; overrideActive[0] = false;
            a.name = Component.text("foreign player list name"); service.removeAll();
            check(a.name.equals(Component.text("foreign player list name")), "cleanup preserves foreign current tablist field");
            check(f.main.teams.isEmpty(), "rank/clan presentation never mutates main scoreboard");
            String invalidTemplates = "nametag:\n  prefix: '<red>broken'\n  suffix: '<reset>'\nclan-tag-format: '<reset>'\n";
            Files.writeString(f.directory.resolve("presentation.yml"), invalidTemplates);
            var recovered = renderer.prepareState(f.presentationConfig.prepareReloadState());
            check(recovered.nametagPrefix().isEmpty() && recovered.nametagSuffix().isEmpty()
                    && recovered.clanTagFormat().equals(dev.vapee.core.presentation.config.PresentationConfig.DEFAULT_CLAN_TAG_FORMAT),
                    "invalid nametag MiniMessage templates fall back independently");
            check(Files.readString(f.directory.resolve("presentation.yml")).equals(invalidTemplates)
                    && logs.stream().filter(log -> log.getMessage().contains("Invalid MiniMessage template")).count() == 3,
                    "new template validation logs exactly affected fields without operator rewrite");
        }
        System.out.println("NametagRenderingHarness passed " + checks + " checks.");
    }
    private static final class MemoryClans implements ClanRepository {
        ClanSnapshot state = ClanSnapshot.empty(); int saves;
        public ClanSnapshot initialize() { return state; }
        public void save(ClanSnapshot value) { state = value; saves++; }
    }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    private static boolean hasClick(Component component) { return component.clickEvent() != null || component.children().stream().anyMatch(NametagRenderingHarness::hasClick); }
    private static boolean containsColor(Component component, NamedTextColor color) { return color.equals(component.color()) || component.children().stream().anyMatch(c -> containsColor(c, color)); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}

package dev.vapee.core.presentation;

import dev.vapee.core.clan.*;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.rank.RankModule;
import dev.vapee.core.reload.ReloadPlan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;

public final class PresentationLifecycleHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var f = new PresentationFixture()) {
            var permission = PresentationFixture.allocate(PermissionModule.class);
            PresentationFixture.set(permission, "luckPermsService", f.luckPerms);
            var rank = PresentationFixture.allocate(RankModule.class);
            PresentationFixture.set(rank, "rankService", f.ranks);
            var players = PresentationFixture.allocate(PlayerModule.class);
            PresentationFixture.set(players, "playerSettingsService", f.settings);
            var economy = PresentationFixture.allocate(EconomyModule.class);
            PresentationFixture.set(economy, "economyService", f.economy);
            var lobby = PresentationFixture.allocate(LobbyModule.class);
            PresentationFixture.set(lobby, "lobbyService", f.lobby);
            MemoryClans storage = new MemoryClans();
            ClanService clans = new ClanService(storage, ClanLimits.defaults(), Clock.systemUTC(), UUID::randomUUID);
            var owner = f.player("Owner"); var member = f.player("Member");
            check(clans.createClan(owner.id, "A different clan name", "<red>") == ClanResult.SUCCESS, "real canonical tag created");
            UUID clanId = clans.getClanOf(owner.id).orElseThrow().id();
            Function<UUID, Optional<String>> tags = id -> clans.getClanOf(id).map(Clan::tag);
            var module = new PresentationModule(f.plugin, f.config, f.messages, permission, rank,
                    players, economy, lobby, tags);
            writeConfig(f, true, "<clan_tag>|<name>");
            module.enable();
            var listener = (PresentationListener) PresentationFixture.get(module, "presentationListener");
            Component oldName = Component.text("before module");
            owner.name = oldName;
            owner.header = Component.text("old header"); owner.footer = Component.text("old footer");
            listener.onPlayerJoin(new PlayerJoinEvent(owner.player, Component.empty()));
            f.tasks.getLast().runnable.run();
            check(plain(owner.name).equals("<red>|Owner"), "join uses canonical tag literally through actual renderer and writer");
            check(owner.name.equals(Component.text("<red>|Owner")),
                    "unsafe-looking tag has no parsed styling");
            module.getPresentationService().updateAll();
            check(plain(member.name).equals("|Member"), "no clan neutral");
            int saves = storage.saves;
            module.getPresentationService().updateAll();
            check(storage.saves == saves, "render performs no clan writes");
            check(clans.inviteMember(owner.id, member.id) == ClanResult.SUCCESS
                    && clans.acceptInvite(member.id, clanId) == ClanResult.SUCCESS, "membership setup");
            module.getPresentationService().updateAll();
            check(plain(member.name).equals("<red>|Member"), "fresh membership read without presentation cache");
            check(clans.changeTag(owner.id, "NEW") == ClanResult.SUCCESS, "tag update");
            writeConfig(f, true, "<name>/<clan_tag>");
            ReloadPlan enabled = module.prepareReload();
            check(plain(owner.name).equals("<red>|Owner"), "prepare has no visible write");
            enabled.apply();
            check(plain(owner.name).equals("Owner/NEW"), "enabled-enabled reload reads current tag");
            enabled.rollback();
            check(plain(owner.name).equals("NEW|Owner"), "rollback restores template with fresh domain reads");
            owner.header = Component.text("foreign header");
            writeConfig(f, false, "<clan_tag>");
            ReloadPlan disabled = module.prepareReload(); disabled.apply();
            check(owner.name.equals(oldName) && owner.header.equals(Component.text("foreign header"))
                    && owner.footer.equals(Component.text("old footer")), "enabled-disabled reload preserves only foreign fields");
            check(f.tasks.stream().filter(t -> t != f.tasks.get(1)).allMatch(t -> t.cancelled), "repeating update task cancelled");
            Component newBaseline = Component.text("between cycles"); owner.name = newBaseline;
            disabled.rollback();
            check(plain(owner.name).equals("NEW|Owner"), "disabled rollback resumes presentation");
            module.disable();
            check(owner.name.equals(newBaseline) && owner.header.equals(Component.text("foreign header")), "module disable restores current cycle");
            int writes = owner.writes; module.disable();
            check(owner.writes == writes, "module disable idempotent");

            // Real enable disabled -> reload enabled, quit and reconnect, followed by full shutdown.
            writeConfig(f, false, "<clan_tag>"); module.enable();
            check(owner.name.equals(newBaseline), "disabled enable leaves values alone");
            writeConfig(f, true, "<clan_tag>"); module.prepareReload().apply();
            check(plain(owner.name).equals("NEW"), "disabled-enabled reload starts a new cycle");
            listener = (PresentationListener) PresentationFixture.get(module, "presentationListener");
            owner.footer = Component.text("foreign quit footer");
            listener.onPlayerQuit(new PlayerQuitEvent(owner.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            check(owner.name.equals(newBaseline) && plain(owner.footer).equals("foreign quit footer"), "quit guarded cleanup");
            owner.online = false;
            var reconnect = f.new TestPlayer(owner.id, "Owner"); f.online.add(reconnect);
            listener.onPlayerJoin(new PlayerJoinEvent(reconnect.player, Component.empty()));
            f.tasks.getLast().runnable.run();
            check(plain(reconnect.name).equals("NEW"), "reconnect reads current clan");
            check(clans.disbandClan(owner.id) == ClanResult.SUCCESS, "disband setup");
            module.getPresentationService().updateAll();
            check(plain(reconnect.name).isEmpty(), "missing clan after disband neutral");
            module.disable();
            check(reconnect.name.equals(Component.text("Owner")) && reconnect.header == null && reconnect.footer == null,
                    "new player session baseline restored without old snapshot");

            for (Optional<String> invalid : Arrays.asList(Optional.<String>empty(), null, Optional.of(""),
                    Optional.of("bad tag"), Optional.of("bad\n"), Optional.of("\u00a0"))) {
                check(plain(f.renderer(id -> invalid).render(member.player).tablistName()).equals("Member"),
                        "default templates remain unchanged with invalid/missing supplier");
                check(plain(MiniMessage.miniMessage().deserialize("a<clan_tag>b",
                        PresentationRenderer.clanTagPlaceholder(invalid))).equals("ab"), "invalid read neutral");
            }
            var renderer = f.renderer(id -> Optional.of("<click:run_command:/op>&a\ue001"));
            var state = renderer.getState();
            renderer.applyState(new PresentationRenderer.RenderState(state.metaFormat(), "<clan_tag>", List.of("<clan_tag>"),
                    "<clan_tag>", List.of("<clan_tag>"), List.of("<clan_tag>")));
            var rendered = renderer.render(member.player);
            Component literal = Component.text("<click:run_command:/op>&a\ue001");
            check(rendered.tablistName().equals(literal) && rendered.scoreboardTitle().equals(literal)
                    && rendered.scoreboardLines().equals(List.of(literal)) && rendered.tablistHeader().equals(literal)
                    && rendered.tablistFooter().equals(literal), "all rendering surfaces preserve literal text, events and optional glyph input");
            MiniMessage.builder().strict(true).build().deserialize("<clan_tag>", PresentationRenderer.emptyPlaceholders());
            check(true, "strict validation recognizes clan tag");
            String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
            check(core.contains("uniqueId -> clanModule.getClanService().getClanOf(uniqueId).map(Clan::tag)"), "bootstrap selects canonical tag");
        }
        System.out.println("PresentationLifecycleHarness passed " + checks + " checks.");
    }
    private static void writeConfig(PresentationFixture f, boolean enabled, String name) throws Exception {
        Files.writeString(f.directory.resolve("presentation.yml"), "enabled: " + enabled
                + "\nscoreboard:\n  lobby-only: false\ntablist:\n  name-format: '" + name + "'\n");
    }
    private static final class MemoryClans implements ClanRepository {
        ClanSnapshot state = ClanSnapshot.empty(); int saves;
        public ClanSnapshot initialize() { return state; }
        public void save(ClanSnapshot next) { state = next; saves++; }
    }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

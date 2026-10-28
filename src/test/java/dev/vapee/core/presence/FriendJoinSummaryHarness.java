package dev.vapee.core.presence;

import static dev.vapee.core.message.MessageColorAssertions.hasTextColor;

import dev.vapee.core.friend.*;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.social.SocialService;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Prefix-free join summary with actual translations, presence, friendship and Ignore services. */
public final class FriendJoinSummaryHarness {
    private static int checks;
    private static final UUID SELF = new UUID(40, 0);

    public static void main(String[] args) throws Exception {
        try (var runtime = new ModuleRuntimeFixture()) {
            var output = new ArrayList<Component>();
            var locale = new Locale[]{Locale.US};
            Player actor = ModuleRuntimeFixture.proxy(Player.class, (method, values) -> switch (method) {
                case "getUniqueId" -> SELF; case "getName" -> "JoiningPlayer"; case "locale" -> locale[0];
                case "sendMessage" -> { for (Object value : values) if (value instanceof Component c) output.add(c); yield null; }
                default -> throw new AssertionError("Unexpected actor call: " + method);
            });
            var messages = new MessageService(() -> "<red>[Operator]</red> ", LocalizationService.bundled(runtime.logger));
            runtime.players.loadPlayer(SELF, "JoiningPlayer");
            var ids = new ArrayList<UUID>();
            var relationships = new ArrayList<Friendship>();
            for (int i = 1; i <= 12; i++) {
                UUID id = new UUID(40, i); ids.add(id);
                runtime.players.loadPlayer(id, "Friend" + i);
                relationships.add(new Friendship(SELF, id, Instant.EPOCH));
            }
            int[] writes = {0};
            var snapshot = new FriendSnapshot(relationships, List.of());
            var friends = new FriendService(new FriendRepository() {
                public FriendSnapshot initialize() { return snapshot; }
                public void save(FriendSnapshot value) { writes[0]++; throw new AssertionError("summary must not save"); }
            }, FriendLimits.defaults(), FriendRequestPolicy.allowAll(), Clock.systemUTC());
            var presence = new PresenceService();
            var social = new SocialService(runtime.players, runtime.logger);
            var summary = new FriendJoinSummary(friends, presence, social, messages, runtime.settings);

            for (Locale language : List.of(Locale.US, Locale.GERMANY)) {
                locale[0] = language;
                for (int online : List.of(0, 1, 3, 12)) {
                    presence.clear(); ids.stream().limit(online).forEach(presence::markOnline);
                    int before = output.size(); summary.send(actor);
                    check(output.size() == before + 1, "one compact line per send");
                    Component line = output.getLast();
                    String expected = expected(language, online, 12);
                    check(plain(line).equals(expected), "exact localized zero/singular/plural text: " + expected);
                    check(!plain(line).contains("[") && !plain(line).contains("\n") && !plain(line).contains("\r"), "neither channel nor operator prefix, no decoration or extra line");
                    check(noEvents(line), "summary has no clickable or hoverable spans");
                    if (online > 0) {
                        check(literalColor(line, null, Integer.toString(online), NamedTextColor.GREEN), "online count green including singular 1 beside total 12");
                        check(literalColor(line, null, "12", NamedTextColor.WHITE), "friend total white");
                        check(hasTextColor(line, language.equals(Locale.US) ? " of your " : " von ", NamedTextColor.GRAY)
                                && hasTextColor(line, language.equals(Locale.US) ? " friends " : " Freunden ", NamedTextColor.GRAY), "surrounding body gray, count color does not leak");
                    } else check(hasTextColor(line, expected, NamedTextColor.GRAY), "zero text entirely muted gray");
                }
            }

            // Summary obeys the same loaded preference as the existing friend status notifications.
            locale[0] = Locale.US; presence.clear(); presence.markOnline(ids.getFirst());
            runtime.settings.setFriendPresenceNotificationsEnabled(SELF, false);
            int disabledCount = output.size();
            summary.send(actor);
            check(output.size() == disabledCount, "disabled friend presence notifications suppress the join summary");
            runtime.settings.setFriendPresenceNotificationsEnabled(SELF, true);
            summary.send(actor);
            check(output.size() == disabledCount + 1 && plain(output.getLast()).equals(expected(Locale.US, 1, 12)), "re-enabled preference is read live without a second cache");
            social.ignore(SELF, ids.getFirst()); summary.send(actor);
            check(plain(output.getLast()).equals(expected(Locale.US, 0, 12)), "recipient Ignore hides online presence");
            social.unignore(SELF, ids.getFirst()); social.ignore(ids.getFirst(), SELF); summary.send(actor);
            check(plain(output.getLast()).equals(expected(Locale.US, 0, 12)), "reverse Ignore hides online presence");
            social.unignore(ids.getFirst(), SELF);
            var noFriends = new FriendService(new FriendRepository() {
                public FriendSnapshot initialize() { return FriendSnapshot.empty(); }
                public void save(FriendSnapshot value) { throw new AssertionError("read only"); }
            }, FriendLimits.defaults(), FriendRequestPolicy.allowAll(), Clock.systemUTC());
            new FriendJoinSummary(noFriends, presence, social, messages, runtime.settings).send(actor);
            check(plain(output.getLast()).equals(expected(Locale.US, 0, 0)), "no friends uses the same compact zero form");

            var statuses = new ArrayList<PresenceStatus>();
            var loaded = new AtomicBoolean(false);
            var listener = new PresenceListener(presence, ignored -> loaded.get(),
                    (id, name, status) -> statuses.add(status), runtime.logger, summary::send);
            var join = new PlayerJoinEvent(actor, Component.empty());
            var quit = new PlayerQuitEvent(actor, Component.empty());
            output.clear(); listener.onPlayerJoin(join);
            check(output.isEmpty() && statuses.isEmpty(), "failed/unloaded profile cannot receive summary");
            loaded.set(true); listener.onPlayerJoin(join); listener.onPlayerJoin(join);
            check(output.size() == 1 && statuses.equals(List.of(PresenceStatus.ONLINE)), "duplicate join emits one summary and one existing friend-status dispatch");
            listener.onPlayerQuit(quit); listener.onPlayerQuit(quit);
            check(output.size() == 1 && statuses.equals(List.of(PresenceStatus.ONLINE, PresenceStatus.OFFLINE)), "quit never emits a self summary and retains status deduplication");
            presence.markOffline(ids.getFirst()); listener.onPlayerJoin(join);
            check(output.size() == 2 && plain(output.getLast()).equals(expected(Locale.US, 0, 12)), "rejoin recomputes current online count");
            listener.onPlayerQuit(quit); presence.markOnline(SELF); listener.onPlayerJoin(join);
            check(output.size() == 2, "already seeded online player never gets synthetic enable summary");

            presence.markOffline(SELF);
            var brokenStatus = new PresenceListener(presence, ignored -> true,
                    (id, name, status) -> { throw new IllegalStateException("status transport"); }, runtime.logger, summary::send);
            brokenStatus.onPlayerJoin(join);
            check(output.size() == 3 && presence.isOnline(SELF), "status delivery failure does not prevent private summary");
            presence.markOffline(SELF); int[] attempts = {0}; statuses.clear();
            var brokenSummary = new PresenceListener(presence, ignored -> true,
                    (id, name, status) -> statuses.add(status), runtime.logger,
                    player -> { attempts[0]++; throw new IllegalStateException("summary transport"); });
            brokenSummary.onPlayerJoin(join); brokenSummary.onPlayerJoin(join);
            check(attempts[0] == 1 && presence.isOnline(SELF) && statuses.equals(List.of(PresenceStatus.ONLINE)), "summary failure does not roll back status or create retries");
            check(writes[0] == 0 && friends.getFriends(SELF).size() == 12 && friends.getIncomingRequests(SELF).isEmpty(), "no persistence, friendship or request mutations");
            presence.markOffline(SELF); runtime.settings.setFriendPresenceNotificationsEnabled(SELF, false);
            int disabledJoin = output.size(); listener.onPlayerJoin(join); listener.onPlayerJoin(join);
            check(output.size() == disabledJoin && presence.isOnline(SELF), "disabled join sends no summary while recording normal presence exactly once");
            listener.onPlayerQuit(quit); runtime.settings.setFriendPresenceNotificationsEnabled(SELF, true); listener.onPlayerJoin(join);
            check(output.size() == disabledJoin + 1, "next real join after enabling notifications sends one summary");
            int beforeUnload = output.size(); runtime.players.unloadPlayer(SELF); summary.send(actor);
            check(output.size() == beforeUnload, "summary itself fails closed for an unloaded preference");
        }
        System.out.println("FriendJoinSummaryHarness passed " + checks + " checks.");
    }

    private static String expected(Locale locale, int online, int total) {
        if (online == 0) return locale.equals(Locale.US) ? "None of your friends are currently online." : "Momentan ist keiner deiner Freunde online.";
        return locale.equals(Locale.US)
                ? "Currently, " + online + " of your " + total + " friends " + (online == 1 ? "is" : "are") + " online."
                : "Aktuell " + (online == 1 ? "ist" : "sind") + " " + online + " von " + total + " Freunden online.";
    }
    private static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private static boolean noEvents(Component c) { return c.clickEvent() == null && c.hoverEvent() == null && c.children().stream().allMatch(FriendJoinSummaryHarness::noEvents); }
    private static boolean literalColor(Component c, TextColor inherited, String text, TextColor expected) {
        TextColor effective = c.color() == null ? inherited : c.color();
        return c instanceof TextComponent literal && literal.content().equals(text) && expected.equals(effective)
                || c.children().stream().anyMatch(child -> literalColor(child, effective, text, expected));
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

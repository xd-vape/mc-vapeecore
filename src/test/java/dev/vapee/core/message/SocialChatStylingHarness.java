package dev.vapee.core.message;

import static dev.vapee.core.message.MessageColorAssertions.hasTextColor;

import dev.vapee.core.clan.*;
import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.friend.*;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presence.FriendPresenceNotifier;
import dev.vapee.core.presence.PresenceStatus;
import dev.vapee.core.rank.RankService;
import dev.vapee.core.social.SocialService;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.entity.Player;

/** Real commands, repositories, translations and rank resolver; only transport/LP storage are seams. */
public final class SocialChatStylingHarness {
    private static int checks;
    private static final TextColor RGB = TextColor.color(0xc35cff);

    public static void main(String[] args) throws Exception {
        try (Fixture f = new Fixture()) {
            Actor a = f.actor("Twin"), b = f.actor("Twin"), unknown = f.actor("Unloaded");
            f.lp.primary.put(a.id, "named"); f.lp.primary.put(b.id, "rgb");
            f.lp.colors.put("named", "gold"); f.lp.colors.put("rgb", "#c35cff");
            check(hasTextColor(f.names.format(a.id, a.name), "Twin", NamedTextColor.GOLD), "configured named color through real RankService");
            check(hasTextColor(f.names.format(b.id, b.name), "Twin", RGB), "same name different UUID gets RGB primary rank");
            check(hasTextColor(f.names.format(unknown.id, unknown.name), unknown.name, NamedTextColor.WHITE), "unloaded LP user white without loading");
            f.lp.primary.put(unknown.id, "no-color"); f.lp.colors.put("no-color", null);
            check(hasTextColor(f.names.format(unknown.id, unknown.name), unknown.name, NamedTextColor.WHITE), "missing metadata white");
            f.lp.primary.put(unknown.id, "missing-group");
            check(hasTextColor(f.names.format(unknown.id, unknown.name), unknown.name, NamedTextColor.WHITE), "missing group white");
            for (String invalid : List.of("<red>", "#12345", "not-a-color")) {
                f.lp.colors.put("no-color", invalid); f.lp.primary.put(unknown.id, "no-color");
                check(hasTextColor(f.names.format(unknown.id, unknown.name), unknown.name, NamedTextColor.WHITE), "invalid rank color never parsed as markup");
            }
            f.lp.primary.remove(unknown.id);
            String attack = "<click:run_command:'/op @s'><red>Name</red></click>";
            Component literal = f.names.format(a.id, attack);
            check(plain(literal).equals(attack) && clicks(literal).isEmpty(), "literal name cannot inject markup or commands");

            // Sent target and received actor deliberately have equal names and different primary ranks.
            f.friend.onCommand(a.player, null, "friend", new String[]{"add", b.id.toString()});
            Component sent = a.last(), received = b.last();
            check(plain(sent).equals("[Server] [Friends] Friend request sent to Twin"), "English sent prefix and exact translation");
            check(hasTextColor(sent, "Twin", RGB), "sent feedback uses target UUID");
            check(hasTextColor(received, "Twin", NamedTextColor.GOLD), "received request uses actor UUID");
            check(hasTextColor(received, " sent you a friend request. ", NamedTextColor.GRAY), "rank color confined to name");
            check(hasTextColor(received, "[Friends]", NamedTextColor.GOLD), "muted gold Friends prefix");
            check(hasTextColor(received, "[Accept]", NamedTextColor.GREEN) && hasTextColor(received, "[Deny]", NamedTextColor.RED), "action colors preserved");
            check(clicks(received).equals(List.of(ClickEvent.runCommand("/friend accept " + a.id), ClickEvent.runCommand("/friend deny " + a.id))), "only intended direct UUID buttons clickable");
            once(received, "[Friends]");
            b.locale = Locale.GERMANY;
            f.friend.onCommand(b.player, null, "friend", new String[]{"accept", a.id.toString()});
            check(plain(b.last()).startsWith("[Server] [Freunde] ") && hasTextColor(b.last(), "Twin", NamedTextColor.GOLD), "German feedback retains target attribution");
            check(f.friends.getRelation(a.id, b.id) == FriendRelation.FRIENDS && hasTextColor(a.last(), "Twin", RGB), "accept domain state and notice actor attribution");
            byte[] accepted = Files.readAllBytes(f.root.resolve("friends.yml"));
            f.friend.onCommand(b.player, null, "friend", new String[]{"accept", a.id.toString()});
            check(Arrays.equals(accepted, Files.readAllBytes(f.root.resolve("friends.yml"))), "stale accept does not rewrite persistence");
            check(hasTextColor(b.last(), "Fehler: ", NamedTextColor.RED) && hasTextColor(b.last(), "Ihr seid bereits befreundet.", NamedTextColor.GRAY), "localized restrained error highlight");
            once(b.last(), "[Freunde]");

            f.lp.colors.put("named", "dark_red");
            f.friend.onCommand(a.player, null, "friend", new String[]{"remove", b.id.toString()});
            f.friend.onCommand(a.player, null, "friend", new String[]{"add", b.id.toString()});
            check(hasTextColor(b.last(), "Twin", NamedTextColor.DARK_RED) && plain(b.last()).contains("[Freunde]"), "next request reads changed rank and recipient locale");
            f.friend.onCommand(b.player, null, "friend", new String[]{"deny", a.id.toString()});
            check(f.friends.getRelation(a.id, b.id) == FriendRelation.NONE, "direct deny behavior retained");
            b.permitted = false;
            f.friend.onCommand(b.player, null, "friend", new String[]{"add", a.id.toString()});
            once(b.last(), "[Freunde]");
            check(f.friends.getRelation(a.id, b.id) == FriendRelation.NONE, "permission feedback cannot mutate domain");
            b.permitted = true;

            // One channel heading for lists; individual rows are literal rank-colored names.
            f.friend.onCommand(a.player, null, "friend", new String[]{"add", b.id.toString()});
            int start = b.messages.size();
            f.friend.onCommand(b.player, null, "friend", new String[]{"accept"});
            check(count(b.messages.subList(start, b.messages.size()), "[Freunde]") == 1, "friend choice heading prefixed exactly once");
            check(hasTextColor(b.last(), "Twin", NamedTextColor.DARK_RED) && clicks(b.last()).size() == 2, "choice row has live rank and two direct actions");
            f.friend.onCommand(b.player, null, "friend", new String[]{"accept", a.id.toString()});
            f.presence.notifyStatus(a.id, a.name, PresenceStatus.ONLINE);
            check(plain(b.last()).equals("[Server] [Freunde] Dein Freund Twin ist jetzt online."), "recipient-localized presence and prefix");
            check(hasTextColor(b.last(), "Twin", NamedTextColor.DARK_RED) && hasTextColor(b.last(), "Dein Freund ", NamedTextColor.GRAY), "presence actual subject UUID color isolated");
            f.lp.primary.put(a.id, "rgb");
            f.presence.notifyStatus(a.id, a.name, PresenceStatus.OFFLINE);
            check(hasTextColor(b.last(), "Twin", RGB) && hasTextColor(b.last(), " ist offline gegangen.", NamedTextColor.GRAY), "offline next message reflects changed primary group");
            int before = b.messages.size();
            f.settings.setFriendPresenceNotificationsEnabled(b.id, false);
            f.presence.notifyStatus(a.id, a.name, PresenceStatus.ONLINE);
            check(b.messages.size() == before, "presence opt-out preserved");
            f.settings.setFriendPresenceNotificationsEnabled(b.id, true);
            f.social.ignore(a.id, b.id); f.presence.notifyStatus(a.id, a.name, PresenceStatus.ONLINE);
            check(b.messages.size() == before, "reverse Ignore preserved");
            f.social.unignore(a.id, b.id); f.social.ignore(b.id, a.id);
            f.presence.notifyStatus(a.id, a.name, PresenceStatus.ONLINE);
            check(b.messages.size() == before, "recipient Ignore preserved");
            f.social.unignore(b.id, a.id); b.online = false;
            f.presence.notifyStatus(a.id, a.name, PresenceStatus.ONLINE);
            check(b.messages.size() == before, "offline recipient excluded"); b.online = true;

            f.clan.onCommand(a.player, null, "clan", new String[]{"create", "STY", "Style Clan"});
            UUID clanId = f.clans.getClanOf(a.id).orElseThrow().id();
            f.clan.onCommand(a.player, null, "clan", new String[]{"invite", b.id.toString()});
            Component invitation = b.last();
            once(invitation, "[Clan]");
            check(hasTextColor(invitation, "[Clan]", NamedTextColor.DARK_PURPLE), "restrained purple Clan prefix");
            check(hasTextColor(invitation, "Twin", RGB) && hasTextColor(invitation, "Style Clan", NamedTextColor.GOLD), "clan invitation owner UUID and literal gold clan name");
            check(hasTextColor(a.last(), "Twin", RGB), "clan invite sent uses target UUID");
            check(clicks(invitation).equals(List.of(ClickEvent.runCommand("/clan accept " + clanId), ClickEvent.runCommand("/clan deny " + clanId))), "clan UUID RUN_COMMAND and action-only events preserved");
            f.lp.primary.put(b.id, "named");
            f.clan.onCommand(b.player, null, "clan", new String[]{"accept", clanId.toString()});
            check(f.clans.getClanOf(b.id).orElseThrow().id().equals(clanId) && hasTextColor(a.last(), "Twin", NamedTextColor.DARK_RED), "clan accept state and joining player's live UUID color");
            start = a.messages.size(); f.clan.onCommand(a.player, null, "clan", new String[]{"info"});
            check(count(a.messages.subList(start, a.messages.size()), "[Clan]") == 1, "clan info one channel heading across rows");
            check(a.messages.subList(start, a.messages.size()).stream().anyMatch(c -> hasTextColor(c, "Twin", RGB)), "clan owner info actual UUID");
            f.clan.onCommand(a.player, null, "clan", new String[]{"kick", b.id.toString()});
            check(hasTextColor(a.last(), "Twin", NamedTextColor.DARK_RED) && f.clans.getClanOf(b.id).isEmpty(), "member-management target UUID and unchanged kick");
            f.lp.primary.remove(a.id);
            f.clan.onCommand(a.player, null, "clan", new String[]{"invite", unknown.id.toString()});
            check(hasTextColor(unknown.last(), "Twin", NamedTextColor.WHITE), "unloaded inviter uses neutral fallback in real delivery");
            check(f.lp.userReads.containsAll(List.of(a.id, b.id, unknown.id)), "real UUIDs reach loaded-user boundary");
            check(f.lp.forbidden == 0, "no LP load/save/mutation or prefix parsing");
            try (var files = Files.list(f.root)) {
                check(files.map(p -> p.getFileName().toString()).sorted().toList().equals(List.of("clans.yml", "friends.yml", "players")), "no new persistence files");
            }
            check(!Files.readString(f.root.resolve("friends.yml")).contains("color") && !Files.readString(f.root.resolve("clans.yml")).contains("prefix"), "presentation does not enter persistent domain schema");
        }
        System.out.println("SocialChatStylingHarness passed " + checks + " checks.");
    }

    private static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private static List<ClickEvent> clicks(Component c) {
        List<ClickEvent> events = new ArrayList<>(); if (c.clickEvent() != null) events.add(c.clickEvent());
        c.children().forEach(child -> events.addAll(clicks(child))); return events;
    }
    private static int count(List<Component> messages, String token) {
        return messages.stream().mapToInt(c -> (plain(c).length() - plain(c).replace(token, "").length()) / token.length()).sum();
    }
    private static void once(Component c, String token) { check(count(List.of(c), token) == 1, "channel exactly once: " + token); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> calls.apply(m.getName(), a));
    }

    private static final class LoadedLuckPerms {
        final Map<UUID, String> primary = new HashMap<>(); final Map<String, String> colors = new HashMap<>();
        final Set<UUID> userReads = new HashSet<>(); int forbidden;
        final UserManager users = proxy(UserManager.class, (name, args) -> {
            if (!name.equals("getUser")) return forbidden(name);
            UUID id = (UUID) args[0]; userReads.add(id);
            return primary.containsKey(id) ? proxy(User.class, (n, a) -> n.equals("getPrimaryGroup") ? primary.get(id) : forbidden(n)) : null;
        });
        final GroupManager groups = proxy(GroupManager.class, (name, args) -> {
            if (!name.equals("getGroup")) return forbidden(name);
            String group = (String) args[0]; if (!colors.containsKey(group)) return null;
            return proxy(Group.class, (n, a) -> switch (n) {
                case "getName" -> group; case "getDisplayName" -> null; case "getWeight" -> OptionalInt.empty();
                case "getCachedData" -> proxy(CachedDataManager.class, (cn, ca) -> cn.equals("getMetaData")
                        ? proxy(CachedMetaData.class, (mn, ma) -> mn.equals("getMetaValue")
                                ? (RankService.COLOR_META_KEY.equals(ma[0]) ? colors.get(group) : null) : forbidden(mn)) : forbidden(cn));
                default -> forbidden(n);
            });
        });
        final LuckPerms api = proxy(LuckPerms.class, (name, args) -> switch (name) {
            case "getUserManager" -> users; case "getGroupManager" -> groups; default -> forbidden(name);
        });
        Object forbidden(String name) { forbidden++; throw new AssertionError("Unexpected LP API: " + name); }
    }

    private static final class Actor {
        final UUID id = UUID.randomUUID(); final String name; final List<Component> messages = new ArrayList<>();
        Locale locale = Locale.US; boolean permitted = true, online = true; final Player player;
        Actor(String name) {
            this.name = name;
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id; case "getName" -> name; case "locale" -> locale; case "getLocale" -> locale.toString();
                case "isOnline" -> online; case "hasPermission" -> permitted;
                case "sendMessage" -> { for (Object arg : args) if (arg instanceof Component c) messages.add(c); yield null; }
                default -> throw new AssertionError("Unexpected actor API: " + method);
            });
        }
        Component last() { return messages.getLast(); }
    }

    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("social-chat-styling-");
        final ModuleRuntimeFixture runtime = runtime(root);
        final Logger log = runtime.logger;
        final LoadedLuckPerms lp = new LoadedLuckPerms();
        final RankService rank = new RankService(new ConfigService(runtime.plugin), new LuckPermsService(lp.api));
        final PlayerNameFormatter names = new PlayerNameFormatter(() -> rank);
        final MessageService messages = new MessageService(() -> "<blue>[Server]</blue> ", LocalizationService.bundled(log));
        final FilePlayerRepository repository = new FilePlayerRepository(root.resolve("players"), log);
        final PlayerService players = new PlayerService(repository, log);
        final PlayerSettingsService settings = new PlayerSettingsService(players);
        final SocialService social = new SocialService(players, log);
        final FriendService friends = new FriendService(new FileFriendRepository(root.resolve("friends.yml"), log), FriendLimits.defaults(), FriendRequestPolicy.allowAll(), Clock.systemUTC());
        final ClanService clans = new ClanService(new FileClanRepository(root.resolve("clans.yml")), ClanLimits.defaults(), Clock.systemUTC(), UUID::randomUUID);
        final Map<UUID, Actor> actors = new LinkedHashMap<>();
        final PlayerIdentityService identities = new PlayerIdentityService(players);
        final CommandHelpRenderer help = new CommandHelpRenderer(messages);
        final FriendCommand friend = new FriendCommand(friends, identities, messages, help, this::onlinePlayers, this::onlinePlayer, log, p -> {}, names);
        final ClanCommand clan = new ClanCommand(clans, identities, messages, help, this::onlinePlayers, this::onlinePlayer, log, p -> {}, p -> {}, names);
        final FriendPresenceNotifier presence = new FriendPresenceNotifier(friends, settings, social, this::onlinePlayer, messages, log, names);
        Fixture() throws Exception { repository.initialize(); }
        private static ModuleRuntimeFixture runtime(Path root) throws Exception {
            var fixture = new ModuleRuntimeFixture();
            ModuleRuntimeFixture.set(fixture.plugin, "dataFolder", root.toFile());
            return fixture;
        }
        Actor actor(String name) { Actor a = new Actor(name); actors.put(a.id, a); players.loadPlayer(a.id, name); return a; }
        List<Player> onlinePlayers() { return actors.values().stream().filter(a -> a.online).map(a -> a.player).toList(); }
        Player onlinePlayer(UUID id) { Actor a = actors.get(id); return a == null ? null : a.player; }
        @Override public void close() throws Exception {
            runtime.close();
            if (!root.toAbsolutePath().normalize().startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())) throw new AssertionError("temporary root escaped");
            try (var paths = Files.walk(root)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
    }
}

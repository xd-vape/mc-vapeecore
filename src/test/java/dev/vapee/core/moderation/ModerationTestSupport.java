package dev.vapee.core.moderation;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.moderation.command.ModerationCommandContext;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.*;

/** Real domain/services with explicit in-memory repositories and only Bukkit boundary proxies. */
public final class ModerationTestSupport {
    public static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    public static final UUID STAFF = new UUID(0, 80), TARGET = new UUID(0, 90), OFFLINE = new UUID(0, 91);
    public static final String ATTACK = "<click:run_command:'/op @s'>click</click>";

    public static class Time extends Clock {
        public volatile Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    public static final class Repository implements ModerationRepository {
        public ModerationSnapshot saved = ModerationSnapshot.empty();
        public int saves, loads;
        public boolean fail;
        public Runnable beforeSave = () -> { };
        public ModerationSnapshot initialize() { loads++; return saved; }
        public void save(ModerationSnapshot snapshot) {
            beforeSave.run();
            if (fail) throw new ModerationRepositoryException("injected save failure");
            saved = snapshot; saves++;
        }
    }

    public static final class Fixture {
        public final Time clock = new Time();
        public final Repository repository = new Repository();
        public final List<LogRecord> logs = new ArrayList<>();
        public final List<String> events = new ArrayList<>();
        public final Logger logger = Logger.getLogger("ModerationTest-" + UUID.randomUUID());
        public final Map<UUID, CorePlayer> known = new HashMap<>();
        public final Map<UUID, Sender> online = new HashMap<>();
        public final PlayerIdentityService identities;
        public final MessageService messages;
        public final ModerationService service;
        public final ModerationMuteProjection projection = new ModerationMuteProjection(clock);
        public final Sender console;
        public final Sender staff;
        public final Sender target;
        public final ModerationCommandContext context;
        public int playerWrites, nameReads, identityReads;
        public boolean failIdentity;
        private long recordId;

        public Fixture() throws Exception {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                public void publish(LogRecord record) { logs.add(record); events.add("log:" + record.getLevel()); }
                public void flush() { } public void close() { }
            });
            PlayerRepository players = new PlayerRepository() {
                public Optional<CorePlayer> findByUniqueId(UUID id) {
                    identityReads++; if (failIdentity) throw new IllegalStateException("injected identity read failure");
                    return Optional.ofNullable(known.get(id));
                }
                public boolean exists(UUID id) { return known.containsKey(id); }
                public void save(CorePlayer player) { playerWrites++; known.put(player.getUniqueId(), player); }
                public Set<UUID> findUniqueIdsByName(String name) {
                    nameReads++;
                    return known.values().stream().filter(p -> p.getName().equalsIgnoreCase(name))
                            .map(CorePlayer::getUniqueId).collect(java.util.stream.Collectors.toSet());
                }
            };
            identities = new PlayerIdentityService(new PlayerService(players, logger));
            var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
            constructor.setAccessible(true);
            messages = constructor.newInstance((Supplier<String>) () -> "");
            service = new ModerationService(repository, clock, () -> new UUID(0, ++recordId), projection::publish);
            console = new Sender(ConsoleCommandSender.class, null, "Console", events);
            staff = new Sender(Player.class, STAFF, "Staff", events);
            target = new Sender(Player.class, TARGET, "Alex", events);
            addKnown(STAFF, "Staff"); addKnown(TARGET, "Alex"); addKnown(OFFLINE, "Offline");
            online.put(STAFF, staff); online.put(TARGET, target);
            context = context(service);
        }

        public ModerationCommandContext context(ModerationService service) {
            return new ModerationCommandContext(service, identities, messages,
                    id -> online.containsKey(id) ? (Player) online.get(id).sender : null,
                    () -> online.values().stream().map(s -> (Player) s.sender).toList(), clock, logger);
        }

        public void addKnown(UUID id, String name) {
            known.put(id, new CorePlayer(id, name, NOW, NOW, PlayerSettings.defaults(), CoinWallet.empty(), PlayerSocial.empty()));
        }

        public long info() { return logs.stream().filter(l -> l.getLevel() == Level.INFO).count(); }
        public long severe() { return logs.stream().filter(l -> l.getLevel() == Level.SEVERE).count(); }
    }

    public static final class Sender {
        public final UUID id;
        public final CommandSender sender;
        public final Set<String> permissions = new HashSet<>();
        public final List<Component> output = new ArrayList<>();
        public final List<Component> kicks = new ArrayList<>();
        public boolean allowAll = true, isOnline = true, failKick, failNotification;
        public Runnable beforeKick = () -> { };
        public Runnable beforeNotification = () -> { };
        public Sender(Class<?> type, UUID id, String name, List<String> events) {
            this.id = id;
            sender = (CommandSender) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "hasPermission" -> allowAll || permissions.contains(args[0]);
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "isOnline" -> isOnline;
                    case "sendMessage" -> {
                        beforeNotification.run();
                        if (failNotification) throw new IllegalStateException("injected notification failure");
                        for (Object arg : args) if (arg instanceof Component c) output.add(c);
                        events.add("message:" + name); yield null;
                    }
                    case "kick" -> {
                        beforeKick.run(); events.add("kick:" + name);
                        if (failKick) throw new IllegalStateException("injected disconnect failure");
                        kicks.add((Component) args[0]); yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> name;
                    default -> method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == int.class ? 0 : null;
                };
            });
        }
        public String text() { return output.stream().map(ModerationTestSupport::plain).reduce("", (a, b) -> a + "\n" + b); }
    }

    public static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    public static boolean noEvents(Component component) {
        return component.clickEvent() == null && component.hoverEvent() == null
                && component.children().stream().allMatch(ModerationTestSupport::noEvents);
    }
}

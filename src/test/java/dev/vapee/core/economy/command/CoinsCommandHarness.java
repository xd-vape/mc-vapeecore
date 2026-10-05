package dev.vapee.core.economy.command;

import dev.vapee.core.command.StaffTargetTestFixture;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public final class CoinsCommandHarness {
    private static int checks;
    private static final Command COMMAND = new Command("coins") {
        @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
    };

    public static void main(String[] args) throws Exception {
        testPermissionsHelpAndUsage();
        testKnownReads();
        testMutations();
        testFailures();
        testLiteralComponents();
        testCompletion();
        testSafeCompletionArguments();
        testStaffTargets();
        System.out.println("CoinsCommandHarness passed " + checks + " checks.");
    }

    private static void testPermissionsHelpAndUsage() throws Exception {
        Fixture f = new Fixture();
        Actor player = f.player("Normal", 1_234L, false);
        Actor admin = f.player("Admin", 0L, true);
        Actor console = new Actor(null, "Console", true);
        f.run(player);
        check(player.text().equals("Coins: 1,234"), "own formatted balance");
        f.run(console);
        check(console.text().contains("Only players"), "console own player-only");
        player.base = false;
        f.run(player);
        check(player.text().contains("permission"), "explicit base denial");
        f.run(player, "get", admin.name);
        check(player.text().contains("permission") && f.repo.saves == 0, "base denies admin paths");
        player.base = true;
        f.run(player, "help");
        check(player.text().contains("Player") && !player.text().contains("Administration")
                && !player.text().contains("/coins get"), "normal help filtered");
        f.run(admin, "HELP");
        check(admin.text().contains("Administration") && admin.text().contains("<player|uuid>")
                && admin.text().contains("offline") && admin.text().contains("must be online"),
                "admin help has new grammar and boundaries");
        f.run(console, "help");
        check(console.text().contains("Administration"), "console admin help");
        for (String action : List.of("get", "add", "remove", "set")) {
            f.run(player, action, admin.name, "10");
            check(player.text().contains("permission") && !player.text().contains("Unknown"),
                    "recognized action denied " + action);
        }
        for (Actor actor : List.of(player, admin)) {
            f.run(actor, "random");
            check(actor.text().contains("Unknown coins command: random")
                    && actor.text().contains("/coins help"), "unknown is not permission denial");
        }
        f.run(admin, "help", "extra");
        check(admin.text().equals("Usage: /coins help"), "help arity specific");
        f.run(admin, "get");
        check(admin.text().contains("Missing player.") && admin.text().contains("Usage: /coins get <player|uuid>"),
                "get missing target");
        f.run(admin, "get", player.name, "extra");
        check(admin.text().equals("Usage: /coins get <player|uuid>"), "get extra arguments");
        for (String action : List.of("add", "remove", "set")) {
            f.run(admin, action);
            check(admin.text().contains("Missing player and amount.") && admin.text().contains("Usage: /coins " + action),
                    action + " missing both");
            f.run(admin, action, player.name);
            check(admin.text().contains("Missing amount.") && admin.text().contains("<amount>"), action + " missing amount");
            f.run(admin, action, player.name, "1", "extra");
            check(admin.text().equals("Usage: /coins " + action + " <player|uuid> <amount>"), action + " extra args");
        }
        check(f.repo.saves == 0 && f.logs.records.isEmpty(), "UX paths have no write or audit");
        f.players.unloadPlayer(player.id);
        f.run(player);
        check(player.text().contains("is not loaded"), "own unloaded controlled");
    }

    private static void testKnownReads() throws Exception {
        Fixture f = new Fixture();
        Actor admin = f.player("Admin", 0L, true);
        Actor online = f.player("Online", 500L, false);
        UUID offline = f.seed("Offline", 1_250L);
        for (String input : List.of("oNlInE", online.id.toString())) {
            f.run(admin, "GeT", input);
            check(admin.text().equals("Online's coins: 500"), "online read name/UUID");
        }
        for (String input : List.of("offline", offline.toString())) {
            f.run(admin, "get", input);
            check(admin.text().equals("Offline's coins: 1,250") && !f.players.isLoaded(offline),
                    "offline read name/UUID does not load");
        }
        for (String input : List.of("Unknown", UUID.randomUUID().toString(), "Onl", "1-1-1-1-1")) {
            f.run(admin, "get", input);
            check(admin.text().contains("not known") && !admin.text().contains("not online"), "unknown/exact lookup");
        }
        f.seed("Duplicate", 10L);
        UUID duplicate = f.seed("duplicate", 20L);
        f.run(admin, "get", "DUPLICATE");
        check(admin.text().contains("ambiguous") && admin.text().contains("UUID"), "ambiguous rejected");
        f.run(admin, "get", duplicate.toString());
        check(admin.text().equals("duplicate's coins: 20"), "UUID resolves ambiguity");
        f.repo.failRead = true;
        f.run(admin, "get", offline.toString());
        check(admin.text().contains("could not be read") && f.logs.severe() == 1, "read failure controlled");
        check(f.repo.saves == 0 && f.logs.info() == 0, "reads never mutate or success audit");
    }

    private static void testMutations() throws Exception {
        Fixture f = new Fixture();
        Actor admin = f.player("Admin", 100L, true);
        Actor target = f.player("Target", 1_000L, false);
        f.run(admin, "AdD", "target", "500");
        check(admin.text().equals("Added 500 coins to Target. New balance: 1,500."), "add feedback");
        check(target.text().contains("increased by 500") && target.text().contains("1,500"), "add notification");
        check(f.balance(target.id) == 1_500L && f.repo.persisted.get(target.id) == 1_500L && f.repo.saves == 1,
                "add one durable save");
        String audit = f.logs.lastInfo();
        check(audit.contains("actor=Admin") && audit.contains("target=" + target.id)
                && audit.contains("name=Target") && audit.contains("action=ADD")
                && audit.contains("amount=500") && audit.contains("previous=1000") && audit.contains("new=1500"),
                "audit contains full success context");
        f.run(admin, "add", target.id.toString(), "1");
        check(f.balance(target.id) == 1_501L, "add UUID");
        f.run(admin, "remove", target.id.toString(), "251");
        check(admin.text().contains("Removed 251") && admin.text().contains("1,250")
                && target.text().contains("251 coins were removed"), "remove feedback/notification");
        f.run(admin, "set", target.name, "5000");
        check(admin.text().contains("Set Target") && admin.text().contains("5,000")
                && target.text().contains("set to 5,000"), "set feedback/notification");
        f.run(admin, "set", target.name, "0");
        check(f.balance(target.id) == 0L, "set zero valid");
        f.run(admin, "set", target.name, Long.toString(Long.MAX_VALUE));
        check(f.balance(target.id) == Long.MAX_VALUE, "max long command valid");
        f.run(admin, "add", admin.name, "10");
        check(admin.received.size() == 1 && admin.text().contains("Added 10"), "no self duplicate notification");
        Actor console = new Actor(null, "Console", true);
        f.run(console, "set", target.id.toString(), "40");
        check(f.balance(target.id) == 40L && console.text().contains("Set Target")
                && f.logs.lastInfo().contains("actor=CONSOLE"), "console mutation and actor");
        f.run(console, "get", target.name);
        check(console.text().equals("Target's coins: 40"), "console read");
        check(f.logs.info() == 8 && f.logs.severe() == 0, "exactly one audit per successful mutation");
        check(!f.orderViolation, "notification and audit occur only after durable save");
    }

    private static void testFailures() throws Exception {
        Fixture f = new Fixture();
        Actor admin = f.player("Admin", 0L, true);
        Actor target = f.player("Target", 100L, false);
        UUID offline = f.seed("Offline", 50L);
        f.seed("Dup", 1L); f.seed("dup", 2L);
        for (String action : List.of("add", "remove", "set")) {
            f.run(admin, action, offline.toString(), "10");
            check(admin.text().contains("is known") && admin.text().contains("online")
                    && !f.players.isLoaded(offline), action + " offline gate");
            f.run(admin, action, "Unknown", "10");
            check(admin.text().contains("not known"), action + " unknown");
            f.run(admin, action, "Dup", "10");
            check(admin.text().contains("ambiguous"), action + " ambiguous");
            List<String> invalid = new ArrayList<>(List.of("-1", "1.5", "nope", "1e3", "1,000",
                    "+1", "9223372036854775808"));
            if (!action.equals("set")) invalid.add("0");
            for (String amount : invalid) {
                f.run(admin, action, target.name, amount);
                check(admin.text().contains("whole number") && f.balance(target.id) == 100L,
                        action + " invalid amount " + amount);
            }
        }
        f.run(admin, "remove", target.name, "101");
        check(admin.text().contains("enough coins") && f.balance(target.id) == 100L, "insufficient funds");
        check(f.repo.saves == 0 && f.logs.info() == 0 && target.received.isEmpty(), "validation/insufficient no side effects");
        f.economy.setCoins(target.id, Long.MAX_VALUE);
        f.repo.saves = 0;
        f.run(admin, "add", target.name, "1");
        check(admin.text().contains("too large") && f.repo.saves == 0 && target.received.isEmpty()
                && f.logs.info() == 0 && f.balance(target.id) == Long.MAX_VALUE, "overflow no side effects");
        f.economy.setCoins(target.id, 100L);
        for (String action : List.of("add", "remove", "set")) {
            f.repo.failNext = true;
            f.run(admin, action, target.id.toString(), "10");
            check(admin.text().contains("could not be saved") && !admin.text().contains("Exception")
                    && f.balance(target.id) == 100L && f.repo.persisted.get(target.id) == 100L,
                    action + " save failure rollback and safe feedback");
            check(target.received.isEmpty() && f.logs.info() == 0, action + " no notify/audit failure");
            String failure = f.logs.records.getLast().getMessage();
            check(failure.contains("actor=Admin") && failure.contains("target=" + target.id)
                    && failure.contains("action=" + action.toUpperCase(Locale.ROOT)), "failure log context");
        }
        check(f.logs.severe() == 3, "all persistence failures severe");
        target.online = false;
        f.run(admin, "add", target.name, "10");
        check(admin.text().contains("require the player to be online"), "non-null offline Bukkit target rejected");
        target.online = true;
        f.players.unloadPlayer(target.id);
        int saves = f.repo.saves;
        f.run(admin, "add", target.id.toString(), "10");
        check(admin.text().contains("Player data for Target is not loaded.") && f.repo.saves == saves,
                "online but unloaded fallback never loads/writes");
    }

    private static void testLiteralComponents() throws Exception {
        Fixture f = new Fixture();
        Actor admin = f.player("Admin", 0L, true);
        Actor target = f.player("<red>Safe</red>", 100L, false);
        f.run(admin, "get", target.id.toString());
        check(admin.text().equals("<red>Safe</red>'s coins: 100"), "target name literal");
        f.run(admin, "add", target.id.toString(), "1");
        check(admin.text().contains("<red>Safe</red>") && safe(admin.received.getFirst()), "mutation literal safe");
        String attack = "<click:run_command:'/op me'>x</click>";
        f.run(admin, attack);
        check(admin.text().contains(attack) && safe(admin.received.getFirst()), "unknown literal no click event");
    }

    private static boolean safe(Component c) {
        return c.clickEvent() == null && c.children().stream().allMatch(CoinsCommandHarness::safe);
    }

    private static void testCompletion() throws Exception {
        Fixture f = new Fixture();
        Actor player = f.player("Normal", 0L, false);
        Actor admin = f.player("Admin", 0L, true);
        Actor a = f.player("Duplicate", 0L, false);
        Actor b = f.player("duplicate", 0L, false);
        f.seed("OfflineOnly", 0L);
        check(f.tab(player, "").equals(List.of("help")), "normal root");
        check(f.tab(admin, "").equals(List.of("add", "get", "help", "remove", "set")), "admin root");
        player.base = false;
        check(f.tab(player, "").isEmpty() && f.tab(player, "get", "").isEmpty(), "base denied no completion");
        player.base = true;
        for (String action : List.of("get", "add", "remove", "set")) {
            List<String> suggestions = f.tab(admin, action.toUpperCase(Locale.ROOT), "");
            check(suggestions.containsAll(List.of("Admin", "Normal", a.id.toString(), b.id.toString()))
                    && !suggestions.contains("Duplicate") && !suggestions.contains("duplicate")
                    && !suggestions.contains("OfflineOnly"), action + " safe online suggestions and self");
            check(f.tab(player, action, "").isEmpty(), action + " nonadmin no targets");
        }
        check(f.tab(admin, "g").equals(List.of("get")), "root prefix");
        check(f.tab(admin, "get", "nO").equals(List.of("Normal")), "case-insensitive target prefix");
        check(f.tab(admin, "get", a.id.toString().substring(0, 8)).contains(a.id.toString()), "UUID prefix");
        check(f.tab(admin, "add", "Normal", "").isEmpty(), "no amount suggestions");
        check(f.tab(admin, "help", "").isEmpty() && f.tab(admin, "unknown", "").isEmpty(), "irrelevant targets empty");
        check(f.repo.reads == 0, "completion never loads offline snapshots");
    }


    private static void testSafeCompletionArguments() throws Exception {
        Fixture f = new Fixture();
        Actor admin = f.player("Admin", 0L, true);
        Actor spaced = f.player("Two Words", 0L, false);
        Actor uuidNamed = f.player(admin.id.toString(), 0L, false);
        Actor unknown = new Actor(UUID.randomUUID(), "UnknownOnline", false);
        Actor mismatch = new Actor(UUID.randomUUID(), "WrongId", false);
        f.seed("WrongId", 0L);
        f.seed("UnrelatedOffline", 0L);
        f.online.put(unknown.id, unknown);
        f.online.put(mismatch.id, mismatch);
        for (String action : List.of("get", "add", "remove", "set")) {
            check(Set.copyOf(f.tab(admin, action, "")).equals(Set.of("Admin", spaced.id.toString(),
                    uuidNamed.id.toString(), unknown.id.toString(), mismatch.id.toString())),
                    "exact online universe and UUID safety including UUID-looking name " + action);
            check(f.tab(admin, action, "Two").isEmpty(), "unsafe spelling is not suggested " + action);
        }
        check(f.repo.reads == 0 && f.repo.saves == 0, "safe completion uses only indexed names, no snapshot reads or writes");
    }

    private static void testStaffTargets() throws Exception {
        for (String action : List.of("add", "remove", "set"))
            for (String actorGroup : Arrays.asList("default", "vip", "builder", "moderator", "admin", "owner", null))
                for (String targetGroup : Arrays.asList("default", "vip", "builder", "moderator", "admin", "owner", null)) {
                    Fixture f = new Fixture();
                    Actor actor = f.player("Actor", 100L, true), target = f.player("Target", 100L, false);
                    f.staff.groups.put(actor.id, actorGroup); f.staff.groups.put(target.id, targetGroup);
                    int al = (actorGroup == null ? -1 : dev.vapee.core.rank.staff.StaffHierarchyConfig.DEFAULT_GROUPS.indexOf(actorGroup));
                    int tl = (targetGroup == null ? -1 : dev.vapee.core.rank.staff.StaffHierarchyConfig.DEFAULT_GROUPS.indexOf(targetGroup));
                    boolean allowed = targetGroup != null && (tl < 0 || actorGroup != null && al > tl);
                    f.run(actor, action, target.id.toString(), "10");
                    check(f.repo.saves == (allowed ? 1 : 0) && f.logs.info() == (allowed ? 1 : 0),
                            "one save/audit only authorized " + action + " " + actorGroup + " -> " + targetGroup);
                    check(target.received.isEmpty() != allowed, "denial never notifies target");
                    check(f.balance(target.id) == (allowed ? action.equals("add") ? 110L : action.equals("remove") ? 90L : 10L : 100L),
                            "wallet balance and durable value " + action);
                    check(f.repo.persisted.get(target.id) == f.balance(target.id), "durable wallet unchanged on denial");
                    if (!allowed) check(actor.text().equals(targetGroup == null || actorGroup == null && tl >= 0
                            ? dev.vapee.core.command.OnlineStaffTargetGuard.UNAVAILABLE_MESSAGE
                            : dev.vapee.core.command.OnlineStaffTargetGuard.DENIED_MESSAGE), "controlled no-level denial");
                    check(f.tab(actor, action, "").contains("Target") == allowed, "mutation target completion filtered");
                    int reads = f.staff.reads;
                    f.run(actor, "get", target.id.toString());
                    check(actor.text().contains("Target's coins:") && f.staff.reads == reads, "get is unprotected online read");
                    check(f.tab(actor, "get", "").contains("Target") && f.staff.reads == reads, "get completion unfiltered/read-only");
                    actor.admin = false;
                    reads = f.staff.reads;
                    f.run(actor, action, target.name, "10");
                    check(actor.text().contains("permission") && f.staff.reads == reads, "admin capability before hierarchy");
                }
        for (String action : List.of("add", "remove", "set")) {
            Fixture f = new Fixture();
            Actor actor = f.player("Actor", 100L, true), target = f.player("Target", 100L, false);
            f.staff.groups.put(actor.id, null); f.staff.groups.put(target.id, "owner");
            int reads = f.staff.reads;
            f.run(actor, action, actor.id.toString(), "10");
            check(f.repo.saves == 1 && f.staff.reads == reads && actor.received.size() == 1, "economy self no hierarchy/no duplicate notify");
            Actor console = new Actor(null, "Console", true);
            f.run(console, action, target.name, "10");
            check(f.repo.saves == 2 && f.staff.reads == reads, "console authority with capability no LP reads");
            f.staff.fail = true;
            long balance = f.balance(target.id);
            f.run(actor, action, target.name, "10");
            check(f.repo.saves == 2 && f.balance(target.id) == balance && target.received.isEmpty()
                    && f.logs.info() == 2 && f.logs.severe() == 0, "lookup exception before save/audit/notify");
            check(f.logs.records.getLast().getLevel() == Level.WARNING
                    && f.logs.records.getLast().getMessage().contains(actor.id.toString())
                    && f.logs.records.getLast().getMessage().contains(target.id.toString()), "lookup warning context");
            UUID offline = f.seed("OfflineOwner", 900L);
            reads = f.staff.reads;
            f.run(actor, "get", offline.toString());
            check(actor.text().equals("OfflineOwner's coins: 900") && f.staff.reads == reads && !f.players.isLoaded(offline),
                    "offline get unchanged, no LP loading or hierarchy");
            List<Component> feedback = new ArrayList<>();
            CommandSender unsupported = proxy(CommandSender.class, (name, values) -> switch (name) {
                case "hasPermission" -> true;
                case "getName" -> "CommandBlock";
                case "sendMessage" -> { for (Object value : values) if (value instanceof Component c) feedback.add(c); yield null; }
                default -> null;
            });
            f.command.onCommand(unsupported, COMMAND, "coins", new String[]{action, target.name, "10"});
            check(f.repo.saves == 2 && f.balance(target.id) == balance && !feedback.isEmpty(), "unsupported sender not console");
        }
    }

    private static final class Fixture {
        final Repository repo = new Repository();
        final Logs logs = new Logs();
        final Logger logger = Logger.getAnonymousLogger();
        final StaffTargetTestFixture staff = new StaffTargetTestFixture(logger);
        final PlayerService players;
        final EconomyService economy;
        final Map<UUID, Actor> online = new LinkedHashMap<>();
        final CoinsCommand command;
        boolean orderViolation;

        Fixture() throws Exception {
            logger.setUseParentHandlers(false); logger.setLevel(Level.ALL); logger.addHandler(logs);
            players = new PlayerService(repo, logger); economy = new EconomyService(players);
            logs.saved = () -> {
                if (online.values().stream().anyMatch(a -> players.isLoaded(a.id)
                        && repo.persisted.get(a.id) != economy.getCoins(a.id).orElseThrow())) orderViolation = true;
            };
            Server server = proxy(Server.class, (name, args) -> switch (name) {
                case "getPlayer" -> { Actor a = online.get(args[0]); yield a == null ? null : a.sender; }
                case "getOnlinePlayers" -> online.values().stream().map(a -> (Player) a.sender).toList();
                default -> null;
            });
            var constructor = MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);
            constructor.setAccessible(true);
            MessageService messages = constructor.newInstance((java.util.function.Supplier<String>) () -> "");
            command = new CoinsCommand(server, logger, economy, players, messages, new CommandHelpRenderer(messages), staff.guard);
        }
        UUID seed(String name, long balance) {
            UUID id = UUID.randomUUID(); Instant now = Instant.now();
            repo.data.put(id, new CorePlayer(id, name, now, now, PlayerSettings.defaults(),
                    CoinWallet.of(balance), PlayerSocial.empty()));
            repo.persisted.put(id, balance); return id;
        }
        Actor player(String name, long balance, boolean admin) {
            UUID id = seed(name, balance); players.loadPlayer(id, name);
            Actor actor = new Actor(id, name, admin);
            actor.afterSend = () -> {
                if (players.isLoaded(id) && repo.persisted.get(id) != balance(id)) orderViolation = true;
            };
            online.put(id, actor); repo.reads = 0; return actor;
        }
        long balance(UUID id) { return economy.getCoins(id).orElseThrow(); }
        void run(Actor sender, String... args) {
            online.values().forEach(a -> a.received.clear()); sender.received.clear();
            check(command.onCommand(sender.sender, COMMAND, "coins", args), "command handles input");
        }
        List<String> tab(Actor sender, String... args) { return command.onTabComplete(sender.sender, COMMAND, "coins", args); }
    }

    private static final class Actor {
        final UUID id; final String name; final CommandSender sender;
        final List<Component> received = new ArrayList<>();
        boolean base = true, admin, online = true;
        Runnable afterSend = () -> { };
        Actor(UUID id, String name, boolean admin) {
            this.id = id; this.name = name; this.admin = admin;
            sender = proxy(id == null ? ConsoleCommandSender.class : Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> online;
                case "isOp" -> true;
                case "hasPermission" -> args[0].equals("vapeecore.economy.coins") ? base : this.admin;
                case "sendMessage" -> {
                    for (Object arg : args) if (arg instanceof Component c) received.add(c);
                    afterSend.run(); yield null;
                }
                default -> null;
            });
        }
        String text() { return received.stream().map(PlainTextComponentSerializer.plainText()::serialize)
                .collect(Collectors.joining("\n")); }
    }

    private static final class Repository implements PlayerRepository {
        final Map<UUID, CorePlayer> data = new HashMap<>();
        final Map<UUID, Long> persisted = new HashMap<>();
        int saves, reads; boolean failNext, failRead;
        @Override public Optional<CorePlayer> findByUniqueId(UUID id) {
            reads++; if (failRead) throw new IllegalStateException("simulated read failure");
            return Optional.ofNullable(data.get(id));
        }
        @Override public Set<UUID> findUniqueIdsByName(String name) {
            return data.values().stream().filter(p -> p.getName().equalsIgnoreCase(name))
                    .map(CorePlayer::getUniqueId).collect(Collectors.toSet());
        }
        @Override public void save(CorePlayer player) {
            saves++;
            if (failNext) { failNext = false; throw new IllegalStateException("simulated save failure"); }
            data.put(player.getUniqueId(), player); persisted.put(player.getUniqueId(), player.getWallet().getCoins());
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
    }

    private static final class Logs extends Handler {
        final List<LogRecord> records = new ArrayList<>();
        Runnable saved = () -> { };
        @Override public void publish(LogRecord r) { records.add(r); if (r.getLevel() == Level.INFO) saved.run(); }
        @Override public void flush() { }
        @Override public void close() { }
        long info() { return records.stream().filter(r -> r.getLevel() == Level.INFO).count(); }
        long severe() { return records.stream().filter(r -> r.getLevel() == Level.SEVERE).count(); }
        String lastInfo() { return records.stream().filter(r -> r.getLevel() == Level.INFO).toList().getLast().getMessage(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type, Action action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(instance);
                case "equals" -> instance == args[0];
                default -> type.getSimpleName() + "Proxy";
            };
            Object value = action.call(method.getName(), args == null ? new Object[0] : args);
            if (value != null || !method.getReturnType().isPrimitive()) return value;
            Class<?> t = method.getReturnType();
            if (t == boolean.class) return false;
            if (t == int.class) return 0;
            if (t == long.class) return 0L;
            if (t == float.class) return 0F;
            if (t == double.class) return 0D;
            if (t == byte.class) return (byte) 0;
            if (t == short.class) return (short) 0;
            if (t == char.class) return '\0';
            return null;
        });
    }
    @FunctionalInterface private interface Action { Object call(String method, Object[] args); }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
}

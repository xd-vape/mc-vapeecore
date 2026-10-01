package dev.vapee.core.command;

import dev.vapee.core.rank.staff.StaffHierarchyConfig;
import dev.vapee.core.rank.staff.StaffTargetDecision;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class OnlineStaffTargetGuardHarness {
    private static int checks;

    public static void main(String[] args) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<LogRecord> logs = new ArrayList<>();
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { logs.add(record); }
            public void flush() { }
            public void close() { }
        });
        StaffTargetTestFixture f = new StaffTargetTestFixture(logger);
        UUID actorId = UUID.randomUUID(), targetId = UUID.randomUUID();
        Player actor = sender(Player.class, actorId), target = sender(Player.class, targetId);
        List<String> groups = new ArrayList<>(List.of("default", "vip", "developer", "builder", "moderator", "admin", "owner"));
        groups.add(null);
        for (String a : groups) for (String t : groups) {
            f.groups.put(actorId, a); f.groups.put(targetId, t);
            int al = (a == null ? -1 : StaffHierarchyConfig.DEFAULT_GROUPS.indexOf(a)), tl = (t == null ? -1 : StaffHierarchyConfig.DEFAULT_GROUPS.indexOf(t));
            StaffTargetDecision expected = t == null ? StaffTargetDecision.UNAVAILABLE : tl < 0
                    ? StaffTargetDecision.ALLOW : a == null ? StaffTargetDecision.UNAVAILABLE : al < 0
                    ? StaffTargetDecision.DENY_ACTOR_NOT_PROTECTED : al > tl ? StaffTargetDecision.ALLOW
                    : StaffTargetDecision.DENY_SAME_OR_HIGHER;
            int before = logs.size();
            check(f.guard.check(actor, target, "matrix") == expected, "loaded matrix " + a + " -> " + t);
            check(logs.size() - before == (expected == StaffTargetDecision.UNAVAILABLE ? 1 : 0), "unavailable-only logging");
            check(f.guard.canSuggest(actor, target) == (expected == StaffTargetDecision.ALLOW), "silent completion parity");
            check(logs.size() - before == (expected == StaffTargetDecision.UNAVAILABLE ? 1 : 0), "completion no log spam");
        }
        int reads = f.reads;
        check(f.guard.check(actor, actor, "explicit self") == StaffTargetDecision.ALLOW && reads == f.reads,
                "self UUID never queries group even unavailable/OP");
        check(f.guard.check(sender(ConsoleCommandSender.class, null), target, "console")
                == StaffTargetDecision.ALLOW && reads == f.reads, "console authority without LP");
        check(f.guard.check(sender(CommandSender.class, null), target, "unsupported")
                == StaffTargetDecision.UNAVAILABLE && reads == f.reads, "unsupported not console");
        f.groups.put(actorId, "vip"); f.groups.put(targetId, "owner");
        check(f.guard.check(actor, target, "op") == StaffTargetDecision.DENY_ACTOR_NOT_PROTECTED, "OP no bypass");
        f.groups.put(actorId, "admin"); f.groups.put(targetId, "developer");
        check(f.guard.canSuggest(actor, target), "custom unprotected default");
        f.config = new StaffHierarchyConfig(List.of("builder", "moderator", "admin", "developer", "owner"));
        check(!f.guard.canSuggest(actor, target), "current reload snapshot protects custom immediately");
        f.config = StaffHierarchyConfig.defaults();
        check(f.guard.canSuggest(actor, target), "rollback no secondary cache");
        f.fail = true;
        int before = logs.size();
        List<net.kyori.adventure.text.Component> feedback = new ArrayList<>();
        check(!f.guard.authorize(actor, target, "fly", (sender, message) -> feedback.add(message)),
                "unexpected lookup fail closed");
        check(feedback.size() == 1 && logs.size() == before + 1, "one controlled feedback and warning");
        LogRecord last = logs.getLast();
        check(last.getLevel() == Level.WARNING && last.getThrown() != null
                && last.getMessage().contains(actorId.toString()) && last.getMessage().contains(targetId.toString())
                && last.getMessage().contains("fly"), "failure contextual actor UUID target UUID action warning");
        check(!f.guard.canSuggest(actor, target) && logs.size() == before + 1, "failure completion silent");
        check(f.guard.suggestiblePlayers(actor, java.util.Arrays.asList(null, target, actor)).equals(List.of(actor)),
                "filtered collection null/failure/self");
        System.out.println("OnlineStaffTargetGuardHarness passed " + checks + " checks.");
    }

    @SuppressWarnings("unchecked")
    private static <T extends CommandSender> T sender(Class<T> type, UUID id) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> id == null ? "CONSOLE" : "Player";
                case "isOnline", "isOp", "hasPermission" -> true;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            };
        });
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}

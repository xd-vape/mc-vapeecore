package dev.vapee.core.utility.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BuildCommandHarness {

    private static int checks;

    private BuildCommandHarness() {
    }

    public static void main(String[] args) {
        List<String> messages = new ArrayList<>();
        AtomicBoolean inLobby = new AtomicBoolean(true);
        AtomicBoolean participating = new AtomicBoolean(false);
        MutableBuildState state = new MutableBuildState();
        BuildCommand command = new BuildCommand(
                silentLogger(),
                ignored -> inLobby.get(),
                state,
                ignored -> participating.get(),
                (sender, message) -> messages.add(message)
        );

        CommandSender console = proxy(CommandSender.class, (method, arguments) -> defaultValue(method.getReturnType()));
        check(command.onCommand(console, null, "build", new String[0]), "console invocation is handled");
        check(last(messages).contains("only be used by a player"), "console receives a controlled error");

        Player denied = player(false);
        check(command.onCommand(denied, null, "build", new String[0]), "denied invocation is handled");
        check(last(messages).contains("do not have permission"), "missing permission receives a controlled error");
        check(!state.build, "permission failure does not mutate state");

        Player allowed = player(true);
        command.onCommand(allowed, null, "build", new String[]{"extra"});
        check(last(messages).contains("Usage"), "arguments receive controlled usage");

        inLobby.set(false);
        command.onCommand(allowed, null, "build", new String[0]);
        check(last(messages).contains("lobby world"), "activation outside the lobby is rejected");
        check(!state.build, "world rejection does not enter BUILD");

        inLobby.set(true);
        participating.set(true);
        command.onCommand(allowed, null, "build", new String[0]);
        check(last(messages).contains("participating in an activity"),
                "activity participation rejects BUILD activation");
        check(!state.build && state.enterCount == 0, "activity rejection leaves state untouched");

        participating.set(false);
        command.onCommand(allowed, null, "build", new String[0]);
        check(state.build && state.enterCount == 1, "eligible player enters BUILD exactly once");
        check(last(messages).contains("enabled"), "BUILD activation reports success");

        inLobby.set(false);
        command.onCommand(allowed, null, "build", new String[0]);
        check(!state.build && state.exitCount == 1, "active BUILD can always be exited safely");
        check(last(messages).contains("disabled"), "BUILD exit reports success");
        check(command.onTabComplete(allowed, null, "build", new String[]{""}).isEmpty(),
                "/build exposes no tab completions");

        System.out.println("BuildCommandHarness passed " + checks + " checks.");
    }

    private static Player player(boolean permission) {
        UUID id = UUID.randomUUID();
        return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "hasPermission" -> permission;
            case "isOnline" -> true;
            case "getName" -> "HarnessPlayer";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static String last(List<String> messages) {
        return messages.get(messages.size() - 1);
    }

    private static Logger silentLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    default -> null;
                };
            }
            return handler.invoke(method, args == null ? new Object[0] : args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }

    private static final class MutableBuildState implements BuildCommand.BuildStateAccess {
        private boolean build;
        private int enterCount;
        private int exitCount;

        @Override
        public boolean isBuildMode(UUID playerId) {
            return build;
        }

        @Override
        public void enterBuildMode(Player player) {
            build = true;
            enterCount++;
        }

        @Override
        public void exitBuildMode(Player player) {
            build = false;
            exitCount++;
        }
    }
}

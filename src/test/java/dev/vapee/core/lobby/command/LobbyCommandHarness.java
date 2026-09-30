package dev.vapee.core.lobby.command;

import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LobbyCommandHarness {
    private static int checks;
    private static final List<Component> output = new ArrayList<>();
    private static boolean permitted = true;

    public static void main(String[] args) throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        MessageService messages = constructor.newInstance((Supplier<String>) () -> "");
        UUID id = UUID.randomUUID();
        Location location = new Location(null, 10, 70, -4);
        Player player = sender(Player.class, id, location);
        CommandSender console = sender(CommandSender.class, id, location);
        boolean[] configured = {false};
        boolean[] available = {false};
        boolean[] teleported = {false};
        int[] calls = {0};
        SpawnCommand spawn = new SpawnCommand(() -> configured[0], () -> available[0],
                target -> { check(target == player, "spawn uses actual player"); calls[0]++; return teleported[0]; }, messages);
        List<LogRecord> logs = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { logs.add(record); }
            public void flush() { }
            public void close() { }
        });
        int[] saves = {0};
        boolean[] fail = {false};
        SetSpawnCommand setspawn = new SetSpawnCommand(value -> {
            saves[0]++;
            check(value == location, "setspawn uses actual player location");
            if (fail[0]) throw new IllegalStateException("Injected save failure");
        }, messages, logger);
        for (TabExecutor command : List.of(spawn, setspawn)) {
            permitted = false;
            run(command, player, "permission");
            permitted = true;
            run(command, player, "Invalid usage.", "extra");
            run(command, console, "Only players");
            check(command.onTabComplete(player, null, "lobby", new String[]{""}).isEmpty(), "lobby completion is empty");
        }
        check(calls[0] == 0 && saves[0] == 0, "permission, arity and console failures never mutate state");
        run(spawn, player, "not configured");
        configured[0] = true;
        run(spawn, player, "currently unavailable");
        check(calls[0] == 0, "missing worlds do not attempt teleports");
        available[0] = true;
        run(spawn, player, "failed or was cancelled");
        check(!text().contains("Teleported to"), "cancelled teleport does not claim success");
        teleported[0] = true;
        run(spawn, player, "Teleported to the lobby spawn");
        check(calls[0] == 2, "valid spawn calls teleport once per invocation");
        run(setspawn, player, "Lobby spawn has been set");
        fail[0] = true;
        run(setspawn, player, "could not be saved");
        check(saves[0] == 2 && !text().contains("has been set"), "failed save has no false success and no uncaught exception");
        check(logs.size() == 1 && logs.getFirst().getLevel() == Level.SEVERE
                && logs.getFirst().getMessage().contains(id.toString()) && logs.getFirst().getThrown() != null,
                "setspawn logs its save failure once with actor UUID and exception");
        System.out.println("LobbyCommandHarness passed " + checks + " checks.");
    }

    private static void run(TabExecutor command, CommandSender sender, String expected, String... args) {
        output.clear();
        check(command.onCommand(sender, null, "lobby", args) && text().contains(expected),
                command.getClass().getSimpleName() + " reports " + expected + "; received " + text());
        if (args.length > 0) check(text().contains(command instanceof SpawnCommand ? "/spawn" : "/setspawn"), "exact lobby usage");
    }

    private static String text() {
        return output.stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("", (a, b) -> a + "\n" + b);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    @SuppressWarnings("unchecked")
    private static <T> T sender(Class<T> type, UUID id, Location location) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "hasPermission" -> permitted;
            case "getUniqueId" -> id;
            case "getLocation" -> location;
            case "sendMessage" -> { for (Object arg : args) if (arg instanceof Component c) output.add(c); yield null; }
            case "toString" -> "LobbyHarnessSender";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> method.getReturnType() == boolean.class ? false : null;
        });
    }
}

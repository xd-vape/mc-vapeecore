package dev.vapee.core.lobby.warp.command;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.lobby.warp.*;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.*;

public final class WarpCommandHarness {
    private static int checks;
    private static boolean permission = true;
    private static final List<Component> output = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        MessageService messages = constructor.newInstance((Supplier<String>) () -> "");
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<LogRecord> logs = new ArrayList<>();
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { logs.add(record); }
            public void flush() { }
            public void close() { }
        });
        Path root = Path.of("target", "harness-temp").toAbsolutePath();
        Files.createDirectories(root);
        Path file = Files.createTempDirectory(root, "warp-command-").resolve("warps.yml");
        WarpConfig config = new WarpConfig(file, logger);
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, values) -> method.getName().equals("getName") ? "world" : null);
        WarpService service = new WarpService(config, ignored -> world, config.initialize());
        for (String id : List.of("alpha", "beta", "hidden_custom")) service.setWarp(id, new Location(world, 1, 64, 3));
        service.setNavigatorVisible("hidden_custom", false);
        WarpCommand command = new WarpCommand(logger, service, messages, new CommandHelpRenderer(messages));
        CommandSender console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, values) -> switch (method.getName()) {
                    case "hasPermission" -> permission;
                    case "sendMessage" -> {
                        for (Object value : values) if (value instanceof Component c) output.add(c);
                        yield null;
                    }
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
        run(command, console, "Warp Administration", "help");
        check(text().contains("/warp show <id>") && text().contains("/warp hide <id>")
                        && text().contains("/warp order <id> <order>") && text().contains("navigator only"),
                "help documents new admin actions and navigator-only visibility");
        for (String action : List.of("show", "hide", "order")) {
            run(command, console, "Invalid usage.", action);
            run(command, console, "Invalid usage.", action, "alpha", "1", "extra");
            check(text().contains("/warp " + action), "exact new usage feedback");
        }
        run(command, console, "hidden from the navigator", "hide", "ALPHA");
        check(!service.getWarp("alpha").orElseThrow().navigation().visible(), "hide changes service state");
        run(command, console, "hidden from the navigator", "hide", "alpha");
        run(command, console, "visible in the navigator", "show", "alpha");
        check(service.getWarp("alpha").orElseThrow().navigation().visible(), "show changes service state");
        run(command, console, "navigator order is 30", "order", "alpha", "30");
        run(command, console, "Navigator: Visible", "info", "alpha");
        check(text().contains("Order: 30"), "info has friendly metadata values");
        run(command, console, "Navigator: Hidden", "info", "hidden_custom");
        run(command, console, "Configured Warps", "list");
        check(text().contains("alpha • Visible • order 30") && text().contains("hidden_custom • Hidden • order 0"),
                "admin list retains hidden entries with compact metadata");
        check(complete(command, console, "show", "").equals(List.of("hidden_custom")), "show suggests hidden only");
        check(complete(command, console, "hide", "").equals(List.of("alpha", "beta")), "hide suggests visible only");
        check(complete(command, console, "ORDER", "").equals(List.of("alpha", "beta", "hidden_custom")),
                "order suggests all ids");
        check(complete(command, console, "SHOW", "HID").equals(List.of("hidden_custom")), "completion ignores case");
        check(complete(command, console, "order", "alpha", "").isEmpty(), "order has no artificial number list");
        for (String value : List.of("0", "1", "2147483647")) {
            run(command, console, "navigator order is " + value, "order", "alpha", value);
            check(WarpCommand.parseOrder(value).equals(Integer.valueOf(value)), "valid strict parser boundary: " + value);
        }
        WarpPoint unchanged = service.getWarp("alpha").orElseThrow();
        for (String value : List.of("-1", "+1", "1.0", "1e2", "2147483648", "", " ", " 1 ", "1 2",
                "999999999999999999999999999999999")) {
            run(command, console, "integer from 0 to 2147483647", "order", "alpha", value);
            check(service.getWarp("alpha").orElseThrow() == unchanged && WarpCommand.parseOrder(value) == null,
                    "invalid order controlled without mutation: " + value);
        }
        for (String action : List.of("show", "hide", "order")) {
            String[] arguments = action.equals("order") ? new String[]{action, "invalid id", "1"}
                    : new String[]{action, "invalid id"};
            run(command, console, "does not exist", arguments);
        }
        run(command, console, "Only players", "set", "custom");
        run(command, console, "Unknown subcommand", "alpha");
        permission = false;
        byte[] bytes = Files.readAllBytes(file);
        for (String action : List.of("help", "set", "remove", "list", "info", "name", "icon", "show", "hide", "order")) {
            run(command, console, "do not have permission", action, "alpha", "5");
            check(!text().contains("hidden_custom") && complete(command, console, action, "").isEmpty(),
                    "permission first, no information/ID completion leak: " + action);
        }
        check(complete(command, console, "").isEmpty() && Arrays.equals(bytes, Files.readAllBytes(file)),
                "denied commands leave persistence unchanged and root completion hidden");
        permission = true;
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("block-replacement"), "Injected save failure");
        run(command, console, "could not be saved", "hide", "alpha");
        check(service.getWarp("alpha").orElseThrow() == unchanged && !text().contains("hidden from"),
                "failed hide preserves runtime without false success");
        run(command, console, "could not be saved", "order", "alpha", "30");
        check(service.getWarp("alpha").orElseThrow() == unchanged && logs.size() == 2
                        && logs.stream().allMatch(record -> record.getLevel() == Level.SEVERE && record.getThrown() != null),
                "both mutation failures logged and controlled");
        run(command, console, "visible in the navigator", "show", "alpha");
        run(command, console, "navigator order is 2147483647", "order", "alpha", "2147483647");
        check(logs.size() == 2, "same-state success performs no persistence write");
        System.out.println("WarpCommandHarness passed " + checks + " checks.");
    }

    private static List<String> complete(WarpCommand command, CommandSender sender, String... args) {
        return command.onTabComplete(sender, null, "warp", args);
    }

    private static void run(WarpCommand command, CommandSender sender, String expected, String... args) {
        output.clear();
        check(command.onCommand(sender, null, "warp", args) && text().contains(expected),
                Arrays.toString(args) + " expected " + expected + ", got " + text());
    }

    private static String text() {
        return output.stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("", (a, b) -> a + b);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

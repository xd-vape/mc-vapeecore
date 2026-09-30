package dev.vapee.core.command;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.reload.ReloadResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Exercises the real executor, not a copy of its dispatch logic. */
public final class CoreCommandHarness {
    private static int checks;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public static void main(String[] args) throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        MessageService messages = constructor.newInstance((Supplier<String>) () -> "");
        Sender player = new Sender(true);
        Sender console = new Sender(false);
        int[] reloads = {0};
        int[] groupReads = {0};
        ReloadResult.Status[] result = {ReloadResult.Status.SUCCESS};
        String attack = "<click:run_command:'/op @s'>literal</click>";
        CoreCommand command = new CoreCommand(messages, new CommandHelpRenderer(messages),
                () -> new CoreCommand.RuntimeInfo("VapeeCore", attack, "Paper", "1.21.11", "21", 25, 2, false),
                id -> { groupReads[0]++; return Optional.of(attack); },
                () -> { reloads[0]++; return new ReloadResult(result[0], attack, 6, 0, 1); });

        run(command, player, "core", "Active Modules: 25");
        check(player.text().contains("Loaded Players: 2") && player.text().contains("Primary Group: " + attack),
                "overview includes current loaded state and literal group");
        check(player.output.stream().noneMatch(CoreCommandHarness::hasClick), "runtime/group values do not inject events");
        run(command, console, "vapeecore", "Status: Running");
        check(groupReads[0] == 1 && !console.text().contains("Primary Group:"), "console status omits player-only group lookup");
        for (String alias : List.of("core", "vapeecore")) {
            run(command, player, alias, "Java version: 21", "VeRsIoN");
            check(player.text().contains("VapeeCore version: " + attack) && player.text().contains("Paper/Bukkit version: Paper / 1.21.11"),
                    "version reports literal plugin, Paper and Java data through " + alias);
        }
        for (String action : List.of("help", "version", "reload")) {
            run(command, player, "core", "Use: /core " + action, action, "extra");
            check(player.text().contains("Invalid usage."), "exact core subcommand arity for " + action);
        }
        check(reloads[0] == 0, "bad reload arity never reaches coordinator");
        run(command, player, "core", attack, attack);
        check(player.output.stream().noneMatch(CoreCommandHarness::hasClick), "unknown subcommand remains literal");
        run(command, player, "core", "permission to reload", "reload");
        check(reloads[0] == 0, "direct executor enforces admin permission");
        check(command.onTabComplete(player.sender, null, "core", new String[]{""}).equals(List.of("help", "version")),
                "non-admin root completion excludes reload");
        check(command.onTabComplete(player.sender, null, "core", new String[]{"help", ""}).isEmpty(), "core has no second argument completion");
        run(command, player, "core", "Command Overview", "help");
        check(!player.text().contains("/profile") && !player.text().contains("/warp") && !player.text().contains("Administration"),
                "overview omits unauthorized domains and empty sections");
        player.permissions.addAll(Set.of("vapeecore.profile.view", "vapeecore.friend.use", "vapeecore.clan.use",
                "vapeecore.message.use", "vapeecore.social.ignore", "vapeecore.lobby.spawn", "vapeecore.settings.use",
                "vapeecore.rank.view", "vapeecore.ranks.view", "vapeecore.economy.coins"));
        run(command, player, "core", "Lobby & Settings", "help");
        for (String syntax : List.of("/profile [player|uuid]", "/friend help", "/clan help", "/reply <message>",
                "/ignorelist", "/settings visibility", "/spawn", "/rank [player]", "/ranks", "/coins help")) {
            check(player.text().contains(syntax), "overview contains permitted " + syntax);
        }
        run(command, console, "core", "Administration", "help");
        for (String syntax : List.of("/ping [player]", "/clear [player]", "/invsee <player>", "/enderchest [player]",
                "/core reload", "/setspawn", "/warp help", "/blackjack help")) {
            check(console.text().contains(syntax), "admin overview contains " + syntax);
        }
        check(console.output.stream().allMatch(CoreCommandHarness::suggestionsOnly), "all help clicks only suggest commands");
        check(command.onTabComplete(console.sender, null, "core", new String[]{"R"}).equals(List.of("reload")),
                "admin completion filters case-insensitively");
        String[] expected = {"reloaded successfully", "No changes were applied", "previous runtime configuration was restored",
                "could not be restored completely", "already running"};
        int index = 0;
        for (ReloadResult.Status status : ReloadResult.Status.values()) {
            result[0] = status;
            run(command, console, "core", expected[index++], "reload");
            check(console.output.stream().noneMatch(CoreCommandHarness::hasClick), "reload component/error data remain literal for " + status);
        }
        check(reloads[0] == 5, "each permitted reload delegates exactly once");
        System.out.println("CoreCommandHarness passed " + checks + " checks.");
    }

    private static void run(CoreCommand command, Sender sender, String alias, String expected, String... args) {
        sender.output.clear();
        check(command.onCommand(sender.sender, null, alias, args) && sender.text().contains(expected),
                "core " + List.of(args) + " reports " + expected + "; received " + sender.text());
    }

    private static boolean hasClick(Component c) {
        return c.clickEvent() != null || c.children().stream().anyMatch(CoreCommandHarness::hasClick);
    }

    private static boolean suggestionsOnly(Component c) {
        return (c.clickEvent() == null || c.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND)
                && c.children().stream().allMatch(CoreCommandHarness::suggestionsOnly);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static final class Sender {
        private final List<Component> output = new ArrayList<>();
        private final Set<String> permissions = new HashSet<>();
        private final CommandSender sender;

        private Sender(boolean player) {
            UUID id = UUID.randomUUID();
            Class<?> type = player ? Player.class : CommandSender.class;
            sender = (CommandSender) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "hasPermission" -> !player || permissions.contains(args[0]);
                    case "getUniqueId" -> id;
                    case "sendMessage" -> { for (Object arg : args) if (arg instanceof Component c) output.add(c); yield null; }
                    case "toString" -> "CoreHarnessSender";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == boolean.class ? false : null;
                };
            });
        }

        private String text() {
            return output.stream().map(PLAIN::serialize).reduce("", (a, b) -> a + "\n" + b);
        }
    }
}

package dev.vapee.core.message;

import dev.vapee.core.activity.blackjack.command.BlackjackCommand;
import dev.vapee.core.command.CoreCommand;
import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.economy.command.CoinsCommand;
import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpPosition;
import dev.vapee.core.lobby.warp.command.WarpCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CommandHelpHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private CommandHelpHarness() {
    }

    public static void main(String[] args) throws Exception {
        testModelAndRenderer();
        testSafeDynamicTextAndPrefix();
        testCoreAndCoinsHelp();
        testBlackjackAndWarpHelp();
        testTabSuggestions();
        System.out.println("CommandHelpHarness passed " + checks + " checks.");
    }

    private static void testModelAndRenderer() {
        CommandHelpPage page = new CommandHelpPage(
                "Harness",
                "A compact description.",
                List.of(
                        new CommandHelpSection("First", List.of(
                                new CommandHelpEntry("/warp set <id>", "Creates a warp."),
                                new CommandHelpEntry("/admin", "Administrative action.", "test.admin")
                        )),
                        new CommandHelpSection("Hidden", List.of(
                                new CommandHelpEntry("/secret", "Hidden action.", "test.secret")
                        )),
                        new CommandHelpSection("Last", List.of(
                                new CommandHelpEntry("/plain", "Shows <red>Casino</red> literally.")
                        ))
                ),
                "A footer."
        );
        check(page.sections().get(0).title().equals("First"), "section order is retained");
        check(page.sections().get(0).entries().get(0).syntax().equals("/warp set <id>"),
                "entry order is retained");
        expectUnsupported(() -> page.sections().add(new CommandHelpSection("No", List.of())),
                "page sections are immutable");
        expectUnsupported(() -> page.sections().get(0).entries().add(
                new CommandHelpEntry("/no", "No.")), "section entries are immutable");

        Capture normal = capture(Set.of());
        CommandHelpRenderer renderer = new CommandHelpRenderer(normal.messageService());
        renderer.send(normal.sender(), page);
        String output = normal.singleText();
        check(output.indexOf("First") < output.indexOf("Last"), "visible sections retain their order");
        check(output.indexOf("/warp set <id>") < output.indexOf("/plain"),
                "visible entries retain their order");
        check(!output.contains("/admin"), "permission-hidden entry is absent");
        check(!output.contains("Hidden"), "empty section is omitted");
        check(output.contains("/warp set <id>"), "angle-bracket syntax remains literal");
        check(output.contains("<red>Casino</red>"), "description MiniMessage-like text remains literal");
        check(hasClickAction(normal.singleComponent(), ClickEvent.Action.SUGGEST_COMMAND),
                "syntax uses suggest-command click events");
        check(!hasClickAction(normal.singleComponent(), ClickEvent.Action.RUN_COMMAND),
                "help never uses run-command click events");

        Capture admin = capture(Set.of("test.admin"));
        new CommandHelpRenderer(admin.messageService()).send(admin.sender(), page);
        check(admin.singleText().contains("/admin"), "permission-visible entry is present");
    }

    private static void testSafeDynamicTextAndPrefix() {
        Capture capture = capture(Set.of());
        capture.messageService().send(
                capture.sender(),
                "<white><value></white>",
                Placeholder.unparsed("value", "<red>Casino</red>")
        );
        String dynamic = capture.singleText();
        check(dynamic.contains("<red>Casino</red>"), "unparsed placeholder preserves dynamic markup as text");
        check(count(dynamic, "[VapeeCore]") == 1, "dynamic message receives one prefix");

        Capture help = capture(Set.of());
        new CommandHelpRenderer(help.messageService()).send(help.sender(), new CommandHelpPage(
                "Prefix",
                List.of(new CommandHelpSection("Commands", List.of(
                        new CommandHelpEntry("/one <id>", "First line."),
                        new CommandHelpEntry("/two <player>", "Second line.")
                )))
        ));
        check(help.components().size() == 1, "multi-line help is sent once");
        check(count(help.singleText(), "[VapeeCore]") == 1, "multi-line help contains the prefix once");
    }

    private static void testCoreAndCoinsHelp() throws Exception {
        CommandHelpPage core = page(CoreCommand.class, "HELP_PAGE");
        Capture player = capture(Set.of(
                "vapeecore.message.use", "vapeecore.social.ignore", "vapeecore.lobby.spawn",
                "vapeecore.settings.use", "vapeecore.economy.coins"
        ));
        new CommandHelpRenderer(player.messageService()).send(player.sender(), core);
        String playerText = player.singleText();
        check(playerText.contains("/spawn") && playerText.contains("/coins"),
                "core help shows permitted player commands");
        check(!playerText.contains("/setspawn") && !playerText.contains("/build")
                        && !playerText.contains("/fly") && !playerText.contains("/speed")
                        && !playerText.contains("/gamemode") && !playerText.contains("/tphere")
                        && !playerText.contains("/heal") && !playerText.contains("/feed")
                        && !playerText.contains("/warp help") && !playerText.contains("/blackjack help"),
                "core help hides unavailable administration and utility commands");

        Capture builder = capture(Set.of(
                "vapeecore.utility.build", "vapeecore.utility.fly", "vapeecore.utility.speed"
        ));
        new CommandHelpRenderer(builder.messageService()).send(builder.sender(), core);
        String builderText = builder.singleText();
        check(builderText.contains("/build") && builderText.contains("/fly") && builderText.contains("/speed"),
                "core help shows a builder's three permitted utilities");
        check(!builderText.contains("/gamemode") && !builderText.contains("/tp <player>")
                        && !builderText.contains("/tphere") && !builderText.contains("/heal")
                        && !builderText.contains("/feed"),
                "core help hides utilities the builder cannot use");

        Capture admin = capture(Set.of(
                "vapeecore.admin", "vapeecore.lobby.setspawn", "vapeecore.utility.build",
                "vapeecore.utility.fly", "vapeecore.utility.speed", "vapeecore.utility.gamemode",
                "vapeecore.utility.teleport", "vapeecore.utility.teleport.here",
                "vapeecore.utility.heal", "vapeecore.utility.feed",
                "vapeecore.warp.admin", "vapeecore.blackjack.admin"
        ));
        new CommandHelpRenderer(admin.messageService()).send(admin.sender(), core);
        String adminText = admin.singleText();
        check(adminText.contains("/core reload") && adminText.contains("/blackjack help")
                        && adminText.contains("/gamemode <mode> [player]")
                        && adminText.contains("Alias: /gm") && adminText.contains("/tphere <player>"),
                "core help shows all permitted administration and utility commands");

        CommandHelpPage coins = page(CoinsCommand.class, "HELP_PAGE");
        Capture normalCoins = capture(Set.of());
        new CommandHelpRenderer(normalCoins.messageService()).send(normalCoins.sender(), coins);
        check(normalCoins.singleText().contains("/coins") && !normalCoins.singleText().contains("/coins add"),
                "coins help hides mutations from a normal player");
        Capture adminCoins = capture(Set.of("vapeecore.economy.admin"));
        new CommandHelpRenderer(adminCoins.messageService()).send(adminCoins.sender(), coins);
        check(adminCoins.singleText().contains("/coins get <player>")
                        && adminCoins.singleText().contains("/coins add <player> <amount>")
                        && adminCoins.singleText().contains("/coins remove <player> <amount>")
                        && adminCoins.singleText().contains("/coins set <player> <amount>"),
                "coins help shows all mutations to an administrator");
    }

    private static void testBlackjackAndWarpHelp() throws Exception {
        CommandHelpPage main = page(BlackjackCommand.class, "MAIN_HELP_PAGE");
        CommandHelpPage setup = page(BlackjackCommand.class, "SETUP_HELP_PAGE");
        check(main.sections().size() == 1 && main.sections().get(0).title().equals("Table Setup"),
                "blackjack root help is structured");
        String mainText = render(main);
        check(!mainText.contains("create|delete|pos1"), "blackjack root has no pipe-chain usage");
        check(setup.sections().stream().map(CommandHelpSection::title).toList()
                        .equals(List.of("Create & Configure", "Management", "Information")),
                "blackjack setup sections have the required order");
        check(setup.sections().stream().mapToInt(section -> section.entries().size()).sum() == 12,
                "blackjack setup help contains all twelve actions");
        Component workflow = invokeComponent(
                BlackjackCommand.class,
                "createWorkflow",
                new Class<?>[]{String.class},
                "casino-1"
        );
        String workflowText = PLAIN.serialize(workflow);
        check(workflowText.contains("Blackjack table 'casino-1' created.")
                        && workflowText.contains("1. /blackjack setup pos1 casino-1")
                        && workflowText.contains("6. /blackjack setup enable casino-1"),
                "blackjack create workflow shows a safe ordered setup path");
        check(hasClickAction(workflow, ClickEvent.Action.SUGGEST_COMMAND)
                        && !hasClickAction(workflow, ClickEvent.Action.RUN_COMMAND),
                "blackjack create workflow is suggest-only");
        String invalid = PLAIN.serialize(invokeComponent(
                BlackjackCommand.class,
                "invalidDefinitionMessage",
                new Class<?>[]{String.class, List.class},
                "casino-1",
                List.of("missing dealer", "at least one seat is required")
        ));
        check(invalid.contains("Table 'casino-1' is not ready.")
                        && invalid.contains("Dealer position")
                        && invalid.contains("At least one seat")
                        && invalid.contains("/blackjack setup info casino-1"),
                "blackjack validation output explains missing setup and the next check");

        CommandHelpPage warp = page(WarpCommand.class, "HELP_PAGE");
        check(warp.sections().stream().map(CommandHelpSection::title).toList()
                        .equals(List.of("Create & Edit", "Information", "Management")),
                "warp help sections have the required order");
        String warpText = render(warp);
        check(warpText.contains("/warp set <id>") && warpText.contains("/warp remove <id>"),
                "warp help is structured and complete");

        WarpPoint casino = new WarpPoint(
                "casino",
                "<red>Casino</red>",
                Material.GOLD_INGOT,
                new WarpPosition("world", 100.5, 65.0, 100.5, 180.0F, 0.0F)
        );
        Component created = invokeComponent(
                WarpCommand.class,
                "setSuccessMessage",
                new Class<?>[]{WarpPoint.class, boolean.class},
                casino,
                false
        );
        Component updated = invokeComponent(
                WarpCommand.class,
                "setSuccessMessage",
                new Class<?>[]{WarpPoint.class, boolean.class},
                casino,
                true
        );
        check(PLAIN.serialize(created).contains("Warp 'casino' created.")
                        && PLAIN.serialize(created).contains("world @ 100.5, 65.0, 100.5")
                        && PLAIN.serialize(created).contains("/warp name casino <display name>"),
                "warp creation output includes location and next steps");
        check(PLAIN.serialize(updated).contains("Warp 'casino' location updated.")
                        && !PLAIN.serialize(updated).contains("Next:"),
                "warp update output is distinguished from creation");
        String info = PLAIN.serialize(invokeComponent(
                WarpCommand.class,
                "infoMessage",
                new Class<?>[]{WarpPoint.class},
                casino
        ));
        check(info.contains("<red>Casino</red>") && info.contains("GOLD_INGOT")
                        && info.contains("Rotation: 180.0 / 0.0"),
                "warp info is structured and preserves display-name markup literally");
        WarpPoint lobby = new WarpPoint(
                "lobby",
                "Lobby",
                Material.ENDER_PEARL,
                new WarpPosition("world", 0.0, 64.0, 0.0, 0.0F, 0.0F)
        );
        String list = PLAIN.serialize(invokeComponent(
                WarpCommand.class,
                "listMessage",
                new Class<?>[]{List.class},
                List.of(casino, lobby)
        ));
        check(list.contains("2 warp(s) configured.") && list.indexOf("casino") < list.indexOf("lobby"),
                "warp list is multiline and retains stable service order");
    }

    private static void testTabSuggestions() throws Exception {
        check(invokeSuggestions(CoreCommand.class, "rootSuggestions", "h", false).equals(List.of("help")),
                "core h completes to help");
        check(!invokeSuggestions(CoreCommand.class, "rootSuggestions", "r", false).contains("reload"),
                "core reload is hidden without permission");
        check(invokeSuggestions(CoreCommand.class, "rootSuggestions", "r", true).equals(List.of("reload")),
                "core reload is visible with permission");
        check(invokeSuggestions(BlackjackCommand.class, "rootSuggestions", "s").equals(List.of("setup")),
                "blackjack s completes to setup");
        check(invokeSuggestions(BlackjackCommand.class, "setupSuggestions", "i")
                        .equals(List.of("info", "interaction")),
                "blackjack setup i completes info and interaction");
        check(invokeSuggestions(WarpCommand.class, "rootSuggestions", "i").equals(List.of("icon", "info")),
                "warp i completes icon and info");
        check(invokeSuggestions(CoinsCommand.class, "rootSuggestions", "a", true).equals(List.of("add")),
                "coins admin a completes to add");
        check(invokeSuggestions(CoinsCommand.class, "rootSuggestions", "a", false).isEmpty(),
                "coins add is hidden from a normal sender");
    }

    private static String render(CommandHelpPage page) {
        Capture capture = capture(Set.of());
        new CommandHelpRenderer(capture.messageService()).send(capture.sender(), page);
        return capture.singleText();
    }

    private static CommandHelpPage page(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (CommandHelpPage) field.get(null);
    }

    private static Component invokeComponent(
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return (Component) method.invoke(null, arguments);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSuggestions(Class<?> owner, String name, Object... arguments) throws Exception {
        Class<?>[] parameterTypes = java.util.Arrays.stream(arguments)
                .map(argument -> argument instanceof Boolean ? boolean.class : String.class)
                .toArray(Class<?>[]::new);
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, arguments);
    }

    private static Capture capture(Set<String> permissions) {
        List<Component> components = new ArrayList<>();
        Set<String> permissionCopy = new HashSet<>(permissions);
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "CommandHelpHarnessSender";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if (method.getName().equals("hasPermission") && args != null && args.length == 1
                            && args[0] instanceof String permission) {
                        return permissionCopy.contains(permission);
                    }
                    if (method.getName().equals("sendMessage") && args != null) {
                        for (Object argument : args) {
                            if (argument instanceof Component component) {
                                components.add(component);
                                break;
                            }
                        }
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        return new Capture(new MessageService(() -> "<gray>[VapeeCore]</gray> "), sender, components);
    }

    private static boolean hasClickAction(Component component, ClickEvent.Action action) {
        if (component.clickEvent() != null && component.clickEvent().action() == action) {
            return true;
        }
        return component.children().stream().anyMatch(child -> hasClickAction(child, action));
    }

    private static int count(String value, String needle) {
        int occurrences = 0;
        for (int position = value.indexOf(needle); position >= 0;
             position = value.indexOf(needle, position + needle.length())) {
            occurrences++;
        }
        return occurrences;
    }

    private static void expectUnsupported(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
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
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    private record Capture(MessageService messageService, CommandSender sender, List<Component> components) {

        private Component singleComponent() {
            check(components.size() == 1, "exactly one component was sent");
            return components.getFirst();
        }

        private String singleText() {
            return PLAIN.serialize(singleComponent());
        }
    }
}

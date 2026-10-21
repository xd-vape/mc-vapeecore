package dev.vapee.core.lobby.warp.command;

import static dev.vapee.core.module.ModuleRuntimeFixture.proxy;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.i18n.I18nTestSupport;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.i18n.Translations;
import dev.vapee.core.lobby.warp.WarpConfig;
import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.message.MessageService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/** Frozen public behavior oracle, independent of the metadata used to generate help/completion. */
public final class WarpCommandContractHarness {
    private static int checks;
    private static final List<String> TOKENS =
            List.of(
                    "help", "set", "remove", "list", "info", "name", "icon", "show", "hide",
                    "order");
    private static final List<String> HELP =
            List.of(
                    "/warp set <id>",
                    "/warp name <id> <display name...>",
                    "/warp icon <id> <material>",
                    "/warp info <id>",
                    "/warp list",
                    "/warp show <id>",
                    "/warp hide <id>",
                    "/warp order <id> <order>",
                    "/warp remove <id>");
    private static final List<String> DESCRIPTIONS =
            List.of(
                    "warp.help.warp.set.description",
                    "warp.help.warp.name.name.description",
                    "warp.help.warp.icon.description",
                    "warp.help.warp.info.description",
                    "warp.help.warp.list.description",
                    "warp.help.warp.show.description",
                    "warp.help.warp.hide.description",
                    "warp.help.warp.order.description",
                    "warp.help.warp.remove.description");

    public static void main(String[] args) throws Exception {
        dev.vapee.core.lobby.item.LobbyItemsConfigHarness.installRegistries();
        try (var f = new Fixture()) {
            check(
                    Arrays.stream(WarpSubcommand.values())
                            .map(WarpSubcommand::token)
                            .sorted()
                            .toList()
                            .equals(TOKENS.stream().sorted().toList()),
                    "authoritative list retains all ten supported actions");
            check(
                    f.complete(f.console, "").equals(TOKENS.stream().sorted().toList()),
                    "root completion retains every action in stable order");
            check(
                    f.complete(f.console, "I").equals(List.of("icon", "info")),
                    "case-insensitive prefix matching");
            check(f.complete(f.console, "unknown").isEmpty(), "unknown prefix has no completion");
            f.helpAndLocale();
            f.execution();
            f.completion();
            f.invalidArguments();
            f.denied();
            try (var input = Files.newInputStream(Path.of("src/main/resources/plugin.yml"))) {
                var descriptor = new PluginDescriptionFile(input);
                var root = descriptor.getCommands().get("warp");
                check(
                        root.get("permission").equals("vapeecore.warp.admin")
                                && root.get("usage").equals("/warp help"),
                        "descriptor root permission and usage preserved");
                check(!root.containsKey("aliases"), "Warp still declares no aliases");
                check(
                        descriptor.getPermissions().stream()
                                .anyMatch(
                                        permission ->
                                                permission.getName().equals(WarpCommand.PERMISSION)
                                                        && permission.getDefault()
                                                                == org.bukkit.permissions
                                                                        .PermissionDefault.OP),
                        "existing admin node remains op by default");
            }
        }
        System.out.println("WarpCommandContractHarness passed " + checks + " checks.");
    }

    private static final class Fixture implements AutoCloseable {
        final dev.vapee.core.module.ModuleRuntimeFixture runtime =
                new dev.vapee.core.module.ModuleRuntimeFixture("warp");
        final Logger logger = Logger.getAnonymousLogger();
        final Path directory = Files.createTempDirectory("vapeecore-warp-contract-");
        final Path file = directory.resolve("warps.yml");
        final WarpConfig config = new WarpConfig(file, logger);
        final World world =
                proxy(World.class, (method, values) -> method.equals("getName") ? "world" : null);
        final Location location = new Location(world, 2, 70, 4, 30, 10);
        final WarpService service = new WarpService(config, ignored -> world, config.initialize());
        final MessageService messages =
                new MessageService(() -> "", LocalizationService.bundled(logger));
        final WarpCommand command =
                new WarpCommand(logger, service, messages, new CommandHelpRenderer(messages));
        final List<Component> output = new ArrayList<>();
        boolean permitted = true;
        Locale locale = Locale.US;
        final CommandSender console = proxy(CommandSender.class, this::senderCall);
        final Player player = proxy(Player.class, this::senderCall);

        Fixture() throws Exception {
            logger.setUseParentHandlers(false);
            var unsafe =
                    proxy(
                            org.bukkit.UnsafeValues.class,
                            (method, args) -> {
                                if (method.equals("fromLegacy")) return org.bukkit.Material.AIR;
                                throw new AssertionError(method);
                            });
            runtime.serverCalls =
                    (method, args) -> {
                        if (method.equals("getUnsafe")) return unsafe;
                        throw new AssertionError(method);
                    };
        }

        Object senderCall(String method, Object[] values) {
            return switch (method) {
                case "hasPermission", "isOnline" -> permitted;
                case "locale" -> locale;
                case "getName" -> "WarpTester";
                case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000333");
                case "getLocation" -> location.clone();
                case "getWorld" -> world;
                case "sendMessage" -> {
                    for (Object value : values)
                        if (value instanceof Component component) output.add(component);
                    yield null;
                }
                default -> throw new AssertionError("unexpected sender call: " + method);
            };
        }

        void run(CommandSender sender, String expected, String... args) {
            output.clear();
            check(
                    command.onCommand(sender, null, "warp", args),
                    "handled: " + Arrays.toString(args));
            check(
                    text().contains(expected),
                    Arrays.toString(args) + " expected " + expected + ", got " + text());
        }

        List<String> complete(CommandSender sender, String... args) {
            return command.onTabComplete(sender, null, "warp", args);
        }

        String text() {
            return output.stream()
                    .map(PlainTextComponentSerializer.plainText()::serialize)
                    .reduce("", String::concat);
        }

        void helpAndLocale() {
            for (Locale selected : List.of(Locale.US, Locale.GERMANY)) {
                locale = selected;
                run(player, translated(player, "warp.help.title"), "HeLp");
                List<String> suggestions = new ArrayList<>();
                output.forEach(component -> clicks(component, suggestions));
                check(
                        suggestions.equals(HELP),
                        "help sections/order/click suggestions preserve the nine documented actions "
                                + locale);
                for (String key : DESCRIPTIONS)
                    check(
                            text().contains(translated(player, key)),
                            "localized description: " + key + "/" + locale);
                run(player, translated(player, "common.invalid-usage"), "help", "extra");
                check(text().contains("/warp help"), "localized error retains canonical syntax");
            }
            locale = Locale.US;
            run(console, "Warp Administration");
        }

        void execution() throws Exception {
            run(console, "Only players", "set", "alpha");
            check(service.getWarps().isEmpty(), "console cannot create a location warp");
            run(player, "created", "SeT", "ALPHA");
            check(
                    service.getWarp("alpha").orElseThrow().position().x() == 2,
                    "player set uses actual player location");
            run(console, "display name changed", "NaMe", "alpha", "Two", "Words", "Longer");
            check(
                    service.getWarp("alpha").orElseThrow().displayName().equals("Two Words Longer"),
                    "variable name tail preserved");
            run(console, "icon", "ICON", "alpha", "DIAMOND");
            check(
                    service.getWarp("alpha").orElseThrow().icon() == org.bukkit.Material.DIAMOND,
                    "icon delegates to service");
            run(console, "hidden from the navigator", "HIDE", "alpha");
            check(!service.getWarp("alpha").orElseThrow().navigation().visible(), "hide dispatch");
            run(console, "visible in the navigator", "SHOW", "alpha");
            check(service.getWarp("alpha").orElseThrow().navigation().visible(), "show dispatch");
            run(console, "navigator order is 17", "ORDER", "alpha", "17");
            run(console, "Order: 17", "INFO", "alpha");
            run(console, "Configured Warps", "LiSt");
            check(
                    config.initialize().get("alpha").navigation().order() == 17,
                    "domain service persists metadata");
            run(player, "location updated", "set", "alpha");
            check(
                    service.getWarp("alpha").orElseThrow().displayName().equals("Two Words Longer"),
                    "set update preserves presentation metadata");
            run(console, "removed", "REMOVE", "alpha");
            check(
                    service.getWarp("alpha").isEmpty() && config.initialize().isEmpty(),
                    "remove dispatch persisted");
        }

        void completion() {
            for (String id : List.of("alpha", "beta", "hidden_custom"))
                service.setWarp(id, location);
            service.setNavigatorVisible("hidden_custom", false);
            for (String action : List.of("remove", "info", "name", "icon", "order")) {
                check(
                        complete(console, action.toUpperCase(Locale.ROOT), "")
                                .equals(List.of("alpha", "beta", "hidden_custom")),
                        "dynamic ID completion " + action);
            }
            check(
                    complete(console, "show", "HID").equals(List.of("hidden_custom")),
                    "show suggests hidden IDs only");
            check(
                    complete(console, "hide", "").equals(List.of("alpha", "beta")),
                    "hide suggests visible IDs only");
            for (String action : List.of("set", "help", "list", "unknown"))
                check(complete(console, action, "").isEmpty(), "no inapplicable IDs " + action);
            List<String> materials = complete(console, "IcOn", "alpha", "diamond");
            check(
                    materials.contains("DIAMOND")
                            && materials.stream().allMatch(value -> value.startsWith("DIAMOND")),
                    "contextual material prefix");
            check(
                    materials.equals(
                            materials.stream()
                                    .distinct()
                                    .sorted(String.CASE_INSENSITIVE_ORDER)
                                    .toList()),
                    "stable unique material suggestions");
            check(
                    complete(console, "name", "alpha", "").isEmpty()
                            && complete(console, "order", "alpha", "").isEmpty(),
                    "no invented name/number suggestions");
        }

        void invalidArguments() throws Exception {
            byte[] before = Files.readAllBytes(file);
            for (WarpSubcommand action : WarpSubcommand.values()) {
                if (action == WarpSubcommand.HELP || action == WarpSubcommand.LIST) {
                    run(console, "Invalid usage.", action.token(), "extra");
                } else {
                    run(console, "Invalid usage.", action.token());
                    if (action != WarpSubcommand.NAME)
                        run(console, "Invalid usage.", action.token(), "alpha", "1", "extra");
                }
                check(text().contains(action.syntax()), "usage matches existing syntax " + action);
            }
            run(console, "Invalid usage.", "name", "alpha");
            run(console, "Unknown subcommand", "<red>unknown</red>");
            check(text().contains("<red>unknown</red>"), "unknown input remains literal");
            run(console, "does not exist", "info", "missing");
            run(console, "integer from 0 to 2147483647", "order", "alpha", "-1");
            run(console, "displayed as an item", "icon", "alpha", "AIR");
            check(
                    Arrays.equals(before, Files.readAllBytes(file)),
                    "invalid grammar/results never alter persistence");
        }

        void denied() throws Exception {
            byte[] before = Files.readAllBytes(file);
            List<WarpPoint> snapshot = service.getWarps();
            permitted = false;
            for (CommandSender sender : List.of(console, player)) {
                for (String label : List.of("warp", "WARP", "vapeecore:warp")) {
                    for (String token : TOKENS) {
                        output.clear();
                        command.onCommand(
                                sender,
                                null,
                                label,
                                new String[] {token.toUpperCase(Locale.ROOT), "alpha", "1"});
                        check(
                                text().contains("do not have permission")
                                        && !text().contains("/warp "),
                                "permission before dispatch/help/grammar " + label + "/" + token);
                        check(
                                command.onTabComplete(sender, null, label, new String[] {token, ""})
                                        .isEmpty(),
                                "no protected ID suggestions");
                    }
                    output.clear();
                    command.onCommand(sender, null, label, new String[0]);
                    check(text().contains("do not have permission"), "no-args help protected");
                    check(
                            command.onTabComplete(sender, null, label, new String[] {""}).isEmpty(),
                            "root suggestions protected");
                    check(
                            command.onTabComplete(
                                            sender, null, label, new String[] {"icon", "alpha", ""})
                                    .isEmpty(),
                            "material suggestions protected");
                }
            }
            check(
                    service.getWarps().equals(snapshot)
                            && Arrays.equals(before, Files.readAllBytes(file)),
                    "denied actions never mutate service or files");
        }

        @Override
        public void close() throws Exception {
            runtime.close();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private static String translated(CommandSender recipient, String key) {
        return I18nTestSupport.text(recipient, Translations.tr(key));
    }

    private static void clicks(Component component, List<String> values) {
        if (component.clickEvent() != null) {
            check(
                    component.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND,
                    "help preserves suggest-command action");
            values.add(component.clickEvent().value());
        }
        component.children().forEach(child -> clicks(child, values));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}

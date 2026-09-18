package dev.vapee.core.economy.command;

import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.economy.EconomyResult;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.logging.Level;

public final class CoinsCommand implements TabExecutor {

    private static final String ADMIN_PERMISSION = "vapeecore.economy.admin";
    private static final CommandHelpPage HELP_PAGE = new CommandHelpPage(
            "Coins",
            List.of(
                    new CommandHelpSection("Player", List.of(
                            new CommandHelpEntry("/coins", "Shows your current coin balance.")
                    )),
                    new CommandHelpSection("Administration", List.of(
                            new CommandHelpEntry("/coins get <player>", "Shows an online player's balance.",
                                    ADMIN_PERMISSION),
                            new CommandHelpEntry("/coins add <player> <amount>", "Adds coins to an online player.",
                                    ADMIN_PERMISSION),
                            new CommandHelpEntry("/coins remove <player> <amount>",
                                    "Removes coins from an online player.", ADMIN_PERMISSION),
                            new CommandHelpEntry("/coins set <player> <amount>", "Sets an online player's balance.",
                                    ADMIN_PERMISSION)
                    ))
            )
    );

    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;

    public CoinsCommand(
            JavaPlugin plugin,
            EconomyService economyService,
            MessageService messageService,
            CommandHelpRenderer helpRenderer
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendOwnBalance(sender);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            helpRenderer.send(sender, HELP_PAGE);
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to use this command.</red>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "get" -> handleGet(sender, args);
            case "add" -> handleMutation(sender, args, Mutation.ADD);
            case "remove" -> handleMutation(sender, args, Mutation.REMOVE);
            case "set" -> handleMutation(sender, args, Mutation.SET);
            default -> sendUnknown(sender, args[0]);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return rootSuggestions(args[0], sender.hasPermission(ADMIN_PERMISSION));
        }
        if (args.length == 2
                && sender.hasPermission(ADMIN_PERMISSION)
                && List.of("get", "add", "remove", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            return matches(
                    plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(),
                    args[1]
            );
        }
        return List.of();
    }

    static List<String> rootSuggestions(String input, boolean admin) {
        List<String> values = new ArrayList<>(List.of("help"));
        if (admin) {
            values.addAll(List.of("get", "add", "remove", "set"));
        }
        return matches(values, input);
    }

    private void sendOwnBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "<red>Only players can view their own coin balance.</red>");
            return;
        }

        OptionalLong balance = economyService.getCoins(player.getUniqueId());
        if (balance.isEmpty()) {
            sendPlayerNotLoaded(sender, player);
            return;
        }
        messageService.send(player, labeledValue("Coins: ", format(balance.getAsLong())));
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendInvalidUsage(sender, "/coins get <player>");
            return;
        }

        Player target = findOnlinePlayer(sender, args[1]);
        if (target == null) {
            return;
        }

        OptionalLong balance = economyService.getCoins(target.getUniqueId());
        if (balance.isEmpty()) {
            sendPlayerNotLoaded(sender, target);
            return;
        }
        messageService.send(sender, Component.text(target.getName() + "'s coins: ", NamedTextColor.GRAY)
                .append(Component.text(format(balance.getAsLong()), NamedTextColor.WHITE)));
    }

    private void handleMutation(CommandSender sender, String[] args, Mutation mutation) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/coins " + mutation.name().toLowerCase(Locale.ROOT)
                    + " <player> <amount>");
            return;
        }

        Player target = findOnlinePlayer(sender, args[1]);
        if (target == null) {
            return;
        }

        Long amount = parseAmount(sender, args[2], mutation == Mutation.SET);
        if (amount == null) {
            return;
        }

        EconomyResult result;
        try {
            result = switch (mutation) {
                case ADD -> economyService.addCoins(target.getUniqueId(), amount);
                case REMOVE -> economyService.removeCoins(target.getUniqueId(), amount);
                case SET -> economyService.setCoins(target.getUniqueId(), amount);
            };
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Could not persist a coin balance change for " + target.getUniqueId()
                            + " requested by " + sender.getName() + ".",
                    exception
            );
            messageService.send(sender, "<red>The coin balance could not be saved. Check the server log.</red>");
            return;
        }

        sendMutationResult(sender, target, amount, mutation, result);
    }

    private void sendMutationResult(
            CommandSender sender,
            Player target,
            long amount,
            Mutation mutation,
            EconomyResult result
    ) {
        switch (result) {
            case SUCCESS -> sendMutationSuccess(sender, target, amount, mutation);
            case PLAYER_NOT_LOADED -> sendPlayerNotLoaded(sender, target);
            case INSUFFICIENT_FUNDS -> messageService.send(sender,
                    Component.text(target.getName(), NamedTextColor.WHITE)
                            .append(Component.text(" does not have enough coins.", NamedTextColor.RED)));
            case BALANCE_OVERFLOW -> messageService.send(sender,
                    "<red>The resulting coin balance would be too large.</red>"
            );
        }
    }

    private void sendMutationSuccess(CommandSender sender, Player target, long amount, Mutation mutation) {
        long newBalance = economyService.getCoins(target.getUniqueId()).orElseThrow();
        Component message = switch (mutation) {
            case ADD -> mutationMessage("Added ", amount, " coins to ", target.getName(), newBalance);
            case REMOVE -> mutationMessage("Removed ", amount, " coins from ", target.getName(), newBalance);
            case SET -> Component.text("Set ", NamedTextColor.GREEN)
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .append(Component.text("'s coin balance to ", NamedTextColor.GREEN))
                    .append(Component.text(format(newBalance), NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.GREEN));
        };
        messageService.send(sender, message);
    }

    private Player findOnlinePlayer(CommandSender sender, String name) {
        Player target = plugin.getServer().getPlayerExact(name);
        if (target == null) {
            messageService.send(sender, "<red>The specified player is not online.</red>");
        }
        return target;
    }

    private Long parseAmount(CommandSender sender, String input, boolean zeroAllowed) {
        long amount;
        try {
            amount = Long.parseLong(input);
        } catch (NumberFormatException exception) {
            sendInvalidAmount(sender, zeroAllowed);
            return null;
        }

        if (amount < 0L || (!zeroAllowed && amount == 0L)) {
            sendInvalidAmount(sender, zeroAllowed);
            return null;
        }
        return amount;
    }

    private void sendInvalidAmount(CommandSender sender, boolean zeroAllowed) {
        String requirement = zeroAllowed ? "a non-negative" : "a positive";
        messageService.send(sender, Component.text(
                "The amount must be " + requirement + " whole number.", NamedTextColor.RED));
    }

    private void sendPlayerNotLoaded(CommandSender sender, Player player) {
        messageService.send(sender, Component.text("Player data for ", NamedTextColor.RED)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" is not loaded.", NamedTextColor.RED)));
    }

    private void sendUnknown(CommandSender sender, String subcommand) {
        messageService.send(sender,
                "<red>Unknown subcommand '<white><value></white>'.</red>\n"
                        + "<yellow>Use:</yellow> <aqua>/coins help</aqua>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value", subcommand)
        );
    }

    private void sendInvalidUsage(CommandSender sender, String syntax) {
        messageService.send(sender, net.kyori.adventure.text.Component.text(
                        "Invalid usage.", net.kyori.adventure.text.format.NamedTextColor.RED)
                .append(net.kyori.adventure.text.Component.newline())
                .append(net.kyori.adventure.text.Component.text(
                        "Use: ", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .append(net.kyori.adventure.text.Component.text(
                        syntax, net.kyori.adventure.text.format.NamedTextColor.AQUA))
        );
    }

    private static List<String> matches(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String format(long coins) {
        return String.format(Locale.US, "%,d", coins);
    }

    private Component labeledValue(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private Component mutationMessage(String action, long amount, String relation, String playerName, long balance) {
        return Component.text(action, NamedTextColor.GREEN)
                .append(Component.text(format(amount), NamedTextColor.WHITE))
                .append(Component.text(relation, NamedTextColor.GREEN))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(". New balance: ", NamedTextColor.GREEN))
                .append(Component.text(format(balance), NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    private enum Mutation {
        ADD,
        REMOVE,
        SET
    }
}

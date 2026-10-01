package dev.vapee.core.economy.command;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.economy.EconomyResult;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.PlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CoinsCommand implements TabExecutor {

    private static final String BASE_PERMISSION = "vapeecore.economy.coins";
    private static final String ADMIN_PERMISSION = "vapeecore.economy.admin";
    private static final List<String> ADMIN_ACTIONS = List.of("get", "add", "remove", "set");
    private static final CommandHelpPage HELP_PAGE = new CommandHelpPage("Coins", List.of(
            new CommandHelpSection("Player", List.of(
                    new CommandHelpEntry("/coins", "Shows your current coin balance.", BASE_PERMISSION)
            )),
            new CommandHelpSection("Administration", List.of(
                    new CommandHelpEntry("/coins get <player|uuid>",
                            "Shows a known online or offline player's balance.", ADMIN_PERMISSION),
                    new CommandHelpEntry("/coins add <player|uuid> <amount>",
                            "Adds coins; the target must be online.", ADMIN_PERMISSION),
                    new CommandHelpEntry("/coins remove <player|uuid> <amount>",
                            "Removes coins; the target must be online.", ADMIN_PERMISSION),
                    new CommandHelpEntry("/coins set <player|uuid> <amount>",
                            "Sets a balance; the target must be online.", ADMIN_PERMISSION)
            ))
    ));

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final Server server;
    private final Logger logger;
    private final EconomyService economyService;
    private final PlayerService playerService;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;

    public CoinsCommand(JavaPlugin plugin, EconomyService economyService, PlayerService playerService,
                        MessageService messageService, CommandHelpRenderer helpRenderer, OnlineStaffTargetGuard staffTargetGuard) {
        this(Objects.requireNonNull(plugin, "plugin").getServer(), plugin.getLogger(),
                economyService, playerService, messageService, helpRenderer, staffTargetGuard);
    }

    CoinsCommand(Server server, Logger logger, EconomyService economyService, PlayerService playerService,
                 MessageService messageService, CommandHelpRenderer helpRenderer, OnlineStaffTargetGuard staffTargetGuard) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
        this.staffTargetGuard = Objects.requireNonNull(staffTargetGuard, "staffTargetGuard");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(BASE_PERMISSION)) {
            sendDenied(sender);
            return true;
        }
        if (args.length == 0) {
            sendOwnBalance(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("help")) {
            if (args.length == 1) helpRenderer.send(sender, HELP_PAGE);
            else sendUsage(sender, null, "/coins help");
            return true;
        }
        if (!ADMIN_ACTIONS.contains(action)) {
            messageService.send(sender, Component.text("Unknown coins command: ", NamedTextColor.RED)
                    .append(Component.text(args[0], NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Use /coins help to view available commands.", NamedTextColor.YELLOW)));
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sendDenied(sender);
            return true;
        }
        if (action.equals("get")) handleGet(sender, args);
        else handleMutation(sender, args, Mutation.valueOf(action.toUpperCase(Locale.ROOT)));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(BASE_PERMISSION)) return List.of();
        if (args.length == 1) return rootSuggestions(args[0], sender.hasPermission(ADMIN_PERMISSION));
        if (args.length == 2 && sender.hasPermission(ADMIN_PERMISSION)
                && ADMIN_ACTIONS.contains(args[0].toLowerCase(Locale.ROOT))) {
            // Only indexed name lookups: never read offline snapshots or scan storage for completion.
            return matches(server.getOnlinePlayers().stream().filter(Player::isOnline)
                    .filter(player -> args[0].equalsIgnoreCase("get") || staffTargetGuard.canSuggest(sender, player))
                    .map(this::safeArgument).distinct().toList(), args[1]);
        }
        return List.of();
    }

    static List<String> rootSuggestions(String input, boolean admin) {
        List<String> values = new ArrayList<>(List.of("help"));
        if (admin) values.addAll(ADMIN_ACTIONS);
        return matches(values, input);
    }

    private String safeArgument(Player player) {
        Set<UUID> ids = playerService.findKnownIdsByName(player.getName());
        return ids.size() == 1 && ids.contains(player.getUniqueId())
                ? player.getName() : player.getUniqueId().toString();
    }

    private void sendOwnBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "<red>Only players can view their own coin balance.</red>");
            return;
        }
        OptionalLong balance = economyService.getCoins(player.getUniqueId());
        if (balance.isEmpty()) {
            sendPlayerNotLoaded(sender, player.getName());
            return;
        }
        messageService.send(sender, Component.text("Coins: ", NamedTextColor.GRAY)
                .append(Component.text(format(balance.getAsLong()), NamedTextColor.WHITE)));
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendUsage(sender, args.length == 1 ? "Missing player." : null, "/coins get <player|uuid>");
            return;
        }
        Target target = resolve(sender, args[1]);
        if (target == null) return;
        try {
            OptionalLong balance = economyService.getKnownCoins(target.id());
            if (balance.isEmpty()) {
                sendUnknownTarget(sender);
                return;
            }
            messageService.send(sender, Component.text(target.name() + "'s coins: ", NamedTextColor.GRAY)
                    .append(Component.text(format(balance.getAsLong()), NamedTextColor.WHITE)));
        } catch (RuntimeException exception) {
            logReadFailure(sender, target.id().toString(), exception);
        }
    }

    private Target resolve(CommandSender sender, String input) {
        try {
            UUID id = parseUuid(input);
            if (id == null) {
                Set<UUID> ids = playerService.findKnownIdsByName(input);
                if (ids.size() > 1) {
                    messageService.send(sender,
                            "<red>That name is ambiguous. Use the player's UUID.</red>");
                    return null;
                }
                if (ids.isEmpty()) {
                    sendUnknownTarget(sender);
                    return null;
                }
                id = ids.iterator().next();
            }
            Target target = playerService.findKnownPlayer(id)
                    .map(player -> new Target(player.getUniqueId(), player.getName())).orElse(null);
            if (target == null) sendUnknownTarget(sender);
            return target;
        } catch (RuntimeException exception) {
            logReadFailure(sender, input, exception);
            return null;
        }
    }

    private static UUID parseUuid(String input) {
        try {
            UUID id = UUID.fromString(input);
            return id.toString().equalsIgnoreCase(input) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void handleMutation(CommandSender sender, String[] args, Mutation mutation) {
        if (args.length != 3) {
            String missing = args.length == 1 ? "Missing player and amount."
                    : args.length == 2 ? "Missing amount." : null;
            sendUsage(sender, missing, "/coins " + mutation.name().toLowerCase(Locale.ROOT)
                    + " <player|uuid> <amount>");
            return;
        }
        Target target = resolve(sender, args[1]);
        if (target == null) return;
        Player online = server.getPlayer(target.id());
        if (online == null || !online.isOnline()) {
            messageService.send(sender, Component.text(target.name(), NamedTextColor.WHITE)
                    .append(Component.text(
                            " is known, but coin changes currently require the player to be online.",
                            NamedTextColor.RED)));
            return;
        }
        if (!staffTargetGuard.authorize(sender, online, "coins " + mutation.name(), messageService::send)) return;
        OptionalLong previous = economyService.getCoins(target.id());
        if (!playerService.isLoaded(target.id()) || previous.isEmpty()) {
            sendPlayerNotLoaded(sender, target.name());
            return;
        }
        Long amount = parseAmount(sender, args[2], mutation == Mutation.SET);
        if (amount == null) return;

        EconomyResult result;
        try {
            result = switch (mutation) {
                case ADD -> economyService.addCoins(target.id(), amount);
                case REMOVE -> economyService.removeCoins(target.id(), amount);
                case SET -> economyService.setCoins(target.id(), amount);
            };
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not persist coin admin mutation [actor=" + actor(sender)
                    + ", target=" + target.id() + ", action=" + mutation + "]", exception);
            messageService.send(sender, "<red>The coin balance could not be saved. Please try again.</red>");
            return;
        }
        switch (result) {
            case SUCCESS -> {
                long balance = economyService.getCoins(target.id()).orElseThrow();
                logger.info("Coin admin mutation [actor=" + actor(sender) + ", target=" + target.id()
                        + ", name=" + target.name() + ", action=" + mutation + ", amount=" + amount
                        + ", previous=" + previous.getAsLong() + ", new=" + balance + "]");
                sendMutationSuccess(sender, target, amount, mutation, balance);
                Player recipient = server.getPlayer(target.id());
                if (recipient != null && recipient.isOnline()
                        && (!(sender instanceof Player player) || !player.getUniqueId().equals(target.id()))) {
                    notifyTarget(recipient, amount, mutation, balance);
                }
            }
            case PLAYER_NOT_LOADED -> sendPlayerNotLoaded(sender, target.name());
            case INSUFFICIENT_FUNDS -> messageService.send(sender,
                    Component.text(target.name(), NamedTextColor.WHITE)
                            .append(Component.text(" does not have enough coins.", NamedTextColor.RED)));
            case BALANCE_OVERFLOW -> messageService.send(sender,
                    "<red>The resulting coin balance would be too large.</red>");
        }
    }

    private void sendMutationSuccess(CommandSender sender, Target target, long amount,
                                     Mutation mutation, long balance) {
        String text = switch (mutation) {
            case ADD -> "Added " + format(amount) + " coins to " + target.name()
                    + ". New balance: " + format(balance) + ".";
            case REMOVE -> "Removed " + format(amount) + " coins from " + target.name()
                    + ". New balance: " + format(balance) + ".";
            case SET -> "Set " + target.name() + "'s coin balance to " + format(balance) + ".";
        };
        messageService.send(sender, Component.text(text, NamedTextColor.GREEN));
    }

    private void notifyTarget(Player target, long amount, Mutation mutation, long balance) {
        String text = switch (mutation) {
            case ADD -> "Your coin balance increased by " + format(amount)
                    + ". New balance: " + format(balance) + ".";
            case REMOVE -> format(amount) + " coins were removed from your balance. New balance: "
                    + format(balance) + ".";
            case SET -> "Your coin balance was set to " + format(balance) + ".";
        };
        messageService.send(target, Component.text(text, NamedTextColor.GREEN));
    }

    private static String actor(CommandSender sender) {
        return sender instanceof ConsoleCommandSender ? "CONSOLE" : sender.getName();
    }

    private Long parseAmount(CommandSender sender, String input, boolean zeroAllowed) {
        try {
            if (!input.matches("[0-9]+")) throw new NumberFormatException();
            long amount = Long.parseLong(input);
            if (!zeroAllowed && amount == 0L) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException exception) {
            messageService.send(sender, Component.text("The amount must be "
                    + (zeroAllowed ? "a non-negative" : "a positive") + " whole number.", NamedTextColor.RED));
            return null;
        }
    }

    private void sendPlayerNotLoaded(CommandSender sender, String name) {
        messageService.send(sender, Component.text("Player data for " + name + " is not loaded.", NamedTextColor.RED));
    }

    private void sendUnknownTarget(CommandSender sender) {
        messageService.send(sender, "<red>This player is not known to VapeeCore.</red>");
    }

    private void logReadFailure(CommandSender sender, String target, RuntimeException exception) {
        logger.log(Level.SEVERE, "Could not read coin target [actor=" + actor(sender)
                + ", target=" + target + "]", exception);
        messageService.send(sender, "<red>Player coin data could not be read. Please try again.</red>");
    }

    private void sendDenied(CommandSender sender) {
        messageService.send(sender, "<red>You do not have permission to use this command.</red>");
    }

    private void sendUsage(CommandSender sender, String error, String syntax) {
        Component message = error == null ? Component.empty()
                : Component.text(error, NamedTextColor.RED).append(Component.newline());
        messageService.send(sender, message.append(Component.text("Usage: ", NamedTextColor.YELLOW))
                .append(Component.text(syntax, NamedTextColor.AQUA)));
    }

    private static List<String> matches(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static String format(long coins) {
        return String.format(Locale.US, "%,d", coins);
    }

    private record Target(UUID id, String name) { }

    private enum Mutation { ADD, REMOVE, SET }
}

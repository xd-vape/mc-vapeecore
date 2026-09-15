package dev.vapee.core.economy.command;

import dev.vapee.core.economy.EconomyResult;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.logging.Level;

public final class CoinsCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "vapeecore.economy.admin";

    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final MessageService messageService;

    public CoinsCommand(JavaPlugin plugin, EconomyService economyService, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
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
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to manage coin balances.</red>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "get" -> handleGet(sender, args);
            case "add" -> handleMutation(sender, args, Mutation.ADD);
            case "remove" -> handleMutation(sender, args, Mutation.REMOVE);
            case "set" -> handleMutation(sender, args, Mutation.SET);
            default -> sendUsage(sender);
        }
        return true;
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
        messageService.send(player, "<gray>Coins:</gray> <white>" + format(balance.getAsLong()) + "</white>");
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
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
        messageService.send(sender, "<gray>" + target.getName() + "'s coins:</gray> <white>"
                + format(balance.getAsLong()) + "</white>"
        );
    }

    private void handleMutation(CommandSender sender, String[] args, Mutation mutation) {
        if (args.length != 3) {
            sendUsage(sender);
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
                    "<red>" + target.getName() + " does not have enough coins.</red>"
            );
            case BALANCE_OVERFLOW -> messageService.send(sender,
                    "<red>The resulting coin balance would be too large.</red>"
            );
        }
    }

    private void sendMutationSuccess(CommandSender sender, Player target, long amount, Mutation mutation) {
        long newBalance = economyService.getCoins(target.getUniqueId()).orElseThrow();
        String message = switch (mutation) {
            case ADD -> "<green>Added <white>" + format(amount) + "</white> coins to <white>"
                    + target.getName() + "</white>. New balance: <white>" + format(newBalance) + "</white>.</green>";
            case REMOVE -> "<green>Removed <white>" + format(amount) + "</white> coins from <white>"
                    + target.getName() + "</white>. New balance: <white>" + format(newBalance) + "</white>.</green>";
            case SET -> "<green>Set <white>" + target.getName() + "</white>'s coin balance to <white>"
                    + format(newBalance) + "</white>.</green>";
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
        messageService.send(sender, "<red>The amount must be " + requirement + " whole number.</red>");
    }

    private void sendPlayerNotLoaded(CommandSender sender, Player player) {
        messageService.send(sender, "<red>Player data for <white>" + player.getName()
                + "</white> is not loaded.</red>"
        );
    }

    private void sendUsage(CommandSender sender) {
        messageService.send(sender,
                "<yellow>Usage:</yellow> <white>/coins [get \\<player>|add \\<player> \\<amount>"
                        + "|remove \\<player> \\<amount>|set \\<player> \\<amount>]</white>"
        );
    }

    private String format(long coins) {
        return String.format(Locale.US, "%,d", coins);
    }

    private enum Mutation {
        ADD,
        REMOVE,
        SET
    }
}

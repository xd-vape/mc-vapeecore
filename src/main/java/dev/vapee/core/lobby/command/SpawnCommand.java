package dev.vapee.core.lobby.command;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class SpawnCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.lobby.spawn";
    private final BooleanSupplier hasSpawn;
    private final BooleanSupplier spawnAvailable;
    private final Predicate<Player> teleport;
    private final MessageService messageService;

    public SpawnCommand(LobbyService lobbyService, MessageService messageService) {
        this(Objects.requireNonNull(lobbyService, "lobbyService")::hasSpawn,
                () -> lobbyService.getSpawnLocation().isPresent(), lobbyService::teleportToSpawn, messageService);
    }

    SpawnCommand(BooleanSupplier hasSpawn, BooleanSupplier spawnAvailable,
                 Predicate<Player> teleport, MessageService messageService) {
        this.hasSpawn = Objects.requireNonNull(hasSpawn, "hasSpawn");
        this.spawnAvailable = Objects.requireNonNull(spawnAvailable, "spawnAvailable");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to use spawn.</red>");
            return true;
        }
        if (args.length != 0) {
            sendInvalidUsage(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }
        if (!hasSpawn.getAsBoolean()) {
            messageService.send(player, "<red>The lobby spawn is not configured.</red>");
            return true;
        }
        if (!spawnAvailable.getAsBoolean()) {
            messageService.send(player, "<red>The lobby spawn is currently unavailable.</red>");
            return true;
        }
        if (!teleport.test(player)) {
            messageService.send(player, "<red>The spawn teleport failed or was cancelled.</red>");
            return true;
        }

        messageService.send(player, "<green>Teleported to the lobby spawn.</green>");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        return List.of();
    }

    private void sendInvalidUsage(CommandSender sender) {
        messageService.send(sender, net.kyori.adventure.text.Component.text(
                        "Invalid usage.", net.kyori.adventure.text.format.NamedTextColor.RED)
                .append(net.kyori.adventure.text.Component.newline())
                .append(net.kyori.adventure.text.Component.text(
                        "Use: ", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .append(net.kyori.adventure.text.Component.text(
                        "/spawn", net.kyori.adventure.text.format.NamedTextColor.AQUA)));
    }
}

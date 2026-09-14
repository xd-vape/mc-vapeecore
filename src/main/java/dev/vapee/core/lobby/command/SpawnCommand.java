package dev.vapee.core.lobby.command;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class SpawnCommand implements CommandExecutor {

    private final LobbyService lobbyService;
    private final MessageService messageService;

    public SpawnCommand(LobbyService lobbyService, MessageService messageService) {
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length != 0) {
            messageService.send(sender, "<yellow>Usage:</yellow> <white>/spawn</white>");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }
        if (!lobbyService.hasSpawn()) {
            messageService.send(player, "<red>The lobby spawn is not configured.</red>");
            return true;
        }
        if (!lobbyService.teleportToSpawn(player)) {
            messageService.send(player, "<red>The lobby spawn is currently unavailable.</red>");
            return true;
        }

        messageService.send(player, "<green>Teleported to the lobby spawn.</green>");
        return true;
    }
}

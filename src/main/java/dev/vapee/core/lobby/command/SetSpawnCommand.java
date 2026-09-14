package dev.vapee.core.lobby.command;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class SetSpawnCommand implements CommandExecutor {

    private final LobbyService lobbyService;
    private final MessageService messageService;

    public SetSpawnCommand(LobbyService lobbyService, MessageService messageService) {
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
            messageService.send(sender, "<yellow>Usage:</yellow> <white>/setspawn</white>");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        try {
            lobbyService.setSpawn(player.getLocation());
        } catch (RuntimeException exception) {
            messageService.send(player, "<red>The lobby spawn could not be saved.</red>");
            throw exception;
        }

        messageService.send(player, "<green>Lobby spawn has been set.</green>");
        return true;
    }
}

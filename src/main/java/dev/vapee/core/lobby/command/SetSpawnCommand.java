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

public final class SetSpawnCommand implements TabExecutor {

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
            sendInvalidUsage(sender);
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
                        "/setspawn", net.kyori.adventure.text.format.NamedTextColor.AQUA)));
    }
}

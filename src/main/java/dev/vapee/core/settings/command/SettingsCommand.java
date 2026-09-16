package dev.vapee.core.settings.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.settings.SettingsMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class SettingsCommand implements CommandExecutor {

    private final SettingsMenu settingsMenu;
    private final MessageService messageService;

    public SettingsCommand(SettingsMenu settingsMenu, MessageService messageService) {
        this.settingsMenu = Objects.requireNonNull(settingsMenu, "settingsMenu");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "<red>Only players can open player settings.</red>");
            return true;
        }
        if (args.length != 0) {
            messageService.send(player, "<yellow>Usage:</yellow> <white>/settings</white>");
            return true;
        }

        settingsMenu.open(player);
        return true;
    }
}

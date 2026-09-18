package dev.vapee.core.settings.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.settings.SettingsMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class SettingsCommand implements TabExecutor {

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
            sendInvalidUsage(player);
            return true;
        }

        settingsMenu.open(player);
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
                        "/settings", net.kyori.adventure.text.format.NamedTextColor.AQUA)));
    }
}

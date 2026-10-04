package dev.vapee.core.quest.daily.command;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class DailyQuestCommand implements CommandExecutor, TabCompleter {
    private final Consumer<Player> open;
    private final MessageService messages;
    public DailyQuestCommand(DailyQuestMenu menu, MessageService messages) {
        this(player -> menu.open(player, 0), messages);
        Objects.requireNonNull(menu, "menu");
    }
    DailyQuestCommand(Consumer<Player> open, MessageService messages) {
        this.open = Objects.requireNonNull(open, "open");
        this.messages = Objects.requireNonNull(messages, "messages");
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(DailyQuestMenu.PERMISSION)) {
            messages.send(sender, "<red>You do not have permission to view quests.</red>");
        } else if (!(sender instanceof Player player)) {
            messages.send(sender, "<red>Only players can use this command.</red>");
        } else if (args.length != 0) {
            messages.send(sender, "<red>Invalid usage.</red>\n<yellow>Use:</yellow> <aqua>/quests</aqua>");
        } else open.accept(player);
        return true;
    }
    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                        @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}

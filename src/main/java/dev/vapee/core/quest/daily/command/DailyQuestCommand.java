package dev.vapee.core.quest.daily.command;

import static dev.vapee.core.i18n.Translations.tr;

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
    private final java.util.function.BooleanSupplier authorized;
    public DailyQuestCommand(DailyQuestMenu menu, MessageService messages) {
        this(player -> menu.open(player, 0), messages, menu::isAvailable);
        Objects.requireNonNull(menu, "menu");
    }
    DailyQuestCommand(Consumer<Player> open, MessageService messages) {
        this(open, messages, () -> true);
    }
    private DailyQuestCommand(Consumer<Player> open, MessageService messages,
                              java.util.function.BooleanSupplier authorized) {
        this.open = Objects.requireNonNull(open, "open");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!authorized.getAsBoolean()) return true;
        if (!sender.hasPermission(DailyQuestMenu.PERMISSION)) {
            messages.send(sender, tr("daily-quests.denied").color(net.kyori.adventure.text.format.NamedTextColor.RED));
        } else if (!(sender instanceof Player player)) {
            messages.send(sender, tr("daily-quests.command-player-only"));
        } else if (args.length != 0) {
            messages.send(sender, tr("daily-quests.command-usage"));
        } else open.accept(player);
        return true;
    }
    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                        @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}

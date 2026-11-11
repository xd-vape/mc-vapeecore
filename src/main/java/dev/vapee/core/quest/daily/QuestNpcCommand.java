package dev.vapee.core.quest.daily;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.quest.daily.command.CoreQuestCommand;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class QuestNpcCommand implements TabExecutor {
    private static final List<String> ACTIONS = List.of("set", "skin", "name", "remove");
    private final Supplier<QuestNpc> runtime;
    private final MessageService messages;
    private final Logger logger;

    QuestNpcCommand(Supplier<QuestNpc> runtime, MessageService messages, Logger logger) {
        this.runtime = runtime;
        this.messages = messages;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CoreQuestCommand.PERMISSION)) {
            messages.send(sender, messages.commandDenied(sender, "core.quest.denied"));
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            usage(sender, "", "<set|skin|name|remove>");
            return true;
        }
        String argument =
                switch (action) {
                    case "skin" -> "<Minecraft-Name>";
                    case "name" -> "<name>";
                    default -> "";
                };
        int length = argument.isEmpty() ? 1 : 2;
        if (args.length < length || (!action.equals("name") && args.length != length)) {
            usage(sender, " " + action, argument);
            return true;
        }
        if (action.equals("set") && !(sender instanceof Player)) {
            messages.send(sender, tr("core.quest.npc.player-only"));
            return true;
        }
        try {
            QuestNpc npc = runtime.get();
            var current = npc.state();
            var next =
                    switch (action) {
                        case "set" ->
                                new QuestNpcConfig.State(
                                        current.name(),
                                        current.skin(),
                                        new QuestNpcConfig.Position(
                                                ((Player) sender).getLocation()),
                                        current.hologram());
                        case "skin" ->
                                new QuestNpcConfig.State(
                                        current.name(),
                                        args[1],
                                        current.position(),
                                        current.hologram());
                        case "name" ->
                                new QuestNpcConfig.State(
                                        String.join(" ", Arrays.copyOfRange(args, 1, args.length)),
                                        current.skin(),
                                        current.position(),
                                        current.hologram());
                        case "remove" ->
                                new QuestNpcConfig.State(
                                        current.name(), current.skin(), null, current.hologram());
                        default -> throw new IllegalStateException("Unknown quest NPC action");
                    };
            npc.save(next);
            messages.send(sender, tr("core.quest.npc." + action + "-success"));
        } catch (IllegalArgumentException failure) {
            messages.send(sender, tr("core.quest.npc.invalid"));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Could not update the quest NPC", failure);
            messages.send(sender, tr("core.quest.npc.failed"));
        }
        return true;
    }

    private void usage(CommandSender sender, String suffix, String arguments) {
        messages.send(
                sender,
                messages.usage(
                        sender, "core.quest.npc.usage", "/core quest npc" + suffix, arguments));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(CoreQuestCommand.PERMISSION) || args.length != 1)
            return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return ACTIONS.stream().filter(action -> action.startsWith(prefix)).toList();
    }
}

package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.moderation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.util.List;

public final class HistoryCommand extends AbstractModerationCommand {
    public static final int PAGE_SIZE = 5;
    public HistoryCommand(ModerationCommandContext context) { super(context, "history", "<player|uuid> [page]"); }

    @Override protected boolean validArguments(CommandSender sender, String[] args) {
        if (args.length == 0 || args.length > 2) {
            usage(sender, args.length == 0 ? "Missing player." : "Too many arguments."); return false;
        }
        try { page(args); return true; }
        catch (IllegalArgumentException exception) {
            usage(sender, "Page must be a positive whole number."); return false;
        }
    }

    private int page(String[] args) {
        if (args.length == 1) return 1;
        if (!args[1].matches("[0-9]+")) throw new IllegalArgumentException();
        int page = Integer.parseInt(args[1]);
        if (page < 1) throw new IllegalArgumentException();
        return page;
    }

    @Override protected void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        List<ModerationRecord> history = context.service().getHistory(target.uniqueId());
        if (history.isEmpty()) { context.send(sender, "No moderation history for " + target.name() + "."); return; }
        int pages = (history.size() - 1) / PAGE_SIZE + 1;
        int page = page(args);
        if (page > pages) { context.send(sender, "Page does not exist. Available pages: 1–" + pages + "."); return; }
        context.send(sender, "Moderation history for " + target.name() + " • Page " + page + "/" + pages);
        int offset = (page - 1) * PAGE_SIZE;
        Instant now = context.clock().instant();
        for (ModerationRecord record : history.subList(offset, offset + Math.min(history.size() - offset, PAGE_SIZE))) {
            context.messages().send(sender, ModerationComponents.historyEntry(record, now, context::actorName));
        }
        Component navigation = Component.empty();
        if (page > 1) navigation = navigation.append(navigation("Previous", target, page - 1));
        if (page < pages) navigation = navigation.append(Component.space()).append(navigation("Next", target, page + 1));
        if (pages > 1) context.messages().send(sender, navigation);
    }

    private Component navigation(String text, PlayerIdentity target, int page) {
        return Component.text(text, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/history " + target.uniqueId() + " " + page));
    }
}

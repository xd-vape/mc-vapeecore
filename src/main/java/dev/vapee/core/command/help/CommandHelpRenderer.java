package dev.vapee.core.command.help;

import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CommandHelpRenderer {

    private static final Component NEWLINE = Component.newline();
    private static final Component SEPARATOR = Component.text(
            "──────────────────────────",
            NamedTextColor.DARK_GRAY
    );

    private final MessageService messageService;

    public CommandHelpRenderer(MessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    public void send(CommandSender sender, CommandHelpPage page) {
        messageService.send(
                Objects.requireNonNull(sender, "sender"),
                render(sender, Objects.requireNonNull(page, "page"))
        );
    }

    public Component render(CommandSender sender, CommandHelpPage page) {
        CommandSender validatedSender = Objects.requireNonNull(sender, "sender");
        CommandHelpPage validatedPage = Objects.requireNonNull(page, "page");
        List<VisibleSection> visibleSections = visibleSections(validatedSender, validatedPage);

        Component result = Component.empty()
                .append(Component.text("VapeeCore", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(validatedPage.title(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(NEWLINE)
                .append(SEPARATOR);

        if (validatedPage.description() != null) {
            result = result.append(NEWLINE)
                    .append(Component.text(validatedPage.description(), NamedTextColor.GRAY));
        }

        for (VisibleSection section : visibleSections) {
            result = result.append(NEWLINE)
                    .append(NEWLINE)
                    .append(Component.text(section.title(), NamedTextColor.YELLOW, TextDecoration.BOLD));
            for (CommandHelpEntry entry : section.entries()) {
                result = result.append(NEWLINE)
                        .append(syntax(entry.syntax()))
                        .append(NEWLINE)
                        .append(Component.text("  " + entry.description(), NamedTextColor.GRAY));
                if (entry.hint() != null) {
                    result = result.append(NEWLINE)
                            .append(Component.text("  " + entry.hint(), NamedTextColor.YELLOW));
                }
            }
        }

        if (validatedPage.footer() != null) {
            result = result.append(NEWLINE)
                    .append(NEWLINE)
                    .append(Component.text("Tip: ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(validatedPage.footer(), NamedTextColor.GRAY));
        }
        return result;
    }

    private Component syntax(String syntax) {
        return Component.text(syntax, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand(syntax))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Click to insert this command.",
                        NamedTextColor.GRAY
                )));
    }

    private List<VisibleSection> visibleSections(CommandSender sender, CommandHelpPage page) {
        List<VisibleSection> sections = new ArrayList<>();
        for (CommandHelpSection section : page.sections()) {
            List<CommandHelpEntry> entries = section.entries().stream()
                    .filter(entry -> entry.permission() == null || sender.hasPermission(entry.permission()))
                    .toList();
            if (!entries.isEmpty()) {
                sections.add(new VisibleSection(section.title(), entries));
            }
        }
        return List.copyOf(sections);
    }

    private record VisibleSection(String title, List<CommandHelpEntry> entries) {

        private VisibleSection {
            title = Objects.requireNonNull(title, "title");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }
}

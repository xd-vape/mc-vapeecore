package dev.vapee.core.activity.blackjack.command;

import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.blackjack.table.BlackjackBlockPosition;
import dev.vapee.core.activity.blackjack.table.BlackjackDisplayAnchor;
import dev.vapee.core.activity.blackjack.table.BlackjackTableConfig;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDraft;
import dev.vapee.core.activity.blackjack.table.BlackjackTableOperationResult;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.seat.SeatPositionResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

public final class BlackjackCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.blackjack.admin";
    private static final List<String> ACTIONS = List.of(
            "create", "delete", "pos1", "pos2", "dealer", "interaction",
            "display", "seat", "removeseat", "enable", "disable", "info", "list"
    );
    private static final List<String> SETUP_ACTIONS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of("help"),
            ACTIONS.stream()
    ).toList();
    private static final CommandHelpPage MAIN_HELP_PAGE = new CommandHelpPage(
            "Blackjack Administration",
            null,
            List.of(new CommandHelpSection("Table Setup", List.of(
                    new CommandHelpEntry("/blackjack setup", "Shows physical table setup commands."),
                    new CommandHelpEntry("/blackjack setup list", "Lists all configured tables.")
            ))),
            "Use /blackjack setup help for the complete setup workflow."
    );
    private static final CommandHelpPage SETUP_HELP_PAGE = new CommandHelpPage(
            "Blackjack Setup",
            null,
            List.of(
                    new CommandHelpSection("Create & Configure", List.of(
                            new CommandHelpEntry("/blackjack setup create <id>",
                                    "Creates a disabled blackjack table draft."),
                            new CommandHelpEntry("/blackjack setup pos1 <id>",
                                    "Sets the first corner of the table area."),
                            new CommandHelpEntry("/blackjack setup pos2 <id>",
                                    "Sets the second corner of the table area."),
                            new CommandHelpEntry("/blackjack setup dealer <id>",
                                    "Sets the dealer position."),
                            new CommandHelpEntry("/blackjack setup display <id>",
                                    "Sets the visual center and surface height used for cards and table UI."),
                            new CommandHelpEntry("/blackjack setup interaction <id>",
                                    "Sets the block players right-click to join."),
                            new CommandHelpEntry("/blackjack setup seat <id> <1-5>",
                                    "Sets or updates a table seat."),
                            new CommandHelpEntry("/blackjack setup removeseat <id> <1-5>",
                                    "Removes a configured seat.")
                    )),
                    new CommandHelpSection("Management", List.of(
                            new CommandHelpEntry("/blackjack setup enable <id>",
                                    "Validates and enables the table."),
                            new CommandHelpEntry("/blackjack setup disable <id>",
                                    "Disables an unused table."),
                            new CommandHelpEntry("/blackjack setup delete <id>",
                                    "Deletes a disabled table.")
                    )),
                    new CommandHelpSection("Information", List.of(
                            new CommandHelpEntry("/blackjack setup info <id>",
                                    "Shows configuration and runtime state."),
                            new CommandHelpEntry("/blackjack setup list", "Lists configured tables.")
                    ))
            ),
            "Use /blackjack setup info <id> to check setup progress."
    );

    private final JavaPlugin plugin;
    private final BlackjackTableConfig tableConfig;
    private final BlackjackTableService tableService;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;
    private final SeatPositionResolver seatPositionResolver;

    public BlackjackCommand(
            JavaPlugin plugin,
            BlackjackTableConfig tableConfig,
            BlackjackTableService tableService,
            MessageService messageService,
            CommandHelpRenderer helpRenderer,
            SeatPositionResolver seatPositionResolver
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tableConfig = Objects.requireNonNull(tableConfig, "tableConfig");
        this.tableService = Objects.requireNonNull(tableService, "tableService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
        this.seatPositionResolver = Objects.requireNonNull(seatPositionResolver, "seatPositionResolver");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, "<red>You do not have permission to configure blackjack tables.</red>");
            return true;
        }
        if (args.length == 0) {
            helpRenderer.send(sender, MAIN_HELP_PAGE);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            if (args.length == 1) {
                helpRenderer.send(sender, MAIN_HELP_PAGE);
            } else {
                sendInvalidUsage(sender, "/blackjack help");
            }
            return true;
        }
        if (!args[0].equalsIgnoreCase("setup")) {
            sendUnknown(sender, args[0], "/blackjack help");
            return true;
        }
        if (args.length == 1) {
            helpRenderer.send(sender, SETUP_HELP_PAGE);
            return true;
        }
        if (args[1].equalsIgnoreCase("help")) {
            if (args.length == 2) {
                helpRenderer.send(sender, SETUP_HELP_PAGE);
            } else {
                sendInvalidUsage(sender, "/blackjack setup help");
            }
            return true;
        }

        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "pos1" -> setPosition(sender, args, PositionType.POS1);
                case "pos2" -> setPosition(sender, args, PositionType.POS2);
                case "dealer" -> setPosition(sender, args, PositionType.DEALER);
                case "display" -> displayAnchor(sender, args);
                case "interaction" -> interaction(sender, args);
                case "seat" -> seat(sender, args);
                case "removeseat" -> removeSeat(sender, args);
                case "enable" -> enable(sender, args);
                case "disable" -> disable(sender, args);
                case "info" -> info(sender, args);
                case "list" -> list(sender, args);
                default -> sendUnknown(sender, args[1], "/blackjack setup help");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not update blackjack table configuration.", exception);
            messageService.send(sender,
                    "<red>The blackjack table configuration could not be saved. Check the server log.</red>"
            );
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return rootSuggestions(args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return setupSuggestions(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup")
                && !args[1].equalsIgnoreCase("help")
                && !args[1].equalsIgnoreCase("create")
                && !args[1].equalsIgnoreCase("list")) {
            return matches(tableConfig.getDrafts().stream().map(BlackjackTableDraft::getId).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setup")
                && (args[1].equalsIgnoreCase("seat") || args[1].equalsIgnoreCase("removeseat"))) {
            return matches(List.of("1", "2", "3", "4", "5"), args[3]);
        }
        return List.of();
    }

    static List<String> rootSuggestions(String input) {
        return matches(List.of("help", "setup"), input);
    }

    static List<String> setupSuggestions(String input) {
        return matches(SETUP_ACTIONS, input);
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/blackjack setup create <id>");
            return;
        }
        if (!BlackjackTableDraft.isValidId(args[2])) {
            messageService.send(sender, "<red>Table IDs must match [a-z0-9_-]+.</red>");
            return;
        }
        if (!tableConfig.createDraft(args[2])) {
            messageService.send(sender, Component.text("Blackjack table '", NamedTextColor.RED)
                    .append(Component.text(args[2], NamedTextColor.WHITE))
                    .append(Component.text("' already exists.", NamedTextColor.RED)));
            return;
        }
        messageService.send(sender, createWorkflow(args[2]));
    }

    private void delete(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        if (draft.isEnabled() || tableService.isRuntimeActive(draft.getId())) {
            messageService.send(sender, "<red>Disable the table before deleting it.</red>");
            return;
        }
        tableConfig.deleteDraft(draft.getId());
        messageService.send(sender, success("Blackjack table '", draft.getId(), "' deleted."));
    }

    private void setPosition(CommandSender sender, String[] args, PositionType type) {
        BlackjackTableDraft draft = requireEditablePlayerDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        Player player = (Player) sender;
        ActivityPosition position = position(player.getLocation(), type == PositionType.DEALER);
        switch (type) {
            case POS1 -> draft.setPos1(position);
            case POS2 -> draft.setPos2(position);
            case DEALER -> draft.setDealer(position);
        }
        tableConfig.saveDraft(draft);
        messageService.send(sender, success("Blackjack table '", draft.getId(),
                "' " + type.label + " updated."));
    }

    private void interaction(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireEditablePlayerDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        Player player = (Player) sender;
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            messageService.send(sender, "<red>Look at a block within six blocks.</red>");
            return;
        }
        draft.setInteraction(BlackjackBlockPosition.fromBlock(block));
        tableConfig.saveDraft(draft);
        messageService.send(sender, success("Blackjack table '", draft.getId(),
                "' interaction block updated."));
    }

    private void displayAnchor(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireEditablePlayerDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        Player player = (Player) sender;
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            messageService.send(sender, "<red>Look at a table block within six blocks.</red>");
            return;
        }
        double surfaceY = surfaceY(block);
        BlackjackDisplayAnchor anchor = new BlackjackDisplayAnchor(
                block.getWorld().getName(),
                block.getX() + 0.5D,
                surfaceY,
                block.getZ() + 0.5D,
                player.getYaw()
        );
        draft.setDisplayAnchor(anchor);
        tableConfig.saveDraft(draft);
        Component output = Component.text("Blackjack display anchor updated.", NamedTextColor.GREEN)
                .append(Component.newline())
                .append(infoLine("Block: ", anchor.worldName() + " @ "
                        + block.getX() + ", " + block.getY() + ", " + block.getZ(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(infoLine("Surface: ", Double.toString(surfaceY), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Use /blackjack setup info " + draft.getId(), NamedTextColor.AQUA));
        messageService.send(sender, output);
    }

    static double surfaceY(Block block) {
        Block validated = Objects.requireNonNull(block, "block");
        double collisionTop = validated.getCollisionShape().getBoundingBoxes().stream()
                .mapToDouble(box -> box.getMaxY())
                .max()
                .orElse(Double.NaN);
        if (Double.isFinite(collisionTop)) {
            return collisionTop >= -0.0001D && collisionTop <= 1.0001D
                    ? validated.getY() + collisionTop
                    : collisionTop;
        }
        double boundingTop = validated.getBoundingBox().getMaxY();
        return Double.isFinite(boundingTop) && boundingTop > validated.getY()
                ? boundingTop
                : validated.getY() + 1.0D;
    }

    private void seat(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireEditablePlayerDraft(sender, args, 4);
        if (draft == null) {
            return;
        }
        Integer number = seatNumber(sender, args[3]);
        if (number == null) {
            return;
        }
        Player player = (Player) sender;
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            messageService.send(sender, "<red>Look at a supported seat block within six blocks.</red>");
            return;
        }
        Location resolved = seatPositionResolver.resolve(block, player.getYaw()).orElse(null);
        if (resolved == null) {
            messageService.send(sender,
                    "<red>Use a bottom stair or a single bottom/top slab. Top stairs and double slabs are unsupported.</red>");
            return;
        }
        draft.setSeat(number, position(resolved, true), BlackjackBlockPosition.fromBlock(block));
        tableConfig.saveDraft(draft);
        messageService.send(sender, success("Blackjack table '", draft.getId(),
                "' seat " + number + " block and sit position updated."));
    }

    private void removeSeat(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireDraft(sender, args, 4);
        if (draft == null || !ensureEditable(sender, draft)) {
            return;
        }
        Integer number = seatNumber(sender, args[3]);
        if (number == null) {
            return;
        }
        if (!draft.removeSeat(number)) {
            messageService.send(sender, "<red>That seat is not configured.</red>");
            return;
        }
        tableConfig.saveDraft(draft);
        messageService.send(sender, success("Blackjack table '", draft.getId(),
                "' seat " + number + " removed."));
    }

    private void enable(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/blackjack setup enable <id>");
            return;
        }
        BlackjackTableOperationResult result = tableService.enableTable(args[2]);
        if (result.isSuccess()) {
            messageService.send(sender, success("Blackjack table '", args[2], "' enabled."));
            return;
        }
        sendOperationFailure(sender, args[2], result);
    }

    private void disable(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/blackjack setup disable <id>");
            return;
        }
        BlackjackTableOperationResult result = tableService.disableTable(args[2]);
        if (result.isSuccess()) {
            messageService.send(sender, success("Blackjack table '", args[2], "' disabled."));
            return;
        }
        sendOperationFailure(sender, args[2], result);
    }

    private void info(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        Optional<BlackjackSession> session = tableService.getSession(draft.getId());
        String sessionStatus = session
                .map(value -> display(value.getState().name()) + " • "
                        + value.getParticipantCount() + " participant(s)")
                .orElse("None");
        Component output = Component.text("Blackjack Table • ", NamedTextColor.GOLD)
                .append(Component.text(draft.getId(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Status", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(infoLine("Enabled: ", draft.isEnabled() ? "Enabled" : "Disabled",
                        draft.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.newline())
                .append(infoLine("Runtime: ", tableService.isRuntimeActive(draft.getId()) ? "Active" : "Inactive",
                        tableService.isRuntimeActive(draft.getId()) ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.newline())
                .append(infoLine("Session: ", sessionStatus, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Setup", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(infoLine("World: ", configuredWorld(draft), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(configurationLine("Area Pos 1: ", draft.getPos1().isPresent()))
                .append(Component.newline())
                .append(configurationLine("Area Pos 2: ", draft.getPos2().isPresent()))
                .append(Component.newline())
                .append(configurationLine("Dealer: ", draft.getDealer().isPresent()))
                .append(Component.newline())
                .append(infoLine("Display Anchor: ", draft.getDisplayAnchor().isPresent()
                                ? "Configured" : "Missing - using legacy geometry",
                        draft.getDisplayAnchor().isPresent() ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(configurationLine("Interaction: ", draft.getInteraction().isPresent()))
                .append(Component.newline())
                .append(infoLine("Join Mode: ", joinMode(draft), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(infoLine("Seats: ", draft.getSeats().size() + " / 5",
                        draft.getSeats().isEmpty() ? NamedTextColor.RED : NamedTextColor.WHITE));

        SetupStep nextStep = nextSetupStep(draft);
        if (nextStep != null) {
            output = output.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Next", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text(nextStep.description(), NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text(nextStep.syntax(), NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand(nextStep.syntax())));
        }
        messageService.send(sender, output);
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendInvalidUsage(sender, "/blackjack setup list");
            return;
        }
        List<BlackjackTableDraft> drafts = tableConfig.getDrafts();
        if (drafts.isEmpty()) {
            messageService.send(sender, "<yellow>No blackjack tables are configured.</yellow>");
            return;
        }
        Component output = Component.text("Blackjack Tables", NamedTextColor.GOLD)
                .append(Component.newline())
                .append(Component.text(drafts.size() + " table(s) configured.", NamedTextColor.GRAY));
        for (BlackjackTableDraft draft : drafts) {
            BlackjackSession session = tableService.getSession(draft.getId()).orElse(null);
            String status = draft.isEnabled() ? "Enabled" : "Disabled";
            String capacity = session == null ? "" : " " + session.getParticipantCount() + "/"
                    + tableService.getDefinition(draft.getId()).orElseThrow().capacity();
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(draft.getId(), NamedTextColor.AQUA))
                    .append(Component.text(" [" + status + "]" + capacity,
                            draft.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        messageService.send(sender, output);
    }

    private BlackjackTableDraft requireEditablePlayerDraft(CommandSender sender, String[] args, int length) {
        BlackjackTableDraft draft = requireDraft(sender, args, length);
        if (draft == null) {
            return null;
        }
        if (!(sender instanceof Player)) {
            messageService.send(sender, "<red>Only players can capture world positions.</red>");
            return null;
        }
        return ensureEditable(sender, draft) ? draft : null;
    }

    private BlackjackTableDraft requireDraft(CommandSender sender, String[] args, int length) {
        if (args.length != length) {
            sendInvalidUsage(sender, syntaxFor(args.length > 1 ? args[1] : "help"));
            return null;
        }
        BlackjackTableDraft draft = tableConfig.getDraft(args[2]).orElse(null);
        if (draft == null) {
            messageService.send(sender, Component.text("Blackjack table '", NamedTextColor.RED)
                    .append(Component.text(args[2], NamedTextColor.WHITE))
                    .append(Component.text("' does not exist.", NamedTextColor.RED)));
        }
        return draft;
    }

    private boolean ensureEditable(CommandSender sender, BlackjackTableDraft draft) {
        if (!draft.isEnabled() && !tableService.isRuntimeActive(draft.getId())) {
            return true;
        }
        messageService.send(sender, "<red>Disable the table before editing it.</red>");
        return false;
    }

    private Integer seatNumber(CommandSender sender, String input) {
        try {
            int number = Integer.parseInt(input);
            if (number >= 1 && number <= 5) {
                return number;
            }
        } catch (NumberFormatException ignored) {
        }
        messageService.send(sender, "<red>Seat number must be between 1 and 5.</red>");
        return null;
    }

    private ActivityPosition position(Location location, boolean rotation) {
        Location value = Objects.requireNonNull(location, "location");
        return new ActivityPosition(
                Objects.requireNonNull(value.getWorld(), "location world").getName(),
                value.getX(), value.getY(), value.getZ(),
                rotation ? value.getYaw() : 0.0F,
                rotation ? value.getPitch() : 0.0F
        );
    }

    private String configuredWorld(BlackjackTableDraft draft) {
        return draft.getPos1().map(ActivityPosition::worldName)
                .or(() -> draft.getPos2().map(ActivityPosition::worldName))
                .or(() -> draft.getDealer().map(ActivityPosition::worldName))
                .or(() -> draft.getDisplayAnchor().map(BlackjackDisplayAnchor::worldName))
                .or(() -> draft.getInteraction().map(BlackjackBlockPosition::worldName))
                .orElse("Not configured");
    }

    private void sendOperationFailure(
            CommandSender sender,
            String tableId,
            BlackjackTableOperationResult result
    ) {
        switch (result.status()) {
            case TABLE_NOT_FOUND -> messageService.send(sender,
                    error("Blackjack table '", tableId, "' does not exist."));
            case ALREADY_ENABLED -> messageService.send(sender, "<yellow>That blackjack table is already enabled.</yellow>");
            case NOT_ENABLED -> messageService.send(sender, "<yellow>That blackjack table is already disabled.</yellow>");
            case WORLD_NOT_LOADED -> messageService.send(sender, "<red>The table world is not loaded.</red>");
            case INTERACTION_CONFLICT -> messageService.send(sender,
                    "<red>That interaction block is already used by another table.</red>");
            case SEAT_CONFLICT -> messageService.send(sender,
                    "<red>One of those seat blocks is already used by another enabled table.</red>");
            case SEAT_BLOCK_INVALID -> messageService.send(sender,
                    "<red>A configured seat block is no longer a supported stair or slab.</red>");
            case TABLE_IN_USE -> messageService.send(sender,
                    "<red>The table cannot be disabled while occupied or running a round.</red>");
            case INVALID_DEFINITION -> {
                messageService.send(sender, invalidDefinitionMessage(tableId, result.details()));
            }
            case RUNTIME_FAILURE -> messageService.send(sender,
                    "<red>The table runtime could not be activated. Check the server log.</red>");
            case SUCCESS -> throw new IllegalArgumentException("Success is not a failure result");
        }
    }

    private static Component createWorkflow(String tableId) {
        List<String> commands = List.of(
                "/blackjack setup pos1 " + tableId,
                "/blackjack setup pos2 " + tableId,
                "/blackjack setup dealer " + tableId,
                "/blackjack setup display " + tableId,
                "/blackjack setup seat " + tableId + " 1",
                "/blackjack setup enable " + tableId
        );
        Component output = success("Blackjack table '", tableId, "' created.")
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Setup order:", NamedTextColor.YELLOW));
        for (int index = 0; index < commands.size(); index++) {
            String command = commands.get(index);
            output = output.append(Component.newline())
                    .append(Component.text((index + 1) + ". ", NamedTextColor.GRAY))
                    .append(Component.text(command, NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand(command)));
        }
        return output.append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Tip: ", NamedTextColor.YELLOW))
                .append(Component.text("Use /blackjack setup info " + tableId
                        + " to check progress.", NamedTextColor.GRAY));
    }

    private void sendUnknown(CommandSender sender, String subcommand, String helpSyntax) {
        messageService.send(sender, Component.text("Unknown subcommand '", NamedTextColor.RED)
                .append(Component.text(subcommand, NamedTextColor.WHITE))
                .append(Component.text("'.", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text(helpSyntax, NamedTextColor.AQUA)));
    }

    private void sendInvalidUsage(CommandSender sender, String syntax) {
        messageService.send(sender, Component.text("Invalid usage.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Use: ", NamedTextColor.YELLOW))
                .append(Component.text(syntax, NamedTextColor.AQUA)));
    }

    private String syntaxFor(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "create" -> "/blackjack setup create <id>";
            case "delete" -> "/blackjack setup delete <id>";
            case "pos1" -> "/blackjack setup pos1 <id>";
            case "pos2" -> "/blackjack setup pos2 <id>";
            case "dealer" -> "/blackjack setup dealer <id>";
            case "display" -> "/blackjack setup display <id>";
            case "interaction" -> "/blackjack setup interaction <id>";
            case "seat" -> "/blackjack setup seat <id> <1-5>";
            case "removeseat" -> "/blackjack setup removeseat <id> <1-5>";
            case "enable" -> "/blackjack setup enable <id>";
            case "disable" -> "/blackjack setup disable <id>";
            case "info" -> "/blackjack setup info <id>";
            case "list" -> "/blackjack setup list";
            default -> "/blackjack setup help";
        };
    }

    private static Component success(String before, String value, String after) {
        return Component.text(before, NamedTextColor.GREEN)
                .append(Component.text(value, NamedTextColor.WHITE))
                .append(Component.text(after, NamedTextColor.GREEN));
    }

    private static Component error(String before, String value, String after) {
        return Component.text(before, NamedTextColor.RED)
                .append(Component.text(value, NamedTextColor.WHITE))
                .append(Component.text(after, NamedTextColor.RED));
    }

    private Component infoLine(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }

    private Component configurationLine(String label, boolean configured) {
        return infoLine(
                label,
                configured ? "Configured" : "Missing",
                configured ? NamedTextColor.GREEN : NamedTextColor.RED
        );
    }

    private SetupStep nextSetupStep(BlackjackTableDraft draft) {
        String id = draft.getId();
        if (draft.getPos1().isEmpty()) {
            return new SetupStep("Set the first area corner with:", "/blackjack setup pos1 " + id);
        }
        if (draft.getPos2().isEmpty()) {
            return new SetupStep("Set the second area corner with:", "/blackjack setup pos2 " + id);
        }
        if (draft.getDealer().isEmpty()) {
            return new SetupStep("Set the dealer position with:", "/blackjack setup dealer " + id);
        }
        if (draft.getDisplayAnchor().isEmpty()) {
            return new SetupStep("Set the table display surface with:", "/blackjack setup display " + id);
        }
        if (draft.getSeats().isEmpty()) {
            return new SetupStep("Configure at least one seat with:", "/blackjack setup seat " + id + " 1");
        }
        boolean modern = draft.getSeatDefinitions().values().stream().allMatch(seat -> seat.isModern());
        if (!modern && draft.getInteraction().isEmpty()) {
            return new SetupStep("Set the legacy interaction block with:", "/blackjack setup interaction " + id);
        }
        if (!draft.isEnabled()) {
            return new SetupStep("Validate and enable the table with:", "/blackjack setup enable " + id);
        }
        return null;
    }

    private static Component invalidDefinitionMessage(String tableId, List<String> details) {
        Component output = error("Table '", tableId, "' is not ready.")
                .append(Component.newline())
                .append(Component.text("Missing or invalid:", NamedTextColor.YELLOW));
        for (String detail : details) {
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(friendlyDetail(detail), NamedTextColor.WHITE));
        }
        return output.append(Component.newline())
                .append(Component.text("Then: ", NamedTextColor.YELLOW))
                .append(Component.text("/blackjack setup info " + tableId, NamedTextColor.AQUA));
    }

    private static String friendlyDetail(String detail) {
        String value = Objects.requireNonNull(detail, "detail").trim();
        return switch (value) {
            case "missing area pos1" -> "Area Pos 1";
            case "missing area pos2" -> "Area Pos 2";
            case "missing dealer" -> "Dealer position";
            case "missing interaction" -> "Interaction block";
            case "missing seat", "at least one seat is required" -> "At least one seat";
            default -> value.isEmpty()
                    ? "Unknown validation issue"
                    : Character.toUpperCase(value.charAt(0)) + value.substring(1);
        };
    }

    private String display(String enumName) {
        String value = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String joinMode(BlackjackTableDraft draft) {
        if (draft.getSeatDefinitions().isEmpty()) return "Not configured";
        long modern = draft.getSeatDefinitions().values().stream().filter(seat -> seat.isModern()).count();
        if (modern == draft.getSeatDefinitions().size()) return "Modern seat click";
        if (modern == 0) return "Legacy interaction";
        return "Invalid mixed schema";
    }

    private static List<String> matches(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private record SetupStep(String description, String syntax) {

        private SetupStep {
            description = Objects.requireNonNull(description, "description");
            syntax = Objects.requireNonNull(syntax, "syntax");
        }
    }

    private enum PositionType {
        POS1("area pos1"),
        POS2("area pos2"),
        DEALER("dealer position");

        private final String label;

        PositionType(String label) {
            this.label = label;
        }
    }
}

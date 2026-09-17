package dev.vapee.core.activity.blackjack.command;

import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.blackjack.table.BlackjackBlockPosition;
import dev.vapee.core.activity.blackjack.table.BlackjackTableConfig;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDraft;
import dev.vapee.core.activity.blackjack.table.BlackjackTableOperationResult;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.message.MessageService;
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
            "seat", "removeseat", "enable", "disable", "info", "list"
    );

    private final JavaPlugin plugin;
    private final BlackjackTableConfig tableConfig;
    private final BlackjackTableService tableService;
    private final MessageService messageService;

    public BlackjackCommand(
            JavaPlugin plugin,
            BlackjackTableConfig tableConfig,
            BlackjackTableService tableService,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tableConfig = Objects.requireNonNull(tableConfig, "tableConfig");
        this.tableService = Objects.requireNonNull(tableService, "tableService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
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
        if (args.length < 2 || !args[0].equalsIgnoreCase("setup")) {
            sendUsage(sender);
            return true;
        }

        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "pos1" -> setPosition(sender, args, PositionType.POS1);
                case "pos2" -> setPosition(sender, args, PositionType.POS2);
                case "dealer" -> setPosition(sender, args, PositionType.DEALER);
                case "interaction" -> interaction(sender, args);
                case "seat" -> seat(sender, args);
                case "removeseat" -> removeSeat(sender, args);
                case "enable" -> enable(sender, args);
                case "disable" -> disable(sender, args);
                case "info" -> info(sender, args);
                case "list" -> list(sender, args);
                default -> sendUsage(sender);
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
            return matches(List.of("setup"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return matches(ACTIONS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup")
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

    private void create(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return;
        }
        if (!BlackjackTableDraft.isValidId(args[2])) {
            messageService.send(sender, "<red>Table IDs must match [a-z0-9_-]+.</red>");
            return;
        }
        if (!tableConfig.createDraft(args[2])) {
            messageService.send(sender, "<red>A blackjack table with that ID already exists.</red>");
            return;
        }
        messageService.send(sender, "<green>Created disabled blackjack table draft <white>"
                + args[2] + "</white>.</green>");
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
        messageService.send(sender, "<green>Blackjack table draft deleted.</green>");
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
        messageService.send(sender, "<green>Blackjack table " + type.label + " updated.</green>");
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
        messageService.send(sender, "<green>Blackjack interaction block updated.</green>");
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
        draft.setSeat(number, position(((Player) sender).getLocation(), true));
        tableConfig.saveDraft(draft);
        messageService.send(sender, "<green>Blackjack seat " + number + " updated.</green>");
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
        messageService.send(sender, "<green>Blackjack seat " + number + " removed.</green>");
    }

    private void enable(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return;
        }
        BlackjackTableOperationResult result = tableService.enableTable(args[2]);
        if (result.isSuccess()) {
            messageService.send(sender, "<green>Blackjack table enabled.</green>");
            return;
        }
        sendOperationFailure(sender, result);
    }

    private void disable(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return;
        }
        BlackjackTableOperationResult result = tableService.disableTable(args[2]);
        if (result.isSuccess()) {
            messageService.send(sender, "<green>Blackjack table disabled.</green>");
            return;
        }
        sendOperationFailure(sender, result);
    }

    private void info(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        Optional<BlackjackSession> session = tableService.getSession(draft.getId());
        messageService.send(sender, "<gold>Blackjack table " + draft.getId() + "</gold>");
        messageService.send(sender, "<gray>Enabled:</gray> <white>" + draft.isEnabled() + "</white>");
        messageService.send(sender, "<gray>Runtime active:</gray> <white>"
                + tableService.isRuntimeActive(draft.getId()) + "</white>");
        messageService.send(sender, "<gray>World:</gray> <white>" + configuredWorld(draft) + "</white>");
        messageService.send(sender, "<gray>Area pos1/pos2:</gray> <white>"
                + draft.getPos1().isPresent() + "/" + draft.getPos2().isPresent() + "</white>");
        messageService.send(sender, "<gray>Dealer:</gray> <white>" + draft.getDealer().isPresent() + "</white>");
        messageService.send(sender, "<gray>Interaction:</gray> <white>"
                + draft.getInteraction().isPresent() + "</white>");
        messageService.send(sender, "<gray>Seats:</gray> <white>" + draft.getSeats().size() + "</white>");
        messageService.send(sender, "<gray>Session:</gray> <white>" + session
                .map(value -> value.getState() + ", " + value.getParticipantCount() + " participant(s)")
                .orElse("none") + "</white>");
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return;
        }
        List<BlackjackTableDraft> drafts = tableConfig.getDrafts();
        if (drafts.isEmpty()) {
            messageService.send(sender, "<yellow>No blackjack tables are configured.</yellow>");
            return;
        }
        messageService.send(sender, "<gold>Blackjack tables:</gold>");
        for (BlackjackTableDraft draft : drafts) {
            BlackjackSession session = tableService.getSession(draft.getId()).orElse(null);
            String status = draft.isEnabled() ? "ENABLED" : "DISABLED";
            String capacity = session == null ? "" : " " + session.getParticipantCount() + "/"
                    + tableService.getDefinition(draft.getId()).orElseThrow().capacity();
            messageService.send(sender, "<gray>-</gray> <white>" + draft.getId() + "</white> <yellow>["
                    + status + "]</yellow><white>" + capacity + "</white>");
        }
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
            sendUsage(sender);
            return null;
        }
        BlackjackTableDraft draft = tableConfig.getDraft(args[2]).orElse(null);
        if (draft == null) {
            messageService.send(sender, "<red>That blackjack table does not exist.</red>");
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
                .or(() -> draft.getInteraction().map(BlackjackBlockPosition::worldName))
                .orElse("-");
    }

    private void sendOperationFailure(CommandSender sender, BlackjackTableOperationResult result) {
        switch (result.status()) {
            case TABLE_NOT_FOUND -> messageService.send(sender, "<red>That blackjack table does not exist.</red>");
            case ALREADY_ENABLED -> messageService.send(sender, "<yellow>That blackjack table is already enabled.</yellow>");
            case NOT_ENABLED -> messageService.send(sender, "<yellow>That blackjack table is already disabled.</yellow>");
            case WORLD_NOT_LOADED -> messageService.send(sender, "<red>The table world is not loaded.</red>");
            case INTERACTION_CONFLICT -> messageService.send(sender,
                    "<red>That interaction block is already used by another table.</red>");
            case TABLE_IN_USE -> messageService.send(sender,
                    "<red>The table cannot be disabled while occupied or running a round.</red>");
            case INVALID_DEFINITION -> {
                messageService.send(sender, "<red>The table is incomplete or invalid. Missing/invalid:</red>");
                for (String detail : result.details()) {
                    messageService.send(sender, "<gray>-</gray> <white>" + detail + "</white>");
                }
            }
            case RUNTIME_FAILURE -> messageService.send(sender,
                    "<red>The table runtime could not be activated. Check the server log.</red>");
            case SUCCESS -> messageService.send(sender, "<green>Done.</green>");
        }
    }

    private void sendUsage(CommandSender sender) {
        messageService.send(sender,
                "<yellow>Usage:</yellow> <white>/blackjack setup "
                        + "\\<create|delete|pos1|pos2|dealer|interaction|seat|removeseat|enable|disable|info|list> ...</white>"
        );
    }

    private List<String> matches(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .toList();
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

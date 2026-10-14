package dev.vapee.core.activity.blackjack.command;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewService;
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
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

public final class BlackjackCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.blackjack.admin";
    private static final List<String> ACTIONS = List.of(
            "create", "delete", "pos1", "pos2", "dealer", "interaction",
            "display", "seat", "removeseat", "preview", "enable", "disable", "info", "list"
    );
    private static final List<String> SETUP_ACTIONS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of("help"),
            ACTIONS.stream()
    ).toList();
    private static final CommandHelpPage MAIN_HELP_PAGE = new CommandHelpPage(
            tr("blackjack.help.title"),
            null,
            List.of(new CommandHelpSection(tr("blackjack.help.section.table-setup"), List.of(
                    new CommandHelpEntry("/blackjack setup", tr("blackjack.help.blackjack.setup.description")),
                    new CommandHelpEntry("/blackjack setup list", tr("blackjack.help.blackjack.setup.list.description"))
            ))),
            tr("blackjack.help.footer")
    );
    private static final CommandHelpPage SETUP_HELP_PAGE = new CommandHelpPage(
            tr("blackjack.help.title-setup"),
            null,
            List.of(
                    new CommandHelpSection(tr("blackjack.help.section.create-configure"), List.of(
                            new CommandHelpEntry("/blackjack setup create <id>",
                                    tr("blackjack.help.blackjack.setup.create.description")),
                            new CommandHelpEntry("/blackjack setup pos1 <id>",
                                    tr("blackjack.help.blackjack.setup.pos1.description")),
                            new CommandHelpEntry("/blackjack setup pos2 <id>",
                                    tr("blackjack.help.blackjack.setup.pos2.description")),
                            new CommandHelpEntry("/blackjack setup dealer <id>",
                                    tr("blackjack.help.blackjack.setup.dealer.description")),
                            new CommandHelpEntry("/blackjack setup display <id>",
                                    tr("blackjack.help.blackjack.setup.display.description")),
                            new CommandHelpEntry("/blackjack setup interaction <id>",
                                    tr("blackjack.help.blackjack.setup.interaction.description")),
                            new CommandHelpEntry("/blackjack setup seat <id> <1-5>",
                                    tr("blackjack.help.blackjack.setup.seat.description")),
                            new CommandHelpEntry("/blackjack setup removeseat <id> <1-5>",
                                    tr("blackjack.help.blackjack.setup.removeseat.description")),
                            new CommandHelpEntry("/blackjack setup preview <id>",
                                    tr("blackjack.help.blackjack.setup.preview.description"))
                    )),
                    new CommandHelpSection(tr("blackjack.help.section.management"), List.of(
                            new CommandHelpEntry("/blackjack setup enable <id>",
                                    tr("blackjack.help.blackjack.setup.enable.description")),
                            new CommandHelpEntry("/blackjack setup disable <id>",
                                    tr("blackjack.help.blackjack.setup.disable.description")),
                            new CommandHelpEntry("/blackjack setup delete <id>",
                                    tr("blackjack.help.blackjack.setup.delete.description"))
                    )),
                    new CommandHelpSection(tr("blackjack.help.section.information"), List.of(
                            new CommandHelpEntry("/blackjack setup info <id>",
                                    tr("blackjack.help.blackjack.setup.info.description")),
                            new CommandHelpEntry("/blackjack setup list", tr("blackjack.help.blackjack.setup.list.description-alternate"))
                    ))
            ),
            tr("blackjack.help.footer-setup")
    );

    private final JavaPlugin plugin;
    private final BlackjackTableConfig tableConfig;
    private final BlackjackTableService tableService;
    private final MessageService messageService;
    private final CommandHelpRenderer helpRenderer;
    private final SeatPositionResolver seatPositionResolver;
    private final BlackjackPreviewService previewService;

    public BlackjackCommand(
            JavaPlugin plugin,
            BlackjackTableConfig tableConfig,
            BlackjackTableService tableService,
            MessageService messageService,
            CommandHelpRenderer helpRenderer,
            SeatPositionResolver seatPositionResolver,
            BlackjackPreviewService previewService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tableConfig = Objects.requireNonNull(tableConfig, "tableConfig");
        this.tableService = Objects.requireNonNull(tableService, "tableService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
        this.seatPositionResolver = Objects.requireNonNull(seatPositionResolver, "seatPositionResolver");
        this.previewService = Objects.requireNonNull(previewService, "previewService");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, tr("blackjack.setup.denied"));
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
                case "preview" -> preview(sender, args);
                case "enable" -> enable(sender, args);
                case "disable" -> disable(sender, args);
                case "info" -> info(sender, args);
                case "list" -> list(sender, args);
                default -> sendUnknown(sender, args[1], "/blackjack setup help");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not update blackjack table configuration.", exception);
            messageService.send(sender,
                    tr("blackjack.setup.save-failed")
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
                && acceptsExistingTableId(args[1])) {
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

    static boolean acceptsExistingTableId(String action) {
        String normalized = action.toLowerCase(Locale.ROOT);
        return ACTIONS.contains(normalized) && !normalized.equals("create") && !normalized.equals("list");
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/blackjack setup create <id>");
            return;
        }
        if (!BlackjackTableDraft.isValidId(args[2])) {
            messageService.send(sender, tr("blackjack.setup.id-invalid"));
            return;
        }
        if (!tableConfig.createDraft(args[2])) {
            messageService.send(sender, tr("blackjack.table.exists", Map.of("id", Component.text(args[2], NamedTextColor.WHITE))).color(NamedTextColor.RED));
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
            messageService.send(sender, tr("blackjack.setup.disable-delete"));
            return;
        }
        tableConfig.deleteDraft(draft.getId());
        messageService.send(sender, tr("blackjack.table.deleted", Map.of("id", Component.text(draft.getId()))).color(NamedTextColor.GREEN));
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
        messageService.send(sender, tr("blackjack.table.position", Map.of("id", Component.text(draft.getId()), "position", tr("blackjack.position." + type.name()))).color(NamedTextColor.GREEN));
    }

    private void interaction(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireEditablePlayerDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        Player player = (Player) sender;
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            messageService.send(sender, tr("blackjack.setup.look-block"));
            return;
        }
        draft.setInteraction(BlackjackBlockPosition.fromBlock(block));
        tableConfig.saveDraft(draft);
        messageService.send(sender, tr("blackjack.table.interaction", Map.of("id", Component.text(draft.getId()))).color(NamedTextColor.GREEN));
    }

    private void displayAnchor(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireEditablePlayerDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        Player player = (Player) sender;
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            messageService.send(sender, tr("blackjack.setup.look-table"));
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
        Component output = tr("blackjack.setup.surface-updated").color(NamedTextColor.GREEN)
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.block"), anchor.worldName() + " @ "
                        + block.getX() + ", " + block.getY() + ", " + block.getZ(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.surface"), Double.toString(surfaceY), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(tr("common.use-short").append(Component.text("/blackjack setup info " + draft.getId())).color(NamedTextColor.AQUA));
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
            messageService.send(sender, tr("blackjack.setup.look-seat"));
            return;
        }
        Location resolved = seatPositionResolver.resolve(block, player.getYaw()).orElse(null);
        if (resolved == null) {
            messageService.send(sender,
                    tr("blackjack.setup.seat-block-invalid"));
            return;
        }
        draft.setSeat(number, position(resolved, true), BlackjackBlockPosition.fromBlock(block));
        tableConfig.saveDraft(draft);
        messageService.send(sender, tr("blackjack.table.seat", Map.of("id", Component.text(draft.getId()), "seat", Component.text(number))).color(NamedTextColor.GREEN));
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
            messageService.send(sender, tr("blackjack.setup.seat-unconfigured"));
            return;
        }
        tableConfig.saveDraft(draft);
        messageService.send(sender, tr("blackjack.table.seat-removed", Map.of("id", Component.text(draft.getId()), "seat", Component.text(number))).color(NamedTextColor.GREEN));
    }

    private void preview(CommandSender sender, String[] args) {
        BlackjackTableDraft draft = requireDraft(sender, args, 3);
        if (draft == null) {
            return;
        }
        if (!(sender instanceof Player player)) {
            messageService.send(sender, tr("blackjack.preview.player-only"));
            return;
        }
        BlackjackPreviewService.PreviewResult result = previewService.show(player.getUniqueId(), draft);
        Component output;
        if (result.status() == BlackjackPreviewService.PreviewStatus.MISSING_SURFACE) {
            output = tr("blackjack.preview.surface-missing").color(NamedTextColor.RED)
                    .append(Component.newline())
                    .append(tr("common.use").color(NamedTextColor.YELLOW))
                    .append(Component.text("/blackjack setup display " + draft.getId(), NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand(
                                    "/blackjack setup display " + draft.getId())));
        } else if (result.status() == BlackjackPreviewService.PreviewStatus.WORLD_UNAVAILABLE) {
            output = tr("blackjack.preview.world-missing").color(NamedTextColor.RED);
        } else {
            output = tr("blackjack.preview.shown", Map.of("seconds", Component.text(BlackjackPreviewService.PREVIEW_SECONDS))).color(NamedTextColor.GREEN)
                    .append(Component.newline())
                    .append(infoLine(tr("blackjack.preview.markers"), Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")), result.markers()), NamedTextColor.WHITE));
        }
        if (!result.missingComponents().isEmpty()) {
            output = output.append(Component.newline())
                    .append(tr("blackjack.preview.missing-heading").color(NamedTextColor.YELLOW));
            for (Component missing : result.missingComponents()) {
                output = output.append(Component.newline())
                        .append(tr("blackjack.preview.missing-item", Map.of("component", missing)).color(NamedTextColor.GRAY));
            }
        }
        messageService.send(sender, output);
    }

    private void enable(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendInvalidUsage(sender, "/blackjack setup enable <id>");
            return;
        }
        BlackjackTableOperationResult result = tableService.enableTable(args[2]);
        if (result.isSuccess()) {
            messageService.send(sender, tr("blackjack.table.enabled", Map.of("id", Component.text(args[2]))).color(NamedTextColor.GREEN));
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
            messageService.send(sender, tr("blackjack.table.disabled", Map.of("id", Component.text(args[2]))).color(NamedTextColor.GREEN));
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
        Component sessionStatus = session.map(value -> tr("blackjack.info.session-count", Map.of(
                "state", tr("blackjack.info.session-state." + value.getState().name()), "count", Component.text(value.getParticipantCount()))))
                .orElseGet(() -> tr("common.none"));
        Component output = tr("blackjack.info.heading").color(NamedTextColor.GOLD)
                .append(Component.text(draft.getId(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.newline())
                .append(tr("blackjack.info.status").color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.enabled"), draft.isEnabled() ? tr("common.enabled") : tr("common.disabled"),
                        draft.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.runtime"), tableService.isRuntimeActive(draft.getId()) ? tr("moderation.status.active") : tr("moderation.status.inactive"),
                        tableService.isRuntimeActive(draft.getId()) ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.session"), sessionStatus, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.newline())
                .append(tr("blackjack.info.physical").color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.world"), configuredWorld(draft), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.area"), draft.getPos1().isPresent() && draft.getPos2().isPresent()
                                ? tr("blackjack.info.configured") : tr("blackjack.info.missing"),
                        draft.getPos1().isPresent() && draft.getPos2().isPresent()
                                ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.newline())
                .append(configurationLine(tr("blackjack.info.pos1"), draft.getPos1().isPresent()))
                .append(Component.newline())
                .append(configurationLine(tr("blackjack.info.pos2"), draft.getPos2().isPresent()))
                .append(Component.newline())
                .append(configurationLine(tr("blackjack.info.dealer"), draft.getDealer().isPresent()))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.table-surface"), draft.getDisplayAnchor().isPresent()
                                ? tr("blackjack.info.configured") : tr("blackjack.info.legacy-geometry"),
                        draft.getDisplayAnchor().isPresent() ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(configurationLine(tr("blackjack.info.interaction"), draft.getInteraction().isPresent()))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.join-mode"), joinMode(draft), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(infoLine(tr("blackjack.info.seats"), draft.getSeats().size() + " / 5",
                        draft.getSeats().isEmpty() ? NamedTextColor.RED : NamedTextColor.WHITE));

        SetupStep nextStep = nextSetupStep(draft);
        if (nextStep != null) {
            output = output.append(Component.newline())
                    .append(Component.newline())
                    .append(tr("blackjack.info.next").color(NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(nextStep.description().color(NamedTextColor.GRAY))
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
            messageService.send(sender, tr("blackjack.setup.empty"));
            return;
        }
        Component output = tr("blackjack.info.tables").color(NamedTextColor.GOLD)
                .append(Component.newline())
                .append(tr("blackjack.info.table-count", Map.of("count", Component.text(drafts.size()))).color(NamedTextColor.GRAY));
        for (BlackjackTableDraft draft : drafts) {
            BlackjackSession session = tableService.getSession(draft.getId()).orElse(null);
            Component status = draft.isEnabled() ? tr("common.enabled") : tr("common.disabled");
            String capacity = session == null ? "" : " " + session.getParticipantCount() + "/"
                    + tableService.getDefinition(draft.getId()).orElseThrow().capacity();
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(draft.getId(), NamedTextColor.AQUA))
                    .append(Component.text(" [").append(status).append(Component.text("]" + capacity)).color(draft.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        messageService.send(sender, output);
    }

    private BlackjackTableDraft requireEditablePlayerDraft(CommandSender sender, String[] args, int length) {
        BlackjackTableDraft draft = requireDraft(sender, args, length);
        if (draft == null) {
            return null;
        }
        if (!(sender instanceof Player)) {
            messageService.send(sender, tr("blackjack.setup.player-only"));
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
            messageService.send(sender, missingTableMessage(args[2]));
        }
        return draft;
    }

    private static Component missingTableMessage(String tableId) {
        return tr("blackjack.table.missing", Map.of("id", Component.text(tableId, NamedTextColor.WHITE))).color(NamedTextColor.RED);
    }

    private boolean ensureEditable(CommandSender sender, BlackjackTableDraft draft) {
        if (!draft.isEnabled() && !tableService.isRuntimeActive(draft.getId())) {
            return true;
        }
        messageService.send(sender, tr("blackjack.setup.disable-edit"));
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
        messageService.send(sender, tr("blackjack.setup.seat-number"));
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

    private Component configuredWorld(BlackjackTableDraft draft) {
        return draft.getPos1().map(ActivityPosition::worldName)
                .or(() -> draft.getPos2().map(ActivityPosition::worldName))
                .or(() -> draft.getDealer().map(ActivityPosition::worldName))
                .or(() -> draft.getDisplayAnchor().map(BlackjackDisplayAnchor::worldName))
                .or(() -> draft.getInteraction().map(BlackjackBlockPosition::worldName))
                .map(value -> (Component) Component.text(value)).orElseGet(() -> tr("blackjack.info.not-configured"));
    }

    private void sendOperationFailure(
            CommandSender sender,
            String tableId,
            BlackjackTableOperationResult result
    ) {
        switch (result.status()) {
            case TABLE_NOT_FOUND -> messageService.send(sender,
                    tr("blackjack.table.missing", Map.of("id", Component.text(tableId, NamedTextColor.WHITE))).color(NamedTextColor.RED));
            case ALREADY_ENABLED -> messageService.send(sender, tr("blackjack.setup.already-enabled"));
            case NOT_ENABLED -> messageService.send(sender, tr("blackjack.setup.already-disabled"));
            case WORLD_NOT_LOADED -> messageService.send(sender, tr("blackjack.setup.world-missing"));
            case INTERACTION_CONFLICT -> messageService.send(sender,
                    tr("blackjack.setup.interaction-used"));
            case SEAT_CONFLICT -> messageService.send(sender,
                    tr("blackjack.setup.seat-used"));
            case SEAT_BLOCK_INVALID -> messageService.send(sender,
                    tr("blackjack.setup.seat-invalid"));
            case TABLE_IN_USE -> messageService.send(sender,
                    tr("blackjack.setup.busy"));
            case INVALID_DEFINITION -> {
                messageService.send(sender, invalidDefinitionMessage(tableId, result.details()));
            }
            case RUNTIME_FAILURE -> messageService.send(sender,
                    tr("blackjack.setup.activate-failed"));
            case SUCCESS -> throw new IllegalArgumentException("Success is not a failure result");
        }
    }

    private static Component createWorkflow(String tableId) {
        List<WorkflowStep> steps = List.of(
                new WorkflowStep(tr("blackjack.workflow.build"), null),
                new WorkflowStep(tr("blackjack.workflow.create"),
                        "/blackjack setup create " + tableId),
                new WorkflowStep(tr("blackjack.workflow.pos1"),
                        "/blackjack setup pos1 " + tableId),
                new WorkflowStep(tr("blackjack.workflow.pos2"),
                        "/blackjack setup pos2 " + tableId),
                new WorkflowStep(tr("blackjack.workflow.dealer"),
                        "/blackjack setup dealer " + tableId),
                new WorkflowStep(tr("blackjack.workflow.surface"),
                        "/blackjack setup display " + tableId),
                new WorkflowStep(tr("blackjack.workflow.seat"),
                        "/blackjack setup seat " + tableId + " <number>"),
                new WorkflowStep(tr("blackjack.workflow.preview"),
                        "/blackjack setup preview " + tableId),
                new WorkflowStep(tr("blackjack.workflow.enable"),
                        "/blackjack setup enable " + tableId)
        );
        Component output = tr("blackjack.table.created", Map.of("id", Component.text(tableId))).color(NamedTextColor.GREEN)
                .append(Component.newline())
                .append(Component.newline())
                .append(tr("blackjack.workflow.order").color(NamedTextColor.YELLOW));
        for (int index = 0; index < steps.size(); index++) {
            WorkflowStep step = steps.get(index);
            output = output.append(Component.newline())
                    .append(Component.text((index + 1) + ". ", NamedTextColor.GRAY))
                    .append(step.description().color(NamedTextColor.WHITE));
            if (step.command() != null) {
                output = output.append(Component.newline())
                        .append(Component.text("   " + step.command(), NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.suggestCommand(step.command())));
            }
        }
        return output.append(Component.newline())
                .append(Component.newline())
                .append(tr("common.help-tip").color(NamedTextColor.YELLOW))
                .append(tr("blackjack.workflow.progress", Map.of("command", Component.text("/blackjack setup info " + tableId))).color(NamedTextColor.GRAY));
    }

    private void sendUnknown(CommandSender sender, String subcommand, String helpSyntax) {
        messageService.send(sender, tr("blackjack.unknown-command", Map.of("command", Component.text(subcommand, NamedTextColor.WHITE))).color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text(helpSyntax, NamedTextColor.AQUA)));
    }

    private void sendInvalidUsage(CommandSender sender, String syntax) {
        messageService.send(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
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
            case "preview" -> "/blackjack setup preview <id>";
            case "enable" -> "/blackjack setup enable <id>";
            case "disable" -> "/blackjack setup disable <id>";
            case "info" -> "/blackjack setup info <id>";
            case "list" -> "/blackjack setup list";
            default -> "/blackjack setup help";
        };
    }

    private Component infoLine(Component label, Component value, NamedTextColor valueColor) {
        return label.color(NamedTextColor.GRAY).append(value.color(valueColor));
    }

    private Component infoLine(Component label, String value, NamedTextColor valueColor) {
        return infoLine(label, Component.text(value), valueColor);
    }
    private Component configurationLine(Component label, boolean configured) {
        return infoLine(
                label,
                configured ? tr("blackjack.info.configured") : tr("blackjack.info.missing"),
                configured ? NamedTextColor.GREEN : NamedTextColor.RED
        );
    }

    private SetupStep nextSetupStep(BlackjackTableDraft draft) {
        String id = draft.getId();
        if (draft.getPos1().isEmpty()) {
            return new SetupStep(tr("blackjack.next.pos1"), "/blackjack setup pos1 " + id);
        }
        if (draft.getPos2().isEmpty()) {
            return new SetupStep(tr("blackjack.next.pos2"), "/blackjack setup pos2 " + id);
        }
        if (draft.getDealer().isEmpty()) {
            return new SetupStep(tr("blackjack.next.dealer"),
                    "/blackjack setup dealer " + id);
        }
        if (draft.getDisplayAnchor().isEmpty()) {
            return new SetupStep(tr("blackjack.next.display"), "/blackjack setup display " + id);
        }
        if (draft.getSeats().isEmpty()) {
            return new SetupStep(tr("blackjack.next.seat"), "/blackjack setup seat " + id + " 1");
        }
        boolean modern = draft.getSeatDefinitions().values().stream().allMatch(seat -> seat.isModern());
        if (!modern && draft.getInteraction().isEmpty()) {
            return new SetupStep(tr("blackjack.next.interaction"), "/blackjack setup interaction " + id);
        }
        if (!draft.isEnabled()) {
            return new SetupStep(tr("blackjack.next.preview"),
                    "/blackjack setup preview " + id);
        }
        return null;
    }

    private static Component invalidDefinitionMessage(String tableId, List<String> details) {
        Component output = tr("blackjack.table.not-ready", Map.of("id", Component.text(tableId, NamedTextColor.WHITE))).color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("blackjack.validation.heading").color(NamedTextColor.YELLOW));
        for (String detail : details) {
            output = output.append(Component.newline())
                    .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                    .append(friendlyDetail(detail).color(NamedTextColor.WHITE));
        }
        return output.append(Component.newline())
                .append(tr("blackjack.validation.then").color(NamedTextColor.YELLOW))
                .append(Component.text("/blackjack setup info " + tableId, NamedTextColor.AQUA));
    }

    private static Component friendlyDetail(String detail) {
        String value = Objects.requireNonNull(detail, "detail").trim();
        java.util.regex.Matcher match;
        match = java.util.regex.Pattern.compile("seat number ([-0-9]+) is outside 1-5").matcher(value);
        if (match.matches()) return tr("blackjack.validation.seat-number", Map.of("seat", Component.text(match.group(1))));
        match = java.util.regex.Pattern.compile("seat ([-0-9]+) uses a different world").matcher(value);
        if (match.matches()) return tr("blackjack.validation.seat-world", Map.of("seat", Component.text(match.group(1))));
        match = java.util.regex.Pattern.compile("seat block ([-0-9]+) uses a different world").matcher(value);
        if (match.matches()) return tr("blackjack.validation.seat-block-world", Map.of("seat", Component.text(match.group(1))));
        match = java.util.regex.Pattern.compile("duplicate seat number ([-0-9]+)").matcher(value);
        if (match.matches()) return tr("blackjack.validation.duplicate-number", Map.of("seat", Component.text(match.group(1))));
        match = java.util.regex.Pattern.compile("seat ([-0-9]+) is outside the area").matcher(value);
        if (match.matches()) return tr("blackjack.validation.seat-outside", Map.of("seat", Component.text(match.group(1))));
        match = java.util.regex.Pattern.compile("seat ([-0-9]+) has no block").matcher(value);
        if (match.matches()) return tr("blackjack.validation.seat-no-block", Map.of("seat", Component.text(match.group(1))));
        match = java.util.regex.Pattern.compile("duplicate seat block at seat ([-0-9]+)").matcher(value);
        if (match.matches()) return tr("blackjack.validation.duplicate-block", Map.of("seat", Component.text(match.group(1))));
        match = java.util.regex.Pattern.compile("seat block ([-0-9]+) is outside the area").matcher(value);
        if (match.matches()) return tr("blackjack.validation.seat-block-outside", Map.of("seat", Component.text(match.group(1))));
        return switch (value) {
            case "more than 5 seats configured" -> tr("blackjack.validation.too-many");
            case "at most five seats are allowed" -> tr("blackjack.validation.max-seats");
            case "legacy and modern seats cannot be mixed" -> tr("blackjack.validation.mixed-seats");
            case "area corners use different worlds" -> tr("blackjack.validation.area-worlds");
            case "dealer uses a different world" -> tr("blackjack.validation.dealer-world");
            case "dealer is outside the area" -> tr("blackjack.validation.dealer-outside");
            case "interaction uses a different world" -> tr("blackjack.validation.interaction-world");
            case "interaction is outside the area" -> tr("blackjack.validation.interaction-outside");
            case "display anchor uses a different world" -> tr("blackjack.validation.display-world");
            case "display anchor is outside the area" -> tr("blackjack.validation.display-outside");
            case "missing area pos1" -> tr("blackjack.validation.area-pos1");
            case "missing area pos2" -> tr("blackjack.validation.area-pos2");
            case "missing dealer" -> tr("blackjack.validation.dealer");
            case "missing interaction" -> tr("blackjack.validation.interaction");
            case "missing seat", "at least one seat is required" -> tr("blackjack.validation.seat");
            default -> tr("blackjack.validation.unknown");
        };
    }

    private String display(String enumName) {
        String value = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Component joinMode(BlackjackTableDraft draft) {
        if (draft.getSeatDefinitions().isEmpty()) return tr("blackjack.info.not-configured");
        long modern = draft.getSeatDefinitions().values().stream().filter(seat -> seat.isModern()).count();
        if (modern == draft.getSeatDefinitions().size()) return tr("blackjack.info.join-mode.modern");
        if (modern == 0) return tr("blackjack.info.join-mode.legacy");
        return tr("blackjack.info.join-mode.invalid");
    }

    private static List<String> matches(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private record SetupStep(Component description, String syntax) {

        private SetupStep {
            description = Objects.requireNonNull(description, "description");
            syntax = Objects.requireNonNull(syntax, "syntax");
        }
    }

    private record WorkflowStep(Component description, String command) {

        private WorkflowStep {
            description = Objects.requireNonNull(description, "description");
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

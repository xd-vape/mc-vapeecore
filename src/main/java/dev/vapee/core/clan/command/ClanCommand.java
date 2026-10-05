package dev.vapee.core.clan.command;

import dev.vapee.core.clan.*;
import dev.vapee.core.clan.gui.ClanMenu;
import dev.vapee.core.clan.gui.ClanMenuView;
import dev.vapee.core.command.help.*;
import dev.vapee.core.identity.*;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Player-facing clan operations. Domain state and known identities remain authoritative. */
public final class ClanCommand implements TabExecutor {
    public static final String PERMISSION = "vapeecore.clan.use";
    private static final Comparator<String> ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final CommandHelpPage HELP = new CommandHelpPage("Clans", List.of(
            new CommandHelpSection("Getting started", List.of(
                    new CommandHelpEntry("/clan", "Open your clan menu."),
                    new CommandHelpEntry("/clan create <tag> <name...>", "Create a clan; names may contain spaces."),
                    new CommandHelpEntry("/clan info [tag|uuid]", "Show your clan or another clan."),
                    new CommandHelpEntry("/clan invites", "View incoming or outgoing invites."),
                    new CommandHelpEntry("/clan accept <tag|uuid>", "Join a clan that invited you."),
                    new CommandHelpEntry("/clan deny <tag|uuid>", "Decline an invite."),
                    new CommandHelpEntry("/clan leave", "Leave as a member."))),
            new CommandHelpSection("Owner", List.of(
                    new CommandHelpEntry("/clan invite <player|uuid>", "Invite a known player, even if offline."),
                    new CommandHelpEntry("/clan cancel <player|uuid>", "Cancel an outgoing invite."),
                    new CommandHelpEntry("/clan kick <player|uuid>", "Remove a member."),
                    new CommandHelpEntry("/clan transfer <player|uuid>", "Transfer ownership."),
                    new CommandHelpEntry("/clan rename <name...>", "Rename your clan."),
                    new CommandHelpEntry("/clan tag <tag>", "Change your clan tag."),
                    new CommandHelpEntry("/clan disband confirm", "Permanently disband your clan.")))));

    private final ClanService clans;
    private final PlayerIdentityService identities;
    private final MessageService messages;
    private final CommandHelpRenderer help;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;
    private final Logger logger;
    private final Consumer<Player> openMenu;
    private final Consumer<Player> openInvites;
    private final ClanMessages feedback;

    public ClanCommand(JavaPlugin plugin, ClanService clans, PlayerIdentityService identities,
                       MessageService messages, CommandHelpRenderer help, ClanMenu menu) {
        this(clans, identities, messages, help, plugin.getServer()::getOnlinePlayers,
                plugin.getServer()::getPlayer, plugin.getLogger(), menu::open,
                player -> menu.open(player, ClanMenuView.INVITES, 0));
    }

    public ClanCommand(ClanService clans, PlayerIdentityService identities, MessageService messages,
                       CommandHelpRenderer help, Supplier<? extends Collection<? extends Player>> onlinePlayers,
                       Function<UUID, Player> onlinePlayer, Logger logger, Consumer<Player> openMenu,
                       Consumer<Player> openInvites) {
        this.clans = Objects.requireNonNull(clans, "clans");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.help = Objects.requireNonNull(help, "help");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.openMenu = Objects.requireNonNull(openMenu, "openMenu");
        this.openInvites = Objects.requireNonNull(openInvites, "openInvites");
        this.feedback = new ClanMessages(messages, Objects.requireNonNull(onlinePlayer, "onlinePlayer"));
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                        @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "Only players can manage clans.", NamedTextColor.RED);
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            send(player, "You do not have permission to use clans.", NamedTextColor.RED);
            return true;
        }
        try {
            execute(player, args);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not process clan command for player " + player.getUniqueId(), exception);
            send(player, "The clan data could not be updated. Please try again.", NamedTextColor.RED);
        }
        return true;
    }

    private void execute(Player actor, String[] args) {
        if (args.length == 0) { openMenu.accept(actor); return; }
        String action = args[0].toLowerCase(Locale.ROOT);
        UUID self = actor.getUniqueId();
        switch (action) {
            case "help" -> handleHelp(actor, args);
            case "invites" -> handleInvites(actor, args);
            case "info" -> handleInfo(actor, self, args);
            case "create" -> handleCreate(actor, self, args);
            case "rename" -> handleRename(actor, self, args);
            case "tag" -> handleTag(actor, self, args);
            case "leave" -> handleLeave(actor, self, args);
            case "disband" -> handleDisband(actor, self, args);
            case "accept", "deny" -> handleIncomingInviteAction(actor, self, action, args);
            case "invite", "cancel", "kick", "transfer" -> handlePlayerAction(actor, self, action, args);
            default -> sendUnknownCommand(actor, args[0]);
        }
    }

    private void handleHelp(Player actor, String[] args) {
        if (args.length != 1) {
            sendUsage(actor, "/clan help");
            return;
        }
        help.send(actor, HELP);
    }

    private void handleInvites(Player actor, String[] args) {
        if (args.length != 1) {
            sendUsage(actor, "/clan invites");
            return;
        }
        openInvites.accept(actor);
    }

    private void handleInfo(Player actor, UUID self, String[] args) {
        if (args.length > 2) {
            sendUsage(actor, "/clan info [tag|uuid]");
            return;
        }
        Clan clan = args.length == 1 ? clans.getClanOf(self).orElse(null) : resolveClan(args[1]);
        if (clan == null) {
            send(actor, args.length == 1 ? "You are not in a clan." : "Clan not found.", NamedTextColor.RED);
            return;
        }
        send(actor, clanIdentity(clan));
        send(actor, "Members: " + clan.members().size() + "/" + clans.getLimits().maxMembers(), NamedTextColor.WHITE);
        send(actor, Component.text("Owner: ", NamedTextColor.GRAY)
                .append(Component.text(displayName(clan.ownerId()), NamedTextColor.WHITE)));
        send(actor, Component.text("ID: " + clan.id(), NamedTextColor.GRAY));
    }

    private void handleCreate(Player actor, UUID self, String[] args) {
        if (args.length == 1) {
            send(actor, "Missing clan tag and name.", NamedTextColor.YELLOW);
            sendUsage(actor, "/clan create <tag> <name...>");
            sendExample(actor, "/clan create BMW BMW Community");
            return;
        }
        if (args.length == 2) {
            send(actor, "Missing clan name.", NamedTextColor.YELLOW);
            sendUsage(actor, "/clan create <tag> <name...>");
            sendExample(actor, "/clan create BMW BMW Community");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        feedback.report(actor, "create", name, clans.createClan(self, name, args[1]));
    }

    private void handleRename(Player actor, UUID self, String[] args) {
        if (args.length == 1) {
            send(actor, "Missing clan name.", NamedTextColor.YELLOW);
            sendUsage(actor, "/clan rename <name...>");
            sendExample(actor, "/clan rename BMW Drivers");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        feedback.report(actor, "rename", name, clans.renameClan(self, name));
    }

    private void handleTag(Player actor, UUID self, String[] args) {
        if (args.length == 1) {
            send(actor, "Missing clan tag.", NamedTextColor.YELLOW);
            sendUsage(actor, "/clan tag <tag>");
            sendExample(actor, "/clan tag BMW");
            return;
        }
        if (args.length != 2) {
            sendUsage(actor, "/clan tag <tag>");
            return;
        }
        feedback.report(actor, "tag", args[1], clans.changeTag(self, args[1]));
    }

    private void handleLeave(Player actor, UUID self, String[] args) {
        if (args.length != 1) {
            sendUsage(actor, "/clan leave");
            return;
        }
        String tag = clans.getClanOf(self).map(Clan::tag).orElse("");
        feedback.report(actor, "leave", tag, clans.leaveClan(self));
    }

    private void handleDisband(Player actor, UUID self, String[] args) {
        if (args.length != 2 || !args[1].equalsIgnoreCase("confirm")) {
            send(actor, Component.text("To permanently disband your clan, use ", NamedTextColor.YELLOW)
                    .append(commandSuggestion("/clan disband confirm", NamedTextColor.RED,
                            "Click to prepare the permanent disband command."))
                    .append(Component.text(".", NamedTextColor.YELLOW)));
            return;
        }
        String tag = clans.getClanOf(self).map(Clan::tag).orElse("");
        feedback.report(actor, "disband", tag, clans.disbandClan(self));
    }

    private void handleIncomingInviteAction(Player actor, UUID self, String action, String[] args) {
        if (args.length == 1) {
            sendIncomingInviteChoices(actor, self, action);
            return;
        }
        if (args.length != 2) {
            sendUsage(actor, "/clan " + action + " <tag|uuid>");
            return;
        }
        Clan target = resolveClan(args[1]);
        if (target == null) {
            feedback.report(actor, action, args[1], ClanResult.CLAN_NOT_FOUND);
            return;
        }
        UUID owner = target.ownerId();
        ClanResult result = action.equals("accept") ? clans.acceptInvite(self, target.id())
                : clans.denyInvite(self, target.id());
        feedback.report(actor, action, target.tag(), result);
        if (result == ClanResult.SUCCESS && action.equals("accept")) {
            feedback.notifyAccept(owner, actor.getName());
        }
    }

    private void handlePlayerAction(Player actor, UUID self, String action, String[] args) {
        if (args.length == 1) {
            if (action.equals("cancel") && sendOutgoingInviteChoices(actor, self)) {
                return;
            }
            if (action.equals("invite")) {
                send(actor, "Missing player.", NamedTextColor.YELLOW);
                sendUsage(actor, "/clan invite <player|uuid>");
                send(actor, "Use Tab to view online candidates.", NamedTextColor.GRAY);
            } else {
                sendUsage(actor, "/clan " + action + " <player|uuid>");
            }
            return;
        }
        if (args.length != 2) {
            sendUsage(actor, "/clan " + action + " <player|uuid>");
            return;
        }
        PlayerLookupResult lookup = identities.resolve(args[1]);
        if (lookup.status() == PlayerLookupStatus.NOT_FOUND) {
            send(actor, "This player is not known to VapeeCore.", NamedTextColor.RED);
            return;
        }
        if (lookup.status() == PlayerLookupStatus.AMBIGUOUS) {
            send(actor, "That name is ambiguous; use the player's UUID.", NamedTextColor.YELLOW);
            return;
        }
        PlayerIdentity target = lookup.identity().orElseThrow();
        Clan source = clans.getClanOf(self).orElse(null);
        String tag = source == null ? "" : source.tag();
        ClanResult result = switch (action) {
            case "invite" -> clans.inviteMember(self, target.uniqueId());
            case "cancel" -> clans.cancelInvite(self, target.uniqueId());
            case "kick" -> clans.kickMember(self, target.uniqueId());
            case "transfer" -> clans.transferOwnership(self, target.uniqueId());
            default -> throw new IllegalStateException("Unknown clan action");
        };
        feedback.report(actor, action, target.name(), result);
        if (result == ClanResult.SUCCESS) {
            switch (action) {
                case "invite" -> {
                    if (source != null) {
                        feedback.notifyInvite(target.uniqueId(), actor.getName(), source.name(), source.tag());
                    }
                }
                case "kick" -> feedback.notifyKick(target.uniqueId(), tag);
                case "transfer" -> feedback.notifyTransfer(target.uniqueId(), tag);
                default -> { }
            }
        }
    }

    private void sendIncomingInviteChoices(Player actor, UUID self, String action) {
        List<ClanInvite> invites = clans.getIncomingInvites(self);
        if (invites.isEmpty()) {
            send(actor, "You have no pending clan invites.", NamedTextColor.YELLOW);
            send(actor, "Invites will appear here when a clan owner invites you.", NamedTextColor.GRAY);
            return;
        }
        send(actor, "Pending clan invites:", NamedTextColor.GOLD);
        for (ClanInvite invite : invites) {
            Clan source = clans.getClan(invite.clanId()).orElse(null);
            String identifier = source == null ? invite.clanId().toString() : source.tag();
            Component identity = source == null
                    ? Component.text("Unknown clan [" + invite.clanId() + "]", NamedTextColor.GRAY)
                    : clanIdentity(source);
            send(actor, identity.append(Component.space()).append(actionButton(action, identifier)));
        }
        sendUse(actor, "/clan " + action + " <tag|uuid>");
    }

    /** @return true when the actor owns a clan and the missing-target case was fully handled. */
    private boolean sendOutgoingInviteChoices(Player actor, UUID self) {
        Clan clan = clans.getClanOf(self).orElse(null);
        if (clan == null || !clan.ownerId().equals(self)) {
            return false;
        }
        List<ClanInvite> invites = clans.getOutgoingInvites(clan.id());
        if (invites.isEmpty()) {
            send(actor, "Your clan has no pending outgoing invites.", NamedTextColor.YELLOW);
            return true;
        }
        send(actor, "Outgoing clan invites:", NamedTextColor.GOLD);
        for (ClanInvite invite : invites) {
            String display = displayName(invite.recipient());
            String argument = identityCommandArgument(invite.recipient());
            send(actor, Component.text(display, NamedTextColor.WHITE)
                    .append(Component.space())
                    .append(actionButton("cancel", argument)));
        }
        sendUse(actor, "/clan cancel <player|uuid>");
        return true;
    }

    private Component clanIdentity(Clan clan) {
        return Component.text(clan.name(), NamedTextColor.GOLD)
                .append(Component.text(" [", NamedTextColor.GRAY))
                .append(Component.text(clan.tag(), NamedTextColor.AQUA))
                .append(Component.text("]", NamedTextColor.GRAY));
    }

    private Component actionButton(String action, String argument) {
        String label = Character.toUpperCase(action.charAt(0)) + action.substring(1);
        return commandSuggestion("[" + label + "]", "/clan " + action + " " + argument,
                action.equals("deny") || action.equals("cancel") ? NamedTextColor.RED : NamedTextColor.GREEN,
                "Click to prepare the clan " + action + " command.");
    }

    private Component commandSuggestion(String label, String command, NamedTextColor color, String hover) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private Component commandSuggestion(String command, NamedTextColor color, String hover) {
        return commandSuggestion(command, command, color, hover);
    }

    private void sendUsage(Player actor, String syntax) {
        send(actor, Component.text("Usage: ", NamedTextColor.YELLOW)
                .append(commandSuggestion(syntax, NamedTextColor.AQUA, "Click to insert this command.")));
    }

    private void sendExample(Player actor, String command) {
        send(actor, Component.text("Example: ", NamedTextColor.GRAY)
                .append(commandSuggestion(command, NamedTextColor.AQUA, "Click to use this example.")));
    }

    private void sendUse(Player actor, String syntax) {
        send(actor, Component.text("Use ", NamedTextColor.GRAY)
                .append(commandSuggestion(syntax, NamedTextColor.AQUA, "Click to insert this command.")));
    }

    private void sendUnknownCommand(Player actor, String input) {
        send(actor, Component.text("Unknown clan command: ", NamedTextColor.RED)
                .append(Component.text(input, NamedTextColor.WHITE)));
        send(actor, Component.text("Use ", NamedTextColor.GRAY)
                .append(commandSuggestion("/clan help", NamedTextColor.AQUA,
                        "Click to view all clan commands."))
                .append(Component.text(" to view all clan commands.", NamedTextColor.GRAY)));
    }

    private Clan resolveClan(String token) {
        try {
            UUID id = UUID.fromString(token);
            if (id.toString().equalsIgnoreCase(token)) return clans.getClan(id).orElse(null);
        } catch (IllegalArgumentException ignored) { }
        return clans.findClanByTag(token).orElse(null);
    }

    private String displayName(UUID id) {
        return identities.findById(id).map(PlayerIdentity::name).orElse(id.toString());
    }

    private String identityCommandArgument(UUID id) {
        return IdentityCommandArgument.nameOrUuid(id, displayName(id),
                name -> identities.resolve(name).identity().map(PlayerIdentity::uniqueId));
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                             @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) return List.of();
        try {
            UUID self = player.getUniqueId();
            Clan clan = clans.getClanOf(self).orElse(null);
            if (args.length == 1) {
                List<String> options = new ArrayList<>(List.of("help", "info"));
                if (clan == null) options.addAll(List.of("create", "invites", "accept", "deny"));
                else if (clan.ownerId().equals(self)) options.addAll(List.of("invite", "invites", "cancel", "kick", "transfer", "rename", "tag", "disband"));
                else options.add("leave");
                return matching(options, args[0]);
            }
            if (args.length != 2) return List.of();
            List<String> options = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> clan == null || !clan.ownerId().equals(self) ? List.of() : onlinePlayers.get().stream()
                        .filter(online -> !online.getUniqueId().equals(self) && !clans.isMember(online.getUniqueId()))
                        .map(Player::getName).toList();
                case "cancel" -> clan == null || !clan.ownerId().equals(self) ? List.of() : clans.getOutgoingInvites(clan.id()).stream()
                        .map(invite -> identityCommandArgument(invite.recipient())).toList();
                case "kick", "transfer" -> clan == null || !clan.ownerId().equals(self) ? List.of() : clan.members().stream()
                        .filter(member -> !member.playerId().equals(self))
                        .map(member -> identityCommandArgument(member.playerId())).toList();
                case "accept", "deny" -> clan != null ? List.of() : clans.getIncomingInvites(self).stream()
                        .map(invite -> clans.getClan(invite.clanId()).map(Clan::tag).orElse(invite.clanId().toString())).toList();
                case "disband" -> clan != null && clan.ownerId().equals(self) ? List.of("confirm") : List.of();
                default -> List.of();
            };
            return matching(options, args[1]);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not complete clan command.", exception);
            return List.of();
        }
    }

    private static List<String> matching(Collection<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct().sorted(ORDER).toList();
    }

    private void send(CommandSender sender, String text, NamedTextColor color) {
        messages.send(sender, Component.text(text, color));
    }
    private void send(CommandSender sender, Component text) { messages.send(sender, text); }
}

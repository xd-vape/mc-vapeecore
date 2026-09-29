package dev.vapee.core.clan.command;

import dev.vapee.core.clan.*;
import dev.vapee.core.clan.gui.ClanMenu;
import dev.vapee.core.clan.gui.ClanMenuView;
import dev.vapee.core.command.help.*;
import dev.vapee.core.identity.*;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
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
        if (action.equals("help") && args.length == 1) { help.send(actor, HELP); return; }
        if (action.equals("invites") && args.length == 1) { openInvites.accept(actor); return; }
        if (action.equals("info") && args.length <= 2) {
            Clan clan = args.length == 1 ? clans.getClanOf(self).orElse(null) : resolveClan(args[1]);
            if (clan == null) { send(actor, args.length == 1 ? "You are not in a clan." : "Clan not found.", NamedTextColor.RED); return; }
            send(actor, Component.text(clan.name(), NamedTextColor.GOLD)
                    .append(Component.text(" [", NamedTextColor.GRAY))
                    .append(Component.text(clan.tag(), NamedTextColor.AQUA))
                    .append(Component.text("]", NamedTextColor.GRAY)));
            send(actor, "Members: " + clan.members().size() + "/" + clans.getLimits().maxMembers(), NamedTextColor.WHITE);
            send(actor, Component.text("Owner: ", NamedTextColor.GRAY)
                    .append(Component.text(displayName(clan.ownerId()), NamedTextColor.WHITE)));
            send(actor, Component.text("ID: " + clan.id(), NamedTextColor.GRAY));
            return;
        }
        if (action.equals("create") && args.length >= 3) {
            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            feedback.report(actor, "create", name, clans.createClan(self, name, args[1]));
            return;
        }
        if (action.equals("rename") && args.length >= 2) {
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            feedback.report(actor, "rename", name, clans.renameClan(self, name));
            return;
        }
        if (action.equals("tag") && args.length == 2) {
            feedback.report(actor, "tag", args[1], clans.changeTag(self, args[1]));
            return;
        }
        if (action.equals("leave") && args.length == 1) {
            String tag = clans.getClanOf(self).map(Clan::tag).orElse("");
            feedback.report(actor, "leave", tag, clans.leaveClan(self));
            return;
        }
        if (action.equals("disband")) {
            if (args.length != 2 || !args[1].equalsIgnoreCase("confirm")) {
                send(actor, "To permanently disband your clan, use /clan disband confirm.", NamedTextColor.YELLOW);
                return;
            }
            String tag = clans.getClanOf(self).map(Clan::tag).orElse("");
            feedback.report(actor, "disband", tag, clans.disbandClan(self));
            return;
        }
        if ((action.equals("accept") || action.equals("deny")) && args.length == 2) {
            Clan target = resolveClan(args[1]);
            if (target == null) { feedback.report(actor, action, args[1], ClanResult.CLAN_NOT_FOUND); return; }
            UUID owner = target.ownerId();
            ClanResult result = action.equals("accept") ? clans.acceptInvite(self, target.id())
                    : clans.denyInvite(self, target.id());
            feedback.report(actor, action, target.tag(), result);
            if (result == ClanResult.SUCCESS && action.equals("accept")) feedback.notifyAccept(owner, actor.getName());
            return;
        }
        if (List.of("invite", "cancel", "kick", "transfer").contains(action) && args.length == 2) {
            PlayerLookupResult lookup = identities.resolve(args[1]);
            if (lookup.status() == PlayerLookupStatus.NOT_FOUND) {
                send(actor, "This player is not known to VapeeCore.", NamedTextColor.RED); return;
            }
            if (lookup.status() == PlayerLookupStatus.AMBIGUOUS) {
                send(actor, "That name is ambiguous; use the player's UUID.", NamedTextColor.YELLOW); return;
            }
            PlayerIdentity target = lookup.identity().orElseThrow();
            String tag = clans.getClanOf(self).map(Clan::tag).orElse("");
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
                    case "invite" -> feedback.notifyInvite(target.uniqueId(), actor.getName(), tag);
                    case "kick" -> feedback.notifyKick(target.uniqueId(), tag);
                    case "transfer" -> feedback.notifyTransfer(target.uniqueId(), tag);
                    default -> { }
                }
            }
            return;
        }
        send(actor, "Use: /clan help", NamedTextColor.YELLOW);
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
                        .map(invite -> displayName(invite.recipient())).toList();
                case "kick", "transfer" -> clan == null || !clan.ownerId().equals(self) ? List.of() : clan.members().stream()
                        .filter(member -> !member.playerId().equals(self)).map(member -> displayName(member.playerId())).toList();
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

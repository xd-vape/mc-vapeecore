package dev.vapee.core.friend.command;

import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.friend.FriendRelation;
import dev.vapee.core.friend.FriendRequest;
import dev.vapee.core.friend.FriendMessages;
import dev.vapee.core.friend.FriendResult;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.gui.FriendMenu;
import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.identity.PlayerLookupResult;
import dev.vapee.core.identity.PlayerLookupStatus;
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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FriendCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.friend.use";
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "add", "accept", "deny", "cancel", "remove", "list", "requests");
    private static final Comparator<String> NAME_ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(
            Comparator.naturalOrder());
    private static final CommandHelpPage HELP = new CommandHelpPage("Friends", List.of(
            new CommandHelpSection("Requests", List.of(
                    new CommandHelpEntry("/friend add <player|uuid>", "Send a friend request."),
                    new CommandHelpEntry("/friend accept <player|uuid>", "Accept an incoming request."),
                    new CommandHelpEntry("/friend deny <player|uuid>", "Decline an incoming request."),
                    new CommandHelpEntry("/friend cancel <player|uuid>", "Cancel your outgoing request."),
                    new CommandHelpEntry("/friend requests", "View incoming and outgoing requests."))),
            new CommandHelpSection("Friends", List.of(
                    new CommandHelpEntry("/friend list", "View your friends."),
                    new CommandHelpEntry("/friend remove <player|uuid>", "Remove a friend.")))
    ));

    private final FriendService friends;
    private final PlayerIdentityService identities;
    private final MessageService messages;
    private final CommandHelpRenderer helpRenderer;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;
    private final Logger logger;
    private final Consumer<Player> openMenu;
    private final FriendMessages feedback;

    public FriendCommand(JavaPlugin plugin, FriendService friends, PlayerIdentityService identities,
                         MessageService messages, CommandHelpRenderer helpRenderer, FriendMenu menu) {
        this(friends, identities, messages, helpRenderer,
                Objects.requireNonNull(plugin, "plugin").getServer()::getOnlinePlayers,
                plugin.getServer()::getPlayer, plugin.getLogger(), Objects.requireNonNull(menu, "menu")::open);
    }

    public FriendCommand(FriendService friends, PlayerIdentityService identities,
                         MessageService messages, CommandHelpRenderer helpRenderer,
                         Supplier<? extends Collection<? extends Player>> onlinePlayers,
                         Function<UUID, Player> onlinePlayer, Logger logger, Consumer<Player> openMenu) {
        this.friends = Objects.requireNonNull(friends, "friends");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.helpRenderer = Objects.requireNonNull(helpRenderer, "helpRenderer");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.openMenu = Objects.requireNonNull(openMenu, "openMenu");
        this.feedback = new FriendMessages(messages, onlinePlayer);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "Only players can manage friends.", NamedTextColor.RED);
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "You do not have permission to use this command.", NamedTextColor.RED);
            return true;
        }
        try {
            if (args.length == 0) {
                openMenu.accept(player);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
                helpRenderer.send(sender, HELP);
                return true;
            }
            String action = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 1 && action.equals("list")) {
                listFriends(player);
                return true;
            }
            if (args.length == 1 && action.equals("requests")) {
                listRequests(player);
                return true;
            }
            if (args.length != 2 || !List.of("add", "accept", "deny", "cancel", "remove").contains(action)) {
                send(sender, "Use: /friend help", NamedTextColor.YELLOW);
                return true;
            }
            PlayerLookupResult lookup = identities.resolve(args[1]);
            if (lookup.status() == PlayerLookupStatus.NOT_FOUND) {
                send(sender, "This player is not known to VapeeCore.", NamedTextColor.RED);
                return true;
            }
            if (lookup.status() == PlayerLookupStatus.AMBIGUOUS) {
                send(sender, "That name is ambiguous; use the player's UUID.", NamedTextColor.YELLOW);
                return true;
            }
            PlayerIdentity target = lookup.identity().orElseThrow();
            FriendResult result = switch (action) {
                case "add" -> friends.sendRequest(player.getUniqueId(), target.uniqueId());
                case "accept" -> friends.acceptRequest(player.getUniqueId(), target.uniqueId());
                case "deny" -> friends.denyRequest(player.getUniqueId(), target.uniqueId());
                case "cancel" -> friends.cancelRequest(player.getUniqueId(), target.uniqueId());
                case "remove" -> friends.removeFriend(player.getUniqueId(), target.uniqueId());
                default -> throw new IllegalStateException("Unexpected friend action");
            };
            feedback.report(player, action, target.uniqueId(), target.name(), result);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not process friend command for " + player.getUniqueId() + ".", exception);
            send(sender, "The friends data could not be updated. Check the server log.", NamedTextColor.RED);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        try {
            if (args.length == 1) {
                return matching(SUBCOMMANDS, args[0]);
            }
            if (args.length != 2) {
                return List.of();
            }
            UUID self = player.getUniqueId();
            List<String> names = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> onlinePlayers.get().stream()
                        .filter(online -> !online.getUniqueId().equals(self))
                        .filter(online -> {
                            FriendRelation relation = friends.getRelation(self, online.getUniqueId());
                            return relation != FriendRelation.FRIENDS
                                    && relation != FriendRelation.OUTGOING_REQUEST;
                        })
                        .map(Player::getName).toList();
                case "accept", "deny" -> friends.getIncomingRequests(self).stream()
                        .map(FriendRequest::sender).map(this::knownName).flatMap(java.util.Optional::stream).toList();
                case "cancel" -> friends.getOutgoingRequests(self).stream()
                        .map(FriendRequest::recipient).map(this::knownName)
                        .flatMap(java.util.Optional::stream).toList();
                case "remove" -> friends.getFriends(self).stream()
                        .map(this::knownName).flatMap(java.util.Optional::stream).toList();
                default -> List.of();
            };
            return matching(names, args[1]);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not complete friend command.", exception);
            return List.of();
        }
    }

    private void listFriends(Player player) {
        UUID self = player.getUniqueId();
        List<UUID> ids = friends.getFriends(self);
        send(player, "Friends (" + ids.size() + "/" + friends.getLimits().maxFriends() + ")",
                NamedTextColor.GOLD);
        if (ids.isEmpty()) {
            send(player, "None", NamedTextColor.GRAY);
        } else {
            ids.stream().map(this::displayName).sorted(NAME_ORDER)
                    .forEach(name -> sendName(player, name));
        }
    }

    private void listRequests(Player player) {
        UUID self = player.getUniqueId();
        send(player, "Incoming:", NamedTextColor.GOLD);
        showNames(player, friends.getIncomingRequests(self).stream().map(FriendRequest::sender).toList());
        send(player, "Outgoing:", NamedTextColor.GOLD);
        showNames(player, friends.getOutgoingRequests(self).stream().map(FriendRequest::recipient).toList());
    }

    private void showNames(Player player, List<UUID> ids) {
        if (ids.isEmpty()) {
            send(player, "None", NamedTextColor.GRAY);
        } else {
            ids.stream().map(this::displayName).sorted(NAME_ORDER)
                    .forEach(name -> sendName(player, name));
        }
    }

    private void sendName(Player player, String name) {
        messages.send(player, Component.text("• ", NamedTextColor.GRAY)
                .append(Component.text(name, NamedTextColor.WHITE)));
    }

    private String displayName(UUID uniqueId) {
        return knownName(uniqueId).orElse(uniqueId.toString());
    }

    private java.util.Optional<String> knownName(UUID uniqueId) {
        return identities.findById(uniqueId).map(PlayerIdentity::name);
    }

    private void send(CommandSender sender, String text, NamedTextColor color) {
        messages.send(sender, Component.text(text, color));
    }

    private static List<String> matching(List<String> names, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
                .distinct().sorted(NAME_ORDER).toList();
    }
}

package dev.vapee.core.friend;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.identity.IdentityCommandArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Command and menu feedback for the same friends operations. */
public final class FriendMessages {

    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final Function<UUID, String> commandArgument;

    public FriendMessages(MessageService messages, Function<UUID, Player> onlinePlayer) {
        this(messages, onlinePlayer, UUID::toString);
    }

    public FriendMessages(MessageService messages, Function<UUID, Player> onlinePlayer,
                          Function<UUID, String> commandArgument) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.commandArgument = Objects.requireNonNull(commandArgument, "commandArgument");
    }

    public void report(Player actor, String action, UUID targetId, String targetName, FriendResult result) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(result, "result");
        if (result == FriendResult.SUCCESS) {
            String prefix = switch (action) {
                case "add" -> "Friend request sent to ";
                case "accept" -> "You are now friends with ";
                case "deny" -> "Friend request declined from ";
                case "cancel" -> "Friend request canceled for ";
                case "remove" -> "Removed friend ";
                default -> throw new IllegalArgumentException("Unknown friend action: " + action);
            };
            messages.send(actor, Component.text(prefix, NamedTextColor.GREEN)
                    .append(Component.text(targetName, NamedTextColor.WHITE)));
            if (action.equals("add")) {
                Player target = onlinePlayer.apply(targetId);
                if (target != null && target.isOnline()) {
                    messages.send(target, Component.text(actor.getName(), NamedTextColor.WHITE)
                            .append(Component.text(" sent you a friend request. ", NamedTextColor.GREEN))
                            .append(requestActions(commandArgument.apply(actor.getUniqueId()))));
                }
            } else if (action.equals("accept")) {
                notifyOnline(targetId, actor.getName(), " accepted your friend request.");
            }
            return;
        }
        if (result == FriendResult.AUTO_ACCEPTED) {
            messages.send(actor, Component.text("You are now friends with ", NamedTextColor.GREEN)
                    .append(Component.text(targetName, NamedTextColor.WHITE)));
            notifyOnline(targetId, actor.getName(), " is now your friend.");
            return;
        }
        String message = switch (result) {
            case SELF -> "You cannot add yourself.";
            case ALREADY_FRIENDS -> "You are already friends.";
            case REQUEST_ALREADY_SENT -> "A friend request is already pending.";
            case REQUEST_NOT_FOUND -> "No matching friend request was found.";
            case NOT_FRIENDS -> "You are not friends with that player.";
            case BLOCKED -> "The friend request could not be processed.";
            case REQUESTS_DISABLED -> "This player is not accepting friend requests.";
            case FRIEND_LIMIT_REACHED -> "A friend limit has been reached.";
            case INCOMING_LIMIT_REACHED -> "This player has too many incoming requests.";
            case OUTGOING_LIMIT_REACHED -> "You have too many outgoing requests.";
            default -> throw new IllegalStateException("Unexpected friend result: " + result);
        };
        messages.send(actor, Component.text(message, NamedTextColor.RED));
    }

    public static String commandArgument(PlayerIdentityService identities, UUID id) {
        String name = identities.findById(id).map(PlayerIdentity::name).orElse(null);
        return IdentityCommandArgument.nameOrUuid(id, name,
                candidate -> identities.resolve(candidate).identity().map(PlayerIdentity::uniqueId));
    }

    public static Component requestActions(String argument) {
        return actionButton("accept", argument).append(Component.space()).append(actionButton("deny", argument));
    }

    public static Component actionButton(String action, String argument) {
        String label = Character.toUpperCase(action.charAt(0)) + action.substring(1);
        return Component.text("[" + label + "]", action.equals("accept") ? NamedTextColor.GREEN : NamedTextColor.RED)
                .clickEvent(ClickEvent.suggestCommand("/friend " + action + " " + argument));
    }

    private void notifyOnline(UUID targetId, String actorName, String suffix) {
        Player target = onlinePlayer.apply(targetId);
        if (target != null && target.isOnline()) {
            messages.send(target, Component.text(actorName, NamedTextColor.WHITE)
                    .append(Component.text(suffix, NamedTextColor.GREEN)));
        }
    }
}

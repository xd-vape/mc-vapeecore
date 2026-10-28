package dev.vapee.core.friend;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.PlayerNameFormatter;
import dev.vapee.core.message.SocialChannel;
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
    private final PlayerNameFormatter names;

    public FriendMessages(MessageService messages, Function<UUID, Player> onlinePlayer) {
        this(messages, onlinePlayer, UUID::toString);
    }

    public FriendMessages(MessageService messages, Function<UUID, Player> onlinePlayer,
                          Function<UUID, String> commandArgument) {
        this(messages, onlinePlayer, commandArgument, PlayerNameFormatter.neutral());
    }

    public FriendMessages(MessageService messages, Function<UUID, Player> onlinePlayer,
                          Function<UUID, String> commandArgument, PlayerNameFormatter names) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.names = Objects.requireNonNull(names, "names");
        // Retain the compatible constructor; direct request actions always target the UUID.
        Objects.requireNonNull(commandArgument, "commandArgument");
    }

    public void report(Player actor, String action, UUID targetId, String targetName, FriendResult result) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(result, "result");
        if (result == FriendResult.SUCCESS) {
            String key = switch (action) {
                case "add" -> "friends.request.sent";
                case "accept" -> "friends.request.accepted";
                case "deny" -> "friends.request.denied";
                case "cancel" -> "friends.request.canceled";
                case "remove" -> "friends.removed";
                default -> throw new IllegalArgumentException("Unknown friend action: " + action);
            };
            SocialChannel.FRIENDS.send(messages, actor, tr(key, java.util.Map.of("player", names.format(targetId, targetName))).color(MessageStyle.BODY));
            if (action.equals("add")) {
                Player target = onlinePlayer.apply(targetId);
                if (target != null && target.isOnline()) {
                    SocialChannel.FRIENDS.send(messages, target, tr("friends.request.received", java.util.Map.of("player", names.format(actor.getUniqueId(), actor.getName()))).color(MessageStyle.BODY)
                            .append(requestActions(actor.getUniqueId().toString())));
                }
            } else if (action.equals("accept")) {
                notifyOnline(targetId, actor.getUniqueId(), actor.getName(), "friends.request.accept-notice");
            }
            return;
        }
        if (result == FriendResult.AUTO_ACCEPTED) {
            SocialChannel.FRIENDS.send(messages, actor, tr("friends.request.accepted", java.util.Map.of("player", names.format(targetId, targetName))).color(MessageStyle.BODY));
            notifyOnline(targetId, actor.getUniqueId(), actor.getName(), "friends.request.auto-notice");
            return;
        }
        String messageKey = switch (result) {
            case SELF -> "friends.error.self";
            case ALREADY_FRIENDS -> "friends.error.already-friends";
            case REQUEST_ALREADY_SENT -> "friends.error.request-pending";
            case REQUEST_NOT_FOUND -> "friends.error.request-not-found";
            case NOT_FRIENDS -> "friends.error.not-friends";
            case BLOCKED -> "friends.error.blocked";
            case REQUESTS_DISABLED -> "friends.error.disabled";
            case FRIEND_LIMIT_REACHED -> "friends.error.friend-limit";
            case INCOMING_LIMIT_REACHED -> "friends.error.incoming-limit";
            case OUTGOING_LIMIT_REACHED -> "friends.error.outgoing-limit";
            default -> throw new IllegalStateException("Unexpected friend result: " + result);
        };
        SocialChannel.FRIENDS.error(messages, actor, tr(messageKey));
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
        Component label = tr(switch (action) { case "accept" -> "common.accept"; case "deny" -> "common.deny"; case "cancel" -> "common.cancel"; case "remove" -> "common.remove"; default -> throw new IllegalArgumentException("Unknown friend button action: " + action); });
        boolean direct = action.equals("accept") || action.equals("deny");
        if (direct) {
            UUID id = UUID.fromString(Objects.requireNonNull(argument, "argument"));
            if (!id.toString().equalsIgnoreCase(argument)) {
                throw new IllegalArgumentException("Direct friend actions require a canonical UUID");
            }
            argument = id.toString();
        }
        String command = "/friend " + action + " " + argument;
        return Component.text("[").append(label).append(Component.text("]")).color(action.equals("accept") ? NamedTextColor.GREEN : NamedTextColor.RED)
                .clickEvent(direct ? ClickEvent.runCommand(command) : ClickEvent.suggestCommand(command));
    }

    private void notifyOnline(UUID targetId, UUID actorId, String actorName, String key) {
        Player target = onlinePlayer.apply(targetId);
        if (target != null && target.isOnline()) {
            SocialChannel.FRIENDS.send(messages, target, tr(key, java.util.Map.of("player", names.format(actorId, actorName))).color(MessageStyle.BODY));
        }
    }
}

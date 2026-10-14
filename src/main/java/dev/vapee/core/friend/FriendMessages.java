package dev.vapee.core.friend;

import static dev.vapee.core.i18n.Translations.tr;

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
            String key = switch (action) {
                case "add" -> "friends.request.sent";
                case "accept" -> "friends.request.accepted";
                case "deny" -> "friends.request.denied";
                case "cancel" -> "friends.request.canceled";
                case "remove" -> "friends.removed";
                default -> throw new IllegalArgumentException("Unknown friend action: " + action);
            };
            messages.send(actor, tr(key, java.util.Map.of("player", Component.text(targetName, NamedTextColor.WHITE))).color(NamedTextColor.GREEN));
            if (action.equals("add")) {
                Player target = onlinePlayer.apply(targetId);
                if (target != null && target.isOnline()) {
                    messages.send(target, tr("friends.request.received", java.util.Map.of("player", Component.text(actor.getName(), NamedTextColor.WHITE))).color(NamedTextColor.GREEN)
                            .append(requestActions(commandArgument.apply(actor.getUniqueId()))));
                }
            } else if (action.equals("accept")) {
                notifyOnline(targetId, actor.getName(), "friends.request.accept-notice");
            }
            return;
        }
        if (result == FriendResult.AUTO_ACCEPTED) {
            messages.send(actor, tr("friends.request.accepted", java.util.Map.of("player", Component.text(targetName, NamedTextColor.WHITE))).color(NamedTextColor.GREEN));
            notifyOnline(targetId, actor.getName(), "friends.request.auto-notice");
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
        messages.send(actor, tr(messageKey).color(NamedTextColor.RED));
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
        return Component.text("[").append(label).append(Component.text("]")).color(action.equals("accept") ? NamedTextColor.GREEN : NamedTextColor.RED)
                .clickEvent(ClickEvent.suggestCommand("/friend " + action + " " + argument));
    }

    private void notifyOnline(UUID targetId, String actorName, String key) {
        Player target = onlinePlayer.apply(targetId);
        if (target != null && target.isOnline()) {
            messages.send(target, tr(key, java.util.Map.of("player", Component.text(actorName, NamedTextColor.WHITE))).color(NamedTextColor.GREEN));
        }
    }
}

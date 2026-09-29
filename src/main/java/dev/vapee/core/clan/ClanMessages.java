package dev.vapee.core.clan;

import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Safe, shared command and menu feedback; player and clan names are literal text. */
public final class ClanMessages {
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;

    public ClanMessages(MessageService messages, Function<UUID, Player> onlinePlayer) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
    }

    public void report(Player actor, String action, String target, ClanResult result) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(result, "result");
        if (result == ClanResult.SUCCESS) {
            String prefix = switch (action) {
                case "create" -> "Created clan ";
                case "invite" -> "Invited ";
                case "accept" -> "Joined clan ";
                case "deny" -> "Declined invite from ";
                case "cancel" -> "Canceled invite for ";
                case "leave" -> "Left clan ";
                case "kick" -> "Removed ";
                case "transfer" -> "Transferred ownership to ";
                case "rename" -> "Renamed clan to ";
                case "tag" -> "Changed clan tag to ";
                case "disband" -> "Disbanded clan ";
                default -> throw new IllegalArgumentException("Unknown clan action: " + action);
            };
            messages.send(actor, Component.text(prefix, NamedTextColor.GREEN)
                    .append(Component.text(target, NamedTextColor.WHITE)));
            return;
        }
        String error = switch (result) {
            case ALREADY_IN_CLAN -> "You are already in a clan.";
            case CLAN_NOT_FOUND -> "That clan no longer exists.";
            case NOT_IN_CLAN -> "You are not in a clan.";
            case NOT_OWNER -> "Only the clan owner can do that.";
            case TARGET_NOT_MEMBER -> "That player is not a member of your clan.";
            case TARGET_ALREADY_MEMBER -> "That player is already in a clan.";
            case NAME_INVALID -> "Invalid clan name. Check the configured length and characters.";
            case NAME_ALREADY_USED -> "That clan name is already used.";
            case TAG_INVALID -> "Invalid clan tag. Check the configured length and characters.";
            case TAG_ALREADY_USED -> "That clan tag is already used.";
            case MEMBER_LIMIT_REACHED -> "The clan member limit has been reached.";
            case INVITE_ALREADY_EXISTS -> "An invite for this player already exists.";
            case INVITE_NOT_FOUND -> "That clan invite no longer exists.";
            case OUTGOING_INVITE_LIMIT_REACHED -> "Your clan has too many outgoing invites.";
            case INCOMING_INVITE_LIMIT_REACHED -> "That player has too many incoming invites.";
            case OWNER_CANNOT_LEAVE -> "Transfer ownership or disband the clan first.";
            case CANNOT_TARGET_SELF -> "You cannot target yourself.";
            case SUCCESS -> throw new IllegalStateException("Success handled above");
        };
        messages.send(actor, Component.text(error, NamedTextColor.RED));
    }

    public void notifyOnline(UUID recipient, Component notice) {
        Player player = onlinePlayer.apply(recipient);
        if (player != null && player.isOnline()) messages.send(player, notice);
    }

    public void notifyInvite(UUID recipient, String owner, String clanName, String tag) {
        notifyOnline(recipient, Component.text(owner, NamedTextColor.WHITE)
                .append(Component.text(" invited you to clan ", NamedTextColor.GREEN))
                .append(Component.text(clanName, NamedTextColor.GOLD))
                .append(Component.text(" [", NamedTextColor.GRAY))
                .append(Component.text(tag, NamedTextColor.AQUA))
                .append(Component.text("].", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(inviteButton("Accept", "/clan accept " + tag, NamedTextColor.GREEN,
                        "Click to accept this clan invite."))
                .append(Component.space())
                .append(inviteButton("Deny", "/clan deny " + tag, NamedTextColor.RED,
                        "Click to deny this clan invite.")));
    }

    public void notifyAccept(UUID owner, String playerName) {
        notifyOnline(owner, Component.text(playerName, NamedTextColor.WHITE)
                .append(Component.text(" joined your clan.", NamedTextColor.GREEN)));
    }

    public void notifyKick(UUID target, String tag) {
        notifyOnline(target, Component.text("You were removed from clan ", NamedTextColor.YELLOW)
                .append(Component.text(tag + ".", NamedTextColor.WHITE)));
    }

    public void notifyTransfer(UUID target, String tag) {
        notifyOnline(target, Component.text("You are now the owner of clan ", NamedTextColor.GREEN)
                .append(Component.text(tag + ".", NamedTextColor.WHITE)));
    }

    private Component inviteButton(String label, String command, NamedTextColor color, String hover) {
        return Component.text("[" + label + "]", color)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }
}

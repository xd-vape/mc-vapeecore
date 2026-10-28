package dev.vapee.core.clan;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

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
            String key = switch (action) {
                case "create" -> "clan.success.create";
                case "invite" -> "clan.success.invite";
                case "accept" -> "clan.success.accept";
                case "deny" -> "clan.success.deny";
                case "cancel" -> "clan.success.cancel";
                case "leave" -> "clan.success.leave";
                case "kick" -> "clan.success.kick";
                case "transfer" -> "clan.success.transfer";
                case "rename" -> "clan.success.rename";
                case "tag" -> "clan.success.tag";
                case "disband" -> "clan.success.disband";
                default -> throw new IllegalArgumentException("Unknown clan action: " + action);
            };
            messages.send(actor, tr(key, java.util.Map.of("target", Component.text(target, MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
            return;
        }
        String errorKey = switch (result) {
            case ALREADY_IN_CLAN -> "clan.error.already-in-clan";
            case CLAN_NOT_FOUND -> "clan.error.not-found";
            case NOT_IN_CLAN -> "clan.error.not-in-clan";
            case NOT_OWNER -> "clan.error.not-owner";
            case TARGET_NOT_MEMBER -> "clan.error.target-not-member";
            case TARGET_ALREADY_MEMBER -> "clan.error.target-in-clan";
            case NAME_INVALID -> "clan.error.name-invalid";
            case NAME_ALREADY_USED -> "clan.error.name-used";
            case TAG_INVALID -> "clan.error.tag-invalid";
            case TAG_ALREADY_USED -> "clan.error.tag-used";
            case MEMBER_LIMIT_REACHED -> "clan.error.member-limit";
            case INVITE_ALREADY_EXISTS -> "clan.error.invite-exists";
            case INVITE_NOT_FOUND -> "clan.error.invite-not-found";
            case OUTGOING_INVITE_LIMIT_REACHED -> "clan.error.outgoing-limit";
            case INCOMING_INVITE_LIMIT_REACHED -> "clan.error.incoming-limit";
            case OWNER_CANNOT_LEAVE -> "clan.error.owner-leave";
            case CANNOT_TARGET_SELF -> "clan.error.self";
            case SUCCESS -> throw new IllegalStateException("Success handled above");
        };
        messages.send(actor, tr(errorKey).color(NamedTextColor.RED));
    }

    public void notifyOnline(UUID recipient, Component notice) {
        Player player = onlinePlayer.apply(recipient);
        if (player != null && player.isOnline()) messages.send(player, notice);
    }

    public void notifyInvite(UUID recipient, UUID clanId, String owner, String clanName, String tag) {
        Objects.requireNonNull(clanId, "clanId");
        notifyOnline(recipient, tr("clan.invite.received", java.util.Map.of("owner", Component.text(owner, MessageStyle.HIGHLIGHT),
                "clan", Component.text(clanName, MessageStyle.HIGHLIGHT), "tag", Component.text(tag, NamedTextColor.AQUA))).color(MessageStyle.BODY)
                .append(Component.newline())
                .append(inviteButton(tr("common.accept"), "/clan accept " + clanId, NamedTextColor.GREEN, tr("clan.invite.accept-hover")))
                .append(Component.space())
                .append(inviteButton(tr("common.deny"), "/clan deny " + clanId, NamedTextColor.RED, tr("clan.invite.deny-hover"))));
    }

    public void notifyAccept(UUID owner, String playerName) {
        notifyOnline(owner, tr("clan.invite.accepted", java.util.Map.of("player", Component.text(playerName, MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
    }

    public void notifyKick(UUID target, String tag) {
        notifyOnline(target, tr("clan.member.removed", java.util.Map.of("tag", Component.text(tag, MessageStyle.HIGHLIGHT))).color(NamedTextColor.YELLOW));
    }

    public void notifyTransfer(UUID target, String tag) {
        notifyOnline(target, tr("clan.owner.transferred", java.util.Map.of("tag", Component.text(tag, MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
    }

    private Component inviteButton(Component label, String command, NamedTextColor color, Component hover) {
        return Component.text("[").append(label).append(Component.text("]")).color(color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(hover.color(NamedTextColor.GRAY)));
    }
}

package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.identity.PlayerLookupStatus;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.moderation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Local adapter dependencies, not a second moderation state or a global command framework. */
public record ModerationCommandContext(ModerationService service, PlayerIdentityService identities,
                                       MessageService messages, Function<UUID, Player> onlinePlayer,
                                       Supplier<? extends Collection<? extends Player>> onlinePlayers,
                                       Clock clock, Logger logger) {
    public ModerationCommandContext {
        Objects.requireNonNull(service); Objects.requireNonNull(identities); Objects.requireNonNull(messages);
        Objects.requireNonNull(onlinePlayer); Objects.requireNonNull(onlinePlayers);
        Objects.requireNonNull(clock); Objects.requireNonNull(logger);
    }

    public void send(CommandSender sender, String text) {
        messages.send(sender, Component.text(text, NamedTextColor.YELLOW));
    }

    Player online(UUID id) {
        Player player = onlinePlayer.apply(id);
        return player != null && player.isOnline() ? player : null;
    }

    String actorName(ModerationActor actor) {
        return actor.playerId().map(id -> identities.findById(id).map(PlayerIdentity::name).orElse(id.toString()))
                .orElse("Console");
    }

    List<String> targets(CommandSender sender, boolean history, boolean bans, String prefix) {
        var now = clock.instant();
        var ids = bans ? service.getAllRecords().stream()
                .filter(record -> record.action() == ModerationAction.BAN && record.isActiveAt(now))
                .map(ModerationRecord::targetId).distinct().toList()
                : onlinePlayers.get().stream().filter(Player::isOnline).map(Player::getUniqueId).distinct().toList();
        return ids.stream().filter(id -> history || !(sender instanceof Player player) || !id.equals(player.getUniqueId()))
                .map(id -> safeArgument(id)).distinct()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Function.identity())).toList();
    }

    private String safeArgument(UUID id) {
        var identity = identities.findById(id);
        if (identity.isEmpty()) return id.toString();
        String name = identity.get().name();
        if (name.codePoints().anyMatch(point -> Character.isWhitespace(point) || Character.isISOControl(point))) return id.toString();
        var lookup = identities.resolve(name);
        return lookup.status() == PlayerLookupStatus.FOUND && lookup.identity().orElseThrow().uniqueId().equals(id)
                ? name : id.toString();
    }

    void audit(String action, ModerationActor actor, PlayerIdentity target, ModerationRecord record) {
        logger.info("Moderation [actor=" + actor.type() + actor.playerId().map(id -> ":" + id).orElse("")
                + ", target=" + target.uniqueId() + ", name=" + safeLog(target.name()) + ", action=" + action
                + ", record=" + record.id() + ", expiry=" + record.expiresAt().map(Object::toString)
                .orElse(record.action().supportsActiveState() ? "Permanent" : "Not applicable") + "]");
    }

    void failure(String action, ModerationActor actor, String target, RuntimeException exception) {
        logger.log(Level.SEVERE, "Moderation failed [actor=" + actor + ", target=" + safeLog(target)
                + ", action=" + action + "]", exception);
    }

    private static String safeLog(String text) {
        return text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "?");
    }

    void disconnect(CommandSender sender, ModerationActor actor, PlayerIdentity target,
                    ModerationRecord record, boolean ban) {
        try {
            Player player = online(target.uniqueId());
            if (player != null) player.kick(ban ? ModerationComponents.banScreen(record) : ModerationComponents.kickScreen(record));
        } catch (RuntimeException exception) {
            failure(ban ? "BAN_DISCONNECT" : "KICK_DISCONNECT", actor, target.uniqueId().toString(), exception);
            send(sender, (ban ? "Ban" : "Kick record") + " was saved, but the online player could not be disconnected.");
        }
    }
}

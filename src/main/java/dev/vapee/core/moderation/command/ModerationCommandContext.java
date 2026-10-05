package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.identity.IdentityCommandArgument;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.moderation.*;
import dev.vapee.core.rank.staff.StaffHierarchyService;
import dev.vapee.core.rank.staff.StaffTargetDecision;
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
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Local adapter dependencies, not a second moderation state or a global command framework. */
public record ModerationCommandContext(ModerationService service, PlayerIdentityService identities,
                                       MessageService messages, Function<UUID, Player> onlinePlayer,
                                       Supplier<? extends Collection<? extends Player>> onlinePlayers,
                                       Clock clock, Logger logger, StaffHierarchyService hierarchy,
                                       Consumer<Runnable> mainThread, BooleanSupplier active) {
    public ModerationCommandContext {
        Objects.requireNonNull(service); Objects.requireNonNull(identities); Objects.requireNonNull(messages);
        Objects.requireNonNull(onlinePlayer); Objects.requireNonNull(onlinePlayers);
        Objects.requireNonNull(clock); Objects.requireNonNull(logger);
        Objects.requireNonNull(hierarchy); Objects.requireNonNull(mainThread); Objects.requireNonNull(active);
    }

    /** Per-enable lifecycle binding; old pending commands cannot survive disable/re-enable. */
    public ModerationCommandContext withRuntime(Consumer<Runnable> scheduler, BooleanSupplier enabled) {
        return new ModerationCommandContext(service, identities, messages, onlinePlayer, onlinePlayers,
                clock, logger, hierarchy, scheduler, enabled);
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

    List<String> targets(CommandSender sender, boolean history, ModerationAction activeAction, String prefix) {
        var now = clock.instant();
        var ids = activeAction != null ? service.getAllRecords().stream()
                .filter(record -> record.action() == activeAction && record.isActiveAt(now))
                .map(ModerationRecord::targetId).distinct().toList()
                : onlinePlayers.get().stream().filter(Player::isOnline).map(Player::getUniqueId).distinct().toList();
        return ids.stream().filter(id -> history || !(sender instanceof Player player) || !id.equals(player.getUniqueId()))
                .filter(id -> suggestible(sender, id, history))
                .map(id -> safeArgument(id)).distinct()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Function.identity())).toList();
    }

    private boolean suggestible(CommandSender sender, UUID targetId, boolean history) {
        if (!(sender instanceof Player actor)) return true;
        if (history && targetId.equals(actor.getUniqueId())) return true;
        // Keep committed offline unban/unmute candidates. Completion never initiates LP loads.
        if (online(targetId) == null) return true;
        return hierarchy.decideLoaded(actor.getUniqueId(), targetId) == StaffTargetDecision.ALLOW;
    }

    private String safeArgument(UUID id) {
        String name = identities.findById(id).map(PlayerIdentity::name).orElse(null);
        return IdentityCommandArgument.nameOrUuid(id, name,
                candidate -> identities.resolve(candidate).identity().map(PlayerIdentity::uniqueId));
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

    void notifyMute(CommandSender sender, ModerationActor actor, PlayerIdentity target,
                    ModerationRecord record, boolean removed) {
        try {
            Player player = online(target.uniqueId());
            if (player != null) messages.send(player, removed ? ModerationComponents.unmuteNotice(record)
                    : ModerationComponents.muteNotice(record));
        } catch (RuntimeException exception) {
            failure(removed ? "UNMUTE_NOTIFY" : "MUTE_NOTIFY", actor, target.uniqueId().toString(), exception);
            send(sender, (removed ? "Unmute" : "Mute") + " was saved, but the online player could not be notified.");
        }
    }
}

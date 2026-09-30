package dev.vapee.core.moderation.command;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerLookupStatus;
import dev.vapee.core.moderation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Only the shared local permission/actor/identity/error boundary; no generic dispatcher. */
abstract class AbstractModerationCommand implements TabExecutor {
    protected final ModerationCommandContext context;
    private final String name;
    private final String syntax;

    AbstractModerationCommand(ModerationCommandContext context, String name, String syntax) {
        this.context = Objects.requireNonNull(context);
        this.name = name;
        this.syntax = "/" + name + " " + syntax;
    }

    @Override public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!context.active().getAsBoolean()) return true;
        if (!sender.hasPermission("vapeecore.moderation." + name)) {
            context.send(sender, "You do not have permission to use this command.");
            return true;
        }
        ModerationActor actor = actor(sender);
        if (actor == null) {
            context.send(sender, "Only players and the server console can use this command.");
            return true;
        }
        if (!validArguments(sender, args)) return true;
        PlayerIdentity target = null;
        try {
            var lookup = context.identities().resolve(args[0]);
            if (lookup.status() != PlayerLookupStatus.FOUND) {
                context.send(sender, lookup.status() == PlayerLookupStatus.AMBIGUOUS
                        ? "That name is ambiguous. Use the player's UUID." : "This player is not known to VapeeCore.");
                return true;
            }
            target = lookup.identity().orElseThrow();
            if (!name.equals("history") && actor.playerId().filter(target.uniqueId()::equals).isPresent()) {
                context.send(sender, "You cannot moderate yourself.");
                return true;
            }
            authorize(sender, actor, target, args.clone());
        } catch (RuntimeException exception) {
            context.failure(name.toUpperCase(java.util.Locale.ROOT), actor,
                    target == null ? args[0] : target.uniqueId().toString(), exception);
            context.send(sender, "The moderation operation could not be completed. Check the server log.");
        }
        return true;
    }

    private void authorize(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args) {
        if (sender instanceof ConsoleCommandSender
                || (name.equals("history") && actor.playerId().filter(target.uniqueId()::equals).isPresent())) {
            execute(sender, actor, target, args);
            return;
        }
        Optional<String> loaded = context.hierarchy().getLoadedPrimaryGroup(target.uniqueId());
        if (loaded.isPresent()) {
            authorizedExecute(sender, actor, target, args, loaded);
            return;
        }
        // The completion callback only submits immutable result data. All Bukkit/domain work is deferred.
        context.hierarchy().loadPrimaryGroup(target.uniqueId()).whenComplete((group, failure) -> {
            if (!context.active().getAsBoolean()) return;
            try {
                context.mainThread().accept(() -> resume(sender, actor, target, args, group, failure));
            } catch (RuntimeException schedulingFailure) {
                context.failure(name.toUpperCase(java.util.Locale.ROOT) + "_HIERARCHY_SCHEDULE",
                        actor, target.uniqueId().toString(), schedulingFailure);
            }
        });
    }

    private void resume(CommandSender sender, ModerationActor actor, PlayerIdentity target,
                        String[] args, Optional<String> group, Throwable failure) {
        if (!context.active().getAsBoolean()) return;
        try {
            Player player = (Player) sender;
            // Require the original live session, not merely a reconnect with the same UUID.
            if (!player.isOnline() || context.online(actor.playerId().orElseThrow()) != player) return;
            if (!sender.hasPermission("vapeecore.moderation." + name)) {
                context.send(sender, "You do not have permission to use this command.");
                return;
            }
            if (failure != null) throw new IllegalStateException("LuckPerms primary group load failed", failure);
            authorizedExecute(sender, actor, target, args, group);
        } catch (RuntimeException exception) {
            context.failure(name.toUpperCase(java.util.Locale.ROOT) + "_HIERARCHY",
                    actor, target.uniqueId().toString(), exception);
            context.send(sender, "The moderation operation could not be completed. Check the server log.");
        }
    }

    private void authorizedExecute(CommandSender sender, ModerationActor actor, PlayerIdentity target,
                                   String[] args, Optional<String> targetGroup) {
        switch (context.hierarchy().decide(actor.playerId().orElseThrow(), targetGroup)) {
            case ALLOW -> execute(sender, actor, target, args);
            case DENY_SAME_OR_HIGHER -> context.send(sender, "You cannot target a staff member at your level or above.");
            case DENY_ACTOR_NOT_PROTECTED -> context.send(sender, "You cannot target a protected staff member.");
            case UNAVAILABLE -> throw new IllegalStateException("Staff hierarchy primary group unavailable");
        }
    }

    protected abstract boolean validArguments(CommandSender sender, String[] args);
    protected abstract void execute(CommandSender sender, ModerationActor actor, PlayerIdentity target, String[] args);

    protected final void usage(CommandSender sender, String error) {
        context.messages().send(sender, Component.text(error, NamedTextColor.RED).append(Component.newline())
                .append(Component.text("Usage: " + syntax, NamedTextColor.AQUA)));
    }

    protected final String reason(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    protected final ModerationRecord mutate(CommandSender sender, ModerationActor actor, PlayerIdentity target,
                                             Supplier<ModerationResult> operation) {
        ModerationResult result;
        try { result = operation.get(); }
        catch (ModerationRepositoryException exception) {
            context.failure(name.toUpperCase(java.util.Locale.ROOT), actor, target.uniqueId().toString(), exception);
            context.send(sender, "The moderation change could not be saved. No action was applied.");
            return null;
        } catch (IllegalArgumentException exception) {
            context.send(sender, "Invalid reason or expiry: use 1–256 characters without line breaks or control characters, and a future expiry.");
            return null;
        }
        if (result.status() != ModerationStatus.SUCCESS) {
            context.send(sender, switch (result.status()) {
                case ALREADY_BANNED -> "That player is already banned.";
                case NOT_BANNED -> "That player is not currently banned.";
                case ALREADY_MUTED -> "That player is already muted.";
                case NOT_MUTED -> "That player is not currently muted.";
                default -> throw new IllegalStateException("Unexpected moderation status: " + result.status());
            });
            return null;
        }
        ModerationRecord record = result.record().orElseThrow();
        context.audit(name.toUpperCase(java.util.Locale.ROOT), actor, target, record);
        return record;
    }

    private ModerationActor actor(CommandSender sender) {
        if (sender instanceof Player player) return ModerationActor.player(player.getUniqueId());
        if (sender instanceof ConsoleCommandSender) return ModerationActor.console();
        return null;
    }

    @Override public final List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!context.active().getAsBoolean()) return List.of();
        if (!sender.hasPermission("vapeecore.moderation." + name) || actor(sender) == null) return List.of();
        try {
            if (args.length == 1) return context.targets(sender, name.equals("history"),
                    name.equals("unban") ? ModerationAction.BAN : name.equals("unmute") ? ModerationAction.MUTE : null, args[0]);
            return complete(args);
        } catch (RuntimeException exception) {
            context.failure(name.toUpperCase(java.util.Locale.ROOT) + "_COMPLETION", actor(sender), "completion", exception);
            return List.of();
        }
    }

    protected List<String> complete(String[] args) { return List.of(); }
}

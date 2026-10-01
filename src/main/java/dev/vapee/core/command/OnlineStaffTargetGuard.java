package dev.vapee.core.command;

import dev.vapee.core.rank.staff.StaffHierarchyService;
import dev.vapee.core.rank.staff.StaffTargetDecision;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bukkit application boundary for synchronous, loaded-only online staff target checks. */
public final class OnlineStaffTargetGuard {
    public static final String DENIED_MESSAGE = "You cannot target a staff member at your level or above.";
    public static final String UNAVAILABLE_MESSAGE =
            "The target's staff hierarchy could not be verified. No action was applied.";

    private final StaffHierarchyService hierarchy;
    private final Logger logger;

    public OnlineStaffTargetGuard(StaffHierarchyService hierarchy, Logger logger) {
        this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Retains UNAVAILABLE as distinct from a normal policy denial; never loads an LP user. */
    public StaffTargetDecision check(CommandSender actor, Player target, String action) {
        Objects.requireNonNull(action, "action");
        StaffTargetDecision decision;
        try {
            decision = decide(actor, target);
        } catch (RuntimeException exception) {
            warn(actor, target, action, exception);
            return StaffTargetDecision.UNAVAILABLE;
        }
        if (decision == StaffTargetDecision.UNAVAILABLE) warn(actor, target, action, null);
        return decision;
    }

    /** Capability gates and exact target resolution remain the command's responsibility. */
    public boolean authorize(CommandSender actor, Player target, String action,
                             BiConsumer<CommandSender, Component> feedback) {
        StaffTargetDecision decision = check(actor, target, action);
        if (decision == StaffTargetDecision.ALLOW) return true;
        feedback.accept(actor, Component.text(decision == StaffTargetDecision.UNAVAILABLE
                ? UNAVAILABLE_MESSAGE : DENIED_MESSAGE, NamedTextColor.RED));
        return false;
    }

    /** Completion is silent, read-only and fail-closed; no warning spam while typing. */
    public boolean canSuggest(CommandSender actor, Player target) {
        try {
            return target != null && target.isOnline() && decide(actor, target) == StaffTargetDecision.ALLOW;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public List<Player> suggestiblePlayers(CommandSender actor, Collection<? extends Player> players) {
        return players.stream().filter(player -> canSuggest(actor, player)).map(player -> (Player) player).toList();
    }

    private StaffTargetDecision decide(CommandSender actor, Player target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (actor instanceof Player player) {
            if (player.getUniqueId().equals(target.getUniqueId())) return StaffTargetDecision.ALLOW;
            return hierarchy.decideLoaded(player.getUniqueId(), target.getUniqueId());
        }
        if (actor instanceof ConsoleCommandSender) return StaffTargetDecision.ALLOW;
        return StaffTargetDecision.UNAVAILABLE;
    }

    private void warn(CommandSender actor, Player target, String action, RuntimeException failure) {
        String actorId = actor instanceof Player player ? player.getUniqueId().toString()
                : actor instanceof ConsoleCommandSender ? "CONSOLE" : "UNSUPPORTED";
        logger.log(Level.WARNING, "Online staff target hierarchy unavailable: actor=" + actorId
                + ", target=" + target.getUniqueId() + ", action=" + action, failure);
    }
}

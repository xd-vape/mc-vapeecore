package dev.vapee.core.utility.command;

import dev.vapee.core.message.MessageStyle;
import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.command.OnlineStaffTargetGuard;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.OnlinePlayerResolver;
import dev.vapee.core.utility.UtilityService;
import dev.vapee.core.rank.staff.StaffTargetDecision;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EnderChestCommand implements TabExecutor, Listener {

    public static final String PERMISSION = "vapeecore.utility.enderchest";
    public static final String OTHERS_PERMISSION = "vapeecore.utility.enderchest.others";

    private final OnlineStaffTargetGuard staffTargetGuard;
    private final UtilityService utilityService;
    private final Function<String, Player> playerLookup;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;
    private final BiConsumer<CommandSender, Component> messageSender;
    private final Consumer<Runnable> nextTick;
    private final Map<UUID, ForeignView> foreignViews = new HashMap<>();
    private boolean active = true;

    public EnderChestCommand(JavaPlugin plugin, UtilityService utilityService, MessageService messageService,
                            OnlineStaffTargetGuard staffTargetGuard) {
        this(
                utilityService,
                new OnlinePlayerResolver(
                        () -> Objects.requireNonNull(plugin, "plugin").getServer().getOnlinePlayers()
                )::resolveExact,
                () -> plugin.getServer().getOnlinePlayers(),
                Objects.requireNonNull(messageService, "messageService")::send,
                staffTargetGuard,
                task -> plugin.getServer().getScheduler().runTask(plugin, task)
        );
    }

    EnderChestCommand(
            UtilityService utilityService,
            Function<String, Player> playerLookup,
            Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
            BiConsumer<CommandSender, Component> messageSender,
            OnlineStaffTargetGuard staffTargetGuard
    ) {
        this(utilityService, playerLookup, onlinePlayersSupplier, messageSender, staffTargetGuard, Runnable::run);
    }

    EnderChestCommand(UtilityService utilityService, Function<String, Player> playerLookup,
                      Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier,
                      BiConsumer<CommandSender, Component> messageSender,
                      OnlineStaffTargetGuard staffTargetGuard, Consumer<Runnable> nextTick) {
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        this.staffTargetGuard = Objects.requireNonNull(staffTargetGuard, "staffTargetGuard");
        this.nextTick = Objects.requireNonNull(nextTick, "nextTick");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!active) return true;
        if (args.length > 1) {
            invalidUsage(sender);
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            error(sender, tr("common.inventory-player-only"));
            return true;
        }

        Player owner;
        if (args.length == 0) {
            if (!sender.hasPermission(PERMISSION)) {
                error(sender, tr("utility.enderchest.denied-self"));
                return true;
            }
            owner = viewer;
        } else {
            owner = playerLookup.apply(args[0]);
            if (owner == null) {
                playerNotOnline(sender, args[0]);
                return true;
            }
            boolean self = viewer.getUniqueId().equals(owner.getUniqueId());
            if (!sender.hasPermission(self ? PERMISSION : OTHERS_PERMISSION)) {
                error(sender, self
                        ? tr("utility.enderchest.denied-self")
                        : tr("utility.enderchest.denied-other"));
                return true;
            }
        }

        if (!staffTargetGuard.authorize(sender, owner, "enderchest", messageSender)) return true;

        InventoryView opened = utilityService.openEnderChest(viewer, owner);
        if (!viewer.getUniqueId().equals(owner.getUniqueId())) {
            // A cancelled/replaced InventoryOpenEvent must not issue access to a different view.
            if (opened != null && opened == viewer.getOpenInventory()
                    && opened.getTopInventory().equals(owner.getEnderChest())) {
                foreignViews.put(viewer.getUniqueId(), new ForeignView(viewer, owner, opened));
            }
            messageSender.accept(sender, tr("utility.enderchest.opened", java.util.Map.of("player", Component.text(owner.getName(), MessageStyle.HIGHLIGHT))).color(MessageStyle.BODY));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (denyInteraction(event.getView())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (denyInteraction(event.getView())) event.setCancelled(true);
    }

    private boolean denyInteraction(InventoryView view) {
        ForeignView access = foreignViews.get(view.getPlayer().getUniqueId());
        if (access == null || !access.matches(view)) return false;
        if (!access.matches(access.viewer.getOpenInventory())) {
            foreignViews.remove(access.viewerId, access);
            return false;
        }
        if (!access.revoked && authorized(access)) return false;
        revoke(access);
        return true; // All slots/routes, including bottom inventory and outside clicks.
    }

    private boolean authorized(ForeignView access) {
        return active && access.viewer.isOnline() && access.owner.isOnline()
                && currentSession(access.viewerId) == access.viewer
                && currentSession(access.ownerId) == access.owner
                && access.viewer.hasPermission(OTHERS_PERMISSION)
                && staffTargetGuard.check(access.viewer, access.owner, "enderchest") == StaffTargetDecision.ALLOW;
    }

    private Player currentSession(UUID id) {
        return onlinePlayersSupplier.get().stream().filter(p -> p != null && p.getUniqueId().equals(id))
                .findFirst().orElse(null);
    }

    private void revoke(ForeignView access) {
        if (access.revoked) return;
        access.revoked = true;
        // Keep the revoked lease until close, denying further packets in this same tick.
        nextTick.accept(() -> {
            if (active && foreignViews.get(access.viewerId) == access) release(access);
        });
    }

    private void release(ForeignView access) {
        if (!foreignViews.remove(access.viewerId, access)) return;
        if (access.viewer.isOnline() && access.matches(access.viewer.getOpenInventory())) access.viewer.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        ForeignView access = foreignViews.get(event.getPlayer().getUniqueId());
        if (access != null && access.matches(event.getView())) foreignViews.remove(access.viewerId, access);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        for (ForeignView access : List.copyOf(foreignViews.values())) {
            if (access.viewer == leaving) foreignViews.remove(access.viewerId, access);
            else if (access.owner == leaving) revoke(access);
        }
    }

    public void disable() {
        active = false;
        RuntimeException failure = null;
        for (ForeignView access : List.copyOf(foreignViews.values())) {
            try { release(access); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private static final class ForeignView {
        final Player viewer;
        final Player owner;
        final UUID viewerId;
        final UUID ownerId;
        final InventoryView view;
        final Inventory inventory;
        boolean revoked;

        ForeignView(Player viewer, Player owner, InventoryView view) {
            this.viewer = viewer;
            this.owner = owner;
            this.viewerId = viewer.getUniqueId();
            this.ownerId = owner.getUniqueId();
            this.view = view;
            this.inventory = view.getTopInventory();
        }

        boolean matches(InventoryView candidate) {
            return candidate == view && candidate.getPlayer() == viewer && candidate.getTopInventory() == inventory;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player) || args.length != 1 || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        return new OnlinePlayerResolver(
                () -> staffTargetGuard.suggestiblePlayers(sender, onlinePlayersSupplier.get())).suggest(args[0], null);
    }

    private void invalidUsage(CommandSender sender) {
        messageSender.accept(sender, tr("common.invalid-usage").color(NamedTextColor.RED)
                .append(Component.newline())
                .append(tr("common.use").color(NamedTextColor.YELLOW))
                .append(Component.text("/enderchest [player]", NamedTextColor.AQUA)));
    }

    private void playerNotOnline(CommandSender sender, String name) {
        messageSender.accept(sender, tr("common.player-offline", java.util.Map.of("player", Component.text(name, MessageStyle.HIGHLIGHT))).color(NamedTextColor.RED));
    }

    private void error(CommandSender sender, Component message) {
        messageSender.accept(sender, message.color(NamedTextColor.RED));
    }
}

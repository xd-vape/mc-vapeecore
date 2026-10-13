package dev.vapee.core.utility.command;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.utility.UtilityService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BuildCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.build";

    private final Logger logger;
    private final Predicate<Player> lobbyWorldCheck;
    private final BuildStateAccess buildStateAccess;
    private final Predicate<UUID> activityCheck;
    private final Consumer<Player> managedFlightCleanup;
    private final BiConsumer<CommandSender, net.kyori.adventure.text.Component> messageSender;

    public BuildCommand(
            JavaPlugin plugin,
            LobbyService lobbyService,
            LobbyPlayerStateService lobbyPlayerStateService,
            ActivityService activityService,
            UtilityService utilityService,
            MessageService messageService
    ) {
        this(
                Objects.requireNonNull(plugin, "plugin").getLogger(),
                lobbyWorldCheck(lobbyService),
                buildStateAccess(lobbyPlayerStateService),
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(utilityService, "utilityService")::clearManagedFlight,
                Objects.requireNonNull(messageService, "messageService")::send
        );
    }

    BuildCommand(
            Logger logger,
            Predicate<Player> lobbyWorldCheck,
            BuildStateAccess buildStateAccess,
            Predicate<UUID> activityCheck,
            Consumer<Player> managedFlightCleanup,
            BiConsumer<CommandSender, net.kyori.adventure.text.Component> messageSender
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.lobbyWorldCheck = Objects.requireNonNull(lobbyWorldCheck, "lobbyWorldCheck");
        this.buildStateAccess = Objects.requireNonNull(buildStateAccess, "buildStateAccess");
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
        this.managedFlightCleanup = Objects.requireNonNull(managedFlightCleanup, "managedFlightCleanup");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            send(sender, tr("utility.build.player-only"));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            send(player, tr("utility.build.denied"));
            return true;
        }
        if (args.length != 0) {
            send(player, tr("utility.build.usage"));
            return true;
        }

        try {
            if (buildStateAccess.isBuildMode(player.getUniqueId())) {
                buildStateAccess.exitBuildMode(player);
                send(player, tr("utility.build.disabled"));
                return true;
            }
            if (!lobbyWorldCheck.test(player)) {
                send(player, tr("utility.build.world"));
                return true;
            }
            if (activityCheck.test(player.getUniqueId())) {
                send(
                        player,
                        tr("utility.build.activity")
                );
                return true;
            }

            managedFlightCleanup.accept(player);
            buildStateAccess.enterBuildMode(player);
            send(player, tr("utility.build.enabled"));
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE,
                    "Could not toggle lobby build mode for " + player.getUniqueId() + ".",
                    exception
            );
            send(player, tr("utility.build.failed"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        return List.of();
    }

    private void send(CommandSender sender, net.kyori.adventure.text.Component message) {
        messageSender.accept(sender, message);
    }

    private static Predicate<Player> lobbyWorldCheck(LobbyService lobbyService) {
        LobbyService validatedService = Objects.requireNonNull(lobbyService, "lobbyService");
        return player -> validatedService.isLobbyWorld(player.getWorld());
    }

    private static BuildStateAccess buildStateAccess(LobbyPlayerStateService lobbyPlayerStateService) {
        LobbyPlayerStateService validatedService = Objects.requireNonNull(
                lobbyPlayerStateService,
                "lobbyPlayerStateService"
        );
        return new BuildStateAccess() {
            @Override
            public boolean isBuildMode(UUID playerId) {
                return validatedService.isBuildMode(playerId);
            }

            @Override
            public void enterBuildMode(Player player) {
                validatedService.enterBuildMode(player);
            }

            @Override
            public void exitBuildMode(Player player) {
                validatedService.exitBuildMode(player);
            }
        };
    }

    interface BuildStateAccess {

        boolean isBuildMode(UUID playerId);

        void enterBuildMode(Player player);

        void exitBuildMode(Player player);
    }
}

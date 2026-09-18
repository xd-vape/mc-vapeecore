package dev.vapee.core.utility.command;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
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
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BuildCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.utility.build";

    private final Logger logger;
    private final Predicate<Player> lobbyWorldCheck;
    private final BuildStateAccess buildStateAccess;
    private final Predicate<UUID> activityCheck;
    private final BiConsumer<CommandSender, String> messageSender;

    public BuildCommand(
            JavaPlugin plugin,
            LobbyService lobbyService,
            LobbyPlayerStateService lobbyPlayerStateService,
            ActivityService activityService,
            MessageService messageService
    ) {
        this(
                Objects.requireNonNull(plugin, "plugin").getLogger(),
                lobbyWorldCheck(lobbyService),
                buildStateAccess(lobbyPlayerStateService),
                Objects.requireNonNull(activityService, "activityService")::isParticipating,
                Objects.requireNonNull(messageService, "messageService")::send
        );
    }

    BuildCommand(
            Logger logger,
            Predicate<Player> lobbyWorldCheck,
            BuildStateAccess buildStateAccess,
            Predicate<UUID> activityCheck,
            BiConsumer<CommandSender, String> messageSender
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.lobbyWorldCheck = Objects.requireNonNull(lobbyWorldCheck, "lobbyWorldCheck");
        this.buildStateAccess = Objects.requireNonNull(buildStateAccess, "buildStateAccess");
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
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
            send(sender, "<red>This command can only be used by a player.</red>");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            send(player, "<red>You do not have permission to use build mode.</red>");
            return true;
        }
        if (args.length != 0) {
            send(player, "<red>Invalid usage.</red>\n<yellow>Use:</yellow> <aqua>/build</aqua>");
            return true;
        }

        try {
            if (buildStateAccess.isBuildMode(player.getUniqueId())) {
                buildStateAccess.exitBuildMode(player);
                send(player, "<green>Build mode disabled.</green>");
                return true;
            }
            if (!lobbyWorldCheck.test(player)) {
                send(player, "<red>Build mode can only be enabled in the lobby world.</red>");
                return true;
            }
            if (activityCheck.test(player.getUniqueId())) {
                send(
                        player,
                        "<red>You cannot enter build mode while participating in an activity.</red>"
                );
                return true;
            }

            buildStateAccess.enterBuildMode(player);
            send(player, "<green>Build mode enabled.</green>");
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE,
                    "Could not toggle lobby build mode for " + player.getUniqueId() + ".",
                    exception
            );
            send(player, "<red>Build mode could not be changed. Check the server log.</red>");
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

    private void send(CommandSender sender, String message) {
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

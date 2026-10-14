package dev.vapee.core.settings.command;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.identity.IdentityCommandArgument;
import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.identity.PlayerLookupResult;
import dev.vapee.core.identity.PlayerLookupStatus;
import dev.vapee.core.player.settings.AddedVisiblePlayerResult;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.settings.SettingsMenu;
import dev.vapee.core.settings.visibility.VisibilitySettingsMenu;
import dev.vapee.core.visibility.VisibilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SettingsCommand implements TabExecutor {

    public static final String PERMISSION = "vapeecore.settings.use";
    private static final Comparator<String> NAME_ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(
            Comparator.naturalOrder());
    private final Consumer<Player> settingsMenuOpener;
    private final Consumer<Player> visibilityMenuOpener;
    private final PlayerSettingsService settings;
    private final PlayerIdentityService identities;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;
    private final Consumer<Player> applyVisibility;
    private final MessageService messageService;
    private final Logger logger;

    public SettingsCommand(JavaPlugin plugin, SettingsMenu settingsMenu, VisibilitySettingsMenu visibilityMenu,
                           PlayerSettingsService settings, PlayerIdentityService identities,
                           VisibilityService visibilityService, MessageService messageService) {
        this(Objects.requireNonNull(settingsMenu, "settingsMenu")::open,
                Objects.requireNonNull(visibilityMenu, "visibilityMenu")::open, settings, identities,
                Objects.requireNonNull(plugin, "plugin").getServer()::getOnlinePlayers,
                Objects.requireNonNull(visibilityService, "visibilityService")::applyViewerPreference,
                messageService, plugin.getLogger());
    }

    public SettingsCommand(Consumer<Player> settingsMenuOpener, Consumer<Player> visibilityMenuOpener,
                           PlayerSettingsService settings, PlayerIdentityService identities,
                           Supplier<? extends Collection<? extends Player>> onlinePlayers,
                           Consumer<Player> applyVisibility, MessageService messageService, Logger logger) {
        this.settingsMenuOpener = Objects.requireNonNull(settingsMenuOpener, "settingsMenuOpener");
        this.visibilityMenuOpener = Objects.requireNonNull(visibilityMenuOpener, "visibilityMenuOpener");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        this.applyVisibility = Objects.requireNonNull(applyVisibility, "applyVisibility");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, tr("settings.command.player-only"));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            messageService.send(player, tr("common.command-denied").color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            settingsMenuOpener.accept(player);
            return true;
        }
        if (!args[0].equalsIgnoreCase("visibility")) {
            sendUsage(player, "/settings visibility");
            return true;
        }
        if (args.length == 1) {
            visibilityMenuOpener.accept(player);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("add")) {
            if (args.length != 3) sendUsage(player, "/settings visibility add <player|uuid>");
            else add(player, args[2]);
            return true;
        }
        if (action.equals("remove")) {
            if (args.length != 3) sendUsage(player, "/settings visibility remove <player|uuid>");
            else remove(player, args[2]);
            return true;
        }
        sendUsage(player, "/settings visibility add <player|uuid>");
        sendUsage(player, "/settings visibility remove <player|uuid>");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return matching(List.of("visibility"), args[0]);
        if (!args[0].equalsIgnoreCase("visibility")) return List.of();
        if (args.length == 2) return matching(List.of("add", "remove"), args[1]);
        if (args.length != 3) return List.of();
        if (args[1].equalsIgnoreCase("add")) {
            Set<UUID> added = settings.getLobbyAddedVisiblePlayers(player.getUniqueId()).orElse(Set.of());
            return matching(onlinePlayers.get().stream()
                    .filter(Player::isOnline)
                    .filter(candidate -> !candidate.getUniqueId().equals(player.getUniqueId()))
                    .filter(candidate -> !added.contains(candidate.getUniqueId()))
                    .map(Player::getName).toList(), args[2]);
        }
        if (args[1].equalsIgnoreCase("remove")) {
            List<String> targets = settings.getLobbyAddedVisiblePlayers(player.getUniqueId()).orElse(Set.of())
                    .stream().map(this::identityCommandArgument).toList();
            return matching(targets, args[2]);
        }
        return List.of();
    }

    private void add(Player player, String input) {
        PlayerLookupResult lookup = identities.resolve(input);
        if (!validateLookup(player, lookup)) return;
        PlayerIdentity target = lookup.identity().orElseThrow();
        AddedVisiblePlayerResult result;
        try {
            result = settings.addLobbyVisiblePlayer(player.getUniqueId(), target.uniqueId());
        } catch (RuntimeException exception) {
            handleSaveFailure(player, "add", exception);
            return;
        }
        switch (result) {
            case SUCCESS -> {
                apply(player);
                messageService.send(player, tr("visible-players.added", java.util.Map.of("player", Component.text(target.name(), NamedTextColor.WHITE))).color(NamedTextColor.GREEN));
            }
            case CANNOT_ADD_SELF -> messageService.send(player,
                    tr("visible-players.self"));
            case ALREADY_ADDED -> messageService.send(player,
                    tr("visible-players.already-added"));
            case OWNER_NOT_LOADED -> unavailable(player);
            case NOT_ADDED -> throw new IllegalStateException("Add returned remove-only result");
        }
    }

    private void remove(Player player, String input) {
        Set<UUID> added = settings.getLobbyAddedVisiblePlayers(player.getUniqueId()).orElse(null);
        if (added == null) {
            unavailable(player);
            return;
        }
        UUID target = parseUuid(input);
        PlayerIdentity identity = null;
        if (target != null) {
            if (!added.contains(target)) {
                messageService.send(player, tr("visible-players.not-added"));
                return;
            }
            identity = identities.findById(target).orElse(null);
        } else {
            PlayerLookupResult lookup = identities.resolve(input);
            if (!validateLookup(player, lookup)) return;
            identity = lookup.identity().orElseThrow();
            target = identity.uniqueId();
        }
        if (!added.contains(target)) {
            messageService.send(player, tr("visible-players.not-added"));
            return;
        }
        String display = identity == null ? target.toString() : identity.name();
        AddedVisiblePlayerResult result;
        try {
            result = settings.removeLobbyVisiblePlayer(player.getUniqueId(), target);
        } catch (RuntimeException exception) {
            handleSaveFailure(player, "remove", exception);
            return;
        }
        switch (result) {
            case SUCCESS -> {
                apply(player);
                messageService.send(player, tr("visible-players.removed", java.util.Map.of("player", Component.text(display, NamedTextColor.WHITE))).color(NamedTextColor.GREEN));
            }
            case NOT_ADDED -> messageService.send(player,
                    tr("visible-players.not-added"));
            case OWNER_NOT_LOADED -> unavailable(player);
            case CANNOT_ADD_SELF, ALREADY_ADDED -> throw new IllegalStateException(
                    "Remove returned add-only result: " + result);
        }
    }

    private boolean validateLookup(Player player, PlayerLookupResult lookup) {
        if (lookup.status() == PlayerLookupStatus.NOT_FOUND) {
            messageService.send(player, tr("common.unknown-player.message"));
            return false;
        }
        if (lookup.status() == PlayerLookupStatus.AMBIGUOUS) {
            messageService.send(player, tr("common.ambiguous-player.message"));
            messageService.send(player, tr("common.use-uuid.message"));
            return false;
        }
        return true;
    }

    private String identityCommandArgument(UUID id) {
        String name = identities.findById(id).map(PlayerIdentity::name).orElse(null);
        return IdentityCommandArgument.nameOrUuid(id, name,
                candidate -> identities.resolve(candidate).identity().map(PlayerIdentity::uniqueId));
    }

    private void apply(Player player) {
        try {
            applyVisibility.accept(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not immediately apply visible players for "
                    + player.getUniqueId() + ".", exception);
            messageService.send(player,
                    tr("visible-players.apply-failed"));
        }
    }

    private void handleSaveFailure(Player player, String action, RuntimeException exception) {
        logger.log(Level.SEVERE, "Could not " + action + " visible player for "
                + player.getUniqueId() + ".", exception);
        messageService.send(player, tr("visible-players.save-failed"));
    }

    private void unavailable(Player player) {
        messageService.send(player, tr("common.profile-unavailable.message"));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void sendUsage(CommandSender sender, String syntax) {
        messageService.send(sender, tr("common.usage").color(NamedTextColor.YELLOW)
                .append(Component.text(syntax, NamedTextColor.AQUA)));
    }

    private static List<String> matching(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .distinct().sorted(NAME_ORDER).toList();
    }
}

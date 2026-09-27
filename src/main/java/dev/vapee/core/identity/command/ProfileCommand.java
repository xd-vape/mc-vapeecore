package dev.vapee.core.identity.command;

import dev.vapee.core.format.PlaytimeFormatter;
import dev.vapee.core.identity.PlayerProfile;
import dev.vapee.core.identity.PlayerProfileLookupResult;
import dev.vapee.core.identity.PlayerProfileService;
import dev.vapee.core.identity.PlayerLookupStatus;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfileCommand implements TabExecutor {

    private static final String PERMISSION = "vapeecore.profile.view";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;
    private final Logger logger;
    private final PlayerProfileService profileService;
    private final MessageService messageService;
    private final ZoneId zone;

    public ProfileCommand(JavaPlugin plugin, PlayerProfileService profileService, MessageService messageService) {
        this(profileService, messageService,
                Objects.requireNonNull(plugin, "plugin").getServer()::getOnlinePlayers,
                plugin.getLogger(), ZoneId.systemDefault());
    }

    public ProfileCommand(PlayerProfileService profileService, MessageService messageService,
                          Supplier<? extends Collection<? extends Player>> onlinePlayers,
                          Logger logger, ZoneId zone) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messageService.send(sender, Component.text("You do not have permission to use this command.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length > 1) {
            usage(sender);
            return true;
        }
        try {
            PlayerProfileLookupResult result;
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    messageService.send(sender, Component.text("Specify a player: /profile <player|uuid>",
                            NamedTextColor.YELLOW));
                    return true;
                }
                result = profileService.getProfile(player.getUniqueId())
                        .map(profile -> new PlayerProfileLookupResult(
                                PlayerLookupStatus.FOUND,
                                Optional.of(profile)))
                        .orElseGet(() -> new PlayerProfileLookupResult(
                                PlayerLookupStatus.NOT_FOUND,
                                Optional.empty()));
            } else {
                result = profileService.resolveProfile(args[0]);
            }
            switch (result.status()) {
                case FOUND -> sendProfile(sender, result.profile().orElseThrow());
                case NOT_FOUND -> messageService.send(sender, Component.text(
                        "This player is not known to VapeeCore.", NamedTextColor.RED));
                case AMBIGUOUS -> messageService.send(sender, Component.text(
                        "Multiple stored players use that name. Use the UUID.", NamedTextColor.YELLOW));
            }
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not read a player profile.", exception);
            messageService.send(sender, Component.text(
                    "The player profile could not be read. Check the server log.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return onlinePlayers.get().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void usage(CommandSender sender) {
        messageService.send(sender, Component.text("Use: /profile [player|uuid]", NamedTextColor.YELLOW));
    }

    private void sendProfile(CommandSender sender, PlayerProfile profile) {
        messageService.send(sender, Component.text("Profile", NamedTextColor.GOLD));
        line(sender, "Player: ", Component.text(profile.identity().name()));
        line(sender, "Status: ", Component.text(profile.online() ? "Online" : "Offline"));
        line(sender, "Rank: ", profile.online()
                ? profile.rank().map(rank -> rank.displayComponent())
                        .orElse(Component.text("Unavailable"))
                : Component.text("Unavailable while offline"));
        line(sender, "Coins: ", Component.text(String.format(Locale.US, "%,d", profile.coins())));
        line(sender, "Playtime: ", Component.text(profile.playtimeTicks().isPresent()
                ? PlaytimeFormatter.formatTicks(profile.playtimeTicks().getAsLong())
                : "Unavailable while offline"));
        line(sender, "First Join: ", Component.text(DATE_FORMAT.format(
                profile.identity().firstJoin().atZone(zone))));
        line(sender, "Last Join: ", Component.text(DATE_FORMAT.format(
                profile.identity().lastJoin().atZone(zone))));
    }

    private void line(CommandSender sender, String label, Component value) {
        messageService.send(sender, Component.text(label, NamedTextColor.GRAY).append(value));
    }
}

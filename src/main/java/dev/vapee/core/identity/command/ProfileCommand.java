package dev.vapee.core.identity.command;

import static dev.vapee.core.i18n.Translations.tr;

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
            messageService.send(sender, tr("common.command-denied").color(NamedTextColor.RED));
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
                    messageService.send(sender, tr("profile.specify").color(NamedTextColor.YELLOW));
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
                case NOT_FOUND -> messageService.send(sender, tr("common.player-unknown").color(NamedTextColor.RED));
                case AMBIGUOUS -> messageService.send(sender, tr("profile.ambiguous").color(NamedTextColor.YELLOW));
            }
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not read a player profile.", exception);
            messageService.send(sender, tr("profile.failed").color(NamedTextColor.RED));
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
        messageService.send(sender, tr("profile.usage").color(NamedTextColor.YELLOW));
    }

    private void sendProfile(CommandSender sender, PlayerProfile profile) {
        messageService.send(sender, tr("profile.title").color(NamedTextColor.GOLD));
        line(sender, "profile.player", Component.text(profile.identity().name()));
        line(sender, "profile.status", profile.online() ? tr("common.online") : tr("common.offline"));
        line(sender, "profile.rank", profile.online()
                ? profile.rank().map(rank -> rank.displayComponent())
                        .orElse(tr("common.unavailable"))
                : tr("profile.offline-unavailable"));
        line(sender, "profile.coins", Component.text(String.format(Locale.US, "%,d", profile.coins())));
        line(sender, "profile.playtime", profile.playtimeTicks().isPresent()
                ? Component.text(PlaytimeFormatter.formatTicks(profile.playtimeTicks().getAsLong())) : tr("profile.offline-unavailable"));
        line(sender, "profile.first-join", Component.text(DATE_FORMAT.format(
                profile.identity().firstJoin().atZone(zone))));
        line(sender, "profile.last-join", Component.text(DATE_FORMAT.format(
                profile.identity().lastJoin().atZone(zone))));
    }

    private void line(CommandSender sender, String label, Component value) {
        messageService.send(sender, tr(label, java.util.Map.of("value", value)).color(NamedTextColor.GRAY));
    }
}

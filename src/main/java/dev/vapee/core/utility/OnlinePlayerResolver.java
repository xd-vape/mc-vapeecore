package dev.vapee.core.utility;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves online players by a complete, case-insensitive player name.
 * Partial matches and offline-player fallbacks are deliberately unsupported.
 */
public final class OnlinePlayerResolver {

    private final Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier;

    public OnlinePlayerResolver(Supplier<? extends Collection<? extends Player>> onlinePlayersSupplier) {
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
    }

    public @Nullable Player resolveExact(String input) {
        String normalizedInput = normalize(input);
        if (normalizedInput.isEmpty()) {
            return null;
        }

        Player match = null;
        for (Player player : onlinePlayersSupplier.get()) {
            if (player == null || !player.isOnline() || !normalize(player.getName()).equals(normalizedInput)) {
                continue;
            }
            if (match != null && !match.getUniqueId().equals(player.getUniqueId())) {
                return null;
            }
            match = player;
        }
        return match;
    }

    public List<String> suggest(String input, @Nullable UUID excludedPlayerId) {
        String prefix = normalize(input);
        return onlinePlayersSupplier.get().stream()
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .filter(player -> excludedPlayerId == null || !excludedPlayerId.equals(player.getUniqueId()))
                .map(Player::getName)
                .filter(name -> normalize(name).startsWith(prefix))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
    }
}

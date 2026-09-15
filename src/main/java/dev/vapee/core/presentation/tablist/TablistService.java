package dev.vapee.core.presentation.tablist;

import dev.vapee.core.presentation.PresentationRenderer.RenderedPresentation;
import dev.vapee.core.presentation.config.PresentationConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TablistService {

    private final PresentationConfig presentationConfig;
    private final Set<UUID> modifiedPlayers = new HashSet<>();

    public TablistService(PresentationConfig presentationConfig) {
        this.presentationConfig = Objects.requireNonNull(presentationConfig, "presentationConfig");
    }

    public void updatePlayer(Player player, RenderedPresentation presentation) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        RenderedPresentation validatedPresentation = Objects.requireNonNull(presentation, "presentation");

        if (!presentationConfig.isTablistEnabled()) {
            removePlayer(validatedPlayer);
            return;
        }

        modifiedPlayers.add(validatedPlayer.getUniqueId());
        validatedPlayer.playerListName(validatedPresentation.tablistName());
        validatedPlayer.sendPlayerListHeaderAndFooter(
                validatedPresentation.tablistHeader(),
                validatedPresentation.tablistFooter()
        );
    }

    public void removePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!modifiedPlayers.remove(validatedPlayer.getUniqueId())) {
            return;
        }

        validatedPlayer.playerListName(null);
        validatedPlayer.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    public void clearTrackedPlayers() {
        modifiedPlayers.clear();
    }
}

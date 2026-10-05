package dev.vapee.core.presentation.tablist;

import dev.vapee.core.presentation.PresentationRenderer.RenderedPresentation;
import dev.vapee.core.presentation.config.PresentationConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TablistService {

    private final PresentationConfig presentationConfig;
    private final Map<UUID, PlayerFields> modifiedPlayers = new HashMap<>();

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

        PlayerFields fields = modifiedPlayers.computeIfAbsent(validatedPlayer.getUniqueId(), ignored -> new PlayerFields());
        fields.name.write(validatedPlayer::playerListName, validatedPlayer::playerListName,
                validatedPresentation.tablistName());
        fields.header.write(validatedPlayer::playerListHeader, validatedPlayer::sendPlayerListHeader,
                validatedPresentation.tablistHeader());
        fields.footer.write(validatedPlayer::playerListFooter, validatedPlayer::sendPlayerListFooter,
                validatedPresentation.tablistFooter());
    }

    public void removePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        PlayerFields fields = modifiedPlayers.remove(validatedPlayer.getUniqueId());
        if (fields == null) {
            return;
        }

        fields.name.restore(validatedPlayer::playerListName, validatedPlayer::playerListName);
        fields.header.restore(validatedPlayer::playerListHeader, value -> restoreHeader(validatedPlayer, value));
        fields.footer.restore(validatedPlayer::playerListFooter, value -> restoreFooter(validatedPlayer, value));
    }

    public void clearTrackedPlayers() {
        modifiedPlayers.clear();
    }

    @SuppressWarnings("deprecation") // Adventure sends cannot express Paper's nullable header sentinel.
    private static void restoreHeader(Player player, Component value) {
        if (value == null) player.setPlayerListHeader((String) null);
        else player.sendPlayerListHeader(value);
    }

    @SuppressWarnings("deprecation") // No styled Component is ever converted to a legacy String.
    private static void restoreFooter(Player player, Component value) {
        if (value == null) player.setPlayerListFooter((String) null);
        else player.sendPlayerListFooter(value);
    }

    private static final class PlayerFields {
        private final Field name = new Field();
        private final Field header = new Field();
        private final Field footer = new Field();
    }

    /** One active cycle retains its original value across all successful refreshes. */
    private static final class Field {
        private Component previous;
        private Component lastWritten;
        private boolean written;

        private void write(Supplier<Component> read, Consumer<Component> write, Component value) {
            Component before = written ? previous : read.get();
            write.accept(value);
            previous = before;
            // Paper may normalize a name during its Adventure/vanilla roundtrip.
            lastWritten = read.get();
            written = true;
        }

        private void restore(Supplier<Component> read, Consumer<Component> write) {
            if (written && Objects.equals(read.get(), lastWritten)) {
                write.accept(previous);
            }
        }
    }
}

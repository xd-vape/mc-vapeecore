package dev.vapee.core.settings;

import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.ui.UiItemSpec;
import org.bukkit.entity.Player;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Settings-only UI seam: rendering and clicks share the same slot and action.
 * Add entries in SettingsMenuEntries; actual permission/profile/holder gates remain in menu/listener. */
public record SettingsMenuEntry(int slot, int statusSlot, Function<PlayerSettings, UiItemSpec> icon,
                                Function<PlayerSettings, UiItemSpec> status, Action action) {
    public SettingsMenuEntry {
        if (slot < 0 || slot >= SettingsMenu.INVENTORY_SIZE || statusSlot < -1
                || statusSlot >= SettingsMenu.INVENTORY_SIZE || slot == statusSlot) {
            throw new IllegalArgumentException("Invalid settings entry slots");
        }
        Objects.requireNonNull(icon);
        if (statusSlot >= 0) Objects.requireNonNull(status);
        Objects.requireNonNull(action);
    }
    public boolean matches(int clickedSlot) { return clickedSlot == slot || statusSlot >= 0 && clickedSlot == statusSlot; }
    public enum Result { SAVED, OPENED, UNAVAILABLE }
    @FunctionalInterface public interface Action {
        Result execute(Player player, PlayerSettings current, Context context);
    }
    public record Context(PlayerSettingsService settings, Consumer<Player> presentationRefresh,
                          Consumer<Player> visibilityMenuOpener) {
        public Context {
            Objects.requireNonNull(settings);
            Objects.requireNonNull(presentationRefresh);
            Objects.requireNonNull(visibilityMenuOpener);
        }
    }
}

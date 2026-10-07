package dev.vapee.core.lobby.item;

import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/** Explicit Java registrations; YAML only supplies presentation for these stable IDs.
 * Register before configuration is loaded, seal at Lobby enable, activate only after dependent menus exist. */
public final class LobbyItemRegistry {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean sealed;
    private boolean active;

    public void register(String id, LobbyItemDefinition defaults, LobbyItemAction action) {
        register(id, defaults, action, (player, definition) -> definition.appearance());
    }

    public void register(String id, LobbyItemDefinition defaults, LobbyItemAction action,
                         BiFunction<Player, LobbyItemDefinition, LobbyItemDefinition.Appearance> presentation) {
        if (sealed) throw new IllegalStateException("Lobby registrations are sealed");
        if (id == null || !id.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Invalid stable lobby item id: " + id);
        }
        Entry entry = new Entry(id, Objects.requireNonNull(defaults), Objects.requireNonNull(action),
                Objects.requireNonNull(presentation));
        if (entries.putIfAbsent(id, entry) != null) throw new IllegalArgumentException("Duplicate lobby item: " + id);
    }

    public Map<String, Entry> entries() { return Collections.unmodifiableMap(entries); }
    public Optional<Entry> resolve(String id) { return Optional.ofNullable(entries.get(id)); }
    public void seal() { sealed = true; }
    public void activate() { seal(); active = true; }
    public void deactivate() { active = false; }
    public boolean isActive() { return active; }

    public boolean handleClick(String id, Player player, Action click) {
        Entry entry = entries.get(id);
        return active && entry != null && entry.action().handleClick(player, click);
    }

    public record Entry(String id, LobbyItemDefinition defaults, LobbyItemAction action,
                        BiFunction<Player, LobbyItemDefinition, LobbyItemDefinition.Appearance> presentation) { }
}

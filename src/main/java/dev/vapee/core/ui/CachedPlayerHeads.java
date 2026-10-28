package dev.vapee.core.ui;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/** Decorates fresh menu items using only online profiles and Paper's existing profile cache. */
public final class CachedPlayerHeads {
    public static final NamespacedKey PLAYER_ID = Objects.requireNonNull(
            NamespacedKey.fromString("vapeecore:head-player"));
    private final Function<UUID, Player> onlinePlayer;
    private final Function<UUID, PlayerProfile> cachedProfile;

    public CachedPlayerHeads(Server server) {
        this(Objects.requireNonNull(server, "server")::getPlayer, server::createProfile);
    }

    CachedPlayerHeads(Function<UUID, Player> onlinePlayer, Function<UUID, PlayerProfile> cachedProfile) {
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.cachedProfile = Objects.requireNonNull(cachedProfile, "cachedProfile");
    }

    /** The UUID tag is presentation data; click authorization remains with each inventory holder. */
    public ItemStack apply(UUID id, ItemStack item) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        if (item.getType() != Material.PLAYER_HEAD || !(item.getItemMeta() instanceof SkullMeta skull)) return item;
        skull.getPersistentDataContainer().set(PLAYER_ID, PersistentDataType.STRING, id.toString());
        // Paper's SkullMeta starts an async update for textureless owners. A plain fallback avoids it.
        skull.setPlayerProfile(findSkin(id));
        item.setItemMeta(skull);
        return item;
    }

    private PlayerProfile findSkin(UUID id) {
        try {
            Player player = onlinePlayer.apply(id);
            if (player != null && player.isOnline() && id.equals(player.getUniqueId())) {
                PlayerProfile profile = player.getPlayerProfile().clone();
                if (hasSkin(id, profile)) return profile;
            }
        } catch (RuntimeException unavailable) {
            // Cosmetic data must not prevent the menu from opening; still try Paper's cache.
        }
        try {
            PlayerProfile profile = cachedProfile.apply(id);
            if (profile == null || !id.equals(profile.getId())) return null;
            profile = profile.clone();
            profile.completeFromCache();
            return hasSkin(id, profile) ? profile : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static boolean hasSkin(UUID id, PlayerProfile profile) {
        return profile != null && id.equals(profile.getId()) && profile.getTextures().getSkin() != null;
    }
}

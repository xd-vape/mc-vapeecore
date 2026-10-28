package dev.vapee.core.ui;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.vapee.core.module.ModuleRuntimeFixture;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;

/** Controlled Paper boundaries; native acceptance separately uses genuine CraftItemStack/SkullMeta. */
public final class HeadTestSupport {
    private HeadTestSupport() { }
    public static URL skin(int value) {
        try { return URI.create("https://textures.minecraft.net/texture/" + Integer.toHexString(value).repeat(64)).toURL(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    public static final class Profiles {
        public final Map<UUID, PlayerProfile> online = new HashMap<>();
        public final Map<UUID, URL> cached = new HashMap<>();
        public final List<UUID> cacheReads = new ArrayList<>();
        public int forbidden;
        public UUID cacheOverride;
        public final CachedPlayerHeads heads = new CachedPlayerHeads(ModuleRuntimeFixture.proxy(Server.class, (method, args) -> switch (method) {
            case "getPlayer" -> {
                UUID id = (UUID) args[0]; PlayerProfile profile = online.get(id);
                yield profile == null ? null : ModuleRuntimeFixture.proxy(Player.class, (name, values) -> switch (name) {
                    case "getUniqueId" -> id; case "isOnline" -> true; case "getPlayerProfile" -> profile;
                    default -> unexpected(name);
                });
            }
            case "createProfile" -> {
                UUID id = (UUID) args[0]; cacheReads.add(id);
                yield profile(cacheOverride == null ? id : cacheOverride, null, cached::get);
            }
            default -> unexpected(method);
        }));
        public PlayerProfile profile(UUID id, URL skin) { return profile(id, skin, ignored -> skin); }
        private PlayerProfile profile(UUID id, URL initial, Function<UUID, URL> fromCache) {
            URL[] current = {initial};
            return ModuleRuntimeFixture.proxy(PlayerProfile.class, (method, args) -> switch (method) {
                case "getId", "getUniqueId" -> id;
                case "getName" -> "Profile";
                case "getTextures" -> ModuleRuntimeFixture.proxy(PlayerTextures.class, (name, values) -> switch (name) {
                    case "getSkin" -> current[0]; case "isEmpty" -> current[0] == null;
                    default -> unexpected(name);
                });
                case "clone" -> profile(id, current[0], fromCache);
                case "completeFromCache" -> { current[0] = fromCache.apply(id); yield current[0] != null; }
                default -> unexpected(method);
            });
        }
        private Object unexpected(String method) { forbidden++; throw new AssertionError("Unexpected profile/network API: " + method); }
    }

    public static ItemStack item(UiItemSpec spec) { return new HeadItem(spec); }
    public static UUID owner(ItemStack item) {
        return UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(CachedPlayerHeads.PLAYER_ID, PersistentDataType.STRING));
    }
    public static PlayerProfile profile(ItemStack item) { return ((SkullMeta) item.getItemMeta()).getPlayerProfile(); }
    public static URL skin(ItemStack item) { var profile = profile(item); return profile == null ? null : profile.getTextures().getSkin(); }

    private static final class HeadItem extends ItemStack {
        private final Material material;
        private ItemMeta meta;
        HeadItem(UiItemSpec spec) {
            super(); material = spec.material();
            var state = new HashMap<String, Object>(); state.put("name", spec.name()); state.put("lore", spec.lore());
            meta = meta(state, new HashMap<>());
        }
        @Override public Material getType() { return material; }
        @Override public ItemMeta getItemMeta() { return meta.clone(); }
        @Override public boolean setItemMeta(ItemMeta value) { meta = value.clone(); return true; }
    }
    private static SkullMeta meta(Map<String, Object> state, Map<Object, Object> data) {
        var pdc = ModuleRuntimeFixture.proxy(PersistentDataContainer.class, (method, args) -> switch (method) {
            case "set" -> { data.put(args[0], args[2]); yield null; }
            case "get" -> data.get(args[0]); case "has" -> data.containsKey(args[0]);
            case "isEmpty" -> data.isEmpty(); case "getKeys" -> Set.copyOf(data.keySet());
            default -> throw new AssertionError("Unexpected item data API: " + method);
        });
        return ModuleRuntimeFixture.proxy(SkullMeta.class, (method, args) -> switch (method) {
            case "clone" -> meta(new HashMap<>(state), new HashMap<>(data));
            case "getPersistentDataContainer" -> pdc;
            case "setPlayerProfile" -> { state.put("profile", args[0]); yield null; }
            case "getPlayerProfile" -> state.get("profile");
            case "displayName" -> { if (args == null || args.length == 0) yield state.get("name"); state.put("name", args[0]); yield null; }
            case "lore" -> { if (args == null || args.length == 0) yield state.get("lore"); state.put("lore", args[0]); yield null; }
            default -> throw new AssertionError("Unexpected SkullMeta API: " + method);
        });
    }
}

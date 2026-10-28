package dev.vapee.core.ui;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public final class CachedPlayerHeadsHarness {
    private static int checks;
    public static void main(String[] args) {
        var f = new HeadTestSupport.Profiles();
        UUID online = UUID.randomUUID(), offline = UUID.randomUUID(), missing = UUID.randomUUID();
        var a = f.profile(online, HeadTestSupport.skin(1)); f.online.put(online, a);
        f.cached.put(offline, HeadTestSupport.skin(2));
        var spec = new UiItemSpec(Material.PLAYER_HEAD, Component.text("<red>Literal"), List.of(Component.text("Lore")));
        var item = HeadTestSupport.item(spec); f.heads.apply(online, item);
        check(HeadTestSupport.owner(item).equals(online) && HeadTestSupport.profile(item).getId().equals(online), "head and profile use authoritative online UUID");
        check(HeadTestSupport.skin(item).equals(HeadTestSupport.skin(1)) && f.cacheReads.isEmpty(), "online skin avoids even a cache lookup");
        check(HeadTestSupport.profile(item) != a && a.getTextures().getSkin().equals(HeadTestSupport.skin(1)), "online profile cloned without mutation");
        check(item.getItemMeta().displayName().equals(spec.name()) && item.getItemMeta().lore().equals(spec.lore()), "name/lore and their literal styling preserved");
        var cached = HeadTestSupport.item(spec); f.heads.apply(offline, cached);
        check(HeadTestSupport.owner(cached).equals(offline) && HeadTestSupport.profile(cached).getId().equals(offline), "cached offline identity assigned");
        check(HeadTestSupport.skin(cached).equals(HeadTestSupport.skin(2)) && f.cacheReads.equals(List.of(offline)), "offline cache-only completion supplies skin");
        var absent = HeadTestSupport.item(spec); f.heads.apply(missing, absent);
        check(HeadTestSupport.owner(absent).equals(missing) && HeadTestSupport.profile(absent) == null, "missing skin retains UUID tag and plain head without triggering Paper update");
        f.online.put(missing, f.profile(offline, HeadTestSupport.skin(2))); f.heads.apply(missing, absent);
        check(HeadTestSupport.profile(absent) == null, "wrong online profile UUID cannot redirect a head");
        f.cacheOverride = offline; f.heads.apply(missing, absent);
        check(HeadTestSupport.profile(absent) == null && HeadTestSupport.owner(absent).equals(missing), "wrong cached UUID rejected"); f.cacheOverride = null;
        f.online.remove(missing); f.cached.put(missing, HeadTestSupport.skin(3)); f.heads.apply(missing, absent);
        check(HeadTestSupport.skin(absent).equals(HeadTestSupport.skin(3)), "next render observes newly available cache data");
        f.cached.remove(missing); f.heads.apply(missing, absent);
        check(HeadTestSupport.profile(absent) == null, "unavailable skin clears an old profile rather than borrowing another target's identity");
        var paper = HeadTestSupport.item(new UiItemSpec(Material.PAPER, Component.text("Control"), List.of()));
        int reads = f.cacheReads.size(); check(f.heads.apply(missing, paper) == paper && f.cacheReads.size() == reads, "non-head controls untouched");
        var unavailable = new CachedPlayerHeads(id -> { throw new IllegalStateException("offline adapter"); },
                id -> { throw new IllegalStateException("profile cache unavailable"); });
        var fallback = HeadTestSupport.item(spec); unavailable.apply(missing, fallback);
        check(HeadTestSupport.profile(fallback) == null && HeadTestSupport.owner(fallback).equals(missing), "profile API failure safely preserves a UUID-tagged normal head");
        check(fallback.getItemMeta().displayName().equals(spec.name()) && fallback.getItemMeta().lore().equals(spec.lore()), "failure fallback retains names and lore");
        new CachedPlayerHeads(id -> null, id -> null).apply(offline, fallback);
        check(HeadTestSupport.profile(fallback) == null && HeadTestSupport.owner(fallback).equals(offline), "missing cached profile object does not retain stale identity");
        check(f.forbidden == 0, "no complete/update/load/network/scheduler or profile mutation API");
        System.out.println("CachedPlayerHeadsHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}

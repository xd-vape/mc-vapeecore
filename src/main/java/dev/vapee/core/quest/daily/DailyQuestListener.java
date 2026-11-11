package dev.vapee.core.quest.daily;

import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.time.Instant;
import java.util.Objects;

public final class DailyQuestListener implements Listener {

    private final DailyQuestService service;
    private final java.util.function.BooleanSupplier authorized;
    private final DailyQuestCompletionFeedback feedback;
    private final QuestNpc npc;
    private final DailyQuestMenu menu;

    public DailyQuestListener(DailyQuestService service) {
        this(service, () -> true, null, null, null);
    }

    DailyQuestListener(DailyQuestService service, java.util.function.BooleanSupplier authorized,
                       DailyQuestCompletionFeedback feedback, QuestNpc npc, DailyQuestMenu menu) {
        this.service = Objects.requireNonNull(service, "service");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.feedback = feedback;
        this.npc = npc;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // PlayerListener loads CorePlayer at NORMAL; an unavailable player is safely skipped.
        if (!authorized.getAsBoolean()) return;
        if (feedback != null) feedback.joined(event.getPlayer());
        var id = event.getPlayer().getUniqueId();
        if (authorized.getAsBoolean()) service.syncPlayer(id, Instant.now());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (npc != null) npc.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (npc != null) npc.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!owns(event.getRightClicked())) return;
        boolean allowed = !event.isCancelled() && authorized.getAsBoolean();
        event.setCancelled(true);
        if (allowed && event.getHand() == EquipmentSlot.HAND && event.getRightClicked().isValid())
            menu.openFromNpc(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcInteractAt(PlayerInteractAtEntityEvent event) { onNpcInteract(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (owns(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcTeleport(EntityTeleportEvent event) {
        if (owns(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcPortal(EntityPortalEvent event) { onNpcTeleport(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcMount(EntityMountEvent event) {
        if (owns(event.getEntity()) || owns(event.getMount())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcLeash(PlayerLeashEntityEvent event) {
        if (owns(event.getEntity())) event.setCancelled(true);
    }

    private boolean owns(org.bukkit.entity.Entity entity) { return npc != null && npc.owns(entity); }
}

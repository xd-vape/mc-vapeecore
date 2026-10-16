package dev.vapee.core.lobby.experience.glide;

import static dev.vapee.core.quest.QuestCompletionFixture.*;
import dev.vapee.core.activity.*;
import dev.vapee.core.activity.location.*;
import dev.vapee.core.activity.player.ActivityParticipant;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Real equipment controller, Glide, policy and domain owners; only Paper item/player surfaces are replaced. */
public final class LobbyGlideElytraHarness {
    private static int checks;
    public static void main(String[] args)throws Exception {
        lifecycle();occupied();generationsAndForeignReplacement();handoffs();activity();orphans();failures();events();deferredRecovery();
        System.out.println("LobbyGlideElytraHarness passed "+checks+" checks.");
    }
    private static void lifecycle()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Wings");p.contents[38]=null;String before=p.inventoryBytes();
        f.glide.reconcile(p.player);check(p.allow&&p.contents[38]==null&&f.equipmentItems==0,"arming never equips Elytra");
        p.ground=false;f.flight(p);check(p.contents[38]==null,"launch delay has no premature equipment");f.tick();
        var wings=(GlideTestItem)p.contents[38];
        check(p.gliding&&wings!=null&&wings.getType()==Material.ELYTRA&&wings.unbreakable,"only confirmed active glide gets unbreakable wings");
        check(f.equipment.isManaged(wings)&&wings.tags.get(f.equipmentKey).toString().startsWith(p.id+":"),"production binds exact owner/token PDC");
        check(withoutChest(p).equals(before),"only empty chest changed; storage/other armor/offhand/cursor exact");
        f.equipment.cleanupOrphans(p.player);check(p.contents[38]==wings,"orphan recovery preserves live lease");
        p.ground=true;check(!f.stop(p).isCancelled(),"equipped glide permits natural landing");f.flush();
        check(p.contents[38]==null&&p.allow&&f.equipment.leaseCount()==0&&before.equals(p.inventoryBytes()),"landing removes wings and restores empty chest exactly");
        f.active(p);p.water=true;f.stop(p);f.flush();check(p.contents[38]==null&&!p.gliding&&f.equipment.leaseCount()==0,"water removes owned wings");
        p.water=false;p.ground=true;f.glide.moved(p.player,true);f.active(p);f.glide.release(p.player);
        check(p.contents[38]==null&&before.equals(p.inventoryBytes()),"explicit release preserves every original item");
    }
    private static void occupied()throws Exception {
        for(Material type:List.of(Material.DIAMOND_CHESTPLATE,Material.ELYTRA)){
            var f=new LobbyGlideFixture();var p=f.user("Armor");var armor=new GlideTestItem(type);
            armor.tags.put(new NamespacedKey("other","armor"),"valuable");p.contents[38]=armor;String before=p.inventoryBytes();
            f.active(p);check(p.gliding&&p.contents[38]==armor&&f.equipmentItems==0,"occupied chest never overwritten "+type);
            f.glide.release(p.player);f.equipment.cleanupOrphans(p.player);f.glide.shutdown();
            check(before.equals(p.inventoryBytes())&&p.inventoryWrites==0,"foreign armor/metadata byte-equivalent "+type);
        }
    }
    private static void generationsAndForeignReplacement()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Tokens");p.contents[38]=null;f.active(p);long old=f.glide.generation(p.id);var oldWings=((GlideTestItem)p.contents[38]).clone();
        f.glide.release(p.player);p.ground=true;f.active(p);var current=p.contents[38];long token=f.glide.generation(p.id);
        f.equipment.release(p.player,old);check(p.contents[38]==current&&f.equipment.leaseCount()==1,"stale equipment generation cannot remove newer wings");
        var replacement=new GlideTestItem(Material.IRON_CHESTPLATE);p.contents[38]=replacement;
        p.contents[6]=current;p.contents[40]=((GlideTestItem)current).clone();p.cursorStack=((GlideTestItem)current).clone();p.contents[7]=oldWings;
        f.equipment.release(p.player,token);
        check(p.contents[38]==replacement&&p.contents[6]==null&&p.contents[40]==null&&p.cursorStack==null,"cleanup removes only exact generated token wherever moved");
        check(p.contents[7]==oldWings,"exact-token cleanup does not act on a different generation");
        f.equipment.cleanupOrphans(p.player);check(p.contents[7]==null&&p.contents[38]==replacement,"orphan recovery removes old disposable copy and preserves replacement armor");
    }
    private static void handoffs()throws Exception {
        for(String transition:List.of("fly","build","world","quit","death","disable","mode","teleport")){
            var f=new LobbyGlideFixture();var p=f.user(transition);p.contents[38]=null;f.active(p);
            check(f.equipment.isManaged(p.contents[38]),"precondition wings active "+transition);
            switch(transition){
                case "fly"->f.utility.toggleFlight(p.player);
                case "build"->{f.permitInventory=true;f.states.enterBuildMode(p.player);}
                case "world"->{p.world=f.other;var e=new PlayerChangedWorldEvent(p.player,f.lobby);f.listener.beforeWorldChange(e);f.listener.afterWorldChange(e);}
                case "quit"->{var e=new PlayerQuitEvent(p.player,Component.empty());f.listener.onQuit(e);p.onlineState=false;f.listener.afterQuit(e);}
                case "death"->{p.dead=true;var foreign=new GlideTestItem(Material.ELYTRA);var diamond=new GlideTestItem(Material.DIAMOND);var drops=new ArrayList<ItemStack>(List.of(((GlideTestItem)p.contents[38]).clone(),foreign,diamond));var e=death(p,drops);e.getItemsToKeep().add(p.contents[38]);e.getItemsToKeep().add(foreign);f.listener.onDeath(e);f.listener.onEquipmentDeathDrops(e);f.listener.afterDeath(e);check(drops.equals(List.of(foreign,diamond)),"death drops remove only generated wings, preserving real loot");check(e.getItemsToKeep().equals(List.of(foreign)),"kept items cannot restore disposable wings after death");}
                case "disable"->f.glide.shutdown();
                case "mode"->f.utility.setGameMode(p.player,GameMode.CREATIVE);
                case "teleport"->f.listener.onTeleport(new PlayerTeleportEvent(p.player,new Location(p.world,0,64,0),new Location(p.world,9,64,0)));
            }
            check(p.contents[38]==null&&!p.gliding&&f.equipment.leaseCount()==0,"immediate safe equipment handoff "+transition);
            f.flush();check(p.contents[38]==null,"handoff cannot re-equip without a new active glide "+transition);
        }
    }
    private static void activity()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Activity");p.contents[38]=null;f.active(p);
        f.activities.registerActivityType(new ActivityType(){public String getKey(){return "test";}public int getMinParticipants(){return 1;}public int getMaxParticipants(){return 2;}
            public ActivitySession createSession(UUID id,ActivityVenue venue){return new ActivitySession(id,"test",venue){protected void onParticipantJoined(ActivityParticipant participant){check(p.contents[38]==null&&!p.gliding&&!p.allow,"wings released before generic Activity ownership hook");}};}});
        f.activities.registerVenue(new ActivityVenue("test","test",new ActivityArea("lobby",-5,0,-5,5,100,5),new ActivityPosition("lobby",0,64,0,0,0)));
        var session=f.activities.createSession("test","test").session().orElseThrow();check(f.activities.joinSession(p.player,session.getSessionId())==ActivityResult.SUCCESS,"real Activity accepts handoff");f.flush();
        check(p.contents[38]==null&&!f.glide.isOwned(p.id),"no wings or lobby ownership inside Activity");
        p.ground=true;f.activities.leaveCurrentSession(p.id,ActivityLeaveReason.VOLUNTARY);f.flush();check(p.allow&&p.contents[38]==null,"Activity leave rearms without equipping early");
    }
    private static void orphans()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Restart");p.contents[38]=null;f.active(p);var copy=((GlideTestItem)p.contents[38]).clone();f.glide.release(p.player);
        p.contents[38]=copy;p.contents[6]=copy.clone();p.contents[40]=copy.clone();p.cursorStack=copy.clone();
        var fresh=new LobbyGlideEquipment(f.equipmentKey,()->new GlideTestItem(Material.ELYTRA),f.logger);fresh.cleanupOrphans(p.player);
        check(p.contents[38]==null&&p.contents[6]==null&&p.contents[40]==null&&p.cursorStack==null,"fresh runtime removes persisted disposable orphan markers");
        for(String invalid:List.of("", "not-a-token",p.id+":bad",UUID.randomUUID()+":BAD", "0".repeat(73))){
            var item=new GlideTestItem(Material.ELYTRA);item.tags.put(f.equipmentKey,invalid);p.contents[38]=item;
            fresh.cleanupOrphans(p.player);check(p.contents[38]==item&&!fresh.isManaged(item),"malformed metadata is not equipment authority "+invalid);
        }
        var wrongType=new GlideTestItem(Material.ELYTRA);wrongType.tags.put(f.equipmentKey,42);p.contents[38]=wrongType;fresh.cleanupOrphans(p.player);check(p.contents[38]==wrongType&&!fresh.isManaged(wrongType),"wrong PDC type is not authority");
        var foreign=new GlideTestItem(Material.ELYTRA);foreign.tags.put(new NamespacedKey("other","glide_elytra"),copy.tags.get(f.equipmentKey));p.contents[38]=foreign;fresh.cleanupOrphans(p.player);
        check(p.contents[38]==foreign&&!fresh.isManaged(foreign),"foreign namespace is never removed");
        p.contents[38]=copy;p.ground=true;f.glide.onPlayerAvailable(p.player);f.flush();check(p.contents[38]==null&&p.allow,"actual availability hook performs orphan recovery before arming");
    }
    private static void failures()throws Exception {
        for(String reason:List.of("partial","metadata","glide")){
            var f=new LobbyGlideFixture();var p=f.user(reason);p.contents[38]=null;String before=p.inventoryBytes();
            p.failEquipmentWrite=reason.equals("partial");f.rejectEquipmentMeta=reason.equals("metadata");p.rejectGliding=reason.equals("glide");f.active(p);
            check(p.contents[38]==null&&f.equipment.leaseCount()==0&&before.equals(p.inventoryBytes()),"failed start/equip leaves no item or lease "+reason);
            if(!reason.equals("glide"))check(p.gliding&&f.logs.stream().anyMatch(r->r.getMessage().contains(p.id.toString())),"equipment failure logs UUID and retains safe armor-free movement "+reason);
            else check(f.equipmentItems==0,"rejected gliding never creates equipment");
        }
        var f=new LobbyGlideFixture();var bad=f.user("Bad");var good=f.user("Good");bad.contents[38]=null;good.contents[38]=null;f.active(bad);f.active(good);bad.failInventoryRead=true;f.glide.shutdown();
        check(good.contents[38]==null&&f.equipment.leaseCount()==0&&f.glide.ownedCount()==0,"one equipment cleanup failure cannot prevent other players or runtime cleanup");
        bad.failInventoryRead=false;f.equipment.cleanupOrphans(bad.player);check(bad.contents[38]==null,"temporary read failure recovers orphan safely");
    }
    private static void events()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Events");p.contents[38]=null;f.active(p);ItemStack owned=p.contents[38],foreign=new GlideTestItem(Material.ELYTRA);
        ItemStack[] at={owned,null};var inv=proxy(Inventory.class,(m,a)->m.equals("getSize")?9:DEFAULT);
        var view=proxy(InventoryView.class,(m,a)->switch(m){case "getPlayer"->p.player;case "getTopInventory"->inv;case "getBottomInventory","getInventory"->p.player.getInventory();case "convertSlot"->a[0];case "getItem"->at[0];case "getCursor"->at[1];case "getType"->InventoryType.CHEST;default->DEFAULT;});
        for(var click:List.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.SWAP_OFFHAND,ClickType.DROP,ClickType.CONTROL_DROP,ClickType.DOUBLE_CLICK,ClickType.MIDDLE)){
            var e=new InventoryClickEvent(view,InventoryType.SlotType.ARMOR,38,click,InventoryAction.PICKUP_ALL,0);f.listener.onEquipmentClick(e);check(e.isCancelled(),"managed armor cannot be moved via "+click);
        }
        at[0]=null;at[1]=owned;var cursor=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,6,ClickType.LEFT,InventoryAction.PLACE_ALL);f.listener.onEquipmentClick(cursor);check(cursor.isCancelled(),"generated cursor cannot enter containers");
        at[1]=null;p.contents[0]=owned;var hotbar=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,6,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);f.listener.onEquipmentClick(hotbar);check(hotbar.isCancelled(),"generated hotbar swap blocked");p.contents[0]=null;
        p.contents[40]=owned;var offhand=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,6,ClickType.SWAP_OFFHAND,InventoryAction.HOTBAR_SWAP);f.listener.onEquipmentClick(offhand);check(offhand.isCancelled(),"generated offhand swap blocked");p.contents[40]=null;
        at[0]=foreign;var ordinary=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,6,ClickType.LEFT,InventoryAction.PICKUP_ALL);f.listener.onEquipmentClick(ordinary);check(!ordinary.isCancelled(),"real unmarked Elytra interaction untouched");
        var drag=new InventoryDragEvent(view,null,foreign,false,Map.of(6,owned));f.listener.onEquipmentDrag(drag);check(drag.isCancelled(),"drag destination generated item blocked");
        drag=new InventoryDragEvent(view,null,owned,false,Map.of(6,foreign));f.listener.onEquipmentDrag(drag);check(drag.isCancelled(),"generated drag cursor blocked");
        int[] removed={0};var item=proxy(Item.class,(m,a)->switch(m){case "getItemStack"->owned;case "remove"->{removed[0]++;yield null;}default->DEFAULT;});
        var drop=new PlayerDropItemEvent(p.player,item);f.listener.onEquipmentDrop(drop);check(drop.isCancelled(),"generated drop blocked");
        var swap=new PlayerSwapHandItemsEvent(p.player,owned,foreign);f.listener.onEquipmentSwap(swap);check(swap.isCancelled(),"generated hand transfer blocked");
        var damage=new PlayerItemDamageEvent(p.player,owned,1);f.listener.onEquipmentDamage(damage);check(damage.isCancelled(),"generated equipment cannot break");
        var use=new PlayerInteractEvent(p.player,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,owned,null,BlockFace.SELF,EquipmentSlot.HAND);f.listener.onEquipmentUse(use);check(use.isCancelled(),"generated hand item cannot be equipped by a stale interaction");
        var move=new InventoryMoveItemEvent(inv,owned,inv,true);f.listener.onEquipmentMove(move);check(move.isCancelled(),"automated container transfer blocked");
        var pickup=new EntityPickupItemEvent(p.player,item,0);f.listener.onEquipmentPickup(pickup);check(pickup.isCancelled()&&removed[0]==1,"generated entity cannot become player loot");
        var hopper=new InventoryPickupItemEvent(inv,item);f.listener.onEquipmentHopperPickup(hopper);check(hopper.isCancelled()&&removed[0]==2,"generated entity cannot become hopper loot");
        var stand=new PlayerArmorStandManipulateEvent(p.player,proxy(ArmorStand.class,(m,a)->DEFAULT),owned,foreign,EquipmentSlot.CHEST,EquipmentSlot.HAND);f.listener.onEquipmentArmorStand(stand);check(stand.isCancelled(),"generated equipment cannot enter armor stands");
        var foreignMove=new InventoryMoveItemEvent(inv,foreign,inv,true);f.listener.onEquipmentMove(foreignMove);check(!foreignMove.isCancelled(),"ordinary Elytra transfer remains untouched");
    }
    private static void deferredRecovery()throws Exception {
        var f=new LobbyGlideFixture();var p=f.user("Deferred");p.contents[38]=null;f.active(p);var old=((GlideTestItem)p.contents[38]).clone();
        var item=proxy(Item.class,(m,a)->m.equals("getItemStack")?old:DEFAULT);
        f.listener.onEquipmentDrop(new PlayerDropItemEvent(p.player,item));
        f.utility.toggleFlight(p.player);p.contents[0]=old;f.flush();
        check(p.contents[0]==null&&p.contents[38]==null&&p.allow&&f.utility.hasManagedFlight(p.id),"post-event item restoration cannot leak wings or overwrite new fly owner");
        f.utility.toggleFlight(p.player);p.ground=true;f.flush();f.active(p);var current=p.contents[38];
        f.glide.deferEquipmentCleanup(p.id);f.glide.deferReconcile(p.id);f.flush();
        check(p.contents[38]==current&&p.gliding,"coalesced recovery preserves current active lease");
        f.glide.release(p.player);p.dead=false;p.ground=true;f.listener.onRespawn(new PlayerRespawnEvent(p.player,new Location(p.world,0,64,0),false));p.contents[38]=old;f.flush();
        check(p.contents[38]==null&&p.allow,"post-respawn inventory restoration is recovered once without polling");
    }
    private static PlayerDeathEvent death(LobbyGlideFixture.User p,List<ItemStack> drops){return new PlayerDeathEvent(p.player,proxy(org.bukkit.damage.DamageSource.class,(m,a)->DEFAULT),drops,0,Component.empty(),false);}
    private static String withoutChest(LobbyGlideFixture.User p){var item=p.contents[38];try{p.contents[38]=null;return p.inventoryBytes();}finally{p.contents[38]=item;}}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}

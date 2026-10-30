package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;
import dev.vapee.core.lobby.experience.navigator.NavigatorEditorHarness.Fixture;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;

/** Executes the production tool's PDC binding, leases, events and draft mutations; only Paper item transport is fake. */
public final class NavigatorRegionToolHarness {
    private static int checks;
    private static final NamespacedKey KEY=new NamespacedKey("vapeecore","navigator_region_tool");
    private static final class Tick implements NavigatorRegionTool.Task { final Runnable action;boolean cancelled;Tick(Runnable action){this.action=action;}public void cancel(){cancelled=true;} }
    private static final class ToolFixture implements AutoCloseable {
        final Fixture t=new Fixture();final NavigatorFixture.TestPlayer p=t.admin();final List<Tick> ticks=new ArrayList<>();final Set<Integer> reserved=new HashSet<>(Set.of(0,1,8));final NavigatorRegionTool tool;
        int writes;
        ToolFixture()throws Exception {
            t.cmd(p,"edit");t.cmd(p,"add warp custom_000");
            tool=new NavigatorRegionTool(KEY,t.editor,()->reserved,()->new TestItem(Material.BLAZE_ROD),t.f.logger,action->{var tick=new Tick(action);ticks.add(tick);return tick;});t.editor.regionTool(tool);
            p.contents[0]=new TestItem(Material.COMPASS);p.contents[1]=new TestItem(Material.PLAYER_HEAD);p.held=0;
        }
        void runTicks(){var copy=List.copyOf(ticks);ticks.clear();for(var task:copy)if(!task.cancelled)task.action.run();}
        int tools(){return (int)Arrays.stream(p.contents).filter(tool::marked).count()+(tool.marked(p.cursor)?1:0);}
        void start(){t.cmd(p,"region mark");}
        ItemStack held(){return p.contents[p.held];}
        Block block(World world,int x,int y,int z){return proxy(Block.class,(m,a)->{if(m.startsWith("set")){writes++;throw new AssertionError("world write");}return switch(m){case "getLocation"->new Location(world,x,y,z);case "getWorld"->world;case "getType"->Material.STONE;case "getX"->x;case "getY"->y;case "getZ"->z;default->null;};});}
        PlayerInteractEvent hit(boolean first,World world,int x,int y,int z,EquipmentSlot hand,boolean denied){
            var event=new PlayerInteractEvent(p.player,first?Action.LEFT_CLICK_BLOCK:Action.RIGHT_CLICK_BLOCK,hand==EquipmentSlot.HAND?held():p.contents[40],block(world,x,y,z),BlockFace.UP,hand);
            event.setCancelled(denied);tool.interact(event);return event;
        }
        @Override public void close()throws Exception{t.close();runTicks();}
    }
    static final class TestItem extends ItemStack {
        final Material material;final Map<NamespacedKey,Object> tags=new HashMap<>();final Set<ItemFlag> flags=new HashSet<>();Component name;int amount=1;final ItemMeta meta;
        TestItem(Material material){super();this.material=material;
            var data=proxy(PersistentDataContainer.class,(m,a)->switch(m){case "set"->{tags.put((NamespacedKey)a[0],a[2]);yield null;}case "get"->tags.get(a[0]);case "has"->tags.containsKey(a[0]);case "getKeys"->Set.copyOf(tags.keySet());default->null;});
            meta=proxy(ItemMeta.class,(m,a)->switch(m){case "getPersistentDataContainer"->data;case "displayName"->{if(a.length==0)yield name;name=(Component)a[0];yield null;}case "addItemFlags"->{flags.addAll(Arrays.asList((ItemFlag[])a[0]));yield null;}default->null;});
        }
        @Override public Material getType(){return material;}@Override public int getAmount(){return amount;}@Override public void setAmount(int value){amount=value;}
        @Override public boolean isEmpty(){return amount<=0||material==Material.AIR;}@Override public boolean hasItemMeta(){return !tags.isEmpty()||name!=null;}@Override public ItemMeta getItemMeta(){return meta;}@Override public boolean setItemMeta(ItemMeta meta){return true;}
        @Override public TestItem clone(){var copy=new TestItem(material);copy.tags.putAll(tags);copy.flags.addAll(flags);copy.name=name;copy.amount=amount;return copy;}
        @Override public boolean equals(Object o){return o instanceof TestItem other&&material==other.material&&amount==other.amount&&tags.equals(other.tags)&&flags.equals(other.flags)&&Objects.equals(name,other.name);}
        @Override public int hashCode(){return Objects.hash(material,amount,tags,flags,name);}
    }
    public static void main(String[]args)throws Exception{selection();ownership();boundaries();lifecycle();System.out.println("NavigatorRegionToolHarness passed "+checks+" checks.");}
    private static void selection()throws Exception {
        try(var f=new ToolFixture()) {
            var before=Files.readAllBytes(f.t.file);var compass=f.p.contents[0];var head=f.p.contents[1];f.start();var issued=f.held();int slot=f.p.held;
            check(slot==2&&f.tools()==1&&f.p.open==f.p.bottom,"tool reserves empty non-lobby hotbar slot and closes editor");
            check(f.p.contents[0]==compass&&f.p.contents[1]==head,"real hotbar items are never displaced");
            String tag=(String)((TestItem)issued).tags.get(KEY);var target=f.t.s(f.p);
            check(tag.startsWith(f.p.id+":"+target.token+":")&&tag.endsWith(":root:custom_000"),"PDC binds authenticated owner, real session nonce, menu and entry");
            var right=f.hit(false,f.t.f.lobby,10,70,10,EquipmentSlot.HAND,true);check(right.isCancelled()&&f.tools()==1&&f.t.s(f.p).first==null,"missing Pos1 keeps tool and cancels pre-denied Adventure interaction");
            var left=f.hit(true,f.t.f.lobby,10,80,10,EquipmentSlot.HAND,true);
            check(left.useInteractedBlock()==Event.Result.DENY&&left.useItemInHand()==Event.Result.DENY,"selection never enables protected block or item use");
            check(f.t.s(f.p).first.x()==10&&f.t.s(f.p).first.y()==80&&f.t.s(f.p).first.z()==10,"Pos1 uses clicked block, not administrator location");
            var first=f.t.s(f.p).first;f.p.currentWorld=f.t.f.outside;f.tool.world(new PlayerChangedWorldEvent(f.p.player,f.t.f.lobby));
            check(f.tools()==1&&f.t.s(f.p).first==first,"legitimate world travel retains tool and Pos1");
            f.hit(false,f.t.f.outside,0,60,0,EquipmentSlot.HAND,true);check(f.tools()==1&&f.t.s(f.p).first==first&&f.t.entry(f.p).region()==null,"cross-world Pos2 rejection retains correctable tool and bounds");
            f.p.currentWorld=f.t.f.lobby;f.hit(false,f.t.f.lobby,0,60,0,EquipmentSlot.OFF_HAND,true);check(f.t.s(f.p).first==first&&f.tools()==1,"offhand interaction cannot choose Pos2");
            f.hit(false,f.t.f.lobby,0,60,0,EquipmentSlot.HAND,true);
            check(f.t.entry(f.p).region().minX()==0&&f.t.entry(f.p).region().maxY()==80&&f.t.entry(f.p).stackAmount(),"two block clicks update same unsaved draft with stack default");
            check(f.tools()==0&&f.p.held==0&&f.p.contents[slot]==null,"completion removes tool and restores still-owned held slot");
            check(Arrays.equals(before,Files.readAllBytes(f.t.file))&&!f.t.config.getState().enabled(),"tool completion is draft-only");
            var after=f.hit(false,f.t.f.lobby,0,60,0,EquipmentSlot.OFF_HAND,false);check(after.isCancelled(),"same-tick offhand follow-up cannot activate block after completion");f.runTicks();
            var ordinary=f.hit(false,f.t.f.lobby,0,60,0,EquipmentSlot.HAND,false);check(!ordinary.isCancelled(),"ordinary interactions resume after one-shot event fence");
            check(f.writes==0,"selection performs no block or world writes");f.t.cmd(f.p,"save");
            check(f.t.config.getState().menus().get("root").entries().get("custom_000").region()!=null,"normal explicit Save persists completed region");
        }
    }
    private static void ownership()throws Exception {
        try(var f=new ToolFixture()) {
            for(int i=2;i<9;i++)f.p.contents[i]=new TestItem(Material.DIAMOND);var before=f.p.contents.clone();f.start();
            check(f.tools()==0&&Arrays.equals(before,f.p.contents),"full hotbar refuses safely without escrow, loss or overwrite");
        }
        try(var f=new ToolFixture()) {
            f.start();int slot=f.p.held;var issued=f.held().clone();f.p.contents[slot]=new TestItem(Material.EMERALD);f.tool.reconcile(f.p.player);
            check(f.p.contents[slot].getType()==Material.EMERALD&&f.tools()==0&&f.p.held==slot,"external replacement retains field ownership and held selection");
            f.p.contents[20]=issued;f.tool.available(f.p.player);check(f.p.contents[20]==null,"stale issued items are cleaned without interpreting ordinary items");
            f.start();var copy=f.held().clone();var other=f.t.admin();other.contents[0]=copy;other.held=0;
            var foreign=new PlayerInteractEvent(other.player,Action.LEFT_CLICK_BLOCK,copy,f.block(f.t.f.lobby,1,65,1),BlockFace.UP,EquipmentSlot.HAND);f.tool.interact(foreign);
            check(foreign.isCancelled()&&other.contents[0]==null&&f.t.s(other)==null&&f.t.s(f.p).first==null,"foreign UUID cannot operate or retain copied tool");
            var forged=(TestItem)f.held().clone();forged.tags.put(KEY,f.p.id+":"+f.t.s(f.p).token+":"+UUID.randomUUID()+":root:custom_000");f.p.contents[f.p.held]=forged;
            f.hit(true,f.t.f.lobby,1,65,1,EquipmentSlot.HAND,false);check(f.tools()==0&&f.t.s(f.p).first==null,"forged nonce rejected against authoritative issued lease");f.runTicks();
            var ordinary=new TestItem(Material.BLAZE_ROD);ordinary.name=Component.text("Navigator • Region");f.p.contents[f.p.held]=ordinary;
            var arbitrary=f.hit(true,f.t.f.lobby,1,65,1,EquipmentSlot.HAND,false);check(!arbitrary.isCancelled()&&f.t.s(f.p).first==null,"material and visible name never establish tool identity");
            f.p.contents[f.p.held]=null;f.start();var stale=f.held().clone();f.t.cmd(f.p,"discard");f.t.cmd(f.p,"edit");f.t.cmd(f.p,"add warp custom_000");f.p.contents[2]=stale;f.p.held=2;
            f.hit(true,f.t.f.lobby,1,65,1,EquipmentSlot.HAND,false);check(f.tools()==0&&f.t.s(f.p).first==null,"another editor session cannot use stale tool nonce");
        }
        try(var f=new ToolFixture()) {
            f.start();f.hit(true,f.t.f.lobby,1,60,1,EquipmentSlot.HAND,false);var first=f.t.s(f.p).first;f.t.cmd(f.p,"menu select root");
            check(f.tools()==0&&f.t.s(f.p).first==first,"selection change cancels item lease while retaining PendingRegion ownership");
        }
    }
    private static void boundaries()throws Exception {
        try(var f=new ToolFixture()) {
            f.start();int slot=f.p.held;var item=f.held();var block=f.block(f.t.f.lobby,1,65,1);
            var damage=new BlockDamageEvent(f.p.player,block,item,true);f.tool.damage(damage);check(damage.isCancelled()&&!damage.getInstaBreak(),"block damage and instant break denied");
            var broken=new BlockBreakEvent(block,f.p.player);f.tool.breakBlock(broken);check(broken.isCancelled(),"block break denied");
            var placed=new BlockPlaceEvent(block,null,block,item,f.p.player,true,EquipmentSlot.HAND);f.tool.place(placed);check(placed.isCancelled(),"block placement denied");
            var swap=new PlayerSwapHandItemsEvent(f.p.player,item,null);f.tool.swap(swap);check(swap.isCancelled(),"offhand swap denied");
            for(var click:List.of(ClickType.LEFT,ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.MIDDLE,ClickType.DROP,ClickType.SWAP_OFFHAND)) {
                var event=new InventoryClickEvent(view(f.p,f.p.bottom),InventoryType.SlotType.CONTAINER,slot,click,InventoryAction.PICKUP_ALL);f.tool.click(event);check(event.isCancelled(),"selection inventory boundary: "+click);
            }
            var drag=new InventoryDragEvent(view(f.p,f.p.bottom),null,null,false,Map.of(10,item));f.tool.drag(drag);check(drag.isCancelled(),"drag/storing tool denied");
            var moving=new InventoryMoveItemEvent(f.p.inventory,item,inventory(null,27),true);f.tool.moveItem(moving);check(moving.isCancelled(),"hopper/trading inventory move denied");
            f.p.contents[20]=item.clone();f.p.cursor=item.clone();f.runTicks();check(f.tools()==1&&f.p.cursor==null&&f.p.contents[20]==null,"post-event cleanup rejects duplication but preserves the issued slot");
            Item dropped=proxy(Item.class,(m,a)->m.equals("getItemStack")?item:null);f.p.contents[slot]=null;var drop=new PlayerDropItemEvent(f.p.player,dropped);f.tool.drop(drop);check(drop.isCancelled(),"drop is canceled without overriding other protections");
            f.p.contents[slot]=item;f.runTicks();check(f.tools()==1,"vanilla drop return retains one valid tool, not a duplicate");
            f.p.permissions.clear();f.p.contents[slot]=null;var deniedDrop=new PlayerDropItemEvent(f.p.player,dropped);f.tool.drop(deniedDrop);f.p.contents[slot]=item;f.runTicks();
            check(deniedDrop.isCancelled()&&f.tools()==0,"post-event cleanup removes revoked tool returned by vanilla");
            check(f.writes==0,"inventory/block protection handlers never mutate blocks");
        }
    }
    private static void lifecycle()throws Exception {
        for(String action:List.of("cancel","discard","quit","permission","shutdown","restart","save-conflict")) {
            try(var f=new ToolFixture()) {
                var compass=f.p.contents[0];f.start();
                switch(action) {
                    case "cancel"->f.t.cmd(f.p,"region cancel");
                    case "discard"->f.t.cmd(f.p,"discard");
                    case "quit"->f.tool.quit(new PlayerQuitEvent(f.p.player,Component.empty(),PlayerQuitEvent.QuitReason.DISCONNECTED));
                    case "permission"->{f.p.permissions.clear();f.tool.move(new PlayerMoveEvent(f.p.player,new Location(f.t.f.lobby,0,64,0),new Location(f.t.f.lobby,1,64,0)));}
                    case "shutdown"->f.t.editor.shutdown();
                    case "restart"->{var stale=f.held().clone();f.tool.shutdown();f.p.contents[20]=stale;var recovered=new NavigatorRegionTool(KEY,f.t.editor,()->f.reserved,()->new TestItem(Material.BLAZE_ROD),f.t.f.logger,a->()->{});recovered.available(f.p.player);}
                    case "save-conflict"->{Files.writeString(f.t.file,Files.readString(f.t.file)+"# external\n");f.t.cmd(f.p,"save");}
                }
                check(f.tools()==0&&f.p.contents[0]==compass,action+": cleanup preserves genuine hotbar item");
                f.runTicks();check(f.tools()==0,action+": delayed events cannot resurrect tool");
                if(action.equals("permission"))check(f.t.s(f.p)==null,"permission revocation releases editor session too");
                if(action.equals("save-conflict"))check(Files.readString(f.t.file).endsWith("# external\n")&&f.t.s(f.p)!=null,"conflicting Save retains draft and external bytes but releases tool");
            }
        }
        try(var f=new ToolFixture()) {
            f.start();var item=f.held();var drops=new ArrayList<ItemStack>(List.of(item,new TestItem(Material.DIAMOND)));
            var death=new PlayerDeathEvent(f.p.player,null,drops,0,Component.empty(),true);death.getItemsToKeep().add(item);f.tool.death(death);f.tool.deathDrops(death);
            check(f.tools()==0&&death.getDrops().size()==1&&death.getDrops().getFirst().getType()==Material.DIAMOND&&death.getItemsToKeep().isEmpty(),"death/keepInventory drops cannot preserve or duplicate tool");
        }
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

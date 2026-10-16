package dev.vapee.core.lobby.experience.glide;

import static dev.vapee.core.quest.QuestCompletionFixture.*;
import dev.vapee.core.activity.*;
import dev.vapee.core.lobby.experience.navigator.NavigatorAccessPolicy;
import dev.vapee.core.lobby.player.*;
import dev.vapee.core.player.*;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.utility.UtilityService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;

/** Real eligibility, Utility, Lobby state and Activity services; only Bukkit and the one-shot clock are substituted. */
final class LobbyGlideFixture {
    final Map<UUID,User> online = new LinkedHashMap<>();
    final List<Deferred> tasks = new ArrayList<>();
    final List<LogRecord> logs = new ArrayList<>();
    final Logger logger = Logger.getAnonymousLogger();
    final World lobby = world("lobby"), other = world("other");
    World configured = lobby;
    boolean permitInventory;
    int profileReads, equipmentItems;
    boolean rejectEquipmentMeta;
    final PlayerService players;
    final LobbyPlayerStateService states;
    final ActivityService activities;
    final UtilityService utility;
    final LobbyGlideService glide;
    final LobbyGlideListener listener;
    final org.bukkit.NamespacedKey equipmentKey=new org.bukkit.NamespacedKey("vapeecore","glide_elytra");
    final LobbyGlideEquipment equipment;
    LobbyGlideFixture() throws Exception {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord r) { logs.add(r); } public void flush() { } public void close() { }
        });
        players = new PlayerService(new PlayerRepository() {
            public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.empty(); }
            public void save(CorePlayer p) { }
            public boolean exists(UUID id) { return false; }
        }, logger);
        states = construct(LobbyPlayerStateService.class,
                new Class<?>[]{BooleanSupplier.class, Predicate.class, Supplier.class, Consumer.class, Consumer.class, Supplier.class},
                (BooleanSupplier)()->true, (Predicate<Player>)p->p.getWorld()==configured, (Supplier<GameMode>)()->GameMode.ADVENTURE,
                (Consumer<Player>)p->{}, (Consumer<Player>)p->{}, (Supplier<Collection<Player>>)this::players);
        activities = construct(ActivityService.class,
                new Class<?>[]{PlayerService.class,Logger.class,BooleanSupplier.class,Predicate.class},
                players,logger,(BooleanSupplier)()->true,(Predicate<String>)name->true);
        var access = construct(NavigatorAccessPolicy.class,
                new Class<?>[]{Predicate.class,Predicate.class,Function.class,Predicate.class},
                (Predicate<UUID>)id->{profileReads++;return players.isLoaded(id);},
                (Predicate<World>)w->w==configured, (Function<UUID,LobbyPlayerMode>)states::getMode,
                (Predicate<UUID>)activities::isParticipating);
        utility = new UtilityService(()->true,this::players,p->20);
        equipment = new LobbyGlideEquipment(equipmentKey,()->{equipmentItems++;var item=new GlideTestItem(Material.ELYTRA);item.rejectMeta=rejectEquipmentMeta;return item;},logger);
        glide = new LobbyGlideService(id->online.containsKey(id)?online.get(id).player:null,access::canAccess,
                utility::hasManagedFlight, action->{var task=new Deferred(action);tasks.add(task);return task;},logger,equipment);
        listener = new LobbyGlideListener(glide);
        utility.addFlightListener(glide); states.addStateListener(glide); activities.addMembershipListener(glide);
    }
    static <T>T construct(Class<T> type,Class<?>[] signature,Object... arguments)throws Exception {
        var ctor=type.getDeclaredConstructor(signature);ctor.setAccessible(true);return ctor.newInstance(arguments);
    }
    static World world(String name) { return proxy(World.class,(method,args)->method.equals("getName")?name:DEFAULT); }
    List<Player> players(){return online.values().stream().map(p->p.player).toList();}
    User user(String name) { var p=new User(name);online.put(p.id,p);players.loadPlayer(p.id,name);return p; }
    void tick(){var ready=List.copyOf(tasks);tasks.clear();for(var task:ready)if(!task.cancelled)task.action.run();}
    void flush(){for(int i=0;i<6&&!tasks.isEmpty();i++)tick();if(!tasks.isEmpty())throw new AssertionError("Repeating tasks forbidden");}
    PlayerToggleFlightEvent flight(User p){var e=new PlayerToggleFlightEvent(p.player,true);listener.onFlight(e);return e;}
    EntityToggleGlideEvent stop(User p){var e=new EntityToggleGlideEvent(p.player,false);listener.onGlide(e);if(!e.isCancelled())p.gliding=false;return e;}
    void active(User p){glide.reconcile(p.player);p.ground=false;flight(p);tick();}
    static final class Deferred implements LobbyGlideService.Task {
        final Runnable action;boolean cancelled;Deferred(Runnable action){this.action=action;}public void cancel(){cancelled=true;}
    }
    final class User {
        final UUID id=UUID.randomUUID();final Player player;
        final byte[] armor={1,4,9,16}, storage={2,3,5,7,11}, offhand={13}, cursor={17};
        World world=lobby;GameMode mode=GameMode.ADVENTURE;
        boolean ground=true,onlineState=true,dead,water,vehicle,swimming,climbing,allow,flying,gliding,failRelease;
        int launches,glideWrites,inventoryReads,inventoryWrites,messages;
        final ItemStack[] contents=new ItemStack[41];
        ItemStack cursorStack=new GlideTestItem(Material.PAPER);
        boolean failEquipmentWrite, failInventoryRead, rejectGliding;
        Vector velocity=new Vector();
        User(String name){
            contents[3]=new GlideTestItem(Material.DIAMOND);contents[8]=new GlideTestItem(Material.EMERALD);
            contents[36]=new GlideTestItem(Material.DIAMOND_BOOTS);contents[37]=new GlideTestItem(Material.IRON_LEGGINGS);
            contents[38]=new GlideTestItem(Material.DIAMOND_CHESTPLATE);contents[39]=new GlideTestItem(Material.GOLDEN_HELMET);
            contents[40]=new GlideTestItem(Material.SHIELD);
            var inventory=proxy(PlayerInventory.class,(method,args)->{
                if(method.startsWith("set")||method.equals("clear"))inventoryWrites++;
                return switch(method){
                    case "getContents"->contents.clone();case "getStorageContents"->Arrays.copyOf(contents,36);
                    case "getArmorContents"->Arrays.copyOfRange(contents,36,40);case "getSize"->41;
                    case "getChestplate"->contents[38];case "getItemInOffHand"->contents[40];case "getItem"->contents[(int)args[0]];
                    case "setChestplate"->{contents[38]=(ItemStack)args[0];if(failEquipmentWrite){failEquipmentWrite=false;throw new IllegalStateException("partial equip failure");}yield null;}
                    case "setItem"->{contents[(int)args[0]]=(ItemStack)args[1];yield null;}
                    case "setArmorContents"->{System.arraycopy(args[0],0,contents,36,4);yield null;}
                    case "setItemInOffHand"->{contents[40]=(ItemStack)args[0];yield null;}
                    case "clear"->{Arrays.fill(contents,null);yield null;}default->DEFAULT;
                };
            });
            player=proxy(Player.class,(method,args)->switch(method){
                case "getUniqueId"->id;case "getName"->name;case "isOnline"->onlineState;
                case "getWorld"->world;case "getGameMode"->mode;
                case "setGameMode"->{mode=(GameMode)args[0];allow=mode==GameMode.CREATIVE||mode==GameMode.SPECTATOR;flying=false;yield null;}
                case "getLocation"->new Location(world,0,64,0,0,0);case "getYaw"->0f;
                case "isOnGround"->ground;case "isDead"->dead;case "isInWater"->water;
                case "isInsideVehicle"->vehicle;case "isSwimming"->swimming;case "isClimbing"->climbing;
                case "getAllowFlight"->allow;case "isFlying"->flying;case "isGliding"->gliding;
                case "setAllowFlight"->{if(failRelease&&!(boolean)args[0])throw new IllegalStateException("release failure");allow=(boolean)args[0];yield null;}
                case "setFlying"->{flying=(boolean)args[0];yield null;}
                case "setGliding"->{glideWrites++;var e=new EntityToggleGlideEvent(player(),(boolean)args[0]);listener.onGlide(e);if(!e.isCancelled()&&(!(boolean)args[0]||!rejectGliding))gliding=(boolean)args[0];yield null;}
                case "setVelocity"->{launches++;velocity=((Vector)args[0]).clone();yield null;}
                case "getVelocity"->velocity.clone();
                case "getInventory"->{inventoryReads++;if(failInventoryRead)throw new IllegalStateException("inventory read failure");yield inventory;}
                case "getItemOnCursor"->cursorStack;case "setItemOnCursor"->{cursorStack=(ItemStack)args[0];inventoryWrites++;yield null;}
                case "sendMessage"->{messages++;yield null;}
                default->DEFAULT;
            });
        }
        private Player player(){return player;}
        String inventoryBytes(){return Arrays.toString(armor)+Arrays.toString(storage)+Arrays.toString(offhand)+Arrays.toString(cursor)
                +Arrays.stream(contents).map(User::fingerprint).toList()+fingerprint(cursorStack);}
        private static String fingerprint(ItemStack item){return item==null?"empty":((GlideTestItem)item).fingerprint();}
    }
}

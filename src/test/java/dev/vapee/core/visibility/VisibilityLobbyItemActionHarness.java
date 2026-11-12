package dev.vapee.core.visibility;

import dev.vapee.core.lobby.item.*;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.player.settings.VisibilityMode;
import dev.vapee.core.message.MessageService;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real extracted visibility action, save/apply/presentation, cooldown and delayed lifecycle ownership. */
public final class VisibilityLobbyItemActionHarness {
    private record Cooldown(Runnable release, long ticks) { }

    private static int checks;
    public static void main(String[] args) throws Exception {
        var registry=new LobbyItemRegistry();var action=new AtomicReference<VisibilityLobbyItemAction>();
        LobbyItemRegistrations.register(registry,()->null,()->null,()->null,action::get);
        try(var f=new LobbyItemRegistryFixture(registry,null)){
            Map<UUID,CorePlayer> values=new HashMap<>();int[] saves={0};
            var players=new PlayerService(new PlayerRepository(){
                public Optional<CorePlayer> findByUniqueId(UUID id){return Optional.ofNullable(values.get(id));}
                public void save(CorePlayer value){saves[0]++;values.put(value.getUniqueId(),value);}
                public boolean exists(UUID id){return values.containsKey(id);}
            },f.logger);
            players.loadPlayer(f.player.getUniqueId(),"VisibilityPlayer");var settings=new PlayerSettingsService(players);
            var constructor=MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);constructor.setAccessible(true);
            var messages=constructor.newInstance((java.util.function.Supplier<String>)()->"");
            var policy=new VisibilityPolicy(id->settings.getSettings(id).orElseThrow().getVisibility(),(v,t)->false,(v,t)->false,(v,t)->false);
            var service=new VisibilityService(f.plugin,()->List.of(f.player),world->true,policy,f.logger);
            List<Runnable> queued = new ArrayList<>();
            List<Cooldown> cooldowns = new ArrayList<>();
            f.scheduler(queued::add, (release, ticks) -> cooldowns.add(new Cooldown(release, ticks)));
            action.set(new VisibilityLobbyItemAction(f.plugin,f.lobby,f.items,service,settings,messages,p->f.sounds++,
                    p->registry.isActive()&&f.eligible&&f.items.isEnabled("visibility")));
            registry.activate();f.refresh();int initial=saves[0];
            check(settings.getVisibilityMode(f.player.getUniqueId()).orElseThrow() == VisibilityMode.ALL,
                    "visibility starts in ALL");
            rejectLeftClicks(f,action.get(),settings,saves,queued,cooldowns);
            f.click(f.storage[1],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            f.click(f.storage[1],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            check(queued.size()==2&&saves[0]==initial,"duplicate block events both defer without premature save");
            queued.removeFirst().run();
            queued.removeFirst().run();
            check(settings.getVisibilityMode(f.player.getUniqueId()).orElseThrow() == VisibilityMode.FILTERED
                            && saves[0] == initial + 1,
                    "duplicate block events persist a single ALL to FILTERED transition");
            check(f.storage[1].getType()==Material.GRAY_DYE&&f.sounds==1,"filtered item refreshed and success feedback once");
            f.click(f.storage[1],Action.RIGHT_CLICK_AIR,EquipmentSlot.HAND);
            check(saves[0]==initial+1&&f.sounds==1,"cooldown prevents a repeated air toggle after a block toggle");
            expireCooldown(cooldowns);
            rejectLeftClicks(f,action.get(),settings,saves,queued,cooldowns);
            f.click(f.storage[1],Action.RIGHT_CLICK_AIR,EquipmentSlot.HAND);
            check(settings.getVisibilityMode(f.player.getUniqueId()).orElseThrow()==VisibilityMode.HIDDEN&&f.storage[1].getType()==Material.RED_DYE,"second cycle enters HIDDEN presentation");
            check(saves[0]==initial+2&&f.sounds==2,"second real toggle saves/feedback exactly once");
            expireCooldown(cooldowns);
            rejectLeftClicks(f,action.get(),settings,saves,queued,cooldowns);
            f.click(f.storage[1],Action.RIGHT_CLICK_AIR,EquipmentSlot.HAND);
            check(settings.getVisibilityMode(f.player.getUniqueId()).orElseThrow()==VisibilityMode.ALL&&saves[0]==initial+3,"right click cycles mode and saves without opening a menu");
            expireCooldown(cooldowns);
            f.click(f.storage[1],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);f.eligible=false;queued.removeFirst().run();
            check(saves[0]==initial+3,"queued ownership change to BUILD/activity fails closed");f.eligible=true;
            f.click(f.storage[1],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);registry.deactivate();queued.removeFirst().run();
            check(saves[0]==initial+3&&f.sounds==3,"queued teardown cannot call disabled features");registry.activate();
            f.click(f.storage[1],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);f.configure("items:\n  visibility:\n    enabled: false\n");queued.removeFirst().run();
            check(saves[0]==initial+3,"queued config disable fails closed");
            check(f.visibleCooldowns==0,"successful toggles never start a Bukkit item cooldown");
            f.configure("");
            f.click(f.storage[1],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            action.get().close();
            queued.removeFirst().run();
            check(saves[0]==initial+3,"retired action cannot run deferred work even with an active registry");
            check(f.logs.stream().noneMatch(log->log.getLevel().intValue()>=1000),"no visibility runtime error/severe");
        }
        System.out.println("VisibilityLobbyItemActionHarness passed "+checks+" checks.");
    }
    private static void expireCooldown(List<Cooldown> cooldowns) {
        check(cooldowns.size() == 1 && cooldowns.getFirst().ticks() == 3,
                "a single internal cooldown releases after exactly three server ticks");
        cooldowns.removeFirst().release().run();
    }

    private static void rejectLeftClicks(LobbyItemRegistryFixture f, VisibilityLobbyItemAction action,
                                         PlayerSettingsService settings, int[] saves,
                                         List<Runnable> queued, List<Cooldown> cooldowns) {
        var before=settings.getVisibilityMode(f.player.getUniqueId()).orElseThrow();
        int previousSaves=saves[0], sounds=f.sounds, scheduled=queued.size(), timers=cooldowns.size();
        for(var click:List.of(Action.LEFT_CLICK_AIR,Action.LEFT_CLICK_BLOCK)) {
            check(!action.supports(click),"left click not a registered visibility gesture in "+before);
            f.click(f.storage[1],click,EquipmentSlot.HAND);
            action.handleClick(f.player,click);
            check(settings.getVisibilityMode(f.player.getUniqueId()).orElseThrow()==before&&saves[0]==previousSaves
                    &&f.sounds==sounds&&queued.size()==scheduled&&cooldowns.size()==timers,
                    "left click is inert without save, feedback, cooldown or deferred work in "+before);
        }
    }
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
}

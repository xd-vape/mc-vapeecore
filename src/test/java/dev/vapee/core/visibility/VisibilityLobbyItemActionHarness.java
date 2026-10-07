package dev.vapee.core.visibility;

import dev.vapee.core.lobby.item.*;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.message.MessageService;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real extracted visibility action, save/apply/presentation, cooldown and delayed lifecycle ownership. */
public final class VisibilityLobbyItemActionHarness {
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
            List<Runnable> queued=new ArrayList<>(),cooldowns=new ArrayList<>();f.scheduler(queued::add,cooldowns::add);
            action.set(new VisibilityLobbyItemAction(f.plugin,f.lobby,f.items,service,settings,messages,p->f.sounds++,
                    p->registry.isActive()&&f.eligible&&f.items.isEnabled("visibility")));
            registry.activate();f.refresh();int initial=saves[0];
            f.click(f.storage[4],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            check(queued.size()==1&&saves[0]==initial,"block click waits one tick without premature save");
            queued.removeFirst().run();
            check(!settings.areLobbyPlayersVisible(f.player.getUniqueId()).orElseThrow()&&saves[0]==initial+1,"deferred real action persists master toggle once");
            check(f.storage[4].getType()==Material.GRAY_DYE&&f.sounds==1,"filtered item refreshed and success feedback once");
            f.click(f.storage[4],Action.RIGHT_CLICK_AIR,EquipmentSlot.HAND);
            check(saves[0]==initial+1&&f.sounds==1,"cooldown prevents repeated toggle");
            cooldowns.removeFirst().run();f.click(f.storage[4],Action.RIGHT_CLICK_AIR,EquipmentSlot.HAND);
            check(settings.areLobbyPlayersVisible(f.player.getUniqueId()).orElseThrow()&&f.storage[4].getType()==Material.LIME_DYE,"air toggle restores normal presentation");
            check(saves[0]==initial+2&&f.sounds==2,"second real toggle saves/feedback exactly once");
            cooldowns.removeFirst().run();
            f.click(f.storage[4],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);f.eligible=false;queued.removeFirst().run();
            check(saves[0]==initial+2,"queued ownership change to BUILD/activity fails closed");f.eligible=true;
            f.click(f.storage[4],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);registry.deactivate();queued.removeFirst().run();
            check(saves[0]==initial+2&&f.sounds==2,"queued teardown cannot call disabled features");registry.activate();
            f.click(f.storage[4],Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);f.configure("items:\n  visibility:\n    enabled: false\n");queued.removeFirst().run();
            check(saves[0]==initial+2,"queued config disable fails closed");
            check(f.logs.stream().noneMatch(log->log.getLevel().intValue()>=1000),"no visibility runtime error/severe");
        }
        System.out.println("VisibilityLobbyItemActionHarness passed "+checks+" checks.");
    }
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
}

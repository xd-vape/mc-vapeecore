package dev.vapee.core.quest;

import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import dev.vapee.core.settings.SettingsMenu;
import dev.vapee.core.settings.visibility.VisibilitySettingsMenu;
import dev.vapee.core.friend.gui.FriendMenu;
import dev.vapee.core.economy.command.CoinsCommand;
import dev.vapee.core.utility.command.PingCommand;
import dev.vapee.core.visibility.VisibilityService;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.*;

/** Real Quest/Reward/HUD; controlled online roster, spatial query and local source-owner leases. */
final class QuestLobbyTestSupport implements AutoCloseable {
    final QuestHudTestSupport.Fixture hud = new QuestHudTestSupport.Fixture();
    final Owners owners = new Owners();
    final Queue<Runnable> callbacks = new ArrayDeque<>();
    final Set<UUID> denied = new HashSet<>();
    boolean active=true, available=true, primary=true, schedulingFailure;
    int queries, candidates, cancellations;
    Collection<Player> supplied;
    final QuestLobbyProducer producer = new QuestLobbyProducer(hud.domain.players, hud.domain.registry, hud.domain.quests,
            hud.reporter, this::online, p -> actor(p).context && !actor(p).dead,
            (viewer,target) -> !denied.contains(target.getUniqueId()), p -> {
                queries++; if(supplied!=null)return supplied;
                return hud.online.values().stream().map(a->a.player).filter(a->a!=p).toList();
            }, action -> {
                if(schedulingFailure)throw new IllegalStateException("schedule refused");
                callbacks.add(action); return ()->{callbacks.remove(action);cancellations++;};
            }, ()->active, ()->primary, ()->hud.tick, hud.domain.logger);
    QuestLobbyTestSupport() { bind(); }
    void bind() { owners.bind(producer,()->available); }
    Player online(UUID id) {var a=hud.online.get(id);return a==null?null:a.player;}
    QuestHudTestSupport.Actor actor(Player p) {return hud.online.get(p.getUniqueId());}
    QuestHudTestSupport.Actor actor() {var a=hud.actor();producer.seed(a.player);return a;}
    void define(String id,String key,long target) {hud.define(id,key,target,7);}
    void assign(QuestHudTestSupport.Actor actor,String...ids) {hud.assign(actor,ids);}
    long value(QuestHudTestSupport.Actor actor,String id) {return hud.domain.players.getPlayer(actor.id).orElseThrow().getQuestState().snapshot().get(id).progress();}
    void drain(){for(Runnable r;(r=callbacks.poll())!=null;)r.run();}
    void encounter(QuestHudTestSupport.Actor a){hud.tick+=20;producer.afterStateChange(a.id);drain();}
    public void close(){active=false;producer.close();hud.close();}

    static final class Owners {
        final LobbyService lobby=source(LobbyService.class,"successListeners");
        final WarpService warps=source(WarpService.class,"successListeners");
        final NavigatorMenu navigator=source(NavigatorMenu.class,"successListeners");
        final DailyQuestMenu daily=source(DailyQuestMenu.class,"successListeners");
        final SettingsMenu settings=source(SettingsMenu.class,"successListeners");
        final FriendMenu friends=source(FriendMenu.class,"successListeners");
        final VisibilitySettingsMenu visibilityMenu=source(VisibilitySettingsMenu.class,"successListeners");
        final CoinsCommand coins=source(CoinsCommand.class,"successListeners");
        final PingCommand ping=source(PingCommand.class,"successListeners");
        final VisibilityService visibility=source(VisibilityService.class,"refreshListeners");
        static <T>T source(Class<T>type,String field){return ModuleRuntimeFixture.dependency(type,Map.of(field,new LinkedHashSet<>()));}
        void bind(QuestLobbyProducer p,BooleanSupplier available){p.bind(lobby,warps,navigator,daily,settings,friends,visibilityMenu,coins,ping,visibility,available);}
        void questMenu(Player player,DailyQuestMenu.OpenSource source){
            for(var listener:listeners(daily,"successListeners",BiConsumer.class))listener.accept(player,source);
        }
        void warp(Player p,String id,boolean navigator){for(var listener:listeners(warps,"successListeners",WarpService.SuccessListener.class))listener.onSuccess(p,id,navigator);}
        static <T>List<T> listeners(Object source,String field,Class<T>type){return ((Collection<?>)ModuleRuntimeFixture.get(source,field)).stream().map(type::cast).toList();}
    }
}

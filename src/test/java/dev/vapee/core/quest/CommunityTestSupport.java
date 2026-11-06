package dev.vapee.core.quest;

import dev.vapee.core.chat.ChatService;
import dev.vapee.core.chat.config.ChatConfig;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.friend.*;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.privatemessage.PrivateMessageService;
import dev.vapee.core.privatemessage.config.PrivateMessageConfig;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.utility.tpa.TpaTestSupport;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/** Real Social/PM/Friend/TPA/Quest/Reward services; only Paper I/O, clocks and repositories are controlled. */
final class CommunityTestSupport implements AutoCloseable {
    final TpaTestSupport.Fixture social = new TpaTestSupport.Fixture();
    final QuestDefinitionRegistry definitions = new QuestDefinitionRegistry();
    final QuestService quests = new QuestService(definitions, social.players,
            new RewardService(new EconomyService(social.players), social.players, social.logger), social.logger);
    final List<LogRecord> logs = new CopyOnWriteArrayList<>();
    final List<Component> feedback = new ArrayList<>();
    final Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
    final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    final Server server = QuestCompletionFixture.proxy(Server.class, (name, args) -> switch (name) {
        case "getPlayer" -> player((UUID) args[0]);
        case "getOnlinePlayers" -> social.onlinePlayers();
        case "isPrimaryThread" -> this.primary;
        default -> QuestCompletionFixture.DEFAULT;
    });
    final PrivateMessageConfig pmConfig = ModuleRuntimeFixture.dependency(PrivateMessageConfig.class,
            Map.of("state", PrivateMessageConfig.State.defaults(), "configFile", Path.of("synthetic-pm.yml")));
    final PrivateMessageService pm = new PrivateMessageService(server, social.settings, social.social,
            social.messages, social.logger, pmConfig, muted::contains);
    final ChatService chat = ModuleRuntimeFixture.dependency(ChatService.class,
            Map.of("state", new ChatService.RuntimeState(true, "<message>", ChatConfig.MetaFormat.MINI_MESSAGE)));
    boolean friendSaveFailure, active = true, available = true, primary = true, scheduleFailure;
    int friendSaves;
    FriendSnapshot stored = new FriendSnapshot(List.of(), List.of());
    final FriendService friends = new FriendService(new FriendRepository() {
        public FriendSnapshot initialize() { return stored; }
        public void save(FriendSnapshot snapshot) {
            if (friendSaveFailure) throw new IllegalStateException("friend save refused");
            friendSaves++;stored = snapshot;
        }
    }, FriendLimits.defaults(), (a,b) -> social.social.isIgnoring(a,b) || social.social.isIgnoring(b,a)
            ? FriendRequestDecision.BLOCKED : FriendRequestDecision.ALLOW, java.time.Clock.systemUTC());
    final QuestProgressReporter reporter = new QuestProgressReporter(quests, (id, text) -> feedback.add(text), social.logger);
    final QuestCommunityProducer producer = new QuestCommunityProducer(social.players, definitions, quests,
            reporter, this::player, () -> primary, action -> {
                if (scheduleFailure) throw new IllegalStateException("schedule refused");callbacks.add(action);
            }, () -> active, () -> social.now, social.logger);

    CommunityTestSupport() {
        social.logger.addHandler(new Handler() {
            public void publish(LogRecord record) { logs.add(record); }
            public void flush() { }
            public void close() { }
        });
        producer.bind(chat, pm, friends, social.requests, muted::contains, () -> available);
    }

    Player player(UUID id) { var actor = social.online.get(id);return actor == null ? null : actor.player; }
    TpaTestSupport.User actor(String name) { var actor = social.user(name);producer.seed(actor.player);return actor; }
    void define(String id, String key, long target, long coins) {
        var all = new ArrayList<>(definitions.snapshot());all.add(new QuestDefinition(id,id,"Fixture",QuestProgressKey.of(key),target,coins));
        definitions.replaceAll(all);
    }
    void assign(TpaTestSupport.User actor, String... ids) { quests.replaceAssignments(actor.id,List.of(ids)); }
    long progress(TpaTestSupport.User actor, String id) { return social.players.getPlayer(actor.id).orElseThrow().getQuestState().snapshot().get(id).progress(); }
    long coins(TpaTestSupport.User actor) { return social.players.getPlayer(actor.id).orElseThrow().getWallet().getCoins(); }
    AsyncChatEvent event(TpaTestSupport.User actor, String text, boolean async) {
        Component body = Component.text(text);
        return new AsyncChatEvent(async,actor.player,new HashSet<>(Set.of(actor.player)),
                (source,name,message,viewer) -> message,body,body,SignedMessage.system(text,body));
    }
    void say(TpaTestSupport.User actor, String text) { producer.onChat(event(actor,text,true));drain(); }
    void drain() { primary = true;for (Runnable task; (task = callbacks.poll()) != null;) task.run(); }
    void seconds(long value) { social.seconds(value); }
    void chatEnabled(boolean value) { chat.applyState(new ChatService.RuntimeState(value,"<message>",ChatConfig.MetaFormat.MINI_MESSAGE)); }
    public void close() { primary = true;active = false;producer.close();drain(); }
}

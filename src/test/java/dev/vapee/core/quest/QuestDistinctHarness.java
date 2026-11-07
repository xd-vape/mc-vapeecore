package dev.vapee.core.quest;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.reward.RewardService;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;

/** Real aggregate saves, reconstructed services and reward transactions. */
public final class QuestDistinctHarness {
    private static int checks;
    private static final QuestProgressKey KEY = QuestProgressKey.of("lobby:warp_unique");
    public static void main(String[] args) throws Exception {
        assignments(); disk(); pending(); bounds();
        System.out.println("QuestDistinctHarness passed " + checks + " checks.");
    }
    private static QuestDefinition definition(String id, long target) { return QuestCompletionFixture.definition(id, KEY.value(), target, 7); }
    private static long value(PlayerService players, UUID id, String quest) { return players.getPlayer(id).orElseThrow().getQuestState().snapshot().get(quest).progress(); }
    private static void assignments() {
        var f = new QuestCompletionFixture(); UUID id = f.load();
        f.define(definition("one", 3), definition("two", 4)); f.quests.replaceAssignments(id, List.of("one", "two"));
        f.quests.addDistinctProgress(id, KEY, "warp:a"); f.quests.addDistinctProgress(id, KEY, "warp:a");
        check(value(f.players,id,"one") == 1 && value(f.players,id,"two") == 1,"same-key fanout, duplicate rejected per assignment");
        f.quests.addProgress(id, KEY, 50); check(value(f.players,id,"one") == 1,"numeric call cannot bypass evidence");
        f.quests.reconcileAssignments(id,List.of("one","two")); f.quests.addDistinctProgress(id,KEY,"warp:a");
        check(value(f.players,id,"one") == 1,"compatible Force retains evidence");
        f.registry.replaceAll(List.of(definition("one",5),definition("two",4)));
        f.quests.addDistinctProgress(id,KEY,"warp:b"); check(value(f.players,id,"one") == 1 && value(f.players,id,"two") == 2,"changed signature blocks only incompatible assignment on reload");
        f.registry.replaceAll(List.of(definition("one",3),definition("two",4)));
        f.quests.addDistinctProgress(id,KEY,"warp:b"); check(value(f.players,id,"one") == 2 && value(f.players,id,"two") == 2,"catalog rollback preserves evidence and compatible counting");
        f.registry.replaceAll(List.of(definition("one",5),definition("two",4)));
        f.repository.fail=true;
        check(f.quests.reconcileAssignments(id,List.of("one")) == QuestAssignmentResult.PERSISTENCE_FAILED,"failed Force reports persistence failure");
        check(value(f.players,id,"one") == 2 && value(f.players,id,"two") == 2,"failed Force restores whole evidence and assignments");
        f.repository.fail=false; f.quests.reconcileAssignments(id,List.of("one","two"));
        check(value(f.players,id,"one") == 0 && value(f.players,id,"two") == 2,"explicit Force starts incompatible measurement fresh only");
        f.quests.replaceAssignments(id,List.of("one")); f.quests.addDistinctProgress(id,KEY,"warp:a");
        check(value(f.players,id,"one") == 1,"new cycle/same-ID replacement starts fresh");
        var state = f.players.getPlayer(id).orElseThrow().getQuestState();
        state.restore(List.of(new PlayerQuestProgress("one",2,QuestStatus.ACTIVE)));
        f.quests.addDistinctProgress(id,KEY,"warp:c"); check(value(f.players,id,"one") == 2,"legacy nonzero progress cannot invent missing history");
    }
    private static void disk() throws Exception {
        Path root=Files.createTempDirectory("vapeecore-distinct-");
        try {
            var logger=java.util.logging.Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
            var disk=new FilePlayerRepository(root,logger); disk.initialize();
            var players=new PlayerService(disk,logger); UUID id=UUID.randomUUID(); players.loadPlayer(id,"Tester");
            var registry=new QuestDefinitionRegistry();registry.replaceAll(List.of(definition("one",3)));
            var quests=new QuestService(registry,players,new RewardService(new EconomyService(players),players,logger),logger);
            quests.replaceAssignments(id,List.of("one"));quests.addDistinctProgress(id,KEY,"warp:a");players.unloadPlayer(id);
            var fresh=new PlayerService(new FilePlayerRepository(root,logger),logger);fresh.loadPlayer(id,"Tester");
            var restarted=new QuestService(registry,fresh,new RewardService(new EconomyService(fresh),fresh,logger),logger);
            restarted.addDistinctProgress(id,KEY,"warp:a");check(value(fresh,id,"one")==1,"disk roundtrip and reconstructed owners reject old target");
            restarted.addDistinctProgress(id,KEY,"warp:b");restarted.addDistinctProgress(id,KEY,"warp:c");restarted.addDistinctProgress(id,KEY,"warp:c");
            check(value(fresh,id,"one")==3 && fresh.getPlayer(id).orElseThrow().getWallet().getCoins()==7,"three IDs complete exactly once through real reward save");
            fresh.savePlayer(id);
            var loaded=disk.findByUniqueId(id).orElseThrow().getQuestState().snapshot().get("one");
            check(loaded.status()==QuestStatus.COMPLETED && loaded.distinct().identities().size()==3,"reward transaction persists identity and numeric progress together");
            var meetKey=QuestProgressKey.of("social:meet_unique");
            registry.replaceAll(List.of(definition("one",3),QuestCompletionFixture.definition("meet",meetKey.value(),2,7)));
            restarted.assignQuest(id,"meet");String first="player:"+UUID.randomUUID();
            restarted.addDistinctProgress(id,meetKey,first);fresh.unloadPlayer(id);fresh.loadPlayer(id,"Tester");
            restarted.addDistinctProgress(id,meetKey,first);
            check(value(fresh,id,"meet")==1,"meet UUID evidence survives logout/reload independently of warp evidence");
            restarted.addDistinctProgress(id,meetKey,"player:"+UUID.randomUUID());fresh.savePlayer(id);
            check(value(fresh,id,"meet")==2&&fresh.getPlayer(id).orElseThrow().getWallet().getCoins()==14,"second distinct UUID completes once after reload");
            Path file=root.resolve(id+".yml");
            for (String malformed : List.of("scalar", "duplicates", "count", "oversize", "key", "target", "invalid", "blocked")) {
                var yaml=YamlConfiguration.loadConfiguration(file.toFile());
                yaml.set("quests.active.one.progress",1L);yaml.set("quests.active.one.status","ACTIVE");
                yaml.set("quests.active.one.distinct",null);
                yaml.set("quests.active.one.distinct.key",KEY.value());yaml.set("quests.active.one.distinct.target",3L);
                yaml.set("quests.active.one.distinct.identities",List.of("warp:a"));
                switch(malformed) {
                    case "scalar" -> yaml.set("quests.active.one.distinct","broken");
                    case "duplicates" -> {yaml.set("quests.active.one.progress",2L);yaml.set("quests.active.one.distinct.identities",List.of("warp:a","warp:a"));}
                    case "count" -> yaml.set("quests.active.one.progress",2L);
                    case "oversize" -> yaml.set("quests.active.one.distinct.identities",Collections.nCopies(1025,"warp:a"));
                    case "key" -> yaml.set("quests.active.one.distinct.key","BAD KEY");
                    case "target" -> yaml.set("quests.active.one.distinct.target",2000L);
                    case "invalid" -> yaml.set("quests.active.one.distinct.identities",List.of("<bad>"));
                    case "blocked" -> yaml.set("quests.active.one.distinct.blocked",true);
                }
                yaml.save(file.toFile());
                var corrupt=disk.findByUniqueId(id).orElseThrow();var evidence=corrupt.getQuestState().snapshot().get("one");
                check(evidence.distinct().blocked() && evidence.progress()==yaml.getLong("quests.active.one.progress"),"malformed optional state retains numeric and blocks recount: "+malformed);
                disk.save(corrupt);check(disk.findByUniqueId(id).orElseThrow().getQuestState().snapshot().get("one").distinct().blocked(),"blocked sentinel persists: "+malformed);
            }
        } finally { try(var files=Files.walk(root)){for(var path:files.sorted(Comparator.reverseOrder()).toList())Files.delete(path);} }
    }
    private static void pending() {
        var f=new QuestCompletionFixture();UUID id=f.load();f.define(definition("one",1));f.quests.replaceAssignments(id,List.of("one"));
        f.economy.setCoins(id,Long.MAX_VALUE);f.quests.addDistinctProgress(id,KEY,"warp:a");
        var pending=f.players.getPlayer(id).orElseThrow().getQuestState().snapshot().get("one");
        check(pending.status()==QuestStatus.REWARD_PENDING && pending.distinct().identities().contains("warp:a"),"pending stores evidence before failed reward");
        check(f.quests.reconcileAssignments(id,List.of("one"))==QuestAssignmentResult.PENDING_REWARD,"Force cannot erase pending evidence");
        f.economy.setCoins(id,0);f.quests.retryPendingRewards(id);f.quests.addDistinctProgress(id,KEY,"warp:a");
        check(f.players.getPlayer(id).orElseThrow().getWallet().getCoins()==7,"pending retry uses original once-only reward boundary");
    }
    private static void bounds() {
        var f=new QuestCompletionFixture();UUID id=f.load();
        f.define(definition("one",1024),definition("two",1025));f.quests.replaceAssignments(id,List.of("one","two"));
        for(int i=0;i<1100;i++)f.quests.addDistinctProgress(id,KEY,"warp:w"+i);
        check(value(f.players,id,"one")==1024 && value(f.players,id,"two")==0,"bounded target; unsupported target is never silently clamped");
        boolean invalid=false;try{f.quests.addDistinctProgress(id,KEY,"../invalid");}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"identity syntax validated before state changes");
        var state=f.players.getPlayer(id).orElseThrow().getQuestState();var snapshot=state.snapshot().get("one");
        boolean immutable=false;try{snapshot.distinct().identities().add("warp:bad");}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"immutable snapshots cannot forge new evidence");
        var many=new QuestCompletionFixture();UUID owner=many.load();
        many.define(definition("a",1024),definition("b",1024),definition("c",1024),definition("d",1024),definition("e",1024));
        many.quests.replaceAssignments(owner,List.of("a","b","c","d","e"));
        for(int i=0;i<1025;i++)many.quests.addDistinctProgress(owner,KEY,"warp:w"+i);
        long total=many.players.getPlayer(owner).orElseThrow().getQuestState().progressEntries().stream().mapToLong(v->v.distinct().identities().size()).sum();
        check(total==4096,"total retained player evidence is bounded across assignments");
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

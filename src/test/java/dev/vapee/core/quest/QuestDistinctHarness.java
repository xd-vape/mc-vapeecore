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
        pendingIntegrityRegression();
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
    @FunctionalInterface private interface Scenario { void run() throws Exception; }
    private enum PendingPath {
        EXPLICIT, NUMERIC, DISTINCT;
        QuestProgressResult apply(IntegrityFixture f, QuestProgressKey key) {
            return switch(this) {
                case EXPLICIT -> f.quests.retryPendingRewards(f.id);
                case NUMERIC -> f.quests.addProgress(f.id,key,999);
                case DISTINCT -> f.quests.addDistinctProgress(f.id,key,"warp:must_not_be_added");
            };
        }
    }

    private static void pendingIntegrityRegression() throws Exception {
        Map<String,Scenario> scenarios=new LinkedHashMap<>();
        for(PendingPath path:PendingPath.values()) {
            for(boolean fundsAvailable:List.of(false,true)) {
                String suffix=path+" funds="+fundsAvailable;
                scenarios.put("target 1 -> 2 "+suffix,()->pendingTransition(path,1,2,KEY,fundsAvailable));
                scenarios.put("target 2 -> 1 "+suffix,()->pendingTransition(path,2,1,KEY,fundsAvailable));
                scenarios.put("changed key "+suffix,()->pendingTransition(path,1,1,QuestProgressKey.of("social:meet_unique"),fundsAvailable));
            }
            scenarios.put("persisted 1 -> 2 "+path,()->pendingRoundtrip(path,1,2));
            scenarios.put("persisted 2 -> 1 "+path,()->pendingRoundtrip(path,2,1));
            scenarios.put("compatible retries "+path,()->compatibleRetries(path));
            scenarios.put("blocked evidence "+path,()->blockedPending(path));
            scenarios.put("unearned pending "+path,()->unearnedPending(path));
            scenarios.put("legacy 1 -> 2 "+path,()->legacyRetry(path,1,2));
            scenarios.put("legacy 2 -> 1 "+path,()->legacyRetry(path,2,1));
        }
        scenarios.put("daily rotation and Force",QuestDistinctHarness::dailyPending);
        scenarios.put("failed Force save rollback",QuestDistinctHarness::dailySaveRollback);
        scenarios.put("independent same-key pending",QuestDistinctHarness::independentPending);
        List<Throwable> failures=new ArrayList<>();
        for(var entry:scenarios.entrySet()) {
            try { entry.getValue().run(); }
            catch(Exception | AssertionError failure) {
                System.err.println("PENDING REGRESSION FAILED ["+entry.getKey()+"]: "+failure);
                failures.add(failure);
            }
        }
        System.out.println("Distinct pending scenarios: "+scenarios.size()+", failed: "+failures.size());
        if(!failures.isEmpty()) {
            AssertionError aggregate=new AssertionError(failures.size()+" distinct pending integrity scenarios failed");
            failures.forEach(aggregate::addSuppressed);throw aggregate;
        }
    }

    private static void unchanged(IntegrityFixture f, PlayerQuestProgress expected, String operation) {
        PlayerQuestProgress actual=f.state();
        check(actual.equals(expected),operation+" expected="+expected+" actual="+actual);
    }

    private static void pendingTransition(PendingPath path,int original,int changed,QuestProgressKey changedKey,boolean funds) throws Exception {
        try(var f=new IntegrityFixture()) {
            f.pending(original,true);PlayerQuestProgress expected=f.state();
            f.define(changed,changedKey);
            if(funds)f.economy.setCoins(f.id,0);
            for(int i=0;i<2;i++) {
                int attempts=f.attempts;
                path.apply(f,changedKey);
                unchanged(f,expected,"incompatible "+path+" "+original+" -> "+changed);
                check(f.attempts==attempts,"incompatible "+path+" must not invoke RewardService");
                check(f.coins()==(funds?0:Long.MAX_VALUE),"incompatible retry retains wallet");
            }
            f.restart();unchanged(f,expected,"incompatible pending full restart");
            check(!f.state().distinct().blocked(),"service-generated pending evidence remains valid");
            int attempts=f.attempts;path.apply(f,changedKey);unchanged(f,expected,"post-restart incompatible signal");
            check(f.attempts==attempts,"restart cannot unlock incompatible reward");
            f.define(original,KEY);f.economy.setCoins(f.id,0);
            path.apply(f,KEY);
            check(f.state().equals(expected.with(original,QuestStatus.COMPLETED))&&f.coins()==7,"compatible rollback completes once with original identities");
            attempts=f.attempts;
            for(PendingPath alternative:PendingPath.values())alternative.apply(f,KEY);
            check(f.coins()==7&&f.attempts==attempts,"all subsequent entry points preserve once-only completion");
            f.restart();
            check(f.state().equals(expected.with(original,QuestStatus.COMPLETED))&&f.coins()==7,"completed evidence and payout survive reconstructed services");
        }
    }

    private static void pendingRoundtrip(PendingPath path,int original,int changed) throws Exception {
        try(var f=new IntegrityFixture()) {
            f.pending(original,true);var expected=f.state();f.define(changed,KEY);
            path.apply(f,KEY);f.restart();
            unchanged(f,expected,"actual Save/Load after incompatible pending "+path+" "+original+" -> "+changed);
            check(!f.state().distinct().blocked()&&f.state().distinct().identities().size()==original,"valid evidence survives actual disk roundtrip without sentinel");
        }
    }

    private static void compatibleRetries(PendingPath path) throws Exception {
        try(var f=new IntegrityFixture()) {
            f.pending(2,true);var expected=f.state();
            for(int i=0;i<2;i++){path.apply(f,KEY);unchanged(f,expected,"compatible failed "+path);}
            f.restart();unchanged(f,expected,"compatible failed retry logout/restart");
            int attempts=f.attempts;path.apply(f,KEY);
            check(f.attempts==attempts+1,"compatible pending still attempts existing reward boundary");
            unchanged(f,expected,"compatible overflow remains pending");
            f.economy.setCoins(f.id,0);path.apply(f,KEY);
            check(f.state().equals(expected.with(2,QuestStatus.COMPLETED))&&f.coins()==7,"compatible success retains full evidence");
            for(int i=0;i<3;i++)path.apply(f,KEY);
            check(f.coins()==7,"repeated compatible retry never pays twice");
            f.restart();check(f.state().status()==QuestStatus.COMPLETED&&f.coins()==7,"compatible completion restart");
        }
    }

    private static void legacyRetry(PendingPath path,int original,int changed) throws Exception {
        try(var f=new IntegrityFixture()) {
            f.pending(original,false);f.define(changed,KEY);path.apply(f,KEY);
            check(f.state().progress()==changed&&f.state().status()==QuestStatus.REWARD_PENDING&&f.state().distinct()==null,"legacy normalization remains unchanged "+path);
            f.restart();check(f.state().progress()==changed&&f.state().distinct()==null,"legacy pending roundtrip");
            f.economy.setCoins(f.id,0);path.apply(f,KEY);path.apply(f,KEY);
            check(f.state().progress()==changed&&f.state().status()==QuestStatus.COMPLETED&&f.state().distinct()==null&&f.coins()==7,"legacy retry still completes once without inventing evidence");
        }
    }

    private static void blockedPending(PendingPath path) throws Exception {
        try(var f=new IntegrityFixture()) {
            f.pending(1,true);
            var yaml=YamlConfiguration.loadConfiguration(f.file().toFile());
            yaml.set("quests.active.one.distinct.identities",List.of("warp:duplicate","warp:duplicate"));yaml.save(f.file().toFile());
            f.reloadOnly();check(f.state().distinct().blocked()&&f.state().status()==QuestStatus.REWARD_PENDING,"genuinely damaged pending remains fail-closed");
            var expected=f.state();f.economy.setCoins(f.id,0);int attempts=f.attempts;
            path.apply(f,KEY);unchanged(f,expected,"blocked compatible retry "+path);
            f.define(2,KEY);path.apply(f,KEY);unchanged(f,expected,"blocked incompatible retry "+path);
            check(f.attempts==attempts&&f.coins()==0,"blocked evidence cannot authorize a reward or reset its signature");
            f.restart();unchanged(f,expected,"blocked sentinel survives save/restart");
        }
    }

    private static void unearnedPending(PendingPath path) throws Exception {
        try(var f=new IntegrityFixture()) {
            f.define(2,KEY);
            var evidence=new QuestDistinctProgress(KEY.value(),2,Set.of("warp:a"),false);
            var expected=new PlayerQuestProgress("one",1,QuestStatus.REWARD_PENDING,evidence);
            f.profile().getQuestState().restore(List.of(expected));f.economy.setCoins(f.id,0);
            path.apply(f,KEY);
            unchanged(f,expected,"partial evidence must not become a completed target through "+path);
            check(f.attempts==0&&f.coins()==0,"unearned pending cannot pay");
        }
    }

    private static void dailyPending() throws Exception {
        try(var f=new IntegrityFixture()) {
            f.define(1,KEY);var daily=f.daily();var day1=java.time.Instant.parse("2026-11-01T12:00:00Z");var day2=day1.plusSeconds(86400);
            check(daily.syncPlayer(f.id,day1)==dev.vapee.core.quest.daily.DailyQuestSyncResult.INITIALIZED,"real daily initializes assignment");
            f.economy.setCoins(f.id,Long.MAX_VALUE);f.quests.addDistinctProgress(f.id,KEY,"warp:a");var expected=f.state();var cycle=f.profile().getDailyQuestState().cycleId();
            f.define(2,KEY);f.economy.setCoins(f.id,0);int attempts=f.attempts;
            check(daily.syncPlayer(f.id,day1)==dev.vapee.core.quest.daily.DailyQuestSyncResult.CURRENT,"same-cycle sync retains pending");
            check(daily.forcePlayer(f.id,day2)==dev.vapee.core.quest.daily.DailyQuestSyncResult.BLOCKED_PENDING_REWARD,"Force cannot bypass incompatible pending");
            check(daily.syncPlayer(f.id,day2)==dev.vapee.core.quest.daily.DailyQuestSyncResult.BLOCKED_PENDING_REWARD,"new daily cycle cannot rotate incompatible pending");
            unchanged(f,expected,"daily blocked rotation");check(f.profile().getDailyQuestState().cycleId().equals(cycle)&&f.attempts==attempts,"blocked rotation preserves cycle and never attempts unchecked payout");
            f.restart();unchanged(f,expected,"daily blocked state restart");
            f.define(1,KEY);daily=f.daily();
            check(daily.syncPlayer(f.id,day2)==dev.vapee.core.quest.daily.DailyQuestSyncResult.ROTATED,"compatible rollback permits retry then normal daily rotation");
            check(f.coins()==7&&f.state().progress()==0&&f.state().status()==QuestStatus.ACTIVE&&f.state().distinct()==null,"rotation pays old assignment once and starts fresh evidence");
            f.restart();check(f.coins()==7&&f.state().distinct()==null&&f.profile().getDailyQuestState().cycleId().isPresent(),"rotated aggregate persists without duplicate old reward");
        }
    }

    private static void dailySaveRollback() throws Exception {
        try(var f=new IntegrityFixture()) {
            f.pending(1,true);f.economy.setCoins(f.id,0);f.quests.retryPendingRewards(f.id);var completed=f.state();
            var day1=java.time.Instant.parse("2026-11-01T12:00:00Z");f.profile().getDailyQuestState().setCycleId(new dev.vapee.core.quest.daily.DailyQuestCycleResolver().resolve(day1,java.time.ZoneId.of("UTC"),java.time.LocalTime.MIDNIGHT));
            f.players.savePlayer(f.id);var priorCycle=f.profile().getDailyQuestState().cycleId();f.define(2,KEY);f.failSave=true;
            check(f.daily().forcePlayer(f.id,day1.plusSeconds(86400))==dev.vapee.core.quest.daily.DailyQuestSyncResult.ASSIGNMENT_FAILED,"failed real Force commit reports failure");
            unchanged(f,completed,"failed Force restores completed numeric/evidence snapshot");
            check(f.profile().getDailyQuestState().cycleId().equals(priorCycle)&&f.coins()==7,"failed Force restores cycle without another reward");
            f.failSave=false;f.restart();unchanged(f,completed,"Force rollback snapshot persists");
        }
    }

    private static void independentPending() throws Exception {
        try(var f=new IntegrityFixture()) {
            f.registry.replaceAll(List.of(definition("one",1),definition("two",2)));f.quests.replaceAssignments(f.id,List.of("one","two"));f.economy.setCoins(f.id,Long.MAX_VALUE);
            f.quests.addDistinctProgress(f.id,KEY,"warp:a");var first=f.state();var second=f.profile().getQuestState().snapshot().get("two");
            f.registry.replaceAll(List.of(definition("one",3),definition("two",2)));f.quests.retryPendingRewards(f.id);
            unchanged(f,first,"first incompatible pending does not normalize in same-key fanout");
            check(f.profile().getQuestState().snapshot().get("two").equals(second),"retry does not change second active assignment");
            f.quests.addProgress(f.id,KEY,20);unchanged(f,first,"numeric fanout cannot bypass incompatible signature");
            f.quests.addDistinctProgress(f.id,KEY,"warp:b");unchanged(f,first,"distinct fanout keeps first pending evidence unchanged");
            var two=f.profile().getQuestState().snapshot().get("two");
            check(two.status()==QuestStatus.REWARD_PENDING&&two.progress()==2&&two.distinct().identities().equals(Set.of("warp:a","warp:b")),"second quest independently reaches its own target");
            f.economy.setCoins(f.id,0);f.quests.retryPendingRewards(f.id);unchanged(f,first,"second reward does not unlock first");
            check(f.coins()==7&&f.profile().getQuestState().snapshot().get("two").status()==QuestStatus.COMPLETED,"only compatible second quest pays");
            f.registry.replaceAll(List.of(definition("one",1),definition("two",2)));f.quests.retryPendingRewards(f.id);f.quests.retryPendingRewards(f.id);
            check(f.coins()==14&&f.state().status()==QuestStatus.COMPLETED,"rollback independently pays first exactly once");
            f.restart();check(f.coins()==14&&f.profile().getQuestState().snapshot().get("two").equals(two.with(2,QuestStatus.COMPLETED)),"both independent completed evidence sets persist");
        }
    }

    private static final class IntegrityFixture implements AutoCloseable {
        final Path directory=Files.createTempDirectory("vapeecore-pending-integrity-");
        final java.util.logging.Logger logger=java.util.logging.Logger.getAnonymousLogger();
        final QuestDefinitionRegistry registry=new QuestDefinitionRegistry();
        final UUID id=UUID.randomUUID(),ignored=UUID.randomUUID();
        FilePlayerRepository disk;PlayerService players;EconomyService economy;RewardService rewards;QuestService quests;
        int attempts;boolean failSave;long firstJoin;
        IntegrityFixture() throws Exception {
            logger.setUseParentHandlers(false);reloadOnly();players.loadPlayer(id,"Integrity");firstJoin=profile().getFirstJoin().toEpochMilli();
            new dev.vapee.core.player.settings.PlayerSettingsService(players).setSoundsEnabled(id,false);
            profile().getSocial().ignore(ignored);players.savePlayer(id);
            var yaml=YamlConfiguration.loadConfiguration(file().toFile());yaml.set("external-extension.literal","preserved");yaml.save(file().toFile());
        }
        void reloadOnly() {
            disk=new FilePlayerRepository(directory,logger);disk.initialize();
            players=new PlayerService(new dev.vapee.core.player.repository.PlayerRepository() {
                public Optional<dev.vapee.core.player.CorePlayer> findByUniqueId(UUID player){return disk.findByUniqueId(player);}
                public void save(dev.vapee.core.player.CorePlayer player){if(failSave)throw new IllegalStateException("controlled integrity save failure");disk.save(player);}
                public boolean exists(UUID player){return disk.exists(player);}
            },logger);
            economy=new EconomyService(players);rewards=new RewardService(economy,players,logger);
            quests=new QuestService(registry,players,(player,coins,source,reason)->{attempts++;return rewards.grantCoins(player,coins,source,reason);},logger);
            if(disk.exists(id))players.loadPlayer(id,"Integrity");
        }
        dev.vapee.core.player.CorePlayer profile(){return players.getPlayer(id).orElseThrow();}
        PlayerQuestProgress state(){return profile().getQuestState().snapshot().get("one");}
        Path file(){return directory.resolve(id+".yml");}
        long coins(){return profile().getWallet().getCoins();}
        void define(long target,QuestProgressKey key){registry.replaceAll(List.of(QuestCompletionFixture.definition("one",key.value(),target,7)));}
        void pending(int target,boolean distinct) {
            define(target,KEY);quests.replaceAssignments(id,List.of("one"));economy.setCoins(id,Long.MAX_VALUE);
            if(distinct)for(int i=0;i<target;i++)quests.addDistinctProgress(id,KEY,"warp:w"+i);
            else quests.addProgress(id,KEY,target);
            check(state().status()==QuestStatus.REWARD_PENDING,"fixture reaches genuine reward-pending through real RewardService");
            players.savePlayer(id);
        }
        void restart() {
            players.unloadPlayer(id);reloadOnly();
            check(!profile().getSettings().isSoundsEnabled()&&profile().getSocial().isIgnoring(ignored)&&profile().getFirstJoin().toEpochMilli()==firstJoin,"full-profile restart preserves unrelated settings/social/identity");
            check("preserved".equals(YamlConfiguration.loadConfiguration(file().toFile()).getString("external-extension.literal")),"full-profile saves preserve foreign YAML extension");
        }
        dev.vapee.core.quest.daily.DailyQuestService daily(){return new dev.vapee.core.quest.daily.DailyQuestService(players,quests,registry,
                ()->new dev.vapee.core.quest.daily.DailyQuestConfig.State(true,1,java.time.LocalTime.MIDNIGHT,"UTC",java.time.ZoneId.of("UTC"),registry.snapshot()),logger);}
        public void close() throws Exception {
            Path checked=directory.toAbsolutePath().normalize();
            if(!checked.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())||!checked.getFileName().toString().startsWith("vapeecore-pending-integrity-"))throw new IllegalStateException("Unexpected test cleanup path");
            try(var files=Files.walk(checked)){for(var p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

package dev.vapee.core.quest;

import dev.vapee.core.module.ModuleRuntimeFixture;
import org.bukkit.event.player.*;
import java.util.*;

public final class QuestCommunityChatHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        duplicateGuardRegression();
        classification();boundaries();handoff();epochs();bounds();
        System.out.println("QuestCommunityChatHarness passed " + checks + " checks.");
    }
    private static void duplicateGuardRegression() {
        Map<String, Runnable> scenarios = new LinkedHashMap<>();
        scenarios.put("A duplicate inside cooldown", () -> nonmatchingDuplicate(false));
        scenarios.put("B duplicate after cooldown", () -> nonmatchingDuplicate(true));
        scenarios.put("C unchanged cooldown boundary", QuestCommunityChatHarness::nonmatchingCooldown);
        scenarios.put("fingerprint expiry not extended", QuestCommunityChatHarness::nonmatchingExpiry);
        scenarios.put("F negative content preserves guard", QuestCommunityChatHarness::negativeContent);
        scenarios.put("F completed and unrelated quests", QuestCommunityChatHarness::completedAndUnrelated);
        for (int mask = 1; mask <= 7; mask++) {
            int assigned = mask;
            scenarios.put("D assigned chat keys " + mask, () -> mixedGuard(assigned));
        }
        for (String epoch : List.of("assign", "force", "reset", "catalog", "rollback", "session", "runtime"))
            scenarios.put("E valid epoch " + epoch, () -> guardEpoch(epoch));
        List<AssertionError> failures = new ArrayList<>();
        scenarios.forEach((name, scenario) -> {
            try { scenario.run(); }
            catch (AssertionError failure) {
                System.err.println("REGRESSION FAILED [" + name + "]: " + failure.getMessage());
                failures.add(failure);
            }
        });
        if (!failures.isEmpty()) {
            AssertionError failure = new AssertionError(failures.size() + " duplicate-guard scenarios failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void nonmatchingDuplicate(boolean afterCooldown) {
        try (var f = new CommunityTestSupport()) {
            var a = f.actor("Alpha");
            f.define("unique", "social:chat_unique", 2, 7);f.assign(a, "unique");
            f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == 1 && f.coins(a) == 0, "first unique message counted without reward");
            f.seconds(1);f.say(a, "Hi");
            check(f.progress(a, "unique") == 1 && f.feedback.isEmpty(), "unassigned greeting creates no progress/feedback");
            if (afterCooldown) f.seconds(2);
            f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == 1, "expected progress=1 after intervening Hi, actual=" + f.progress(a, "unique") + ", afterCooldown=" + afterCooldown);
            check(f.coins(a) == 0 && f.feedback.isEmpty(), "duplicate cannot complete quest or award coins");
        }
    }

    private static void nonmatchingCooldown() {
        try (var f = new CommunityTestSupport()) {
            var a = f.actor("Alpha");f.define("unique", "social:chat_unique", 10, 7);f.assign(a, "unique");
            f.say(a, "Schöne Lobby!");f.seconds(2);f.say(a, "Hi");
            f.social.now = 3_000_000_000L - 1;
            f.say(a, "Ein schöner Server!");
            check(f.progress(a, "unique") == 1, "Hi must not shorten cooldown: expected=1, actual=" + f.progress(a, "unique"));
            f.social.now++;
            f.say(a, "Ein schöner Server!");
            check(f.progress(a, "unique") == 2, "Hi must not restart cooldown; different message counts at exactly three seconds");
            f.seconds(2);f.say(a, "Hi");f.seconds(1);f.say(a, "Eine hilfreiche Community!");
            check(f.progress(a, "unique") == 3 && f.coins(a) == 0, "nonmatching messages also preserve the next cooldown boundary");
        }
    }

    private static void nonmatchingExpiry() {
        try (var f = new CommunityTestSupport()) {
            var a = f.actor("Alpha");f.define("unique", "social:chat_unique", 10, 7);f.assign(a, "unique");
            f.say(a, "Schöne Lobby!");f.seconds(119);f.say(a, "Hi");
            f.social.now = 120_000_000_000L - 1;f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == 1, "Hi must not erase fingerprint before expiry: actual=" + f.progress(a, "unique"));
            f.social.now++;f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == 2, "Hi must not extend fingerprint expiry beyond exactly 120 seconds");
        }
    }

    private static void mixedGuard(int mask) {
        try (var f = new CommunityTestSupport()) {
            var a = f.actor("Alpha");
            String[] ids = {"hello", "thanks", "unique"};
            String[] keys = {"social:chat_hello", "social:chat_thanks", "social:chat_unique"};
            List<String> assigned = new ArrayList<>();
            Map<String, Long> expected = new TreeMap<>();
            for (int i = 0; i < ids.length; i++) {
                f.define(ids[i], keys[i], 10, 7);
                if ((mask & (1 << i)) != 0) { assigned.add(ids[i]);expected.put(ids[i], 0L); }
            }
            f.assign(a, assigned.toArray(String[]::new));
            f.say(a, "Hello dear friends");
            if (expected.containsKey("hello")) expected.put("hello", 1L);
            if (expected.containsKey("unique")) expected.put("unique", 1L);
            check(counts(f, a.id).equals(expected), "first message credits only matching assigned keys " + mask);
            boolean initialMatch = (mask & 5) != 0;
            f.seconds(1);f.say(a, "ok");f.say(a, "Thanks dear friends");
            if (!initialMatch && expected.containsKey("thanks")) expected.put("thanks", 1L);
            check(counts(f, a.id).equals(expected), "shared cooldown survives nonmatching content " + mask + ": expected=" + expected + ", actual=" + counts(f, a.id));
            f.seconds(2);f.say(a, "Thanks dear friends");
            if (initialMatch) {
                if (expected.containsKey("thanks")) expected.put("thanks", 1L);
                if (expected.containsKey("unique")) expected.put("unique", 2L);
            }
            check(counts(f, a.id).equals(expected), "shared cooldown is not extended across keys " + mask);
            f.say(a, "ok");f.say(a, "Hello dear friends");
            check(counts(f, a.id).equals(expected) && f.coins(a) == 0, "fingerprints span keys without duplicate rewards " + mask);
        }
    }

    private static Map<String, Long> counts(CommunityTestSupport f, UUID id) {
        Map<String, Long> result = new TreeMap<>();
        f.social.players.getPlayer(id).orElseThrow().getQuestState().snapshot()
                .forEach((key, value) -> result.put(key, value.progress()));
        return result;
    }

    private static void guardEpoch(String epoch) {
        try (var f = new CommunityTestSupport()) {
            var a = f.actor("Alpha");f.define("unique", "social:chat_unique", 20, 7);f.assign(a, "unique");
            f.say(a, "Schöne Lobby!");f.seconds(1);f.say(a, "Hi");
            f.producer.onChat(f.event(a, "Queued before epoch transition", true));
            boolean numericReset = epoch.equals("assign") || epoch.equals("reset");
            switch (epoch) {
                case "assign" -> f.assign(a, "unique");
                case "force" -> f.quests.reconcileAssignments(a.id, List.of("unique"));
                case "reset" -> { f.quests.clearAssignments(a.id);f.assign(a, "unique"); }
                case "catalog" -> f.definitions.replaceAll(f.definitions.snapshot());
                case "rollback" -> {
                    var previous = f.definitions.snapshot();
                    f.definitions.replaceAll(List.of());f.definitions.replaceAll(previous);
                }
                case "session" -> {
                    f.producer.onQuit(new PlayerQuitEvent(a.player, net.kyori.adventure.text.Component.empty()));
                    a.online = false;a = f.social.user(a.id, "Alpha");f.producer.seed(a.player);
                }
                case "runtime" -> {
                    f.active = false;f.drain();f.producer.close();
                    f.producer.bind(f.chat, f.pm, f.friends, f.social.requests, f.muted::contains, () -> f.available);
                    f.producer.seed(a.player);f.active = true;
                }
                default -> throw new AssertionError(epoch);
            }
            f.drain();long retained = numericReset ? 0 : 1;
            check(f.progress(a, "unique") == retained, "old queued event cannot cross epoch " + epoch);
            f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == retained + 1, "legitimate epoch permits fresh measurement " + epoch);
            f.say(a, "Hi");f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == retained + 1 && f.coins(a) == 0, "new epoch establishes a fresh guard that Hi cannot erase " + epoch);
        }
    }

    private static void negativeContent() {
        try (var f = new CommunityTestSupport()) {
            var a = f.actor("Alpha");f.define("unique", "social:chat_unique", 2, 7);f.assign(a, "unique");
            f.say(a, "Schöne Lobby!");f.seconds(3);
            for (String text : List.of("", " ", "/quests", "aaaaaaa", "Hi")) {
                f.say(a, text);f.say(a, "Schöne Lobby!");
                check(f.progress(a, "unique") == 1 && f.coins(a) == 0, "negative candidate retains duplicate protection: " + text);
            }
            f.muted.add(a.id);f.say(a, "A muted different message");f.muted.clear();
            var cancelled = f.event(a, "A cancelled different message", true);cancelled.setCancelled(true);
            f.producer.onChat(cancelled);f.drain();f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == 1 && f.feedback.isEmpty(), "muted/cancelled events cannot clear fingerprints or trigger rewards");
        }
    }

    private static void completedAndUnrelated() {
        try (var f = new CommunityTestSupport()) {
            var a = f.actor("Alpha");f.define("unique", "social:chat_unique", 1, 7);
            f.define("private", "social:pm_sent", 2, 11);f.assign(a, "unique", "private");
            f.say(a, "Schöne Lobby!");f.say(a, "Hi");f.seconds(3);f.say(a, "Schöne Lobby!");
            check(f.progress(a, "unique") == 1 && f.progress(a, "private") == 0 && f.coins(a) == 7 && f.feedback.size() == 1,
                    "completed quest and unrelated key cannot earn extra progress/rewards");
            f.assign(a, "private");f.say(a, "Hello dear friends");f.say(a, "Hi");f.say(a, "Hello dear friends");
            check(f.progress(a, "private") == 0 && f.coins(a) == 7 && f.feedback.size() == 1,
                    "no matching active chat key emits no progress or reward");
        }
    }
    private static void classification() {
        for (String text : List.of("Hallo!","HI","hey Leute","Hello everyone","Ｈｉ"))
            check(CommunityChatText.parse(text).hello(),"word greeting " + text);
        for (String text : List.of("Danke!","THANKS","Thank   you","Vielen Dank!","Ｄａｎｋｅ"))
            check(CommunityChatText.parse(text).thanks(),"word thanks " + text);
        for (String text : List.of("this is nice","something fun","highlights","whichever","behind","shiny","hi\u0338"))
            check(!CommunityChatText.parse(text).hello(),"embedded hi is not greeting " + text);
        for (String text : List.of("thanksgiving","dankeschön","unthanks","dankeswort"))
            check(!CommunityChatText.parse(text).thanks(),"embedded thanks not matched " + text);
        for (String text : List.of("", " ","/hello","/msg Bob Hi","aaaaaaa","abababab","abcabcabc","ha ha ha","!!!!","123123123","12345678","hiiiiii","\u200Bhello","Hi\u0001"))
            check(CommunityChatText.parse(text) == null,"rejected spam/command " + text);
        check(CommunityChatText.parse("x".repeat(257)) == null,"input bounded before normalization");
        check(!CommunityChatText.parse("Hallo").unique(),"short greeting does not manufacture unique progress");
        check(CommunityChatText.parse("Nice lobby!").unique(),"meaningful unique sentence");
        check(CommunityChatText.parse("  HELLO\t friends  ").fingerprint()
                .equals(CommunityChatText.parse("hello friends").fingerprint()),"NFKC/case/whitespace stable digest");
        check(CommunityChatText.parse("Hello, friends!").fingerprint().equals(CommunityChatText.parse("hello friends.").fingerprint()),"punctuation-only changes cannot evade duplicate detection");
    }
    private static void boundaries() {
        try (var f = new CommunityTestSupport()) {
            var a=f.actor("Alpha");f.define("hello","social:chat_hello",10,7);f.define("thanks","social:chat_thanks",10,7);
            f.define("three","social:chat_unique",3,7);f.define("six","social:chat_unique",6,7);
            f.assign(a,"hello","thanks","three","six");
            f.say(a,"Hallo friends!");check(f.progress(a,"hello")==1&&f.progress(a,"three")==1&&f.progress(a,"six")==1,"one event updates all assigned same-key quests");
            f.seconds(3);f.say(a,"  HALLO   FRIENDS! ");check(f.progress(a,"three")==1,"normalized duplicate not counted");
            f.say(a,"Thanks everyone!");check(f.progress(a,"thanks")==1&&f.progress(a,"three")==2,"duplicate rejection does not move cooldown");
            f.say(a,"Another meaningful sentence");check(f.progress(a,"three")==2,"three second cooldown");
            f.seconds(3);f.say(a,"Another meaningful sentence");check(f.progress(a,"three")==3&&f.progress(a,"six")==3&&f.coins(a)==7,"separate targets and exactly one central reward");
            f.seconds(3);f.say(a,"A fourth meaningful sentence");check(f.progress(a,"three")==3&&f.progress(a,"six")==4&&f.coins(a)==7,"completed quest remains capped");
            var event=f.event(a,"Hello cancelled friends",true);event.setCancelled(true);f.producer.onChat(event);f.drain();
            check(f.progress(a,"hello")==1,"cancelled event not counted");
            f.seconds(3);f.muted.add(a.id);f.say(a,"Hello muted friends");check(f.progress(a,"hello")==1,"active mute denied");f.muted.clear();
            f.chatEnabled(false);f.say(a,"Hello disabled friends");check(f.progress(a,"hello")==1,"disabled chat denied");f.chatEnabled(true);
            event=f.event(a,"Hello no audience",true);event.viewers().clear();f.producer.onChat(event);f.drain();check(f.progress(a,"hello")==1,"empty delivery audience denied");
            f.say(a,"/hello command");check(f.progress(a,"hello")==1,"command-like content denied");
            f.available=false;f.say(a,"Hello unavailable owner");check(f.progress(a,"hello")==1,"unavailable source owner denied");f.available=true;
            f.say(a,"Hello available owner");check(f.progress(a,"hello")==2,"failed source observation does not consume duplicate state");
        }
    }
    private static void handoff() throws Exception {
        try (var f=new CommunityTestSupport()) {
            var a=f.actor("Alpha");f.define("chat","social:chat_unique",20,1);f.assign(a,"chat");
            f.primary=false;
            Thread thread=new Thread(()->f.producer.onChat(f.event(a,"A safe async message",true)));thread.start();thread.join();
            check(f.progress(a,"chat")==0&&f.callbacks.size()==1,"async callback only enqueues immutable signal");
            boolean refused=false;try { f.callbacks.peek().run(); } catch(IllegalStateException expected){refused=true;}
            check(refused&&f.progress(a,"chat")==0,"wrong-thread drain cannot reach QuestService");
            f.drain();check(f.progress(a,"chat")==1,"main-thread continuation reaches actual quest service");
            f.seconds(3);f.producer.onChat(f.event(a,"Cancelled by subsequent mute",true));f.muted.add(a.id);f.drain();
            check(f.progress(a,"chat")==1,"mute rechecked after handoff");f.muted.clear();
            f.producer.onChat(f.event(a,"Player quits before task",true));f.producer.onQuit(new PlayerQuitEvent(a.player,net.kyori.adventure.text.Component.empty()));
            a.online=false;f.social.online.remove(a.id);f.drain();check(f.progress(a,"chat")==1,"logout before continuation rejects old message");
            var replacement=f.social.user(a.id,"Alpha");f.producer.seed(replacement.player);
            f.producer.onChat(f.event(a,"Old player tries to chat",true));f.drain();check(f.progress(replacement,"chat")==1,"old session cannot borrow new session authority");
            f.say(replacement,"New session valid message");check(f.progress(replacement,"chat")==2,"rejoin permits fresh session");
            f.seconds(3);f.scheduleFailure=true;f.say(replacement,"Scheduler failure message");check(f.producer.pendingChats()==0&&f.progress(replacement,"chat")==2,"failed schedule clears bounded handoff without progress");
            f.scheduleFailure=false;f.say(replacement,"Scheduler recovery message");check(f.progress(replacement,"chat")==3&&!f.logs.isEmpty(),"scheduler recovers, failure diagnosed without retry");
        }
    }
    private static void epochs() {
        try(var f=new CommunityTestSupport()) {
            var a=f.actor("Alpha");f.define("chat","social:chat_unique",50,1);f.assign(a,"chat");f.say(a,"Same meaningful sentence");
            f.producer.onChat(f.event(a,"Queued before forced assignment",true));f.quests.reconcileAssignments(a.id,List.of("chat"));f.drain();
            check(f.progress(a,"chat")==1,"Force invalidates queued chat but retains numeric progress");
            f.say(a,"Same meaningful sentence");check(f.progress(a,"chat")==2,"Force clears duplicate/cooldown epoch");
            f.producer.onChat(f.event(a,"Queued before daily reset",true));f.assign(a,"chat");f.drain();check(f.progress(a,"chat")==0,"replacement invalidates queued chat");
            f.say(a,"Same meaningful sentence");check(f.progress(a,"chat")==1,"new assignment counts fresh message");
            f.producer.onChat(f.event(a,"Queued before catalog reload",true));f.definitions.replaceAll(f.definitions.snapshot());f.drain();check(f.progress(a,"chat")==1,"catalog publication invalidates queued pre-reload message");
            f.say(a,"Same meaningful sentence");check(f.progress(a,"chat")==2,"catalog change invalidates temporary duplicate state");
            f.producer.onChat(f.event(a,"Queued before chat reload",true));f.chatEnabled(true);f.drain();check(f.progress(a,"chat")==2,"chat configuration identity rechecked");
            f.producer.onChat(f.event(a,"Queued before disable",true));f.producer.close();f.drain();check(f.progress(a,"chat")==2&&f.producer.released()&&f.producer.trackedPlayers()==0,"disable removes observers and inerts queued work");
            f.producer.bind(f.chat,f.pm,f.friends,f.social.requests,f.muted::contains,()->f.available);f.producer.seed(a.player);
            f.say(a,"Same meaningful sentence");check(f.progress(a,"chat")==3,"explicit re-enable starts fresh temporary state");
            f.quests.clearAssignments(a.id);f.say(a,"Hello no assignments");check(f.producer.trackedPlayers()==0,"no matching quest retains no chat fingerprints");
            f.assign(a,"chat");f.say(a,"A loaded profile message");
            var profile=f.social.players.getPlayer(a.id).orElseThrow();f.social.players.unloadPlayer(a.id);f.seconds(3);f.say(a,"An unloaded profile message");
            check(profile.getQuestState().snapshot().get("chat").progress()==1&&f.producer.trackedPlayers()==0,"unloaded profile earns nothing and releases temporary chat state");
        }
    }
    private static void bounds() {
        try(var f=new CommunityTestSupport()) {
            var a=f.actor("Alpha");f.define("chat","social:chat_unique",1000,1);f.assign(a,"chat");
            for(int i=0;i<100;i++)f.producer.onChat(f.event(a,"Pending message number "+i,true));
            check(f.producer.pendingChats()==1&&f.callbacks.size()==1,"burst bounded to one pending signal and one shared drain");f.drain();
            check(f.progress(a,"chat")==1,"burst earns at most one observation");
            for(int i=0;i<40;i++){f.seconds(3);f.say(a,"A meaningful number "+i);}
            @SuppressWarnings("unchecked") var states=(Map<UUID,?>)ModuleRuntimeFixture.get(f.producer,"chatStates");
            var hashes=(Map<?,?>)ModuleRuntimeFixture.get(states.get(a.id),"fingerprints");
            check(hashes.size()==32&&hashes.keySet().stream().allMatch(k->k.toString().matches("[a-f0-9]{64}")),"history is exactly bounded SHA-256 digests, no chat bodies");
            long before=f.progress(a,"chat");f.seconds(3);f.say(a,"A meaningful number 39");check(f.progress(a,"chat")==before,"recent digest remains rejected");
            f.seconds(120);f.say(a,"A meaningful number 39");check(f.progress(a,"chat")==before+1,"documented transient window expires");
        }
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

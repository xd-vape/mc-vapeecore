package dev.vapee.core.quest.daily;

import dev.vapee.core.quest.QuestCompletionFixture;
import dev.vapee.core.quest.daily.menu.ResetCountdownFormatter;
import java.time.*;
import java.nio.file.*;
import java.util.*;

public final class DailyQuestResetHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var resolver = new DailyQuestCycleResolver();
        ZoneId utc = ZoneId.of("UTC"), berlin = ZoneId.of("Europe/Berlin");
        if (args.length > 0 && args[0].equals("dst-only")) {
            boundary(resolver, "2026-10-24T02:00:00Z", berlin, LocalTime.of(4,0), "2026-10-25T03:00:00Z", 25*3600);
            System.out.println("DailyQuestResetHarness passed "+checks+" checks."); return;
        }
        boundary(resolver, "2026-10-18T01:59:59Z", berlin, LocalTime.of(4,0), "2026-10-18T02:00:00Z", 1);
        boundary(resolver, "2026-10-18T02:00:00Z", berlin, LocalTime.of(4,0), "2026-10-19T02:00:00Z", 86400);
        boundary(resolver, "2026-10-18T02:00:01Z", berlin, LocalTime.of(4,0), "2026-10-19T02:00:00Z", 86399);
        boundary(resolver, "2026-10-18T23:59:59Z", utc, LocalTime.MIDNIGHT, "2026-10-19T00:00:00Z", 1);
        boundary(resolver, "2026-10-19T00:00:00Z", utc, LocalTime.MIDNIGHT, "2026-10-20T00:00:00Z", 86400);
        boundary(resolver, "2026-03-28T03:00:00Z", berlin, LocalTime.of(4,0), "2026-03-29T02:00:00Z", 23*3600);
        boundary(resolver, "2026-10-24T02:00:00Z", berlin, LocalTime.of(4,0), "2026-10-25T03:00:00Z", 25*3600);
        boundary(resolver, "2026-03-29T01:29:59Z", berlin, LocalTime.of(2,30), "2026-03-29T01:30:00Z", 1);
        boundary(resolver, "2026-03-29T01:30:00Z", berlin, LocalTime.of(2,30), "2026-03-30T00:30:00Z", 23*3600);
        boundary(resolver, "2026-10-25T00:29:59Z", berlin, LocalTime.of(2,30), "2026-10-25T00:30:00Z", 1);
        boundary(resolver, "2026-10-25T00:30:00Z", berlin, LocalTime.of(2,30), "2026-10-26T01:30:00Z", 25*3600);
        boundary(resolver, "2026-10-25T01:15:00Z", berlin, LocalTime.of(2,30), "2026-10-26T01:30:00Z", 24*3600+900);
        ZoneId system = ZoneId.systemDefault(); LocalDate date = LocalDate.of(2026,10,18);
        Instant systemStart = date.atTime(4,0).atZone(system).toInstant();
        check(resolver.nextReset(systemStart,system,LocalTime.of(4,0)).equals(date.plusDays(1).atTime(4,0).atZone(system).toInstant()), "system zone uses real local boundaries");
        for (var example : Map.of(0L,"00:00:00",1L,"00:00:01",3599L,"00:59:59",3600L,"01:00:00",21290L,"05:54:50",86399L,"23:59:59",86400L,"24:00:00",90000L,"25:00:00").entrySet())
            check(ResetCountdownFormatter.format(Duration.ofSeconds(example.getKey())).equals(example.getValue()),"elapsed format "+example.getValue());
        check(ResetCountdownFormatter.format(Duration.ofNanos(-1)).equals("00:00:00"),"negative fractional remaining duration clamps");
        check(ResetCountdownFormatter.format(Duration.ofSeconds(-3601)).equals("00:00:00"),"negative duration cannot display negative time");
        check(ResetCountdownFormatter.format(Duration.ofHours(125)).equals("125:00:00"),"hours do not truncate or wrap");

        Path directory=Files.createTempDirectory("vapeecore-reset-read-"); Path file=directory.resolve("daily-quests.yml");
        try {
            var domain=new QuestCompletionFixture(); var id=domain.load();
            Files.writeString(file,yaml("00:00","UTC")); var config=new DailyQuestConfig(file,domain.logger); config.initialize();
            domain.registry.replaceAll(config.getState().definitions());
            var daily=new DailyQuestService(domain.players,domain.quests,domain.registry,config::getState,domain.logger);
            Instant now=Instant.parse("2026-10-18T01:00:00Z"); var original=config.getState(); var definitions=domain.registry.snapshot();
            int saves=domain.repository.saves;
            check(daily.nextReset(now).equals(Instant.parse("2026-10-19T00:00:00Z")),"service reads active schedule");
            Files.writeString(file,yaml("04:00","Europe/Berlin")); byte[] operator=Files.readAllBytes(file);
            var plan=DailyQuestModule.prepareReloadPlan(config,domain.registry);
            check(config.getState()==original && daily.nextReset(now).equals(Instant.parse("2026-10-19T00:00:00Z")),"prepare publishes no new schedule");
            plan.apply();
            check(daily.nextReset(now).equals(Instant.parse("2026-10-18T02:00:00Z")),"reload apply updates real service reset API");
            plan.rollback();
            check(config.getState()==original && domain.registry.snapshot().equals(definitions) && daily.nextReset(now).equals(Instant.parse("2026-10-19T00:00:00Z")),"rollback restores schedule and catalog together");
            check(Arrays.equals(operator,Files.readAllBytes(file)),"read/prepare/apply/rollback never rewrite operator config");
            Files.writeString(file,"quests: [broken\n");
            boolean failed=false;try{DailyQuestModule.prepareReloadPlan(config,domain.registry);}catch(RuntimeException expected){failed=true;}
            check(failed && config.getState()==original && domain.registry.snapshot().equals(definitions),"invalid reload leaves old schedule and catalog authoritative");
            check(domain.repository.saves==saves && domain.players.getPlayer(id).orElseThrow().getQuestState().snapshot().isEmpty(),"reset reads never assign, persist or grant rewards");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
        System.out.println("DailyQuestResetHarness passed "+checks+" checks.");
    }
    private static void boundary(DailyQuestCycleResolver resolver,String time,ZoneId zone,LocalTime reset,String expected,long seconds){
        Instant now=Instant.parse(time),next=resolver.nextReset(now,zone,reset);
        check(next.equals(Instant.parse(expected)),"actual next boundary "+time+" "+zone+" "+reset);
        check(Duration.between(now,next).equals(Duration.ofSeconds(seconds)),"actual remaining duration "+time);
        check(resolver.resolve(next.minusNanos(1),zone,reset).date().plusDays(1).equals(resolver.resolve(next,zone,reset).date()),"countdown boundary agrees with actual cycle resolver");
    }
    private static String yaml(String time,String zone){return "enabled: true\nreset:\n  time: '"+time+"'\n  timezone: '"+zone+"'\nquests: {}\n";}
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}

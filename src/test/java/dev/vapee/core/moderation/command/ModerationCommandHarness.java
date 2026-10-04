package dev.vapee.core.moderation.command;

import static dev.vapee.core.moderation.ModerationTestSupport.*;
import dev.vapee.core.moderation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ModerationCommandHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        guards(); targets(); warnings(); bans(); unbans(); kicks(); history(); completion(); mutes(); descriptor();
        hierarchyMatrix(); hierarchyFreshness(); hierarchyAsync(); hierarchyCompletion();
        System.out.println("ModerationCommandHarness passed " + checks + " checks.");
    }

    private static TabExecutor command(Fixture f, String name) {
        return switch (name) {
            case "warn" -> new WarnCommand(f.context); case "ban" -> new BanCommand(f.context);
            case "unban" -> new UnbanCommand(f.context); case "kick" -> new KickCommand(f.context);
            case "history" -> new HistoryCommand(f.context); default -> throw new AssertionError(name);
            case "mute" -> new MuteCommand(f.context); case "unmute" -> new UnmuteCommand(f.context);
        };
    }
    private static String[] arguments(String name, String target) {
        return switch (name) {
            case "ban", "mute" -> new String[]{target, "7d", ATTACK};
            case "warn", "kick" -> new String[]{target, ATTACK};
            default -> new String[]{target};
        };
    }
    private static void run(TabExecutor c, Sender sender, String expected, String... args) {
        sender.output.clear();
        check(c.onCommand(sender.sender, null, "unused-alias", args), "handled executor " + Arrays.toString(args));
        check(sender.text().contains(expected), expected + "; received " + sender.text());
    }

    private static void guards() throws Exception {
        for (String name : List.of("warn", "ban", "unban", "kick", "history", "mute", "unmute")) {
            Fixture f = new Fixture(); TabExecutor c = command(f, name);
            f.staff.allowAll = false;
            run(c, f.staff, "permission", arguments(name, "Alex"));
            int reads = f.identityReads + f.nameReads;
            check(c.onTabComplete(f.staff.sender, null, name, new String[]{""}).isEmpty(), "permission-aware tab " + name);
            check(f.repository.saves == 0 && reads == 0 && f.target.output.isEmpty() && f.target.kicks.isEmpty(), "denied no lookup/state/external action " + name);
            f.staff.permissions.add("vapeecore.moderation." + name);
            run(c, f.staff, name.equals("history") ? "No moderation history" : "yourself", arguments(name, STAFF.toString()));
            run(c, f.staff, name.equals("history") ? "No moderation history" : "yourself", arguments(name, "sTaFf"));
            check(f.repository.saves == 0, "self never mutates " + name);
            Sender unsupported = new Sender(CommandSender.class, null, "CommandBlock", f.events);
            run(c, unsupported, "Only players and the server console", arguments(name, "Alex"));
            check(c.onTabComplete(unsupported.sender, null, name, new String[]{""}).isEmpty(), "unsupported tab no actor guess " + name);
            run(c, f.console, "Missing player", new String[0]);
            check(f.repository.saves == 0, "bad syntax no save " + name);
        }
        for (String name : List.of("warn", "ban", "kick", "mute")) {
            Fixture f = new Fixture(); TabExecutor c = command(f, name);
            boolean timed = name.equals("ban") || name.equals("mute");
            run(c, f.console, timed ? "Missing duration and reason" : "Missing reason", "Alex");
            if (timed) run(c, f.console, "Missing reason", "Alex", "7d");
            run(c, f.console, "Missing reason", timed ? new String[]{"Alex", "7d", "  "} : new String[]{"Alex", "  "});
            check(f.repository.saves == 0, "missing reason has no mutation " + name);
            for (String bad : List.of("x".repeat(257), "bad\nreason", "bad\u2028reason", "bad\u0000reason")) {
                run(c, f.console, "Invalid reason", timed ? new String[]{"Alex", "7d", bad} : new String[]{"Alex", bad});
                check(f.repository.saves == 0 && f.info() == 0 && f.severe() == 0, "invalid reason normal rejection " + name);
            }
        }
    }

    private static void targets() throws Exception {
        for (String name : List.of("warn", "ban", "unban", "kick", "history", "mute", "unmute")) {
            Fixture f = new Fixture(); TabExecutor c = command(f, name);
            for (String unknown : List.of("Missing", UUID.randomUUID().toString(), "@a", "Ale")) {
                run(c, f.console, "not known", arguments(name, unknown));
                check(f.repository.saves == 0 && f.info() == 0, "unknown/no phantom " + name);
            }
            f.addKnown(new UUID(0, 92), "Alex");
            run(c, f.console, "ambiguous", arguments(name, "aLeX"));
            check(f.repository.saves == 0 && f.target.kicks.isEmpty(), "ambiguous no action " + name);
            f.failIdentity = true;
            run(c, f.console, "could not be completed", arguments(name, TARGET.toString()));
            check(f.severe() == 1 && f.repository.saves == 0, "lookup error controlled " + name);
        }
    }

    private static void warnings() throws Exception {
        for (String target : List.of("aLeX", TARGET.toString(), "Offline", OFFLINE.toString())) {
            Fixture f = new Fixture(); var c = new WarnCommand(f.context);
            f.repository.beforeSave = () -> check(f.target.output.isEmpty() && f.info() == 0, "warn notification/audit only after save");
            run(c, f.console, "Warned", target, ATTACK);
            var record = f.service.getAllRecords().getFirst();
            check(record.action() == ModerationAction.WARNING && record.actor().equals(ModerationActor.console()), "console warning actor");
            check(record.reason().equals(ATTACK) && f.console.text().contains(ATTACK), "literal warn reason");
            check(f.repository.saves == 1 && f.info() == 1 && f.severe() == 0, "warning saved/audited once");
            String audit = f.logs.getFirst().getMessage();
            check(audit.contains("actor=CONSOLE") && audit.contains("target=" + record.targetId())
                    && audit.contains("record=" + record.id()) && audit.contains("action=WARN") && audit.contains("name="), "complete success audit fields");
            check(f.console.output.stream().allMatch(ModerationTestSupport::noEvents), "warning feedback no injection");
            if (record.targetId().equals(TARGET)) {
                check(f.target.text().contains("You received a warning.") && f.target.text().contains(ATTACK), "online warning");
                check(f.target.output.stream().allMatch(ModerationTestSupport::noEvents), "warning target literal");
            } else check(f.target.output.isEmpty() && f.online.size() == 2, "offline no queued notification");
            check(f.target.kicks.isEmpty() && f.playerWrites == 0, "warning never kick/player mutation");
        }
        Fixture f = new Fixture();
        run(new WarnCommand(f.context), f.staff, "Warned Alex", "Alex", "first", "second");
        check(f.service.getHistory(TARGET).getFirst().actor().equals(ModerationActor.player(STAFF))
                && f.service.getHistory(TARGET).getFirst().reason().equals("first second"), "player actor/multipart reason");
        Fixture failed = new Fixture(); failed.repository.fail = true;
        run(new WarnCommand(failed.context), failed.console, "could not be saved", "Alex", ATTACK);
        check(failed.target.output.isEmpty() && failed.repository.saved.records().isEmpty()
                && failed.service.getAllRecords().isEmpty() && failed.info() == 0 && failed.severe() == 1, "warning save failure atomic/no false success");
        check(failed.logs.getFirst().getMessage().contains(TARGET.toString()) && failed.logs.getFirst().getMessage().contains("CONSOLE")
                && failed.logs.getFirst().getMessage().contains("WARN") && !failed.console.text().contains("injected save failure"), "failure context/no internal error disclosure");
        Fixture notification = new Fixture(); notification.target.failNotification = true;
        run(new WarnCommand(notification.context), notification.console, "was saved, but", "Alex", "Warning");
        check(notification.service.getHistory(TARGET).size() == 1 && notification.info() == 1 && notification.severe() == 1, "notification failure preserves persisted warning");
    }

    private static void bans() throws Exception {
        for (String duration : List.of("permanent", "perm", "30s", "10m", "2h", "7d", "2w")) {
            Fixture f = new Fixture(); var c = new BanCommand(f.context);
            f.repository.beforeSave = () -> check(f.target.kicks.isEmpty() && f.info() == 0 && !f.service.isBanned(TARGET), "ban save before domain swap/external action");
            f.target.beforeKick = () -> check(f.repository.saves == 1 && f.service.isBanned(TARGET)
                    && f.info() == 1 && f.console.text().contains("Banned"), "ban persist/audit/feedback before disconnect");
            run(c, f.console, "Banned Alex", TARGET.toString(), duration, ATTACK);
            var record = f.service.getActiveBan(TARGET).orElseThrow();
            check(record.expiresAt().equals(ModerationDurationParser.parse(duration).expiresAt(NOW)), "ban expiry " + duration);
            check(f.target.kicks.size() == 1 && f.repository.saves == 1 && f.info() == 1, "ban online exactly once");
            check(plain(f.target.kicks.getFirst()).contains(ATTACK) && noEvents(f.target.kicks.getFirst()), "ban literal screen");
            check(f.target.kicks.getFirst().equals(ModerationComponents.banScreen(record)), "same screen as login");
            check(f.console.text().contains(record.expiresAt().isEmpty() ? "permanently" : "for " + duration), "human duration feedback");
            run(c, f.console, "already banned", "Alex", duration, "Duplicate");
            check(f.repository.saves == 1 && f.info() == 1 && f.target.kicks.size() == 1 && f.severe() == 0, "already ban no save/audit/kick");
        }
        for (String target : List.of("Offline", OFFLINE.toString())) {
            Fixture f = new Fixture(); run(new BanCommand(f.context), f.staff, "Banned Offline", target, "7d", "Offline ban");
            check(f.service.isBanned(OFFLINE) && f.target.kicks.isEmpty() && f.playerWrites == 0, "known offline ban " + target);
            check(f.service.getActiveBan(OFFLINE).orElseThrow().actor().equals(ModerationActor.player(STAFF)), "player ban actor");
        }
        for (String invalid : List.of("0m", "-1h", "+2h", "1.5h", "1,5h", "1e3s", "1d12h", "1x", " ", "9223372036854775807w", "9223372036854775807s")) {
            Fixture f = new Fixture(); run(new BanCommand(f.context), f.console, "Invalid duration", "Alex", invalid, "Reason");
            check(f.repository.saves == 0 && f.target.kicks.isEmpty() && f.info() == 0 && f.severe() == 0, "invalid duration no action " + invalid);
        }
        Fixture failed = new Fixture(); failed.repository.fail = true;
        run(new BanCommand(failed.context), failed.console, "could not be saved", "Alex", "7d", ATTACK);
        check(!failed.service.isBanned(TARGET) && failed.target.kicks.isEmpty() && failed.info() == 0 && failed.severe() == 1, "ban save failure no disconnect");
        Fixture external = new Fixture(); external.target.failKick = true;
        run(new BanCommand(external.context), external.console, "Ban was saved, but", "Alex", "permanent", "Reason");
        check(external.service.isBanned(TARGET) && external.repository.saved.records().size() == 1
                && external.info() == 1 && external.severe() == 1, "disconnect failure no rollback/reissue");
        Fixture stale = new Fixture(); stale.target.isOnline = false;
        run(new BanCommand(stale.context), stale.console, "Banned", "Alex", "7d", "Reason");
        check(stale.service.isBanned(TARGET) && stale.target.kicks.isEmpty(), "actual online presence checked");
    }

    private static void unbans() throws Exception {
        for (String target : List.of("Offline", OFFLINE.toString())) {
            for (String reason : List.of("", ATTACK, "  ")) {
                Fixture f = new Fixture(); var c = new UnbanCommand(f.context);
                run(c, f.console, "not currently banned", target);
                check(f.repository.saves == 0 && f.info() == 0 && f.severe() == 0, "not banned no save/log error");
                var original = f.service.issueBan(OFFLINE, ModerationActor.console(), "Reason", Optional.empty()).record().orElseThrow();
                run(c, f.staff, "Unbanned Offline", reason.isEmpty() ? new String[]{target} : new String[]{target, reason});
                var revoked = f.service.getRecord(original.id()).orElseThrow();
                check(!f.service.isBanned(OFFLINE) && revoked.id().equals(original.id()) && f.service.getHistory(OFFLINE).size() == 1, "same record revoked");
                check(revoked.revocation().orElseThrow().reason().equals(reason.isBlank() ? Optional.empty() : Optional.of(reason)), "optional literal revoke reason");
                check(revoked.revocation().orElseThrow().actor().equals(ModerationActor.player(STAFF)), "player unban actor");
                check(f.info() == 1 && f.target.output.isEmpty() && f.target.kicks.isEmpty(), "unban audit/no target notification");
            }
        }
        Fixture failed = new Fixture(); failed.service.issueBan(TARGET, ModerationActor.console(), "Reason", Optional.empty());
        failed.repository.fail = true;
        run(new UnbanCommand(failed.context), failed.console, "could not be saved", "Alex", ATTACK);
        check(failed.service.isBanned(TARGET) && failed.info() == 0 && failed.severe() == 1, "failed revoke active state unchanged");
        Fixture expired = new Fixture(); expired.service.issueBan(TARGET, ModerationActor.console(), "Reason", Optional.of(NOW.plusSeconds(1)));
        expired.clock.now = NOW.plusSeconds(1);
        run(new UnbanCommand(expired.context), expired.console, "not currently banned", "Alex");
        check(expired.repository.saves == 1, "expired unban no cleanup/write");
        Fixture console = new Fixture(); console.service.issueBan(OFFLINE, ModerationActor.player(STAFF), "Ban", Optional.empty());
        run(new UnbanCommand(console.context), console.console, "Unbanned", OFFLINE.toString());
        check(console.service.getHistory(OFFLINE).getFirst().revocation().orElseThrow().actor().equals(ModerationActor.console()), "console successful revoke actor");
    }

    private static void kicks() throws Exception {
        for (String target : List.of("Alex", TARGET.toString())) {
            Fixture f = new Fixture(); var c = new KickCommand(f.context);
            f.repository.beforeSave = () -> check(f.target.kicks.isEmpty() && f.info() == 0, "kick no external action before save");
            f.target.beforeKick = () -> check(f.repository.saves == 1 && f.service.getHistory(TARGET).size() == 1 && f.info() == 1, "kick record/audit before disconnect");
            run(c, f.console, "Kicked Alex", target, ATTACK);
            check(f.service.getHistory(TARGET).getFirst().action() == ModerationAction.KICK && !f.service.isBanned(TARGET), "history-only kick");
            check(f.target.kicks.size() == 1 && plain(f.target.kicks.getFirst()).contains("You were kicked")
                    && !plain(f.target.kicks.getFirst()).contains("You are banned"), "distinct kick screen");
            check(plain(f.target.kicks.getFirst()).contains(ATTACK) && noEvents(f.target.kicks.getFirst()), "literal kick");
        }
        Fixture offline = new Fixture(); run(new KickCommand(offline.context), offline.console, "not currently online", "Offline", "Reason");
        check(offline.service.getAllRecords().isEmpty() && offline.repository.saves == 0 && offline.info() == 0, "no offline kick history");
        Fixture failed = new Fixture(); failed.repository.fail = true;
        run(new KickCommand(failed.context), failed.staff, "could not be saved", TARGET.toString(), "Reason");
        check(failed.target.kicks.isEmpty() && failed.service.getAllRecords().isEmpty() && failed.info() == 0 && failed.severe() == 1, "failed kick save never disconnects");
        Fixture external = new Fixture(); external.target.failKick = true;
        run(new KickCommand(external.context), external.console, "Kick record was saved, but", "Alex", "Reason");
        check(external.service.getHistory(TARGET).size() == 1 && external.repository.saves == 1 && external.info() == 1 && external.severe() == 1, "external kick failure preserves fact");
    }

    private static void history() throws Exception {
        Fixture f = new Fixture(); var c = new HistoryCommand(f.context);
        run(c, f.console, "No moderation history for Offline", OFFLINE.toString());
        for (String bad : List.of("0", "-1", "+1", "1.5", "1e3", "bad", "2147483648", " ")) {
            run(c, f.console, "positive whole number", "Alex", bad);
        }
        run(c, f.console, "Too many arguments", "Alex", "1", "extra");
        f.service.issueWarning(TARGET, ModerationActor.player(STAFF), ATTACK);
        run(c, f.staff, "Actor: Staff", TARGET.toString());
        check(f.staff.text().contains("Recorded") && f.staff.text().contains(ATTACK), "single warning/actor/literal reason");
        for (int i = 1; i <= 5; i++) {
            f.clock.now = NOW.plusSeconds(i);
            f.service.issueWarning(TARGET, i == 1 ? ModerationActor.player(new UUID(0, 800)) : ModerationActor.console(), "Reason " + i);
        }
        f.clock.now = NOW.plusSeconds(6);
        f.service.issueBan(TARGET, ModerationActor.console(), "Active ban", Optional.empty());
        int saves = f.repository.saves;
        run(c, f.console, "Page 1/2", "Alex", "1");
        check(f.console.text().contains("Active ban") && f.console.text().contains("Permanent") && f.console.text().contains("Active"), "active permanent history");
        check(f.console.text().indexOf("Active ban") < f.console.text().indexOf("Reason 5")
                && !f.console.text().contains("Reason 1"), "newest first/page size five");
        check(clicks(f.console.output).equals(List.of("/history " + TARGET + " 2")), "next UUID suggestion only");
        run(c, f.console, "Page 2/2", TARGET.toString(), "2");
        check(f.console.text().contains(new UUID(0, 800).toString()) && f.console.text().contains(ATTACK), "unknown actor fallback/history literal");
        check(clicks(f.console.output).equals(List.of("/history " + TARGET + " 1")), "previous suggestion");
        check(f.console.output.stream().anyMatch(ModerationCommandHarness::hasRecordHover), "full record ID available in hover");
        run(c, f.console, "Page does not exist", "Alex", "2147483647");
        check(f.repository.saves == saves, "pagination no writes");
        f.service.revokeBan(TARGET, ModerationActor.player(STAFF), Optional.of(ATTACK));
        run(c, f.console, "Revoked by: Staff", "Alex");
        check(f.console.text().contains("Revocation reason: " + ATTACK) && f.console.text().contains("Console"), "revoked actor/reason/console actor");
        f.service.issueBan(OFFLINE, ModerationActor.console(), "Expired ban", Optional.of(f.clock.now.plusSeconds(1)));
        f.clock.now = f.clock.now.plusSeconds(1);
        run(c, f.console, "Expired", "Offline");
        check(f.console.text().contains("UTC"), "history UTC expiry");
        f.service.issueMute(OFFLINE, ModerationActor.console(), "Active mute", Optional.empty());
        run(c, f.console, "MUTE", "Offline");
        check(f.console.text().contains("Active") && f.console.text().contains("Permanent"), "foundation mute rendered without command/enforcement");
        var future = new ModerationRecord(UUID.randomUUID(), ModerationAction.BAN, OFFLINE, ModerationActor.console(), "Future", f.clock.now.plusSeconds(1), Optional.empty(), Optional.empty());
        check(ModerationComponents.status(future, f.clock.now).equals("Inactive"), "future record status");
        f.service.recordKick(OFFLINE, ModerationActor.console(), "Recorded kick");
        run(c, f.console, "KICK", "Offline");
        check(f.console.text().contains("Recorded"), "kick history state");
        f.service.issueWarning(STAFF, ModerationActor.console(), "Staff history");
        run(c, f.staff, "Staff history", STAFF.toString());
        check(f.severe() == 0, "history has no errors");
    }

    private static List<String> clicks(List<Component> output) {
        var values = new ArrayList<String>(); output.forEach(c -> collectClicks(c, values)); return values;
    }
    private static void collectClicks(Component c, List<String> values) {
        if (c.clickEvent() != null) {
            check(c.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND, "navigation never runs");
            values.add(c.clickEvent().value());
        }
        c.children().forEach(child -> collectClicks(child, values));
    }
    private static boolean hasRecordHover(Component c) {
        return c.hoverEvent() != null && c.hoverEvent().value() instanceof Component hover
                && plain(hover).matches("Record ID: [0-9a-f-]{36}") || c.children().stream().anyMatch(ModerationCommandHarness::hasRecordHover);
    }

    private static void completion() throws Exception {
        Fixture f = new Fixture();
        for (String name : List.of("warn", "ban", "kick", "history")) {
            var c = command(f, name);
            check(c.onTabComplete(f.staff.sender, null, name, new String[]{"a"}).equals(List.of("Alex")), "online prefix " + name);
            check(c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Staff") == name.equals("history"), "self completion " + name);
            check(!c.onTabComplete(f.console.sender, null, name, new String[]{""}).contains("Offline"), "no offline global enumeration " + name);
        }
        f.addKnown(new UUID(0, 92), "Alex");
        check(new WarnCommand(f.context).onTabComplete(f.console.sender, null, "warn", new String[]{""}).contains(TARGET.toString()), "ambiguous completion UUID");
        f.known.remove(TARGET);
        check(new KickCommand(f.context).onTabComplete(f.console.sender, null, "kick", new String[]{""}).contains(TARGET.toString()), "unknown identity completion UUID");
        f.addKnown(TARGET, "Alex");
        f.service.issueBan(OFFLINE, ModerationActor.console(), "Ban", Optional.empty());
        var unban = new UnbanCommand(f.context);
        check(unban.onTabComplete(f.console.sender, null, "unban", new String[]{"off"}).equals(List.of("Offline")), "active offline ban completion");
        f.service.revokeBan(OFFLINE, ModerationActor.console(), Optional.empty());
        check(unban.onTabComplete(f.console.sender, null, "unban", new String[]{""}).isEmpty(), "revoked excluded");
        f.service.issueBan(STAFF, ModerationActor.console(), "Ban", Optional.of(NOW.plusSeconds(1)));
        check(unban.onTabComplete(f.staff.sender, null, "unban", new String[]{""}).isEmpty(), "unban self excluded");
        f.clock.now = NOW.plusSeconds(1);
        check(unban.onTabComplete(f.console.sender, null, "unban", new String[]{""}).isEmpty(), "expired excluded");
        var ban = new BanCommand(f.context);
        check(ban.onTabComplete(f.console.sender, null, "ban", new String[]{"Alex", "P"}).equals(List.of("permanent")), "duration prefix");
        check(ban.onTabComplete(f.console.sender, null, "ban", new String[]{"Alex", ""}).size() == 10, "ten common durations");
        for (String name : List.of("warn", "ban", "unban", "kick", "history")) {
            check(command(f, name).onTabComplete(f.console.sender, null, name, new String[]{"Alex", "7d", ""}).isEmpty(), "no free-reason suggestions " + name);
        }
        check(f.playerWrites == 0, "no completion player mutation");
    }

    private static void mutes() throws Exception {
        for (String duration : List.of("permanent", "perm", "30s", "10m", "2h", "7d", "2w")) {
            Fixture timed = new Fixture();
            run(new MuteCommand(timed.context), timed.console, "Muted", "Alex", duration, "duration coverage");
            check(timed.service.getActiveMute(TARGET).orElseThrow().expiresAt().equals(
                    ModerationDurationParser.parse(duration).expiresAt(NOW)), "exact command duration " + duration);
        }
        for (String revokeReason : List.of("", "  ", ATTACK)) {
            Fixture revoked = new Fixture();
            revoked.service.issueMute(TARGET, ModerationActor.console(), "original", Optional.empty());
            run(new UnmuteCommand(revoked.context), revoked.staff, "Unmuted", "Alex", revokeReason);
            var record = revoked.service.getHistory(TARGET).getFirst();
            check(record.revocation().orElseThrow().actor().equals(ModerationActor.player(STAFF)), "player unmute actor");
            check(record.revocation().orElseThrow().reason().equals(revokeReason.isBlank() ? Optional.empty() : Optional.of(ATTACK)),
                    "optional blank/literal revoke reason semantics");
            check(revoked.target.output.stream().allMatch(ModerationTestSupport::noEvents), "unmute literal notice");
        }
        for (String target : List.of("aLeX", TARGET.toString(), "Offline", OFFLINE.toString())) {
            for (String duration : List.of("7d", "PERM")) {
                Fixture f = new Fixture();
                var mute = new MuteCommand(f.context); var unmute = new UnmuteCommand(f.context);
                f.repository.beforeSave = () -> check(!f.projection.isMuted(TARGET) && f.info() == 0
                        && f.target.output.isEmpty(), "save before mute publication/audit/notification");
                f.target.beforeNotification = () -> check(f.projection.isMuted(TARGET) && f.info() == 1,
                        "published and audited before online notification");
                run(mute, f.console, "Muted", target, duration, ATTACK);
                var record = f.service.getAllRecords().getFirst();
                check(record.action() == ModerationAction.MUTE && f.projection.isMuted(record.targetId()), "actual mute committed");
                check(f.repository.saves == 1 && f.info() == 1 && f.playerWrites == 0 && f.target.kicks.isEmpty(), "mute only fact/notification");
                check(f.console.output.stream().allMatch(ModerationTestSupport::noEvents), "mute literal feedback");
                if (record.targetId().equals(TARGET)) {
                    check(f.target.text().contains(ATTACK) && f.target.text().contains(duration.equals("7d") ? "UTC" : "Permanent"), "literal timed/permanent notice");
                    check(f.target.output.stream().allMatch(ModerationTestSupport::noEvents), "mute notice no events");
                } else check(f.target.output.isEmpty(), "offline no notification");
                f.repository.beforeSave = () -> { }; f.target.beforeNotification = () -> { };
                run(mute, f.console, "already muted", target, "1h", "duplicate");
                check(f.repository.saves == 1 && f.info() == 1, "duplicate mute no save/audit");
                check(unmute.onTabComplete(f.console.sender, null, "unmute", new String[]{""}).contains(record.targetId().equals(TARGET) ? "Alex" : "Offline"), "unmute includes committed offline/online target");
                run(unmute, f.console, "Unmuted", target, ATTACK);
                check(!f.projection.isMuted(record.targetId()) && f.repository.saves == 2 && f.info() == 2, "unmute immediate publication");
                if (record.targetId().equals(TARGET)) check(f.target.text().contains("Your mute has been removed.") && f.target.text().contains(ATTACK), "literal optional revocation notice");
                run(unmute, f.console, "not currently muted", target);
                check(f.repository.saves == 2 && f.info() == 2, "unmute noop");
                check(unmute.onTabComplete(f.console.sender, null, "unmute", new String[]{""}).isEmpty(), "revoked no completion");
            }
        }
        Fixture f = new Fixture(); var mute = new MuteCommand(f.context); var unmute = new UnmuteCommand(f.context);
        for (String invalid : List.of("0m", "-1h", "1.5h", "10", "999999999999999999w")) {
            run(mute, f.console, "Invalid duration", "Alex", invalid, "reason");
            check(f.repository.saves == 0 && f.info() == 0, "bad duration no mutation");
        }
        check(mute.onTabComplete(f.console.sender, null, "mute", new String[]{"Alex", "P"}).equals(List.of("permanent")), "mute duration prefix");
        check(mute.onTabComplete(f.console.sender, null, "mute", new String[]{"Alex", ""}).size() == 10, "mute durations");
        f.repository.fail = true;
        run(mute, f.console, "could not be saved", "Alex", "1h", "reason");
        check(!f.projection.isMuted(TARGET) && f.info() == 0 && f.target.output.isEmpty(), "failed mute no effect/audit/notification");
        f.repository.fail = false; f.target.failNotification = true;
        run(mute, f.console, "Mute was saved, but", "Alex", "1h", ATTACK);
        check(f.projection.isMuted(TARGET) && f.info() == 1 && f.severe() == 2, "notification failure retains mute");
        f.repository.fail = true;
        run(unmute, f.console, "could not be saved", "Alex");
        check(f.projection.isMuted(TARGET) && f.info() == 1, "failed unmute retains enforcement");
        f.repository.fail = false;
        run(unmute, f.console, "Unmute was saved, but", "Alex");
        check(!f.projection.isMuted(TARGET) && f.info() == 2 && f.severe() == 4, "unmute notify failure keeps revoke");
        f.target.failNotification = false;
        run(mute, f.staff, "Muted", "Offline", "1s", "temporary");
        check(f.service.getHistory(OFFLINE).getFirst().actor().equals(ModerationActor.player(STAFF)), "player actor");
        f.clock.now = NOW.plusSeconds(1);
        check(unmute.onTabComplete(f.console.sender, null, "unmute", new String[]{""}).isEmpty(), "expiry completion read only");
        run(unmute, f.console, "not currently muted", "Offline");
        run(mute, f.console, "Muted", "Offline", "permanent", "renew");
        f.addKnown(new UUID(0, 92), "Offline");
        check(unmute.onTabComplete(f.console.sender, null, "unmute", new String[]{""}).equals(List.of(OFFLINE.toString())), "ambiguous active mute UUID completion");
        f.service.issueMute(STAFF, ModerationActor.console(), "self", Optional.empty());
        check(!unmute.onTabComplete(f.staff.sender, null, "unmute", new String[]{""}).contains("Staff"), "unmute self completion excluded");
    }

    private static final List<String> MODERATION = List.of("warn", "mute", "unmute", "ban", "unban", "kick", "history");

    private static void seed(Fixture f, String name, UUID target) {
        if (name.equals("unban")) f.service.issueBan(target, ModerationActor.console(), "seed", Optional.empty());
        if (name.equals("unmute")) f.service.issueMute(target, ModerationActor.console(), "seed", Optional.empty());
    }

    private static void hierarchyMatrix() throws Exception {
        var config = dev.vapee.core.rank.staff.StaffHierarchyConfig.defaults();
        for (String name : MODERATION) {
            for (String actorGroup : List.of("default", "vip", "custom", "builder", "moderator", "admin", "owner")) {
                for (String targetGroup : List.of("default", "vip", "custom", "builder", "moderator", "admin", "owner")) {
                    Fixture f = new Fixture(); seed(f, name, TARGET);
                    f.primaryGroups.put(STAFF, actorGroup); f.primaryGroups.put(TARGET, targetGroup);
                    int before = f.repository.saves;
                    var snapshot = f.repository.saved;
                    check(command(f, name).onCommand(f.staff.sender, null, name, arguments(name, "Alex")), "matrix handled");
                    boolean allow = config.level(targetGroup) < 0 || config.level(actorGroup) > config.level(targetGroup);
                    String label = name + ": " + actorGroup + " -> " + targetGroup;
                    check(f.groupLoadCalls == 0 && f.mainTasks.isEmpty(), "loaded matrix synchronous " + label);
                    if (!allow) {
                        check(f.repository.saves == before && f.repository.saved == snapshot && f.info() == 0,
                                "deny no save/state/audit " + label);
                        check(f.target.output.isEmpty() && f.target.kicks.isEmpty() && f.playerWrites == 0,
                                "deny no notification/disconnect/player write " + label);
                        check(f.staff.text().contains("cannot target") && !f.staff.text().contains(targetGroup),
                                "controlled deny without group leak " + label);
                    } else {
                        check(f.repository.saves == before + (name.equals("history") ? 0 : 1), "allow expected writes " + label);
                        check(f.info() == (name.equals("history") ? 0 : 1) && f.severe() == 0, "allow audit " + label);
                        check(!f.staff.text().contains("cannot target"), "allowed feedback " + label);
                    }
                }
            }
            Fixture console = new Fixture(); seed(console, name, TARGET);
            console.primaryGroups.clear();
            int before = console.repository.saves;
            command(console, name).onCommand(console.console.sender, null, name, arguments(name, "Alex"));
            check(console.repository.saves == before + (name.equals("history") ? 0 : 1)
                    && console.groupLoadCalls == 0 && console.severe() == 0, "console bypass no LP resolution " + name);

            Fixture self = new Fixture();
            self.primaryGroups.clear();
            command(self, name).onCommand(self.staff.sender, null, name, arguments(name, "Staff"));
            check(self.repository.saves == 0 && self.groupLoadCalls == 0
                    && self.staff.text().contains(name.equals("history") ? "No moderation history" : "yourself"),
                    "self priority before group lookup " + name);
        }
        Fixture f = new Fixture();
        f.primaryGroups.put(STAFF, "ADMIN"); f.primaryGroups.put(TARGET, " Moderator ");
        run(new WarnCommand(f.context), f.staff, "Warned", "Alex", "case");
        check(f.repository.saves == 1, "case-insensitive integration");
        f.hierarchyConfig.set(new dev.vapee.core.rank.staff.StaffHierarchyConfig(List.of("admin", "moderator")));
        run(new WarnCommand(f.context), f.staff, "cannot target", "Alex", "reloaded");
        check(f.repository.saves == 1, "reload changes next command without service replacement");
        f.hierarchyConfig.set(config);
        run(new WarnCommand(f.context), f.staff, "Warned", "Alex", "rollback");
        check(f.repository.saves == 2, "rollback restores protection");
    }

    private static void hierarchyFreshness() throws Exception {
        Thread main = Thread.currentThread();
        // Ban first: the audit's offline-target race must reach a real save/disconnect path if bypassed.
        for (String name : List.of("ban", "warn", "mute", "unmute", "unban", "kick", "history")) {
            for (String scenario : List.of("higher", "equal", "demotion", "unloaded", "blank", "invalid",
                    "null-completion", "revoked", "logout", "reconnect", "actor-demoted", "actor-promoted",
                    "actor-unavailable", "reload", "disabled")) {
                Fixture f = new Fixture(); seed(f, name, OFFLINE);
                if (name.equals("history")) f.service.issueWarning(OFFLINE, ModerationActor.console(), "private history fact");
                f.primaryGroups.put(STAFF, scenario.equals("equal") ? "moderator"
                        : scenario.equals("actor-promoted") ? "builder" : "admin");
                f.primaryGroups.remove(OFFLINE);
                var future = new java.util.concurrent.CompletableFuture<Optional<String>>();
                f.groupLoads.put(OFFLINE, future);
                var asyncAccess = new java.util.concurrent.atomic.AtomicInteger();
                Runnable guard = () -> {
                    if (Thread.currentThread() != main) {
                        asyncAccess.incrementAndGet(); throw new AssertionError("Bukkit/domain accessed asynchronously");
                    }
                };
                var target = new Sender(org.bukkit.entity.Player.class, OFFLINE, "Offline", f.events);
                f.staff.beforeAccess = guard; target.beforeAccess = guard; f.repository.beforeSave = guard;
                int before = f.repository.saves;
                var snapshot = f.repository.saved;
                var records = f.service.getAllRecords();
                var history = f.service.getHistory(OFFLINE);
                var ban = f.service.getActiveBan(OFFLINE);
                var mute = f.service.getActiveMute(OFFLINE);
                boolean projected = f.projection.isMuted(OFFLINE);
                String label = name + "/" + scenario;
                check(command(f, name).onCommand(f.staff.sender, null, name, arguments(name, "Offline")), "handled race " + label);
                check(f.groupLoadCalls == 1 && !future.isDone() && f.mainTasks.isEmpty()
                        && f.repository.saves == before && f.staff.output.isEmpty(), "offline pending without effects " + label);
                String oldGroup = scenario.equals("equal") ? "builder" : scenario.equals("demotion") ? "owner" : "moderator";
                Thread worker = new Thread(() -> future.complete(scenario.equals("null-completion") ? null : Optional.of(oldGroup)),
                        "lp-freshness-completion-worker");
                worker.start(); worker.join();
                check(asyncAccess.get() == 0 && f.mainTasks.size() == 1 && f.repository.saves == before
                        && f.repository.saved == snapshot && f.info() == 0 && f.staff.output.isEmpty()
                        && target.output.isEmpty() && target.kicks.isEmpty(), "completion only queues resume " + label);
                // All authority/session/config changes happen AFTER completion and BEFORE the queued resume.
                // The initially offline player comes online here, making stale ban/kick/notification effects observable.
                f.online.put(OFFLINE, target);
                f.primaryGroups.put(OFFLINE, scenario.equals("higher") ? "owner" : "moderator");
                switch (scenario) {
                    case "unloaded", "null-completion" -> f.primaryGroups.remove(OFFLINE);
                    case "blank" -> f.primaryGroups.put(OFFLINE, " ");
                    case "invalid" -> f.primaryGroups.put(OFFLINE, "admin\n");
                    case "revoked" -> f.staff.allowAll = false;
                    case "logout" -> f.staff.isOnline = false;
                    case "reconnect" -> {
                        var replacement = new Sender(org.bukkit.entity.Player.class, STAFF, "Staff", f.events);
                        replacement.beforeAccess = guard;
                        f.online.put(STAFF, replacement);
                    }
                    case "actor-demoted" -> f.primaryGroups.put(STAFF, "builder");
                    case "actor-promoted" -> f.primaryGroups.put(STAFF, "admin");
                    case "actor-unavailable" -> f.primaryGroups.remove(STAFF);
                    case "reload" -> f.hierarchyConfig.set(new dev.vapee.core.rank.staff.StaffHierarchyConfig(List.of("admin", "moderator")));
                    case "disabled" -> f.active.set(false);
                    default -> { }
                }
                f.mainTasks.remove().run();
                boolean allowed = List.of("demotion", "actor-promoted").contains(scenario);
                boolean unavailable = List.of("unloaded", "blank", "invalid", "null-completion", "actor-unavailable").contains(scenario);
                if (allowed) {
                    check(f.repository.saves == before + (name.equals("history") ? 0 : 1)
                            && f.info() == (name.equals("history") ? 0 : 1), "fresh authority allows " + label);
                    check(!f.staff.output.isEmpty() && (name.equals("history") ? f.staff.text().contains("private history fact")
                            : !f.staff.text().contains("cannot") && !f.staff.text().contains("server log")), "fresh success feedback " + label);
                    if (List.of("ban", "kick").contains(name)) check(target.kicks.size() == 1, "allowed real disconnect " + label);
                } else {
                    String expected = unavailable ? "The moderation operation could not be completed. Check the server log."
                            : scenario.equals("revoked") ? "You do not have permission to use this command."
                            : "You cannot target a staff member at your level or above.";
                    boolean silent = List.of("logout", "reconnect", "disabled").contains(scenario);
                    check(silent ? f.staff.output.isEmpty() : f.staff.output.size() == 1 && f.staff.text().trim().equals(expected),
                            "fresh hierarchy denies without success/history feedback " + label + "; received " + f.staff.text());
                    check(f.repository.saves == before && f.repository.saved == snapshot && f.service.getAllRecords().equals(records)
                            && f.service.getHistory(OFFLINE).equals(history), "denied save/snapshot/history unchanged " + label);
                    check(f.service.getActiveBan(OFFLINE).equals(ban) && f.service.getActiveMute(OFFLINE).equals(mute)
                            && f.projection.isMuted(OFFLINE) == projected, "denied ban/mute/projection unchanged " + label);
                    check(target.kicks.isEmpty() && target.output.isEmpty() && f.info() == 0 && f.playerWrites == 0,
                            "denied no external action/notification/success audit " + label);
                    if (scenario.equals("reconnect")) check(f.online.get(STAFF).output.isEmpty(), "replacement session untouched " + label);
                }
                check(f.severe() == (unavailable ? 1 : 0), "controlled unavailable logging only " + label);
                if (unavailable) {
                    String log = f.logs.getLast().getMessage();
                    check(log.contains(name.toUpperCase(Locale.ROOT) + "_HIERARCHY") && log.contains(STAFF.toString())
                            && log.contains(OFFLINE.toString()), "failure action/actor/target context " + label);
                }
                check(asyncAccess.get() == 0 && f.mainTasks.isEmpty() && f.groupLoadCalls == 1,
                        "one nonblocking main-thread continuation, no retry/fallback " + label);
            }
        }
    }

    private static void hierarchyAsync() throws Exception {
        Thread main = Thread.currentThread();
        for (String name : MODERATION) {
            for (String scenario : List.of("normal", "lower", "equal", "higher", "vip", "revoked", "logout",
                    "reconnect", "demoted", "promoted", "failure", "empty", "invalid", "disabled", "reload")) {
                Fixture f = new Fixture(); seed(f, name, TARGET);
                f.primaryGroups.put(STAFF, "admin"); f.primaryGroups.remove(TARGET);
                var future = new java.util.concurrent.CompletableFuture<Optional<String>>();
                f.groupLoads.put(TARGET, future);
                var asyncAccess = new java.util.concurrent.atomic.AtomicInteger();
                Runnable guard = () -> {
                    if (Thread.currentThread() != main) {
                        asyncAccess.incrementAndGet(); throw new AssertionError("Bukkit accessed asynchronously");
                    }
                };
                f.staff.beforeAccess = guard; f.target.beforeAccess = guard;
                f.repository.beforeSave = guard;
                int before = f.repository.saves;
                var snapshot = f.repository.saved;
                String[] input = arguments(name, "Alex");
                command(f, name).onCommand(f.staff.sender, null, name, input);
                check(f.groupLoadCalls == 1 && f.repository.saves == before && f.info() == 0
                        && f.mainTasks.isEmpty(), "pending no mutation " + name + "/" + scenario);
                // Caller-owned argument arrays must not alter a queued command.
                input[0] = "Staff";
                switch (scenario) {
                    case "revoked" -> f.staff.allowAll = false;
                    case "logout" -> f.staff.isOnline = false;
                    case "reconnect" -> f.online.put(STAFF, new Sender(org.bukkit.entity.Player.class, STAFF, "Staff", f.events));
                    case "demoted", "vip" -> f.primaryGroups.put(STAFF, "vip");
                    case "promoted" -> f.primaryGroups.put(STAFF, "owner");
                    case "disabled" -> f.active.set(false);
                    case "reload" -> f.hierarchyConfig.set(new dev.vapee.core.rank.staff.StaffHierarchyConfig(List.of("admin", "moderator")));
                    default -> { }
                }
                String targetGroup = switch (scenario) {
                    case "normal" -> "default"; case "equal" -> "admin"; case "higher", "promoted" -> "owner";
                    case "invalid" -> "admin\n"; default -> "moderator";
                };
                // A promoted actor must have strict advantage, not merely equality.
                if (scenario.equals("promoted")) targetGroup = "admin";
                final String resolved = targetGroup;
                Thread worker = new Thread(() -> {
                    if (scenario.equals("failure")) future.completeExceptionally(new IllegalStateException("injected LP failure"));
                    else future.complete(scenario.equals("empty") ? Optional.empty() : Optional.of(resolved));
                }, "lp-completion-worker");
                worker.start(); worker.join();
                // Successful LP loading makes the user available to the subsequent loaded-state read.
                if (!List.of("failure", "empty").contains(scenario)) f.primaryGroups.put(TARGET, resolved);
                check(asyncAccess.get() == 0 && f.repository.saves == before && f.info() == 0
                        && f.target.output.isEmpty() && f.target.kicks.isEmpty(), "worker only schedules " + name + "/" + scenario);
                check(f.mainTasks.size() == (scenario.equals("disabled") ? 0 : 1), "one main continuation " + scenario);
                Runnable task;
                while ((task = f.mainTasks.poll()) != null) task.run();
                boolean allowed = List.of("normal", "lower", "promoted").contains(scenario);
                check(f.repository.saves == before + (allowed && !name.equals("history") ? 1 : 0),
                        "continued expected saves " + name + "/" + scenario);
                if (!allowed) {
                    check(f.repository.saved == snapshot && f.info() == 0 && f.target.output.isEmpty()
                            && f.target.kicks.isEmpty(), "aborted continuation has no effects " + name + "/" + scenario);
                }
                if (List.of("failure", "empty", "invalid").contains(scenario)) {
                    check(f.severe() == 1 && f.staff.text().contains("server log")
                            && !f.staff.text().contains("injected") && !f.staff.text().contains("Exception"),
                            "lookup failure contextual server log, safe player feedback " + name + "/" + scenario);
                }
                check(asyncAccess.get() == 0 && f.mainTasks.isEmpty(), "no async access or leaked tasks");
            }
        }
        for (String name : MODERATION) for (String group : List.of("default", "owner")) {
            Fixture f = new Fixture(); seed(f, name, OFFLINE); f.primaryGroups.remove(OFFLINE);
            var future = new java.util.concurrent.CompletableFuture<Optional<String>>();
            f.groupLoads.put(OFFLINE, future);
            int before = f.repository.saves;
            command(f, name).onCommand(f.staff.sender, null, name, arguments(name, "Offline"));
            check(f.repository.saves == before && !future.isDone(), "offline waits without blocking " + name);
            future.complete(Optional.of(group));
            f.primaryGroups.put(OFFLINE, group);
            f.mainTasks.remove().run();
            boolean mutation = group.equals("default") && !List.of("kick", "history").contains(name);
            check(f.repository.saves == before + (mutation ? 1 : 0), "offline known target authorized " + name + "/" + group);
            if (group.equals("owner")) check(f.staff.text().contains("cannot target"), "offline staff cannot be bypassed");
        }
        Fixture f = new Fixture(); f.primaryGroups.put(TARGET, " ");
        run(new WarnCommand(f.context), f.staff, "server log", "Alex", "reason");
        check(f.repository.saves == 0 && f.severe() == 1, "invalid loaded group fails closed");
        f.primaryGroups.put(TARGET, "builder"); f.primaryGroups.remove(STAFF);
        run(new WarnCommand(f.context), f.staff, "server log", "Alex", "reason");
        check(f.repository.saves == 0 && f.severe() == 2, "unavailable actor protected target closed");
    }

    private static void hierarchyCompletion() throws Exception {
        for (String name : MODERATION) {
            Fixture f = new Fixture();
            f.primaryGroups.put(STAFF, "admin"); f.primaryGroups.put(TARGET, "moderator");
            seed(f, name, TARGET);
            if (name.equals("unban")) f.service.issueBan(OFFLINE, ModerationActor.console(), "offline", Optional.empty());
            if (name.equals("unmute")) f.service.issueMute(OFFLINE, ModerationActor.console(), "offline", Optional.empty());
            var c = command(f, name);
            check(c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Alex"), "lower online suggestion " + name);
            for (String group : List.of("admin", "owner")) {
                f.primaryGroups.put(TARGET, group);
                check(!c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Alex"), "protected hidden " + name + "/" + group);
            }
            f.primaryGroups.remove(TARGET);
            check(!c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Alex"), "unknown online group hidden " + name);
            check(c.onTabComplete(f.console.sender, null, name, new String[]{""}).contains("Alex"), "console sees candidate " + name);
            f.primaryGroups.put(TARGET, "builder"); f.primaryGroups.put(STAFF, "vip");
            check(!c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Alex"), "vip actor hides protected target " + name);
            f.primaryGroups.put(TARGET, "default");
            check(c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Alex"), "normal target completion " + name);
            if (List.of("unban", "unmute").contains(name)) {
                f.primaryGroups.remove(OFFLINE);
                check(c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Offline"), "offline active candidate retained");
            }
            check(f.groupLoadCalls == 0 && f.mainTasks.isEmpty(), "no N+1 offline or online LP completion loads " + name);
            if (name.equals("history")) check(c.onTabComplete(f.staff.sender, null, name, new String[]{""}).contains("Staff"), "self history suggested");
        }
    }

    private static void descriptor() throws Exception {
        var yaml = (Map<?, ?>) new Yaml().load(Files.readString(Path.of("src/main/resources/plugin.yml")));
        var commands = (Map<?, ?>) yaml.get("commands"); var permissions = (Map<?, ?>) yaml.get("permissions");
        check(commands.size() == 37, "37 plugin root commands");
        Map<String, String> syntax = Map.of("warn", "<player|uuid> <reason...>", "ban", "<player|uuid> <duration|permanent> <reason...>",
                "unban", "<player|uuid> [reason...]", "kick", "<player|uuid> <reason...>", "history", "<player|uuid> [page]",
                "mute", "<player|uuid> <duration|permanent> <reason...>", "unmute", "<player|uuid> [reason...]");
        for (String name : syntax.keySet()) {
            var entry = (Map<?, ?>) commands.get(name); var permission = (Map<?, ?>) permissions.get("vapeecore.moderation." + name);
            check(entry.get("usage").equals("/" + name + " " + syntax.get(name)) && entry.get("permission").equals("vapeecore.moderation." + name), "descriptor syntax/permission " + name);
            check(entry.containsKey("description") && !entry.containsKey("aliases"), "description/no alias " + name);
            check(permission.get("default").equals("op") && !permission.containsKey("children"), "op independent permission " + name);
        }
        check(!permissions.containsKey("vapeecore.moderation.*") && permissions.keySet().stream().filter(k -> k.toString().startsWith("vapeecore.moderation.")).count() == 7, "seven nodes/no wildcard");
        for (String absent : List.of("moderation", "mod", "punish", "freeze")) check(!commands.containsKey(absent), "no out-of-scope command " + absent);
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}

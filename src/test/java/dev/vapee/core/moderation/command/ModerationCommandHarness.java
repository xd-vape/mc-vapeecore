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
        guards(); targets(); warnings(); bans(); unbans(); kicks(); history(); completion(); descriptor();
        System.out.println("ModerationCommandHarness passed " + checks + " checks.");
    }

    private static TabExecutor command(Fixture f, String name) {
        return switch (name) {
            case "warn" -> new WarnCommand(f.context); case "ban" -> new BanCommand(f.context);
            case "unban" -> new UnbanCommand(f.context); case "kick" -> new KickCommand(f.context);
            case "history" -> new HistoryCommand(f.context); default -> throw new AssertionError(name);
        };
    }
    private static String[] arguments(String name, String target) {
        return switch (name) {
            case "ban" -> new String[]{target, "7d", ATTACK};
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
        for (String name : List.of("warn", "ban", "unban", "kick", "history")) {
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
        for (String name : List.of("warn", "ban", "kick")) {
            Fixture f = new Fixture(); TabExecutor c = command(f, name);
            run(c, f.console, name.equals("ban") ? "Missing duration and reason" : "Missing reason", "Alex");
            if (name.equals("ban")) run(c, f.console, "Missing reason", "Alex", "7d");
            run(c, f.console, "Missing reason", name.equals("ban") ? new String[]{"Alex", "7d", "  "} : new String[]{"Alex", "  "});
            check(f.repository.saves == 0, "missing reason has no mutation " + name);
            for (String bad : List.of("x".repeat(257), "bad\nreason", "bad\u2028reason", "bad\u0000reason")) {
                run(c, f.console, "Invalid reason", name.equals("ban") ? new String[]{"Alex", "7d", bad} : new String[]{"Alex", bad});
                check(f.repository.saves == 0 && f.info() == 0 && f.severe() == 0, "invalid reason normal rejection " + name);
            }
        }
    }

    private static void targets() throws Exception {
        for (String name : List.of("warn", "ban", "unban", "kick", "history")) {
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

    private static void descriptor() throws Exception {
        var yaml = (Map<?, ?>) new Yaml().load(Files.readString(Path.of("src/main/resources/plugin.yml")));
        var commands = (Map<?, ?>) yaml.get("commands"); var permissions = (Map<?, ?>) yaml.get("permissions");
        check(commands.size() == 34, "34 plugin root commands");
        Map<String, String> syntax = Map.of("warn", "<player|uuid> <reason...>", "ban", "<player|uuid> <duration|permanent> <reason...>",
                "unban", "<player|uuid> [reason...]", "kick", "<player|uuid> <reason...>", "history", "<player|uuid> [page]");
        for (String name : syntax.keySet()) {
            var entry = (Map<?, ?>) commands.get(name); var permission = (Map<?, ?>) permissions.get("vapeecore.moderation." + name);
            check(entry.get("usage").equals("/" + name + " " + syntax.get(name)) && entry.get("permission").equals("vapeecore.moderation." + name), "descriptor syntax/permission " + name);
            check(entry.containsKey("description") && !entry.containsKey("aliases"), "description/no alias " + name);
            check(permission.get("default").equals("op") && !permission.containsKey("children"), "op independent permission " + name);
        }
        check(!permissions.containsKey("vapeecore.moderation.*") && permissions.keySet().stream().filter(k -> k.toString().startsWith("vapeecore.moderation.")).count() == 5, "five nodes/no wildcard");
        for (String absent : List.of("mute", "unmute", "moderation", "mod", "punish", "freeze")) check(!commands.containsKey(absent), "no out-of-scope command " + absent);
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}

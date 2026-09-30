package dev.vapee.core.moderation;

import static dev.vapee.core.moderation.ModerationTestSupport.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModerationBanEnforcementHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Fixture f = new Fixture();
        ModerationLoginListener listener = new ModerationLoginListener(f.service, f.logger);
        allow(f, listener, "no ban");
        f.service.issueWarning(TARGET, ModerationActor.console(), "Warning"); allow(f, listener, "warning only");
        f.service.recordKick(TARGET, ModerationActor.console(), "Kick"); allow(f, listener, "kick only");
        f.service.issueMute(TARGET, ModerationActor.console(), "Mute", Optional.empty()); allow(f, listener, "mute only");
        var ban = f.service.issueBan(TARGET, ModerationActor.console(), ATTACK, Optional.empty()).record().orElseThrow();
        deny(f, listener, "Permanent");
        check(plain(listener.rejection(TARGET).orElseThrow()).contains(ATTACK), "literal login reason");
        check(noEvents(listener.rejection(TARGET).orElseThrow()), "no login event injection");
        check(listener.rejection(OFFLINE).isEmpty(), "other UUID independent");
        f.service.revokeBan(TARGET, ModerationActor.console(), Optional.empty()); allow(f, listener, "revoked");
        Instant expiry = NOW.plusSeconds(30);
        f.service.issueBan(TARGET, ModerationActor.console(), "Temporary", Optional.of(expiry));
        f.clock.now = expiry.minusNanos(1); deny(f, listener, "2026-09-30 12:00:30 UTC");
        f.clock.now = expiry; allow(f, listener, "exact expiry");
        f.clock.now = expiry.plusNanos(1); allow(f, listener, "after expiry");
        int saves = f.repository.saves;
        for (int i = 0; i < 20; i++) listener.onPlayerLogin(event(f));
        check(f.repository.saves == saves && f.service.getAllRecords().size() == 5, "all enforcement reads retain expired history without save");
        check(f.playerWrites == 0, "login before join needs no player load/save");
        var previous = event(f);
        previous.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.Component.text("Other policy"));
        listener.onPlayerLogin(previous);
        check(previous.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST, "inactive ban never overrides other rejection");
        var broken = new ModerationLoginListener(id -> { throw new IllegalStateException("query failure"); }, f.logger);
        broken.onPlayerLogin(previous);
        check(previous.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST && f.severe() == 1, "lookup failure logged, no fabricated ban or allow override");
        check(broken.rejection(TARGET).isEmpty() && f.severe() == 2, "controlled unexpected lookup failure");
        Fixture future = new Fixture();
        future.repository.saved = new ModerationSnapshot(List.of(new ModerationRecord(UUID.randomUUID(), ModerationAction.BAN,
                TARGET, ModerationActor.console(), "Future", NOW.plusSeconds(60), Optional.empty(), Optional.empty())));
        var futureService = new ModerationService(future.repository, future.clock, UUID::randomUUID);
        check(new ModerationLoginListener(futureService, future.logger).rejection(TARGET).isEmpty(), "future-created inactive");
        check(ModerationComponents.status(ban, NOW).equals("Active"), "permanent status");
        var maximum = new ModerationRecord(UUID.randomUUID(), ModerationAction.BAN, TARGET, ModerationActor.console(),
                ATTACK, NOW, Optional.of(Instant.MAX), Optional.empty());
        check(plain(ModerationComponents.banScreen(maximum)).contains(Instant.MAX.toString())
                && plain(ModerationComponents.banScreen(maximum)).contains("UTC"), "full domain timestamp range renders safely");
        System.out.println("ModerationBanEnforcementHarness passed " + checks + " checks.");
    }
    private static PlayerLoginEvent event(Fixture f) throws Exception {
        return new PlayerLoginEvent((Player) f.target.sender, "localhost", InetAddress.getLoopbackAddress());
    }
    private static void allow(Fixture f, ModerationLoginListener listener, String label) throws Exception {
        int before = f.repository.saves;
        var event = event(f); listener.onPlayerLogin(event);
        check(event.getResult() == PlayerLoginEvent.Result.ALLOWED, label);
        check(f.repository.saves == before, label + " read-only");
    }
    private static void deny(Fixture f, ModerationLoginListener listener, String text) throws Exception {
        int before = f.repository.saves;
        var event = event(f); listener.onPlayerLogin(event);
        check(event.getResult() == PlayerLoginEvent.Result.KICK_BANNED, "deny " + text);
        check(plain(event.kickMessage()).contains(text) && plain(event.kickMessage()).contains("You are banned"), "screen " + text);
        check(f.repository.saves == before, "deny read-only");
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}

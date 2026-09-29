package dev.vapee.core.clan.gui;

import dev.vapee.core.clan.*;
import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.command.help.CommandHelpRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClanCommandHarness {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;
    public static void main(String[] args) throws Exception {
        ClanMenuFixture f = new ClanMenuFixture();
        var owner = f.player("Owner", true);
        var member = f.player("Member", true);
        var offline = f.player("Offline", false);
        var markup = f.player("<red>Safe</red>", true);
        f.player("Twin", false);
        f.player("twin", false);
        var denied = f.player("Denied", true);
        denied.permitted = false;
        List<UUID> opened = new ArrayList<>();
        List<UUID> invitesOpened = new ArrayList<>();
        ClanCommand command = new ClanCommand(f.clans, f.identity, f.messages,
                new CommandHelpRenderer(f.messages), () -> f.players.values().stream()
                .filter(p -> p.online).map(p -> p.player).toList(),
                id -> { var p = f.players.get(id); return p != null && p.online ? p.player : null; },
                ClanMenuFixture.logger(), p -> opened.add(p.getUniqueId()),
                p -> invitesOpened.add(p.getUniqueId()));
        run(command, owner);
        runAlias(command, owner, "clans");
        check(opened.equals(List.of(owner.id, owner.id)), "/clan and /clans open GUI");
        run(command, owner, "help");
        check(last(owner).contains("/clan create") && all(owner).contains("/clan disband"), "shared help renderer");
        run(command, denied, "create", "NO", "Denied Clan");
        check(last(denied).contains("permission"), "permission denied");
        List<Component> consoleMessages = new ArrayList<>();
        CommandSender console = ClanMenuFixture.proxy(CommandSender.class, (method, argv) -> {
            if (method.equals("sendMessage") && argv != null)
                for (Object arg : argv) if (arg instanceof Component c) consoleMessages.add(c);
            return method.equals("hasPermission") ? true : null;
        });
        command.onCommand(console, null, "clan", new String[]{"create", "NO", "No Clan"});
        check(PLAIN.serialize(consoleMessages.getLast()).contains("Only players"), "console denied");
        run(command, owner, "create", "VAPE", "Vapee", "Community");
        check(f.clans.getClanOf(owner.id).orElseThrow().name().equals("Vapee Community"),
                "create tag then multi-word name");
        UUID clanId = f.clans.getClanOf(owner.id).orElseThrow().id();
        run(command, owner, "info");
        check(all(owner).contains("Vapee Community") && last(owner).contains(clanId.toString()), "own info");
        run(command, member, "info", "VAPE");
        check(all(member).contains("Vapee Community"), "info exact tag");
        run(command, member, "info", clanId.toString());
        check(last(member).contains(clanId.toString()), "info UUID");
        run(command, owner, "invite", "Unknown");
        check(last(owner).contains("not known"), "unknown target rejected");
        run(command, owner, "invite", "TWIN");
        check(last(owner).contains("ambiguous"), "ambiguous identity rejected");
        run(command, owner, "invite", "Member");
        check(f.clans.getIncomingInvites(member.id).size() == 1 && all(member).contains("invited you to clan"),
                "online invite and notification");
        run(command, owner, "invite", "Offline");
        check(f.clans.getIncomingInvites(offline.id).size() == 1 && offline.received.isEmpty(),
                "known offline invite persists without notification");
        run(command, owner, "invites");
        check(invitesOpened.equals(List.of(owner.id)), "invites command opens view");
        run(command, owner, "cancel", "Offline");
        check(f.clans.getIncomingInvites(offline.id).isEmpty(), "cancel outgoing");
        run(command, member, "accept", "VAPE");
        check(f.clans.getClanOf(member.id).isPresent() && all(owner).contains("joined your clan"),
                "accept by tag and owner notification");
        run(command, member, "leave");
        check(f.clans.getClanOf(member.id).isEmpty(), "member leaves");
        run(command, owner, "leave");
        check(last(owner).contains("Transfer ownership or disband"), "owner cannot leave");
        run(command, owner, "invite", "Member");
        run(command, member, "deny", clanId.toString());
        check(f.clans.getIncomingInvites(member.id).isEmpty(), "deny by UUID");
        run(command, owner, "invite", "Member");
        run(command, member, "accept", clanId.toString());
        check(f.clans.getClanOf(member.id).isPresent(), "accept by UUID");
        run(command, owner, "kick", "Member");
        check(f.clans.getClanOf(member.id).isEmpty() && all(member).contains("removed from clan"),
                "kick and notification");
        run(command, owner, "invite", "Member");
        run(command, member, "accept", "VAPE");
        run(command, owner, "rename", "New", "Clan", "Name");
        check(f.clans.getClanOf(owner.id).orElseThrow().name().equals("New Clan Name"), "rename multi-word");
        run(command, owner, "tag", "NEW");
        check(f.clans.findClanByTag("NEW").isPresent(), "tag change");
        run(command, owner, "transfer", "Member");
        check(f.clans.getClanOf(owner.id).orElseThrow().ownerId().equals(member.id)
                && all(member).contains("now the owner"), "transfer and notification");
        run(command, member, "disband");
        check(f.clans.getClanOf(member.id).isPresent() && last(member).contains("confirm"),
                "disband requires confirmation");
        run(command, member, "disband", "confirm");
        check(f.clans.getClanOf(member.id).isEmpty(), "confirmed disband");
        run(command, owner, "create", "X", "Valid Name");
        check(last(owner).contains("Invalid clan tag"), "invalid tag result");
        run(command, owner, "create", "TAG", "X");
        check(last(owner).contains("Invalid clan name"), "invalid name result");
        run(command, owner, "create", "SAFE", "Safe Clan");
        run(command, member, "create", "SAFE2", "Safe Clan");
        check(last(member).contains("name is already used"), "duplicate name result");
        run(command, member, "create", "SAFE", "Another Clan");
        check(last(member).contains("tag is already used"), "duplicate tag result");
        run(command, owner, "invite", markup.id.toString());
        Component result = owner.received.getLast();
        check(PLAIN.serialize(result).contains("<red>Safe</red>")
                && !result.children().stream().anyMatch(c -> NamedTextColor.RED.equals(c.color())),
                "user name remains literal Component text");
        f.repository.failNext = true;
        run(command, owner, "invite", "Offline");
        check(last(owner).contains("could not be updated") && f.clans.getIncomingInvites(offline.id).isEmpty(),
                "storage failure is controlled and state unchanged");
        check(command.onTabComplete(member.player, null, "clan", new String[]{""}).contains("create"),
                "clanless completion");
        check(command.onTabComplete(owner.player, null, "clans", new String[]{""}).contains("transfer"),
                "owner completion");
        check(command.onTabComplete(denied.player, null, "clan", new String[]{""}).isEmpty(),
                "denied completion");
        System.out.println("ClanCommandHarness passed " + checks + " checks.");
    }
    private static void run(ClanCommand command, ClanMenuFixture.TestPlayer sender, String... args) {
        command.onCommand(sender.player, null, "clan", args);
    }
    private static void runAlias(ClanCommand command, ClanMenuFixture.TestPlayer sender, String alias) {
        command.onCommand(sender.player, null, alias, new String[0]);
    }
    private static String last(ClanMenuFixture.TestPlayer player) {
        return PLAIN.serialize(player.received.getLast());
    }
    private static String all(ClanMenuFixture.TestPlayer player) {
        return String.join("\n", player.received.stream().map(PLAIN::serialize).toList());
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}

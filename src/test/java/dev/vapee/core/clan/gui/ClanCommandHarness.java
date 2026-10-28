package dev.vapee.core.clan.gui;

import dev.vapee.core.clan.*;
import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.command.help.CommandHelpRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ClanCommandHarness {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;
    public static void main(String[] args) throws Exception {
        directActions();
        commandUx();
        safeRelationArguments();

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
        run(command, member, "info", "vape");
        check(all(member).contains("Vapee Community"), "info resolves tag case-insensitively");
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
        run(command, member, "accept", "vape");
        check(f.clans.getClanOf(member.id).isPresent() && all(owner).contains("joined your clan"),
                "accept by case-insensitive tag and owner notification");
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

    private static void commandUx() throws Exception {
        ClanMenuFixture syntax = new ClanMenuFixture();
        var actor = syntax.player("Actor", true);
        ClanCommand command = command(syntax);

        run(command, actor, "create");
        check(all(actor).contains("Missing clan tag and name.")
                        && all(actor).contains("Usage: /clan create <tag> <name...>")
                        && all(actor).contains("Example: /clan create BMW BMW Community"),
                "create without arguments explains both missing values, syntax, and example");
        check(hasClick(actor, "/clan create <tag> <name...>")
                        && hasClick(actor, "/clan create BMW BMW Community"),
                "create usage and example are suggestable");

        clear(actor);
        run(command, actor, "create", "BMW");
        check(all(actor).contains("Missing clan name.")
                        && all(actor).contains("Usage: /clan create <tag> <name...>")
                        && all(actor).contains("Example: /clan create BMW BMW Community")
                        && !all(actor).contains("/clan help"),
                "create with tag identifies only the missing clan name");

        clear(actor);
        run(command, actor, "info", "BMW", "extra");
        check(last(actor).contains("Usage: /clan info [tag|uuid]"), "info rejects extra arguments locally");
        clear(actor);
        run(command, actor, "invites", "extra");
        check(last(actor).contains("Usage: /clan invites"), "invites rejects extra arguments locally");
        clear(actor);
        run(command, actor, "invite");
        check(all(actor).contains("Missing player.")
                        && all(actor).contains("Usage: /clan invite <player|uuid>")
                        && all(actor).contains("Use Tab"),
                "invite without target gives targeted guidance");
        clear(actor);
        run(command, actor, "invite", "Player", "extra");
        check(last(actor).contains("Usage: /clan invite <player|uuid>"),
                "invite rejects extra arguments instead of joining them");
        clear(actor);
        run(command, actor, "accept");
        check(all(actor).contains("You have no pending clan invites.")
                        && all(actor).contains("Invites will appear here")
                        && !all(actor).contains("/clan help"),
                "accept without invitations has a useful empty state");
        clear(actor);
        run(command, actor, "deny");
        check(all(actor).contains("You have no pending clan invites."),
                "deny without invitations shares the useful empty state");
        clear(actor);
        run(command, actor, "accept", "BMW", "extra");
        check(last(actor).contains("Usage: /clan accept <tag|uuid>"),
                "accept rejects extra arguments locally");
        clear(actor);
        run(command, actor, "deny", "BMW", "extra");
        check(last(actor).contains("Usage: /clan deny <tag|uuid>"),
                "deny rejects extra arguments locally");
        clear(actor);
        run(command, actor, "help", "extra");
        check(last(actor).contains("Usage: /clan help"), "help rejects additional arguments locally");
        clear(actor);
        run(command, actor, "HeLp");
        check(all(actor).contains("/clan create") && all(actor).contains("/clan disband"),
                "command action matching remains case-insensitive");
        clear(actor);
        run(command, actor, "<red>unknown</red>");
        check(all(actor).contains("Unknown clan command: <red>unknown</red>")
                        && all(actor).contains("Use /clan help to view all clan commands."),
                "unknown command is identified and points to optional help");
        check(hasLiteral(actor.received, "<red>unknown</red>", NamedTextColor.AQUA),
                "unknown user input remains literal component text");

        ClanMenuFixture choices = new ClanMenuFixture();
        var owner = choices.player("<green>Owner</green>", true);
        var secondOwner = choices.player("SecondOwner", true);
        var recipient = choices.player("Invitee", true);
        var unsafeTarget = choices.player("<blue>Target</blue>", true);
        var member = choices.player("Member", true);
        var twin = choices.player("Twin", false);
        choices.player("twin", false);
        check(choices.clans.createClan(owner.id, "<red>Safe</red>", "<X>") == ClanResult.SUCCESS,
                "unsafe-looking clan fixture is valid literal domain data");
        check(choices.clans.createClan(secondOwner.id, "Second Community", "TWO") == ClanResult.SUCCESS,
                "second invite source fixture");
        UUID firstClan = choices.clans.getClanOf(owner.id).orElseThrow().id();
        UUID secondClan = choices.clans.getClanOf(secondOwner.id).orElseThrow().id();
        check(choices.clans.inviteMember(owner.id, recipient.id) == ClanResult.SUCCESS,
                "single incoming invite fixture");
        ClanCommand choicesCommand = command(choices);

        clear(recipient);
        run(choicesCommand, recipient, "accept");
        check(all(recipient).contains("Pending clan invites:")
                        && all(recipient).contains("<red>Safe</red> [<X>]")
                        && all(recipient).contains("Use /clan accept <tag|uuid>"),
                "accept without target lists one invite with name and tag");
        check(hasClick(recipient, "/clan accept " + firstClan),
                "single incoming invite exposes safe accept suggestion");
        check(hasLiteral(recipient.received, "<red>Safe</red>", NamedTextColor.GOLD)
                        && hasLiteral(recipient.received, "<X>", NamedTextColor.AQUA),
                "unsafe-looking clan name and tag remain literal colored components");

        check(choices.clans.inviteMember(secondOwner.id, recipient.id) == ClanResult.SUCCESS,
                "multiple incoming invite fixture");
        clear(recipient);
        run(choicesCommand, recipient, "accept");
        check(all(recipient).contains("<red>Safe</red> [<X>]")
                        && all(recipient).contains("Second Community [TWO]")
                        && hasClick(recipient, "/clan accept " + firstClan)
                        && hasClick(recipient, "/clan accept " + secondClan),
                "accept without target lists multiple actionable invitations");
        check(clickActionsMatchPurpose(recipient.received), "accept choices run exact UUID actions; syntax stays editable");

        clear(recipient);
        run(choicesCommand, recipient, "deny");
        check(all(recipient).contains("Pending clan invites:")
                        && hasClick(recipient, "/clan deny " + firstClan)
                        && hasClick(recipient, "/clan deny " + secondClan),
                "deny without target lists deny suggestions");
        check(clickActionsMatchPurpose(recipient.received), "deny choices run exact UUID actions; syntax stays editable");
        check(Set.copyOf(choicesCommand.onTabComplete(recipient.player, null, "clan",
                        new String[]{"accept", ""})).equals(Set.of("<X>", "TWO"))
                        && Set.copyOf(choicesCommand.onTabComplete(recipient.player, null, "clan",
                        new String[]{"deny", ""})).equals(Set.of("<X>", "TWO")),
                "accept and deny completion contain only current incoming invite tags");

        clear(owner);
        clear(unsafeTarget);
        run(choicesCommand, owner, "invite", unsafeTarget.id.toString());
        check(all(owner).contains("<blue>Target</blue>")
                        && hasLiteral(owner.received, "<blue>Target</blue>", dev.vapee.core.message.MessageStyle.HIGHLIGHT),
                "unsafe-looking player name remains literal in command feedback");
        check(all(unsafeTarget).contains("<green>Owner</green>")
                        && all(unsafeTarget).contains("<red>Safe</red> [<X>]")
                        && hasClick(unsafeTarget, "/clan accept " + firstClan)
                        && hasClick(unsafeTarget, "/clan deny " + firstClan),
                "invite notification explains owner, clan name, tag, accept, and deny");
        check(hasLiteral(unsafeTarget.received, "<green>Owner</green>", dev.vapee.core.message.MessageStyle.HIGHLIGHT)
                        && hasLiteral(unsafeTarget.received, "<red>Safe</red>", dev.vapee.core.message.MessageStyle.HIGHLIGHT)
                        && hasLiteral(unsafeTarget.received, "<X>", NamedTextColor.AQUA),
                "invite notification keeps all user data as literal components");
        check(clickActionsMatchPurpose(unsafeTarget.received), "invite notification uses direct UUID actions");

        check(choices.clans.inviteMember(owner.id, twin.id) == ClanResult.SUCCESS,
                "ambiguous outgoing target fixture");
        clear(owner);
        run(choicesCommand, owner, "cancel");
        check(all(owner).contains("Outgoing clan invites:")
                        && all(owner).contains("Invitee")
                        && all(owner).contains("<blue>Target</blue>")
                        && all(owner).contains("Twin"),
                "cancel without target lists current outgoing invitations");
        check(hasClick(owner, "/clan cancel " + twin.id),
                "ambiguous outgoing player uses UUID in cancel suggestion");
        check(Set.copyOf(choicesCommand.onTabComplete(owner.player, null, "clan",
                        new String[]{"cancel", ""})).equals(Set.of(
                        "Invitee", "<blue>Target</blue>", twin.id.toString())),
                "cancel completion contains only outgoing targets and uses UUID for ambiguity");
        clear(owner);
        run(choicesCommand, owner, "cancel", recipient.name, "extra");
        check(last(owner).contains("Usage: /clan cancel <player|uuid>"),
                "cancel rejects extra arguments locally");

        check(choices.clans.inviteMember(owner.id, member.id) == ClanResult.SUCCESS
                        && choices.clans.acceptInvite(member.id, firstClan) == ClanResult.SUCCESS,
                "member fixture for kick and transfer completion");
        List<String> kickTargets = choicesCommand.onTabComplete(owner.player, null, "clan",
                new String[]{"kick", ""});
        List<String> transferTargets = choicesCommand.onTabComplete(owner.player, null, "clan",
                new String[]{"transfer", ""});
        check(kickTargets.equals(List.of("Member")) && transferTargets.equals(List.of("Member")),
                "kick and transfer completion include only other current members");

        clear(owner);
        run(choicesCommand, owner, "kick");
        check(last(owner).contains("Usage: /clan kick <player|uuid>"), "kick missing target has local usage");
        clear(owner);
        run(choicesCommand, owner, "transfer");
        check(last(owner).contains("Usage: /clan transfer <player|uuid>"),
                "transfer missing target has local usage");
        clear(owner);
        run(choicesCommand, owner, "rename");
        check(all(owner).contains("Missing clan name.")
                        && all(owner).contains("Usage: /clan rename <name...>")
                        && all(owner).contains("Example: /clan rename BMW Drivers"),
                "rename missing name has usage and example");
        clear(owner);
        run(choicesCommand, owner, "tag");
        check(all(owner).contains("Missing clan tag.")
                        && all(owner).contains("Usage: /clan tag <tag>")
                        && all(owner).contains("Example: /clan tag BMW"),
                "tag missing value has usage and example");
        clear(owner);
        run(choicesCommand, owner, "tag", "BMW", "EXTRA");
        check(last(owner).contains("Usage: /clan tag <tag>"), "tag rejects extra arguments locally");
        clear(owner);
        run(choicesCommand, owner, "leave", "extra");
        check(last(owner).contains("Usage: /clan leave") && choices.clans.getClan(firstClan).isPresent(),
                "leave rejects extra arguments without mutation");
        clear(owner);
        run(choicesCommand, owner, "disband");
        check(all(owner).contains("/clan disband confirm")
                        && hasClick(owner, "/clan disband confirm")
                        && choices.clans.getClan(firstClan).isPresent(),
                "disband without confirmation exposes a suggestable safe command");
        clear(owner);
        run(choicesCommand, owner, "disband", "yes");
        check(hasClick(owner, "/clan disband confirm") && choices.clans.getClan(firstClan).isPresent(),
                "wrong disband confirmation never mutates clan state");

        ClanMenuFixture emptyOutgoing = new ClanMenuFixture();
        var emptyOwner = emptyOutgoing.player("EmptyOwner", true);
        check(emptyOutgoing.clans.createClan(emptyOwner.id, "Empty Clan", "EMPTY") == ClanResult.SUCCESS,
                "empty outgoing fixture");
        clear(emptyOwner);
        run(command(emptyOutgoing), emptyOwner, "cancel");
        check(last(emptyOwner).contains("Your clan has no pending outgoing invites."),
                "cancel without outgoing invites has a useful empty state");

        check(choices.clans.getClan(secondClan).isPresent(), "invite choice rendering leaves unrelated clan intact");
    }

    private static void safeRelationArguments() throws Exception {
        ClanMenuFixture f = new ClanMenuFixture();
        var owner = f.player("Owner", true);
        var safe = f.player("Safe", false);
        var spaced = f.player("Two Words", false);
        var wrong = f.player(owner.id.toString(), false);
        var twin = f.player("Twin", false);
        f.player("twin", false);
        var missing = f.player("Missing", false);
        f.player("UnrelatedKnown", false);
        check(f.clans.createClan(owner.id, "Safety Clan", "SAFE") == ClanResult.SUCCESS, "create safety fixture");
        for (var target : List.of(safe, spaced, wrong, twin, missing)) {
            check(f.clans.inviteMember(owner.id, target.id) == ClanResult.SUCCESS, "seed outgoing relation");
        }
        f.known.remove(missing.id);
        ClanCommand c = command(f);
        check(Set.copyOf(c.onTabComplete(owner.player, null, "clan", new String[]{"cancel", ""}))
                .equals(Set.of("Safe", spaced.id.toString(), wrong.id.toString(), twin.id.toString(), missing.id.toString())),
                "cancel keeps only outgoing relations and safe UUID fallbacks");
        run(c, owner, "cancel");
        for (var target : List.of(spaced, wrong, twin, missing)) {
            check(hasClick(owner, "/clan cancel " + target.id), "cancel button uses same safe formatter");
        }
        UUID clan = f.clans.getClanOf(owner.id).orElseThrow().id();
        check(f.clans.acceptInvite(spaced.id, clan) == ClanResult.SUCCESS, "seed spaced member");
        for (String action : List.of("kick", "transfer")) {
            check(c.onTabComplete(owner.player, null, "clan", new String[]{action, ""})
                    .equals(List.of(spaced.id.toString())), "member-only " + action + " keeps UUID safety");
            check(c.onTabComplete(spaced.player, null, "clan", new String[]{action, ""}).isEmpty(),
                    "member gains no owner suggestions");
        }
    }

    private static void directActions() throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("vapeecore-clan-click-");
        try {
            for (String action : List.of("accept", "deny")) {
                ClanMenuFixture f = new ClanMenuFixture();
                var owner = f.player("Owner", true);
                var second = f.player("OtherOwner", true);
                var recipient = f.player("Recipient", true);
                var unrelated = f.player("Unrelated", true);
                java.nio.file.Path file = directory.resolve(action + ".yml");
                ClanService service = new ClanService(new FileClanRepository(file), ClanLimits.defaults(),
                        java.time.Clock.systemUTC(), UUID::randomUUID);
                ClanCommand command = command(f, service);
                run(command, owner, "create", "OLD", "Original Clan");
                UUID original = service.getClanOf(owner.id).orElseThrow().id();
                run(command, owner, "invite", recipient.id.toString());
                List<ClickEvent> buttons = clicks(recipient.received);
                check(buttons.size() == 2 && buttons.stream().allMatch(c -> c.action() == ClickEvent.Action.RUN_COMMAND),
                        "invitation notification has two direct actions");
                check(buttons.stream().map(ClickEvent::value).toList().equals(List.of(
                                "/clan accept " + original, "/clan deny " + original)),
                        "clan notification actions contain the exact clan UUID");
                ClickEvent click = buttons.stream().filter(c -> c.value().startsWith("/clan " + action + " "))
                        .findFirst().orElseThrow();
                run(command, owner, "tag", "NEW");
                run(command, second, "create", "OLD", "Other Clan");
                UUID other = service.getClanOf(second.id).orElseThrow().id();
                run(command, second, "invite", recipient.id.toString());
                clear(recipient);
                run(command, recipient, action);
                check(hasClick(recipient, "/clan " + action + " " + original)
                                && hasClick(recipient, "/clan " + action + " " + other),
                        "incoming choices also target stable UUIDs after tag reuse");
                byte[] pending = java.nio.file.Files.readAllBytes(file);
                recipient.permitted = false;
                click(command, recipient, click);
                check(java.util.Arrays.equals(pending, java.nio.file.Files.readAllBytes(file)),
                        "direct action does not bypass permission");
                recipient.permitted = true;
                click(command, unrelated, click);
                check(java.util.Arrays.equals(pending, java.nio.file.Files.readAllBytes(file)),
                        "copied invitation cannot grant unrelated membership");
                click(command, recipient, click);
                check(service.getClanOf(recipient.id).map(Clan::id).orElse(null) == null ||
                                service.getClanOf(recipient.id).orElseThrow().id().equals(original),
                        "old tag button never selects the other clan");
                check(service.getClanOf(recipient.id).isPresent() == action.equals("accept")
                                && service.getIncomingInvites(recipient.id).stream().noneMatch(i -> i.clanId().equals(original)),
                        "click changes only the intended invitation via existing service");
                if (action.equals("deny")) check(service.getIncomingInvites(recipient.id).stream().anyMatch(i -> i.clanId().equals(other)),
                        "denying original invitation preserves unrelated pending invitation");
                byte[] handled = java.nio.file.Files.readAllBytes(file);
                click(command, recipient, click);
                check(java.util.Arrays.equals(handled, java.nio.file.Files.readAllBytes(file)),
                        "handled invitation cannot save or apply twice");
                ClanSnapshot cold = new FileClanRepository(file).initialize();
                check(cold.clans().stream().anyMatch(c -> c.id().equals(original)
                                && c.members().stream().anyMatch(m -> m.playerId().equals(recipient.id))) == action.equals("accept"),
                        "cold clan repository confirms final membership");
            }
            ClanMenuFixture f = new ClanMenuFixture();
            var owner = f.player("Owner", true); var replacement = f.player("Replacement", true);
            var recipient = f.player("Recipient", true);
            java.nio.file.Path file = directory.resolve("disbanded.yml");
            ClanService service = new ClanService(new FileClanRepository(file), ClanLimits.defaults(),
                    java.time.Clock.systemUTC(), UUID::randomUUID);
            ClanCommand command = command(f, service);
            run(command, owner, "create", "OLD", "Original");
            run(command, owner, "invite", recipient.id.toString());
            ClickEvent stale = clicks(recipient.received).getFirst();
            run(command, owner, "disband", "confirm");
            run(command, replacement, "create", "OLD", "Replacement");
            run(command, replacement, "invite", recipient.id.toString());
            byte[] current = java.nio.file.Files.readAllBytes(file);
            click(command, recipient, stale);
            check(service.getClanOf(recipient.id).isEmpty() && java.util.Arrays.equals(current, java.nio.file.Files.readAllBytes(file)),
                    "disbanded clan UUID cannot accept an unrelated tag replacement");
            ClickEvent cancelled = clicks(recipient.received).stream().filter(c -> c.action() == ClickEvent.Action.RUN_COMMAND)
                    .reduce((a, b) -> b).orElseThrow();
            run(command, replacement, "cancel", recipient.id.toString());
            byte[] absent = java.nio.file.Files.readAllBytes(file);
            click(command, recipient, cancelled);
            check(java.util.Arrays.equals(absent, java.nio.file.Files.readAllBytes(file)),
                    "cancelled invitation direct action is rejected without a save");
        } finally {
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
            }
        }
    }

    private static void click(ClanCommand command, ClanMenuFixture.TestPlayer actor, ClickEvent click) {
        check(click.action() == ClickEvent.Action.RUN_COMMAND && click.value().startsWith("/clan "),
                "execute only generated invitation command");
        run(command, actor, click.value().substring("/clan ".length()).split(" "));
    }

    private static ClanCommand command(ClanMenuFixture fixture) {
        return command(fixture, fixture.clans);
    }

    private static ClanCommand command(ClanMenuFixture fixture, ClanService service) {
        return new ClanCommand(service, fixture.identity, fixture.messages,
                new CommandHelpRenderer(fixture.messages), () -> fixture.players.values().stream()
                .filter(player -> player.online).map(player -> player.player).toList(),
                id -> { var player = fixture.players.get(id); return player != null && player.online
                        ? player.player : null; }, ClanMenuFixture.logger(), ignored -> { }, ignored -> { });
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
    private static void clear(ClanMenuFixture.TestPlayer player) { player.received.clear(); }
    private static ClickEvent.Action expectedAction(String command) {
        return command.matches("/clan (accept|deny) [0-9a-f-]{36}")
                ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
    }
    private static boolean hasClick(ClanMenuFixture.TestPlayer player, String command) {
        return clicks(player.received).stream().anyMatch(click -> click.action() == expectedAction(command)
                && click.value().equals(command));
    }
    private static boolean clickActionsMatchPurpose(List<Component> messages) {
        List<ClickEvent> clicks = clicks(messages);
        return !clicks.isEmpty() && clicks.stream()
                .allMatch(click -> click.action() == expectedAction(click.value()));
    }
    private static List<ClickEvent> clicks(List<Component> messages) {
        List<ClickEvent> result = new ArrayList<>();
        for (Component message : messages) collectClicks(message, result);
        return result;
    }
    private static void collectClicks(Component component, List<ClickEvent> result) {
        if (component.clickEvent() != null) result.add(component.clickEvent());
        for (Component child : component.children()) collectClicks(child, result);
    }
    private static boolean hasLiteral(List<Component> messages, String text, NamedTextColor color) {
        return messages.stream().anyMatch(message -> hasLiteral(message, text, color));
    }
    private static boolean hasLiteral(Component component, String text, NamedTextColor color) {
        if (component instanceof TextComponent literal
                && literal.content().equals(text) && color.equals(component.color())) return true;
        return component.children().stream().anyMatch(child -> hasLiteral(child, text, color));
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}

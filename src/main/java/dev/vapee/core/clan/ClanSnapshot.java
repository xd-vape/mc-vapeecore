package dev.vapee.core.clan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Complete, validated, immutable and deterministically ordered persistent state. */
public record ClanSnapshot(List<Clan> clans, List<ClanInvite> invites) {
    public ClanSnapshot {
        Objects.requireNonNull(clans, "clans");
        Objects.requireNonNull(invites, "invites");
        ArrayList<Clan> sortedClans = new ArrayList<>(clans.size());
        Map<UUID, Clan> byId = new HashMap<>();
        Set<String> names = new HashSet<>(), tags = new HashSet<>();
        Set<UUID> members = new HashSet<>();
        for (Clan clan : clans) {
            Clan checked = Objects.requireNonNull(clan, "clan");
            if (byId.putIfAbsent(checked.id(), checked) != null)
                throw new IllegalArgumentException("Duplicate clan ID: " + checked.id());
            if (!names.add(ClanText.key(checked.name())))
                throw new IllegalArgumentException("Duplicate clan name: " + checked.name());
            if (!tags.add(ClanText.key(checked.tag())))
                throw new IllegalArgumentException("Duplicate clan tag: " + checked.tag());
            for (ClanMember member : checked.members()) {
                if (!members.add(member.playerId()))
                    throw new IllegalArgumentException("Player belongs to multiple clans: " + member.playerId());
            }
            sortedClans.add(checked);
        }
        sortedClans.sort((a, b) -> a.id().compareTo(b.id()));

        ArrayList<ClanInvite> sortedInvites = new ArrayList<>(invites.size());
        Set<InviteKey> inviteKeys = new HashSet<>();
        for (ClanInvite invite : invites) {
            ClanInvite checked = Objects.requireNonNull(invite, "invite");
            if (!byId.containsKey(checked.clanId()))
                throw new IllegalArgumentException("Invite references unknown clan: " + checked.clanId());
            if (members.contains(checked.recipient()))
                throw new IllegalArgumentException("Invite recipient is already a clan member: " + checked.recipient());
            if (!inviteKeys.add(new InviteKey(checked.clanId(), checked.recipient())))
                throw new IllegalArgumentException("Duplicate clan invite: " + checked.clanId() + "/" + checked.recipient());
            sortedInvites.add(checked);
        }
        sortedInvites.sort((a, b) -> {
            int clan = a.clanId().compareTo(b.clanId());
            return clan != 0 ? clan : a.recipient().compareTo(b.recipient());
        });
        clans = List.copyOf(sortedClans);
        invites = List.copyOf(sortedInvites);
    }

    public static ClanSnapshot empty() {
        return new ClanSnapshot(List.of(), List.of());
    }

    private record InviteKey(UUID clanId, UUID recipient) { }
}

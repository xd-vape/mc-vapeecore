package dev.vapee.core.clan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable clan: ownership is derived exclusively from the member list. */
public record Clan(UUID id, String name, String tag, Instant createdAt, List<ClanMember> members) {
    public Clan {
        Objects.requireNonNull(id, "id");
        name = ClanText.name(name);
        tag = ClanText.tag(tag);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(members, "members");
        ArrayList<ClanMember> sorted = new ArrayList<>(members.size());
        Set<UUID> players = new HashSet<>();
        int owners = 0;
        for (ClanMember member : members) {
            ClanMember checked = Objects.requireNonNull(member, "member");
            if (!players.add(checked.playerId())) {
                throw new IllegalArgumentException("Duplicate clan member: " + checked.playerId());
            }
            if (checked.role() == ClanRole.OWNER) owners++;
            sorted.add(checked);
        }
        if (owners != 1) throw new IllegalArgumentException("A clan must have exactly one owner");
        sorted.sort((a, b) -> a.playerId().compareTo(b.playerId()));
        members = List.copyOf(sorted);
    }

    public UUID ownerId() {
        return members.stream().filter(member -> member.role() == ClanRole.OWNER)
                .findFirst().orElseThrow().playerId();
    }
}

package dev.vapee.core.clan;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Main-thread-owned clan domain service; no Bukkit, scheduler or concurrent state. */
public final class ClanService {
    private final ClanRepository repository;
    private final Supplier<ClanLimits> limitsSupplier;
    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private ClanSnapshot state;

    public ClanService(ClanRepository repository, ClanLimits limits, Clock clock, Supplier<UUID> idSupplier) {
        this(repository, () -> Objects.requireNonNull(limits, "limits"), clock, idSupplier);
    }

    public ClanService(ClanRepository repository, Supplier<ClanLimits> limitsSupplier,
                       Clock clock, Supplier<UUID> idSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limitsSupplier = Objects.requireNonNull(limitsSupplier, "limitsSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
        ClanSnapshot loaded = Objects.requireNonNull(repository.initialize(), "repository snapshot");
        this.state = new ClanSnapshot(loaded.clans(), loaded.invites());
    }

    public ClanResult createClan(UUID owner, String name, String tag) {
        UUID player = Objects.requireNonNull(owner, "owner");
        if (getClanOf(player).isPresent()) return ClanResult.ALREADY_IN_CLAN;
        ClanLimits limits = limits();
        String normalizedName = validName(name, limits);
        if (normalizedName == null) return ClanResult.NAME_INVALID;
        String normalizedTag = validTag(tag, limits);
        if (normalizedTag == null) return ClanResult.TAG_INVALID;
        if (findClanByName(normalizedName).isPresent()) return ClanResult.NAME_ALREADY_USED;
        if (findClanByTag(normalizedTag).isPresent()) return ClanResult.TAG_ALREADY_USED;
        UUID id = Objects.requireNonNull(idSupplier.get(), "generated clan ID");
        if (getClan(id).isPresent()) throw new IllegalStateException("Generated duplicate clan ID: " + id);
        Instant now = clock.instant();
        Clan clan = new Clan(id, normalizedName, normalizedTag, now,
                List.of(new ClanMember(player, ClanRole.OWNER, now)));
        ArrayList<Clan> next = new ArrayList<>(state.clans());
        next.add(clan);
        // Becoming an owner also invalidates every pending incoming invite.
        persist(next, state.invites().stream().filter(invite -> !invite.recipient().equals(player)).toList());
        return ClanResult.SUCCESS;
    }

    public ClanResult renameClan(UUID actor, String newName) {
        Objects.requireNonNull(newName, "newName");
        Clan clan = ownedClan(actor);
        if (clan == null) return ownershipFailure(actor);
        String name = validName(newName, limits());
        if (name == null) return ClanResult.NAME_INVALID;
        Optional<Clan> existing = findClanByName(name);
        if (existing.isPresent() && !existing.get().id().equals(clan.id())) return ClanResult.NAME_ALREADY_USED;
        replace(clan, new Clan(clan.id(), name, clan.tag(), clan.createdAt(), clan.members()), state.invites());
        return ClanResult.SUCCESS;
    }

    public ClanResult changeTag(UUID actor, String newTag) {
        Objects.requireNonNull(newTag, "newTag");
        Clan clan = ownedClan(actor);
        if (clan == null) return ownershipFailure(actor);
        String tag = validTag(newTag, limits());
        if (tag == null) return ClanResult.TAG_INVALID;
        Optional<Clan> existing = findClanByTag(tag);
        if (existing.isPresent() && !existing.get().id().equals(clan.id())) return ClanResult.TAG_ALREADY_USED;
        replace(clan, new Clan(clan.id(), clan.name(), tag, clan.createdAt(), clan.members()), state.invites());
        return ClanResult.SUCCESS;
    }

    public ClanResult inviteMember(UUID actor, UUID target) {
        UUID recipient = Objects.requireNonNull(target, "target");
        Clan clan = ownedClan(actor);
        if (clan == null) return ownershipFailure(actor);
        if (actor.equals(recipient)) return ClanResult.CANNOT_TARGET_SELF;
        if (isMember(recipient)) return ClanResult.TARGET_ALREADY_MEMBER;
        ClanLimits limits = limits();
        if (clan.members().size() >= limits.maxMembers()) return ClanResult.MEMBER_LIMIT_REACHED;
        if (findInvite(clan.id(), recipient).isPresent()) return ClanResult.INVITE_ALREADY_EXISTS;
        if (getOutgoingInvites(clan.id()).size() >= limits.maxOutgoingInvites())
            return ClanResult.OUTGOING_INVITE_LIMIT_REACHED;
        if (getIncomingInvites(recipient).size() >= limits.maxIncomingInvites())
            return ClanResult.INCOMING_INVITE_LIMIT_REACHED;
        ArrayList<ClanInvite> next = new ArrayList<>(state.invites());
        next.add(new ClanInvite(clan.id(), recipient, clock.instant()));
        persist(state.clans(), next);
        return ClanResult.SUCCESS;
    }

    public ClanResult acceptInvite(UUID playerId, UUID clanId) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        UUID id = Objects.requireNonNull(clanId, "clanId");
        if (isMember(player)) return ClanResult.ALREADY_IN_CLAN;
        Clan clan = getClan(id).orElse(null);
        if (clan == null) return ClanResult.CLAN_NOT_FOUND;
        if (findInvite(id, player).isEmpty()) return ClanResult.INVITE_NOT_FOUND;
        if (clan.members().size() >= limits().maxMembers()) return ClanResult.MEMBER_LIMIT_REACHED;
        ArrayList<ClanMember> members = new ArrayList<>(clan.members());
        members.add(new ClanMember(player, ClanRole.MEMBER, clock.instant()));
        ArrayList<ClanInvite> remaining = new ArrayList<>();
        for (ClanInvite invite : state.invites()) {
            if (!invite.recipient().equals(player)) remaining.add(invite);
        }
        replace(clan, new Clan(clan.id(), clan.name(), clan.tag(), clan.createdAt(), members), remaining);
        return ClanResult.SUCCESS;
    }

    public ClanResult denyInvite(UUID playerId, UUID clanId) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        UUID id = Objects.requireNonNull(clanId, "clanId");
        if (getClan(id).isEmpty()) return ClanResult.CLAN_NOT_FOUND;
        if (findInvite(id, player).isEmpty()) return ClanResult.INVITE_NOT_FOUND;
        persist(state.clans(), withoutInvite(id, player));
        return ClanResult.SUCCESS;
    }

    public ClanResult cancelInvite(UUID actor, UUID target) {
        UUID recipient = Objects.requireNonNull(target, "target");
        Clan clan = ownedClan(actor);
        if (clan == null) return ownershipFailure(actor);
        if (actor.equals(recipient)) return ClanResult.CANNOT_TARGET_SELF;
        if (findInvite(clan.id(), recipient).isEmpty()) return ClanResult.INVITE_NOT_FOUND;
        persist(state.clans(), withoutInvite(clan.id(), recipient));
        return ClanResult.SUCCESS;
    }

    public ClanResult leaveClan(UUID playerId) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        Clan clan = getClanOf(player).orElse(null);
        if (clan == null) return ClanResult.NOT_IN_CLAN;
        if (clan.ownerId().equals(player)) return ClanResult.OWNER_CANNOT_LEAVE;
        replace(clan, withMemberRemoved(clan, player), state.invites());
        return ClanResult.SUCCESS;
    }

    public ClanResult kickMember(UUID actor, UUID target) {
        UUID player = Objects.requireNonNull(target, "target");
        Clan clan = ownedClan(actor);
        if (clan == null) return ownershipFailure(actor);
        if (actor.equals(player)) return ClanResult.CANNOT_TARGET_SELF;
        if (clan.members().stream().noneMatch(member -> member.playerId().equals(player)))
            return ClanResult.TARGET_NOT_MEMBER;
        replace(clan, withMemberRemoved(clan, player), state.invites());
        return ClanResult.SUCCESS;
    }

    public ClanResult transferOwnership(UUID actor, UUID target) {
        UUID player = Objects.requireNonNull(target, "target");
        Clan clan = ownedClan(actor);
        if (clan == null) return ownershipFailure(actor);
        if (actor.equals(player)) return ClanResult.CANNOT_TARGET_SELF;
        if (clan.members().stream().noneMatch(member -> member.playerId().equals(player)))
            return ClanResult.TARGET_NOT_MEMBER;
        ArrayList<ClanMember> members = new ArrayList<>(clan.members().size());
        for (ClanMember member : clan.members()) {
            ClanRole role = member.playerId().equals(actor) ? ClanRole.MEMBER
                    : member.playerId().equals(player) ? ClanRole.OWNER : member.role();
            members.add(new ClanMember(member.playerId(), role, member.joinedAt()));
        }
        replace(clan, new Clan(clan.id(), clan.name(), clan.tag(), clan.createdAt(), members), state.invites());
        return ClanResult.SUCCESS;
    }

    public ClanResult disbandClan(UUID actor) {
        Clan clan = ownedClan(actor);
        if (clan == null) return ownershipFailure(actor);
        ArrayList<Clan> remainingClans = new ArrayList<>();
        for (Clan entry : state.clans()) if (!entry.id().equals(clan.id())) remainingClans.add(entry);
        ArrayList<ClanInvite> remainingInvites = new ArrayList<>();
        for (ClanInvite invite : state.invites()) if (!invite.clanId().equals(clan.id())) remainingInvites.add(invite);
        persist(remainingClans, remainingInvites);
        return ClanResult.SUCCESS;
    }

    public Optional<Clan> getClan(UUID clanId) {
        UUID id = Objects.requireNonNull(clanId, "clanId");
        return state.clans().stream().filter(clan -> clan.id().equals(id)).findFirst();
    }

    public Optional<Clan> getClanOf(UUID playerId) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        return state.clans().stream().filter(clan -> clan.members().stream()
                .anyMatch(member -> member.playerId().equals(player))).findFirst();
    }

    public Optional<Clan> findClanByName(String name) {
        String key = ClanText.key(Objects.requireNonNull(name, "name").strip());
        return state.clans().stream().filter(clan -> ClanText.key(clan.name()).equals(key)).findFirst();
    }

    public Optional<Clan> findClanByTag(String tag) {
        String key = ClanText.key(Objects.requireNonNull(tag, "tag").strip());
        return state.clans().stream().filter(clan -> ClanText.key(clan.tag()).equals(key)).findFirst();
    }

    public Optional<ClanRole> getMemberRole(UUID playerId) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        return getClanOf(player).flatMap(clan -> clan.members().stream()
                .filter(member -> member.playerId().equals(player)).map(ClanMember::role).findFirst());
    }

    public List<ClanMember> getMembers(UUID clanId) {
        return getClan(clanId).map(Clan::members).orElseGet(List::of);
    }

    public List<ClanInvite> getIncomingInvites(UUID playerId) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        return state.invites().stream().filter(invite -> invite.recipient().equals(player)).toList();
    }

    public List<ClanInvite> getOutgoingInvites(UUID clanId) {
        UUID id = Objects.requireNonNull(clanId, "clanId");
        return state.invites().stream().filter(invite -> invite.clanId().equals(id)).toList();
    }

    public int countMembers(UUID clanId) { return getMembers(clanId).size(); }
    public boolean isMember(UUID playerId) { return getClanOf(playerId).isPresent(); }
    public ClanLimits getLimits() { return limits(); }

    private Clan ownedClan(UUID actor) {
        UUID player = Objects.requireNonNull(actor, "actor");
        return getClanOf(player).filter(clan -> clan.ownerId().equals(player)).orElse(null);
    }

    private ClanResult ownershipFailure(UUID actor) {
        return getClanOf(actor).isPresent() ? ClanResult.NOT_OWNER : ClanResult.NOT_IN_CLAN;
    }

    private Optional<ClanInvite> findInvite(UUID clanId, UUID recipient) {
        return state.invites().stream().filter(invite -> invite.clanId().equals(clanId)
                && invite.recipient().equals(recipient)).findFirst();
    }

    private List<ClanInvite> withoutInvite(UUID clanId, UUID recipient) {
        return state.invites().stream().filter(invite -> !invite.clanId().equals(clanId)
                || !invite.recipient().equals(recipient)).toList();
    }

    private Clan withMemberRemoved(Clan clan, UUID player) {
        return new Clan(clan.id(), clan.name(), clan.tag(), clan.createdAt(), clan.members().stream()
                .filter(member -> !member.playerId().equals(player)).toList());
    }

    private String validName(String value, ClanLimits limits) {
        try {
            String text = ClanText.name(Objects.requireNonNull(value, "name"));
            return ClanText.within(text, limits.minNameLength(), limits.maxNameLength()) ? text : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String validTag(String value, ClanLimits limits) {
        try {
            String text = ClanText.tag(Objects.requireNonNull(value, "tag"));
            return ClanText.within(text, limits.minTagLength(), limits.maxTagLength()) ? text : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ClanLimits limits() { return Objects.requireNonNull(limitsSupplier.get(), "clan limits"); }

    private void replace(Clan previous, Clan replacement, List<ClanInvite> invites) {
        ArrayList<Clan> next = new ArrayList<>(state.clans().size());
        for (Clan clan : state.clans()) next.add(clan.id().equals(previous.id()) ? replacement : clan);
        persist(next, invites);
    }

    private void persist(List<Clan> clans, List<ClanInvite> invites) {
        ClanSnapshot next = new ClanSnapshot(clans, invites);
        repository.save(next);
        state = next;
    }
}

package dev.vapee.core.friend;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class FriendServiceHarness {

    private static final Instant NOW = Instant.ofEpochMilli(987_654_321L);
    private static final FriendLimits LARGE_LIMITS = new FriendLimits(20, 20, 20);
    private static int checks;

    private FriendServiceHarness() {
    }

    public static void main(String[] args) {
        sendAndCrossRequest();
        sendPoliciesAndLimits();
        accept();
        denyCancelRemove();
        queries();
        persistenceRollback();
        reloadableLimitsAndPolicy();
        System.out.println("FriendServiceHarness passed " + checks + " checks.");
    }

    private static void sendAndCrossRequest() {
        UUID first = id(1);
        UUID second = id(2);
        MemoryRepository repository = new MemoryRepository(FriendSnapshot.empty());
        FriendService service = service(repository, LARGE_LIMITS, FriendRequestPolicy.allowAll());

        check(service.sendRequest(first, first) == FriendResult.SELF,
                "send rejects self without throwing");
        check(repository.saves == 0, "self send is not persisted");
        check(service.sendRequest(first, second) == FriendResult.SUCCESS, "send succeeds");
        check(repository.saves == 1, "successful send persists immediately");
        check(service.getRelation(first, second) == FriendRelation.OUTGOING_REQUEST
                        && service.getRelation(second, first) == FriendRelation.INCOMING_REQUEST,
                "request relation is directional");
        check(service.getOutgoingRequests(first).getFirst().createdAt().equals(NOW),
                "send uses injected clock");
        check(service.sendRequest(first, second) == FriendResult.REQUEST_ALREADY_SENT,
                "duplicate outgoing request is rejected");
        check(repository.saves == 1, "duplicate request does not persist");

        check(service.sendRequest(second, first) == FriendResult.AUTO_ACCEPTED,
                "cross request auto accepts");
        check(repository.saves == 2, "cross request is one persisted mutation");
        check(service.getRelation(first, second) == FriendRelation.FRIENDS,
                "cross request creates friendship");
        check(service.getIncomingRequests(first).isEmpty()
                        && service.getOutgoingRequests(first).isEmpty(),
                "cross request removes the pending request");
        check(service.sendRequest(first, second) == FriendResult.ALREADY_FRIENDS,
                "friends cannot send another request");
    }

    private static void sendPoliciesAndLimits() {
        UUID first = id(1);
        UUID second = id(2);
        UUID third = id(3);
        MutablePolicy policy = new MutablePolicy();
        MemoryRepository policyRepository = new MemoryRepository(FriendSnapshot.empty());
        FriendService policyService = service(policyRepository, LARGE_LIMITS, policy);

        policy.decision = FriendRequestDecision.BLOCKED;
        check(policyService.sendRequest(first, second) == FriendResult.BLOCKED,
                "ignore policy can block send");
        policy.decision = FriendRequestDecision.REQUESTS_DISABLED;
        check(policyService.sendRequest(first, second) == FriendResult.REQUESTS_DISABLED,
                "privacy policy can disable requests");
        check(policyRepository.saves == 0, "policy rejection does not persist");

        Friendship existing = new Friendship(first, third, Instant.EPOCH);
        FriendService friendLimit = service(
                new MemoryRepository(new FriendSnapshot(List.of(existing), List.of())),
                new FriendLimits(1, 10, 10),
                FriendRequestPolicy.allowAll()
        );
        check(friendLimit.sendRequest(first, second) == FriendResult.FRIEND_LIMIT_REACHED,
                "sender friend limit blocks an impossible request");
        check(friendLimit.sendRequest(second, first) == FriendResult.FRIEND_LIMIT_REACHED,
                "recipient friend limit blocks an impossible request");

        FriendService incomingLimit = service(
                new MemoryRepository(FriendSnapshot.empty()),
                new FriendLimits(10, 1, 10),
                FriendRequestPolicy.allowAll()
        );
        check(incomingLimit.sendRequest(third, second) == FriendResult.SUCCESS,
                "first incoming request fits limit");
        check(incomingLimit.sendRequest(first, second) == FriendResult.INCOMING_LIMIT_REACHED,
                "incoming request limit is enforced");

        FriendService outgoingLimit = service(
                new MemoryRepository(FriendSnapshot.empty()),
                new FriendLimits(10, 10, 1),
                FriendRequestPolicy.allowAll()
        );
        check(outgoingLimit.sendRequest(first, third) == FriendResult.SUCCESS,
                "first outgoing request fits limit");
        check(outgoingLimit.sendRequest(first, second) == FriendResult.OUTGOING_LIMIT_REACHED,
                "outgoing request limit is enforced");
    }

    private static void accept() {
        UUID first = id(1);
        UUID second = id(2);
        UUID third = id(3);
        MemoryRepository repository = new MemoryRepository(FriendSnapshot.empty());
        FriendService service = service(repository, LARGE_LIMITS, FriendRequestPolicy.allowAll());
        check(service.acceptRequest(second, first) == FriendResult.REQUEST_NOT_FOUND,
                "unknown request cannot be accepted");
        check(service.sendRequest(first, second) == FriendResult.SUCCESS, "accept setup sends request");
        check(service.acceptRequest(first, second) == FriendResult.REQUEST_NOT_FOUND,
                "wrong request direction cannot be accepted");
        check(service.acceptRequest(second, first) == FriendResult.SUCCESS, "recipient accepts request");
        check(service.getRelation(first, second) == FriendRelation.FRIENDS,
                "accept creates symmetric friendship");
        check(service.getIncomingRequests(second).isEmpty(), "accept removes request");
        check(repository.saves == 2, "accept performs one additional save");
        check(service.acceptRequest(second, first) == FriendResult.ALREADY_FRIENDS,
                "repeated accept has no duplicate side effect");
        check(repository.saves == 2 && service.countFriends(first) == 1,
                "repeated accept does not save or duplicate friendship");

        FriendRequest pending = new FriendRequest(first, second, Instant.EPOCH);
        Friendship occupying = new Friendship(first, third, Instant.EPOCH);
        FriendService changedLimit = service(
                new MemoryRepository(new FriendSnapshot(List.of(occupying), List.of(pending))),
                new FriendLimits(1, 10, 10),
                FriendRequestPolicy.allowAll()
        );
        check(changedLimit.acceptRequest(second, first) == FriendResult.FRIEND_LIMIT_REACHED,
                "accept rechecks a changed friend limit");
        check(changedLimit.getRelation(second, first) == FriendRelation.INCOMING_REQUEST,
                "limit rejection preserves pending request");

        MutablePolicy policy = new MutablePolicy();
        FriendService changedPolicy = service(
                new MemoryRepository(new FriendSnapshot(List.of(), List.of(pending))),
                LARGE_LIMITS,
                policy
        );
        policy.decision = FriendRequestDecision.BLOCKED;
        check(changedPolicy.acceptRequest(second, first) == FriendResult.BLOCKED,
                "accept rechecks ignore policy");
        check(changedPolicy.getRelation(second, first) == FriendRelation.INCOMING_REQUEST,
                "policy rejection preserves request");
        policy.decision = FriendRequestDecision.REQUESTS_DISABLED;
        check(changedPolicy.acceptRequest(second, first) == FriendResult.SUCCESS,
                "accept remains available after requests are disabled");
    }

    private static void denyCancelRemove() {
        UUID first = id(1);
        UUID second = id(2);

        FriendService deny = service(
                new MemoryRepository(new FriendSnapshot(
                        List.of(),
                        List.of(new FriendRequest(first, second, Instant.EPOCH))
                )),
                LARGE_LIMITS,
                FriendRequestPolicy.allowAll()
        );
        check(deny.denyRequest(first, second) == FriendResult.REQUEST_NOT_FOUND,
                "wrong direction cannot deny request");
        check(deny.denyRequest(second, first) == FriendResult.SUCCESS, "recipient denies request");
        check(deny.getRelation(first, second) == FriendRelation.NONE, "deny only removes request");
        check(deny.denyRequest(second, first) == FriendResult.REQUEST_NOT_FOUND,
                "denied request is no longer present");

        FriendService cancel = service(
                new MemoryRepository(new FriendSnapshot(
                        List.of(),
                        List.of(new FriendRequest(first, second, Instant.EPOCH))
                )),
                LARGE_LIMITS,
                FriendRequestPolicy.allowAll()
        );
        check(cancel.cancelRequest(second, first) == FriendResult.REQUEST_NOT_FOUND,
                "wrong direction cannot cancel request");
        check(cancel.cancelRequest(first, second) == FriendResult.SUCCESS, "sender cancels request");
        check(cancel.getRelation(first, second) == FriendRelation.NONE, "cancel only removes request");
        check(cancel.cancelRequest(first, second) == FriendResult.REQUEST_NOT_FOUND,
                "cancelled request is no longer present");

        FriendService remove = service(
                new MemoryRepository(new FriendSnapshot(
                        List.of(new Friendship(second, first, Instant.EPOCH)),
                        List.of()
                )),
                LARGE_LIMITS,
                FriendRequestPolicy.allowAll()
        );
        check(remove.removeFriend(first, first) == FriendResult.SELF,
                "remove rejects self without throwing");
        check(remove.removeFriend(second, first) == FriendResult.SUCCESS,
                "either side can remove canonical friendship");
        check(remove.getRelation(first, second) == FriendRelation.NONE
                        && remove.countFriends(first) == 0 && remove.countFriends(second) == 0,
                "remove clears the single relationship symmetrically");
        check(remove.removeFriend(first, second) == FriendResult.NOT_FRIENDS,
                "removed friendship cannot be removed twice");
    }

    private static void queries() {
        UUID first = id(1);
        UUID second = id(2);
        UUID third = id(3);
        UUID fourth = id(4);
        FriendSnapshot snapshot = new FriendSnapshot(
                List.of(
                        new Friendship(first, third, Instant.EPOCH),
                        new Friendship(first, second, Instant.EPOCH)
                ),
                List.of(
                        new FriendRequest(fourth, first, Instant.EPOCH),
                        new FriendRequest(third, second, Instant.EPOCH),
                        new FriendRequest(second, fourth, Instant.EPOCH)
                )
        );
        FriendService service = service(
                new MemoryRepository(snapshot),
                LARGE_LIMITS,
                FriendRequestPolicy.allowAll()
        );
        check(service.getFriends(first).equals(List.of(second, third)),
                "friend query is deterministic");
        check(service.getFriends(second).equals(List.of(first)), "friend query is symmetric");
        check(service.countFriends(first) == 2, "friend count uses canonical relationships");
        check(service.getIncomingRequests(first).stream().map(FriendRequest::sender).toList()
                        .equals(List.of(fourth)),
                "incoming query selects sender direction");
        check(service.getOutgoingRequests(second).stream().map(FriendRequest::recipient).toList()
                        .equals(List.of(fourth)),
                "outgoing query selects recipient direction");
        check(service.getRelation(id(9), id(10)) == FriendRelation.NONE,
                "unrelated players have no relation");
        check(service.getRelation(first, first) == FriendRelation.NONE,
                "self relation is none");
        rejectUnsupported(() -> service.getFriends(first).clear(),
                "friend query result is immutable");
        rejectUnsupported(() -> service.getIncomingRequests(first).clear(),
                "incoming query result is immutable");
        rejectUnsupported(() -> service.getOutgoingRequests(second).clear(),
                "outgoing query result is immutable");
    }

    private static void persistenceRollback() {
        UUID first = id(1);
        UUID second = id(2);
        FriendRequest pending = new FriendRequest(first, second, Instant.EPOCH);
        Friendship friendship = new Friendship(first, second, Instant.EPOCH);

        FailingRepository sendRepository = new FailingRepository(FriendSnapshot.empty());
        FriendService send = service(sendRepository, LARGE_LIMITS, FriendRequestPolicy.allowAll());
        sendRepository.failNext = true;
        rejectFailure(() -> send.sendRequest(first, second), "failed send propagates persistence failure");
        check(send.getRelation(first, second) == FriendRelation.NONE,
                "failed send rolls back in-memory state");

        FailingRepository crossRepository = new FailingRepository(
                new FriendSnapshot(List.of(), List.of(pending))
        );
        FriendService cross = service(crossRepository, LARGE_LIMITS, FriendRequestPolicy.allowAll());
        crossRepository.failNext = true;
        rejectFailure(() -> cross.sendRequest(second, first),
                "failed cross request propagates persistence failure");
        check(cross.getRelation(first, second) == FriendRelation.OUTGOING_REQUEST,
                "failed cross request preserves original request");

        FailingRepository acceptRepository = new FailingRepository(
                new FriendSnapshot(List.of(), List.of(pending))
        );
        FriendService accept = service(acceptRepository, LARGE_LIMITS, FriendRequestPolicy.allowAll());
        acceptRepository.failNext = true;
        rejectFailure(() -> accept.acceptRequest(second, first),
                "failed accept propagates persistence failure");
        check(accept.getRelation(first, second) == FriendRelation.OUTGOING_REQUEST,
                "failed accept restores request and no friendship");

        FailingRepository denyRepository = new FailingRepository(
                new FriendSnapshot(List.of(), List.of(pending))
        );
        FriendService deny = service(denyRepository, LARGE_LIMITS, FriendRequestPolicy.allowAll());
        denyRepository.failNext = true;
        rejectFailure(() -> deny.denyRequest(second, first),
                "failed deny propagates persistence failure");
        check(deny.getRelation(first, second) == FriendRelation.OUTGOING_REQUEST,
                "failed deny restores request");

        FailingRepository cancelRepository = new FailingRepository(
                new FriendSnapshot(List.of(), List.of(pending))
        );
        FriendService cancel = service(cancelRepository, LARGE_LIMITS, FriendRequestPolicy.allowAll());
        cancelRepository.failNext = true;
        rejectFailure(() -> cancel.cancelRequest(first, second),
                "failed cancel propagates persistence failure");
        check(cancel.getRelation(first, second) == FriendRelation.OUTGOING_REQUEST,
                "failed cancel restores request");

        FailingRepository removeRepository = new FailingRepository(
                new FriendSnapshot(List.of(friendship), List.of())
        );
        FriendService remove = service(removeRepository, LARGE_LIMITS, FriendRequestPolicy.allowAll());
        removeRepository.failNext = true;
        rejectFailure(() -> remove.removeFriend(first, second),
                "failed remove propagates persistence failure");
        check(remove.getRelation(first, second) == FriendRelation.FRIENDS,
                "failed remove restores friendship");
    }

    private static void reloadableLimitsAndPolicy() {
        UUID first = id(1);
        UUID second = id(2);
        UUID third = id(3);
        AtomicReference<FriendLimits> limits = new AtomicReference<>(new FriendLimits(5, 5, 5));
        MutablePolicy policy = new MutablePolicy();
        MemoryRepository repository = new MemoryRepository(FriendSnapshot.empty());
        FriendService service = new FriendService(repository, limits::get, policy,
                Clock.fixed(NOW, ZoneOffset.UTC));
        check(service.sendRequest(first, second) == FriendResult.SUCCESS,
                "request is created under initial limits");
        policy.decision = FriendRequestDecision.REQUESTS_DISABLED;
        check(service.acceptRequest(second, first) == FriendResult.SUCCESS,
                "privacy change does not prevent acceptance of existing request");
        check(service.getRelation(first, second) == FriendRelation.FRIENDS,
                "accepted friendship survives privacy change");
        limits.set(new FriendLimits(1, 5, 5));
        check(service.getLimits().maxFriends() == 1 && service.countFriends(first) == 1,
                "supplier exposes reloaded limits without deleting existing friendship");
        policy.decision = FriendRequestDecision.ALLOW;
        check(service.sendRequest(first, third) == FriendResult.FRIEND_LIMIT_REACHED,
                "reloaded limit applies to future sends");

        FriendRequest pending = new FriendRequest(third, first, Instant.EPOCH);
        FriendService accept = new FriendService(
                new MemoryRepository(new FriendSnapshot(
                        List.of(new Friendship(first, second, Instant.EPOCH)), List.of(pending))),
                limits::get, FriendRequestPolicy.allowAll(), Clock.fixed(NOW, ZoneOffset.UTC));
        check(accept.acceptRequest(first, third) == FriendResult.FRIEND_LIMIT_REACHED
                        && accept.getRelation(first, third) == FriendRelation.INCOMING_REQUEST,
                "reloaded limit blocks acceptance without deleting pending request");

        MutablePolicy crossPolicy = new MutablePolicy();
        FriendService cross = service(new MemoryRepository(FriendSnapshot.empty()),
                LARGE_LIMITS, crossPolicy);
        check(cross.sendRequest(first, third) == FriendResult.SUCCESS, "cross-request setup succeeds");
        crossPolicy.decision = FriendRequestDecision.BLOCKED;
        check(cross.sendRequest(third, first) == FriendResult.BLOCKED
                        && cross.getRelation(first, third) == FriendRelation.OUTGOING_REQUEST,
                "new ignore blocks cross-accept without deleting original request");
        crossPolicy.decision = FriendRequestDecision.REQUESTS_DISABLED;
        check(cross.sendRequest(third, first) == FriendResult.REQUESTS_DISABLED,
                "new recipient privacy applies to reverse cross-request");
    }

    private static FriendService service(
            FriendRepository repository,
            FriendLimits limits,
            FriendRequestPolicy policy
    ) {
        return new FriendService(repository, limits, policy, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static void rejectFailure(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (FriendRepositoryException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    private static void rejectUnsupported(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static class MemoryRepository implements FriendRepository {

        private FriendSnapshot snapshot;
        private int saves;

        private MemoryRepository(FriendSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public FriendSnapshot initialize() {
            return snapshot;
        }

        @Override
        public void save(FriendSnapshot snapshot) {
            this.snapshot = snapshot;
            saves++;
        }
    }

    private static final class FailingRepository extends MemoryRepository {

        private boolean failNext;

        private FailingRepository(FriendSnapshot snapshot) {
            super(snapshot);
        }

        @Override
        public void save(FriendSnapshot snapshot) {
            if (failNext) {
                failNext = false;
                throw new FriendRepositoryException("simulated save failure");
            }
            super.save(snapshot);
        }
    }

    private static final class MutablePolicy implements FriendRequestPolicy {

        private FriendRequestDecision decision = FriendRequestDecision.ALLOW;

        @Override
        public FriendRequestDecision evaluate(UUID sender, UUID recipient) {
            return decision;
        }
    }
}

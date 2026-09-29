package dev.vapee.core.visibility;

import dev.vapee.core.social.IgnoreResult;

public final class IgnoreVisibilityRefreshHarness {
    private static int checks;

    public static void main(String[] args) {
        VisibilityRelationshipFixture fixture = new VisibilityRelationshipFixture();
        var alice = fixture.player("Alice");
        var bob = fixture.player("Bob");
        fixture.social.addRelationshipListener(fixture.visibility::refreshPair);

        fixture.visibility.refreshPair(alice.id, bob.id);
        check(alice.shown(bob.id) && bob.shown(alice.id),
                "master-visible pair starts visible in both directions");
        alice.clear();
        bob.clear();
        check(fixture.social.ignore(alice.id, bob.id) == IgnoreResult.SUCCESS,
                "ignore persists successfully");
        check(alice.hidden(bob.id) && bob.hidden(alice.id),
                "ignore hard deny refreshes both directions immediately");
        alice.clear();
        bob.clear();
        check(fixture.social.unignore(alice.id, bob.id) == IgnoreResult.SUCCESS,
                "unignore persists successfully");
        check(alice.shown(bob.id) && bob.shown(alice.id),
                "unignore immediately re-evaluates normal policy");

        alice.clear();
        bob.clear();
        fixture.playerRepository.failNext = true;
        boolean failed = false;
        try {
            fixture.social.ignore(alice.id, bob.id);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed && !fixture.social.isIgnoring(alice.id, bob.id),
                "failed ignore persistence rolls back relationship");
        check(alice.actions.isEmpty() && bob.actions.isEmpty(),
                "failed ignore mutation emits no visibility callback");
        System.out.println("IgnoreVisibilityRefreshHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

package dev.vapee.core.identity;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class IdentityCommandArgumentHarness {
    private static int checks;

    public static void main(String[] args) {
        UUID expected = new UUID(0, 1);
        UUID other = new UUID(0, 2);
        for (String name : Arrays.asList(null, "", " ", "\t", "two words", "leading ", " trailing",
                "two\twords", "two\nwords", "two\rwords", "two\u2003words", "two\u2028words", "two\u0000words")) {
            AtomicInteger reads = new AtomicInteger();
            check(IdentityCommandArgument.nameOrUuid(expected, name, value -> {
                reads.incrementAndGet(); return Optional.of(expected);
            }).equals(expected.toString()), "unsafe name uses UUID");
            check(reads.get() == 0, "unsafe name never reaches resolver");
        }
        for (String name : java.util.List.of("Alice", "aLiCe", "_Player_7", "<red>Literal</red>")) {
            AtomicInteger reads = new AtomicInteger();
            check(IdentityCommandArgument.nameOrUuid(expected, name, value -> {
                check(value.equals(name), "resolver receives exact untrimmed name");
                reads.incrementAndGet(); return Optional.of(expected);
            }).equals(name), "unique correct identity preserves spelling");
            check(reads.get() == 1, "one caller-supplied resolution");
            check(IdentityCommandArgument.nameOrUuid(expected, name, value -> Optional.empty())
                    .equals(expected.toString()), "unknown or ambiguous uses UUID");
            check(IdentityCommandArgument.nameOrUuid(expected, name, value -> Optional.of(other))
                    .equals(expected.toString()), "wrong UUID uses fallback");
        }
        RuntimeException failure = new IllegalStateException("lookup failure");
        try {
            IdentityCommandArgument.nameOrUuid(expected, "Alice", value -> { throw failure; });
            throw new AssertionError("lookup failure hidden");
        } catch (RuntimeException actual) {
            check(actual == failure, "feature keeps ownership of resolver failures");
        }
        System.out.println("IdentityCommandArgumentHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

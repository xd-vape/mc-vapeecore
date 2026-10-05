package dev.vapee.core.identity;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Formats one caller-selected identity; never selects candidates or grants authority. */
public final class IdentityCommandArgument {
    private IdentityCommandArgument() { }

    /** The resolver returns empty for unknown or ambiguous names. No quoting grammar is added. */
    public static String nameOrUuid(UUID expectedId, String name,
                                    Function<String, Optional<UUID>> resolveUniqueId) {
        if (name == null || name.isBlank()
                || name.codePoints().anyMatch(point -> Character.isWhitespace(point) || Character.isISOControl(point))) {
            return expectedId.toString();
        }
        return resolveUniqueId.apply(name).filter(expectedId::equals).isPresent()
                ? name : expectedId.toString();
    }
}

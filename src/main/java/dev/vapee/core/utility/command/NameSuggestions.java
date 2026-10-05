package dev.vapee.core.utility.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Filters already eligible names; candidate and permission decisions belong to the caller. */
final class NameSuggestions {
    private NameSuggestions() { }

    static List<String> matching(Stream<String> names, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return names.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}

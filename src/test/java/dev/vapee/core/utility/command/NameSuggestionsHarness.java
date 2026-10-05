package dev.vapee.core.utility.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class NameSuggestionsHarness {
    private static int checks;

    public static void main(String[] args) {
        check(NameSuggestions.matching(Stream.empty(), "").isEmpty(), "empty candidates");
        List<String> names = List.of("Zulu", "alpha", "Beta", "ALPHA", "alpha", "Alpine");
        check(NameSuggestions.matching(names.stream(), "").equals(
                List.of("alpha", "ALPHA", "alpha", "Alpine", "Beta", "Zulu")),
                "empty prefix sorts stably without deduplicating");
        check(NameSuggestions.matching(names.stream(), "aL").equals(
                List.of("alpha", "ALPHA", "alpha", "Alpine")), "mixed-case multiple prefix matches");
        check(NameSuggestions.matching(names.stream(), "BE").equals(List.of("Beta")), "one match");
        check(NameSuggestions.matching(names.stream(), "none").isEmpty(), "no match");
        check(NameSuggestions.matching(names.stream(), "pha").isEmpty(), "prefix, not substring");
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            check(NameSuggestions.matching(Stream.of("Indigo"), "i").equals(List.of("Indigo")),
                    "normalization is independent of machine locale");
        } finally {
            Locale.setDefault(before);
        }
        try {
            NameSuggestions.matching(names.stream(), "").add("new");
            throw new AssertionError("mutable result");
        } catch (UnsupportedOperationException expected) {
            check(true, "result remains immutable");
        }
        System.out.println("NameSuggestionsHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

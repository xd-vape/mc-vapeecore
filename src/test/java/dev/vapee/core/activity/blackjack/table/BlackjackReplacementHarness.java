package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.config.ReplacementFaultProbe;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public final class BlackjackReplacementHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (var mode : ReplacementFaultProbe.Mode.values()) {
            for (boolean unsupported : new boolean[]{false, true}) test(mode, unsupported);
        }
        System.out.println("BlackjackReplacementHarness passed " + checks + " checks.");
    }

    private static void test(ReplacementFaultProbe.Mode mode, boolean unsupported) throws Exception {
        Path root = Path.of("target/harness-temp");
        Files.createDirectories(root);
        Path file = Files.createTempDirectory(root, "blackjack-replacement-").resolve("blackjack.yml");
        String original = "# operator comment\nforeign: keep-on-read\ntables: {}\n";
        Files.writeString(file, original);
        ReplacementFaultProbe probe = new ReplacementFaultProbe(mode, unsupported);
        BlackjackTableConfig config = new BlackjackTableConfig(file, Logger.getAnonymousLogger(), new BlackjackTableConfig.ReplacementIO() {
            @Override void move(Path source, Path target, CopyOption... options) throws IOException { probe.move(source, target, options); }
            @Override void copy(Path source, Path target) throws IOException { probe.copy(source, target); }
        });
        config.initialize();
        check(Files.readString(file).equals(original), "Blackjack load never rewrites operator YAML");
        var stateField = BlackjackTableConfig.class.getDeclaredField("drafts");
        stateField.setAccessible(true);
        Object before = stateField.get(config);
        probe.beforeOperation = () -> check(config.getDraft("new").isEmpty(), "Blackjack candidate remains unpublished during replacement");
        probe.afterCommit = () -> check(config.getDraft("new").isEmpty(), "Blackjack publication follows completed disk commit");
        RuntimeException failure = null;
        try { config.createDraft("new"); }
        catch (RuntimeException exception) { failure = exception; }
        check((failure != null) == (mode == ReplacementFaultProbe.Mode.FAIL), "Blackjack replacement controlled outcome");
        check(probe.waits.stream().distinct().count() == 1, "Blackjack replacement performs zero deliberate waits");
        List<String> expected = mode == ReplacementFaultProbe.Mode.ATOMIC ? List.of("atomic")
                : mode == ReplacementFaultProbe.Mode.REPLACE ? List.of("atomic", "replace") : List.of("atomic", "replace", "copy");
        check(probe.calls.equals(expected), "Blackjack has one attempt per supported replacement stage");
        check(probe.threads.stream().allMatch(id -> id == Thread.currentThread().threadId()), "Blackjack replacement stays on caller thread");
        if (failure != null) {
            check(stateField.get(config) == before && config.getDraft("new").isEmpty(), "failed Blackjack save retains exact draft map");
            check(Files.readString(file).equals(original), "injected failed Blackjack replacement preserves prior destination");
            check(failure.getCause() == probe.faults.getLast()
                    && Arrays.asList(failure.getCause().getSuppressed()).containsAll(probe.faults.subList(0, 2)),
                    "Blackjack failure retains atomic move and copy causes");
            check(probe.commits == 0, "failed Blackjack save performs no successful commit");
        } else {
            check(stateField.get(config) != before && config.getDraft("new").isPresent() && probe.commits == 1,
                    "successful Blackjack save publishes one committed candidate");
            BlackjackTableConfig reload = new BlackjackTableConfig(file, Logger.getAnonymousLogger());
            reload.initialize();
            check(reload.getDraft("new").isPresent() && !reload.getDraft("new").orElseThrow().isEnabled(),
                    "Blackjack replacement preserves draft persistence semantics");
            int calls = probe.calls.size();
            check(!config.createDraft("new") && probe.calls.size() == calls, "duplicate Blackjack draft performs no extra save");
        }
        try (var files = Files.list(file.getParent())) {
            check(files.count() == 1, "Blackjack replacement removes its temporary file without new backup/schema artifacts");
        }
    }

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
}

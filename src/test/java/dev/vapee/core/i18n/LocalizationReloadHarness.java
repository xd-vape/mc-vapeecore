package dev.vapee.core.i18n;

import dev.vapee.core.config.ConfigFiles;
import dev.vapee.core.reload.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

public final class LocalizationReloadHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-language-reload-");
        var logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        var service = LocalizationService.bundled(logger);
        int[] refreshes = {0};
        var module = new LocalizationModule(directory, service, () -> refreshes[0]++);
        try {
            for (Language language : Language.values()) {
                Path file = directory.resolve(language.code() + ".yml");
                check(ConfigFiles.copyDefault(file, () -> LocalizationService.class.getResourceAsStream("/lang/" + language.code() + ".yml"), IllegalStateException::new), "missing file copied");
                byte[] original = Files.readAllBytes(file);
                check(!ConfigFiles.copyDefault(file, () -> { throw new AssertionError("must not read defaults"); }, IllegalStateException::new)
                        && Arrays.equals(original, Files.readAllBytes(file)), "existing operator file never overwritten");
            }
            service.apply(service.prepare(directory));
            var previous = service.snapshot();
            Files.writeString(directory.resolve("en.yml"), "common.close: 'EN override'\n", StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("de.yml"), "common.close: 'DE Änderung'\n", StandardCharsets.UTF_8);
            ReloadPlan plan = module.prepareReload();
            check(service.snapshot() == previous && refreshes[0] == 0, "actual participant preparation never publishes or refreshes");
            plan.apply();
            check(service.lookup(Language.ENGLISH, "common.close").equals("EN override")
                    && service.lookup(Language.GERMAN, "common.close").equals("DE Änderung") && refreshes[0] == 1, "both UTF-8 catalogs applied as one snapshot before refresh");
            plan.rollback();
            check(service.snapshot() == previous && refreshes[0] == 2, "actual rollback restores exact whole snapshot and affected views");
            ReloadParticipant failing = new ReloadParticipant() {
                public String getReloadName() { return "LaterFixture"; }
                public ReloadPlan prepareReload() { return ReloadPlan.of(() -> { throw new IllegalStateException("controlled apply failure"); }, () -> { }); }
            };
            var reload = new ReloadService(logger, List.of(module, failing));
            check(reload.reload().status() == ReloadResult.Status.APPLY_FAILED && service.snapshot() == previous,
                    "later participant failure rolls back both language catalogs via real coordinator");
            check(refreshes[0] == 4, "failed transaction refreshes applied then restored views exactly once");
            Files.writeString(directory.resolve("de.yml"), "common: [broken\n");
            check(reload.reload().status() == ReloadResult.Status.PREPARE_FAILED && service.snapshot() == previous
                    && refreshes[0] == 4, "malformed German candidate cannot partially publish new English override");
            check(Files.readString(directory.resolve("en.yml")).equals("common.close: 'EN override'\n"), "reload never modifies live override bytes");
        } finally {
            Files.deleteIfExists(directory.resolve("en.yml")); Files.deleteIfExists(directory.resolve("de.yml")); Files.deleteIfExists(directory);
        }
        System.out.println("LocalizationReloadHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}

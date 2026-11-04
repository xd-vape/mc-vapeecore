package dev.vapee.core.i18n;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Existing operator catalog contracts through actual module startup and transactional reload. */
public final class LocalizationCompatibilityHarness {
    private static int checks;
    private static final String ONLINE = "friends.presence.online";
    private static final String OFFLINE = "friends.presence.offline";
    private static final String NAME = "Alex<click:run_command:'/op Alex'>";

    public static void main(String[] args) throws Exception {
        for (boolean legacyEnglish : List.of(false, true)) {
            for (boolean legacyGerman : List.of(false, true)) startup(legacyEnglish, legacyGerman);
        }
        strictContracts();
        reload();
        System.out.println("LocalizationCompatibilityHarness passed " + checks + " checks.");
    }

    private static void startup(boolean legacyEnglish, boolean legacyGerman) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-legacy-language-startup-");
        Path directory = Files.createDirectory(root.resolve("lang"));
        write(directory, "en", legacyEnglish);
        write(directory, "de", legacyGerman);
        byte[] english = Files.readAllBytes(directory.resolve("en.yml"));
        byte[] german = Files.readAllBytes(directory.resolve("de.yml"));
        try (var fixture = new ModuleRuntimeFixture()) {
            ModuleRuntimeFixture.set(fixture.plugin, "dataFolder", root.toFile());
            var module = new LocalizationModule(fixture.plugin);
            try {
                module.enable();
                check(fixture.registered.contains(module), "real enable reaches locale listener registration");
                var service = module.service();
                for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
                    boolean legacy = locale.equals(Locale.US) ? legacyEnglish : legacyGerman;
                    for (String key : List.of(ONLINE, OFFLINE)) {
                        Component rendered = service.render(locale, Translations.tr(key, Map.of(
                                "player", Component.text(NAME), "status", MessageStyle.status(Component.text(key.equals(ONLINE) ? "online" : "offline"), key.equals(ONLINE)))));
                        String text = plain(rendered);
                        check(text.contains(NAME) && !text.contains("[missing:") && !text.contains("<status>"), "old/new localized presence renders literal name completely");
                        check(!hasClick(rendered), "catalog compatibility does not parse dynamic player text");
                        check(text.startsWith(locale.equals(Locale.US) ? "Friend " : "Dein Freund "), "live operator wording remains authoritative");
                        check(TranslationCatalog.placeholders(service.lookup(LanguageResolver.resolve(locale), key))
                                .equals(legacy ? Set.of("player") : Set.of("player", "status")), "operator contract retained without rewriting");
                    }
                }
                check(Arrays.equals(english, Files.readAllBytes(directory.resolve("en.yml")))
                                && Arrays.equals(german, Files.readAllBytes(directory.resolve("de.yml"))), "real module enable preserves exact operator bytes");
            } finally {
                module.disable();
            }
        } finally {
            cleanup(root);
        }
    }

    private static void strictContracts() {
        Map<String, String> canonical = Map.of(ONLINE, "<player> <status>", OFFLINE, "<player> <status>", "other", "<player> <status>");
        TranslationCatalog.validateOverrides(canonical, Map.of(ONLINE, "<player> is online", OFFLINE, "<player> went offline"));
        check(true, "both documented player-only presence schemas accepted");
        for (String key : List.of(ONLINE, OFFLINE)) {
            for (String invalid : List.of("no player", "<status>", "<friend>", "<player> <state>", "<player> <status> <extra>")) {
                fails(() -> TranslationCatalog.validateOverrides(canonical, Map.of(key, invalid)), "missing player, renamed or extra placeholders still fail");
            }
            fails(() -> TranslationCatalog.validateParity(canonical, Map.of(key, "<player>"), false), "strict parity is never relaxed for bundled/invariant checks");
            fails(() -> TranslationCatalog.validateOverrides(Map.of(key, "<player> <status> <world>"), Map.of(key, "<player>")), "future canonical schema is not silently accepted");
        }
        fails(() -> TranslationCatalog.validateOverrides(canonical, Map.of("other", "<player>")), "unrelated override remains strict");
        fails(() -> TranslationCatalog.validateOverrides(Map.of(ONLINE + ".extra", "<player> <status>"), Map.of(ONLINE + ".extra", "<player>")), "compatibility matches exact keys only");
        fails(() -> TranslationCatalog.validateParity(Map.of(ONLINE, "<player> <status>"), Map.of(ONLINE, "<player>"), true), "bundled EN/DE parity remains strict");
    }

    private static void reload() throws Exception {
        Path root = Files.createTempDirectory("vapeecore-legacy-language-reload-");
        Path directory = Files.createDirectory(root.resolve("lang"));
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var service = LocalizationService.bundled(logger);
        int[] refreshes = {0};
        var module = new LocalizationModule(directory, service, () -> refreshes[0]++);
        try {
            write(directory, "en", true);
            write(directory, "de", true);
            service.apply(service.prepare(directory));
            var previous = service.snapshot();
            write(directory, "en", false);
            var plan = module.prepareReload();
            check(service.snapshot() == previous && refreshes[0] == 0, "mixed-version reload preparation has no side effects");
            plan.apply();
            check(service.snapshot() != previous && refreshes[0] == 1, "mixed-version operator catalogs apply together");
            plan.rollback();
            check(service.snapshot() == previous && refreshes[0] == 2, "rollback restores exact legacy snapshot");
            ReloadParticipant laterFailure = new ReloadParticipant() {
                public String getReloadName() { return "LaterFixture"; }
                public ReloadPlan prepareReload() { return ReloadPlan.of(() -> { throw new IllegalStateException("controlled failure"); }, () -> {}); }
            };
            var result = new ReloadService(logger, List.of(module, laterFailure)).reload();
            check(result.status() == ReloadResult.Status.APPLY_FAILED && service.snapshot() == previous, "actual coordinator rolls back upgraded/legacy mixture");
            Files.writeString(directory.resolve("de.yml"), OFFLINE + ": '<player> <wrong>'\n");
            result = new ReloadService(logger, List.of(module)).reload();
            check(result.status() == ReloadResult.Status.PREPARE_FAILED && service.snapshot() == previous, "invalid German override cannot partially publish valid English");
            check(Files.readString(directory.resolve("de.yml")).equals(OFFLINE + ": '<player> <wrong>'\n"), "failed reload never repairs or replaces operator files");
            write(directory, "de", true);
            result = new ReloadService(logger, List.of(module)).reload();
            check(result.status() == ReloadResult.Status.SUCCESS, "valid mixed catalogs reload after prior rejected input");
            var messages = new MessageService(() -> "", service);
            check(plain(messages.localization().render(Locale.GERMANY, Translations.tr(OFFLINE, Map.of("player", Component.text("Alex"), "status", Component.text("offline")))))
                    .equals("Dein Freund Alex ist offline gegangen."), "legacy German wording renders after real successful reload");
        } finally {
            cleanup(root);
        }
    }

    private static void write(Path directory, String language, boolean legacy) throws Exception {
        String online = language.equals("en") ? "Friend <player> is now " : "Dein Freund <player> ist jetzt ";
        String offline = language.equals("en") ? "Friend <player> went " : "Dein Freund <player> ist ";
        Files.writeString(directory.resolve(language + ".yml"), ONLINE + ": '" + online + (legacy ? "online" : "<status>") + ".'\n"
                + OFFLINE + ": '" + offline + (legacy ? "offline" : "<status>") + (language.equals("en") ? "." : " gegangen.") + "'\n");
    }
    private static void cleanup(Path root) throws Exception {
        Files.deleteIfExists(root.resolve("lang/en.yml"));
        Files.deleteIfExists(root.resolve("lang/de.yml"));
        Files.deleteIfExists(root.resolve("lang"));
        Files.deleteIfExists(root);
    }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    private static boolean hasClick(Component component) { return component.clickEvent() != null || component.children().stream().anyMatch(LocalizationCompatibilityHarness::hasClick); }
    private static void fails(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("expected") && expected.getMessage().contains("actual"), "diagnostic reports compared contracts");
        }
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}

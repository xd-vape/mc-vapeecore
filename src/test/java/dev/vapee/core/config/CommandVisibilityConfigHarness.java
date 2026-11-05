package dev.vapee.core.config;

import dev.vapee.core.permission.CommandVisibilityConfig;
import dev.vapee.core.reload.*;

import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

/** Real ConfigService prepare/apply/rollback and optional-schema behavior; no operator files. */
public final class CommandVisibilityConfigHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vc-command-visibility-config-");
        Path file = root.resolve("config.yml");
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        int[] updates = {0};
        var service = new ConfigService(file, logger, () -> updates[0]++);
        try {
            String initial = "config-version: 2\n# operator bytes\nmessages:\n  prefix: ''\n";
            Files.writeString(file, initial);
            service.load();
            check(
                    service.getCommandVisibilityConfig().equals(CommandVisibilityConfig.defaults()),
                    "missing optional keys have backward-compatible defaults");
            check(
                    updates[0] == 0 && Files.readString(file).equals(initial),
                    "unchanged defaults do not sync or rewrite installed config");
            Files.writeString(
                    file,
                    "commands:\n  visibility:\n    enabled: false\n    hide-profile: false\n");
            var plan = service.prepareReload();
            check(
                    service.getCommandVisibilityConfig().enabled() && updates[0] == 0,
                    "prepare neither publishes policy nor updates players");
            plan.apply();
            check(
                    !service.getCommandVisibilityConfig().enabled()
                            && !service.getCommandVisibilityConfig().hideProfile()
                            && updates[0] == 1,
                    "successful apply publishes policy and syncs exactly once");
            service.prepareReload().apply();
            check(updates[0] == 1, "equal policy causes no redundant sync");
            plan.rollback();
            check(
                    service.getCommandVisibilityConfig().equals(CommandVisibilityConfig.defaults())
                            && updates[0] == 2,
                    "rollback restores policy and resynchronizes once");
            var later =
                    new ReloadParticipant() {
                        public String getReloadName() {
                            return "controlled";
                        }

                        public ReloadPlan prepareReload() {
                            return ReloadPlan.of(
                                    () -> {
                                        throw new IllegalStateException("controlled");
                                    },
                                    () -> {});
                        }
                    };
            var result = new ReloadService(logger, List.of(service, later)).reload();
            check(
                    result.status() == ReloadResult.Status.APPLY_FAILED
                            && service.getCommandVisibilityConfig().enabled()
                            && updates[0] == 4,
                    "real coordinator rollback restores policy after a later failure");
            for (String invalid :
                    List.of(
                            "commands: 3\n",
                            "commands: null\n",
                            "commands:\n  visibility: []\n",
                            "commands:\n  visibility: null\n",
                            "commands:\n  visibility:\n    enabled: bad\n",
                            "commands:\n  visibility:\n    enabled: null\n")) {
                Files.writeString(file, invalid);
                service.prepareReload().apply();
                check(
                        !service.getCommandVisibilityConfig().enabled(),
                        "invalid filter option keeps commands visible: " + invalid);
                check(
                        Files.readString(file).equals(invalid),
                        "invalid optional config is never rewritten");
            }
            for (String value : List.of("bad", "null", "[]", "4")) {
                Files.writeString(
                        file,
                        "commands:\n  visibility:\n    enabled: true\n    hide-profile: "
                                + value
                                + "\n");
                service.prepareReload().apply();
                check(
                        service.getCommandVisibilityConfig().enabled()
                                && !service.getCommandVisibilityConfig().hideProfile(),
                        "invalid cosmetic profile flag preserves the command");
            }
            Files.writeString(file, "commands: [invalid\n");
            var previous = service.getCommandVisibilityConfig();
            int before = updates[0];
            boolean rejected = false;
            try {
                service.prepareReload();
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            check(
                    rejected
                            && service.getCommandVisibilityConfig().equals(previous)
                            && updates[0] == before,
                    "malformed YAML fails prepare without state or sync effects");
            Files.writeString(file, "config-version: 2\n");
            var evolution =
                    new ConfigEvolution(
                            root,
                            logger,
                            name ->
                                    CommandVisibilityConfigHarness.class.getResourceAsStream(
                                            "/" + name));
            check(
                    !evolution.evolve("config.yml")
                            && Files.readString(file).equals("config-version: 2\n"),
                    "runtime-only options do not add an operator migration");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
        System.out.println("CommandVisibilityConfigHarness passed " + checks + " checks.");
    }

    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }
}

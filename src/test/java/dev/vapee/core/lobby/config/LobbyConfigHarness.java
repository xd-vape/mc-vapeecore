package dev.vapee.core.lobby.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LobbyConfigHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-lobby-config-");
        Path file = root.resolve("lobby.yml");
        List<String> warnings = new ArrayList<>();
        try {
            LobbyConfig config = new LobbyConfig(file, logger(warnings));
            config.initialize();
            check(Files.readString(file).equals("player:\n  gamemode: ADVENTURE\n"),
                    "existing injectable lobby default supplier preserved");
            check(warnings.isEmpty() && config.isDamageProtectionEnabled(), "missing lobby booleans silently true");
            var before = config.getState();
            for (String value : List.of("null", "true", "false", "42", "'true'", "[]", "{}")) {
                String source = "protection:\n  damage: " + value + "\n";
                Files.writeString(file, source); warnings.clear();
                var next = config.prepareReloadState();
                boolean invalid = !List.of("null", "true", "false").contains(value);
                check(next.damageProtection() == !value.equals("false"), "lobby boolean fallback");
                check(warnings.size() == (invalid ? 1 : 0), "lobby missing/null/invalid warning parity");
                if (invalid) check(warnings.getFirst().equals("Invalid lobby setting 'protection.damage' in " + file
                        + ": expected a boolean; using 'true'. The file was left unchanged."), "exact lobby warning");
                check(config.getState() == before && Files.readString(file).equals(source),
                        "lobby prepare unpublished/no rewrite");
                config.initialize(); check(Files.readString(file).equals(source), "lobby initialize no rewrite");
                config.applyState(before);
            }
            for (String source : List.of("spawn: []\n", "spawn:\n  world: ''\n",
                    "spawn:\n  world: world\n  x: bad\n")) {
                Files.writeString(file, source); warnings.clear();
                config.initialize();
                var tolerant = config.getState();
                check(tolerant.spawn().isEmpty() && warnings.size() == 1, "startup spawn remains tolerant");
                try { config.prepareReloadState(); throw new AssertionError("invalid strict spawn accepted"); }
                catch (IllegalArgumentException expected) {
                    check(config.getState() == tolerant && Files.readString(file).equals(source),
                            "reload spawn remains fatal with no publication/write");
                }
            }
            Files.writeString(file, "messages:\n  join: false\n  quit:\n    format: '<red>broken'\n");
            warnings.clear();
            var messages = config.prepareReloadState();
            check(messages.joinMessage().format().equals(LobbyConfig.DEFAULT_JOIN_MESSAGE_FORMAT)
                    && messages.quitMessage().format().equals(LobbyConfig.DEFAULT_QUIT_MESSAGE_FORMAT)
                    && warnings.size() == 2, "section validation and strict MiniMessage stay local");
            Files.writeString(file, "messages:\n  join:\n    format: ''\n");
            check(config.prepareReloadState().joinMessage().format().isEmpty(), "lobby blank valid template retained");
            Files.writeString(file, "spawn:\n  world: world\n  x: 1\n  y: 64\n  z: 3\n  yaw: 0\n  pitch: 0\n");
            var valid = config.prepareReloadState();
            check(valid.spawn().isPresent(), "strict valid spawn accepted");
            config.applyState(valid); check(config.getState() == valid, "typed lobby apply");
            config.applyState(before); check(config.getState() == before, "exact previous lobby state restored");
        } finally {
            Files.deleteIfExists(file); Files.deleteIfExists(root);
        }
        System.out.println("LobbyConfigHarness passed " + checks + " checks.");
    }

    private static Logger logger(List<String> warnings) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record.getMessage());
            }
            public void flush() { }
            public void close() { }
        });
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

package dev.vapee.core.i18n;

import dev.vapee.core.lobby.config.*;
import dev.vapee.core.lobby.message.LobbyMessageService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.quest.daily.DailyQuestConfig;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public final class LocalizationSurfacesHarness {
    private static int checks;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    public static void main(String[] args) throws Exception {
        dev.vapee.core.lobby.item.LobbyItemsConfigHarness.installRegistries();
        var logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        var localization = LocalizationService.bundled(logger);
        var messages = new MessageService(() -> "", localization);
        var german = new Viewer(Locale.GERMANY, "German", false);
        var english = new Viewer(Locale.US, "English", false);
        var console = new Viewer(null, "Console", true);
        for (String key : List.of("core.development", "settings.denied", "friends.title", "clan.members-title",
                "social.empty", "warp.empty", "spawn.saved", "utility.build.denied", "profile.title",
                "daily-quests.command-player-only", "moderation.kick.screen")) {
            Component template = Translations.tr(key);
            messages.send(german.audience, template); messages.send(english.audience, template); messages.send(console.audience, template);
            check(german.last().equals(PLAIN.serialize(localization.render(Locale.GERMANY, template))), "German surface " + key);
            check(english.last().equals(PLAIN.serialize(localization.render(Locale.US, template))) && console.last().equals(english.last()), "English player and actual console surface " + key);
            check(!german.last().contains("[missing:") && !english.last().contains("[missing:"), "built-in key exists " + key);
        }
        Path root = Files.createTempDirectory("vapeecore-localized-surfaces-");
        try {
            Path file = root.resolve("lobby.yml");
            String lobby = """
                    messages:
                      join:
                        enabled: true
                        format:
                          en: '<green><name> joined.</green>'
                          de: '<green><name> ist beigetreten.</green>'
                      quit:
                        enabled: true
                        format:
                          en: '<gray><name> left.</gray>'
                          de: '<gray><name> ist gegangen.</gray>'
                    items:
                      friends:
                        enabled: true
                        slot: 6
                        material: PLAYER_HEAD
                        head-owner: self
                        name:
                          en: '<aqua>Friends custom'
                          de: '<aqua>Eigene Freunde'
                        lore:
                          en: ['English lore', '']
                          de: ['Deutsche Beschreibung', '']
                    """;
            Files.writeString(file, lobby);
            var config = new LobbyConfig(file, logger); config.initialize();
            var broadcasts = new LobbyMessageService(config, messages::deserialize, logger, localization, messages::sendRaw);
            check(broadcasts.perRecipient(true) && broadcasts.perRecipient(false), "localized lobby wrappers use recipient broadcast path");
            String attack = "<red>Literal <click:run_command:'/op me'>Name";
            var subject = new Viewer(Locale.GERMANY, attack, false);
            broadcasts.broadcast((Player) subject.audience, true, List.of(german.audience, english.audience), console.audience);
            check(german.last().equals(attack + " ist beigetreten."), "German broadcast recipient language, safe subject name");
            check(english.last().equals(attack + " joined.") && console.last().equals(english.last()), "simultaneous English recipient and console never use subject locale");
            check(!hasClick(german.received.getLast()) && !hasClick(english.received.getLast()), "broadcast identity cannot inject click action");
            broadcasts.broadcast((Player) subject.audience, false, List.of(german.audience, english.audience), console.audience);
            check(german.last().equals(attack + " ist gegangen.") && english.last().equals(attack + " left."), "quit wrappers independently localized");
            var friends = config.getItems().get("friends");
            check(friends.slot() == 6 && friends.appearance().selfHead(), "localized presentation leaves hotbar slot and self skin behavior intact");
            check(PLAIN.serialize(friends.appearance().name(Locale.GERMANY)).equals("Eigene Freunde")
                    && PLAIN.serialize(friends.appearance().name(Locale.US)).equals("Friends custom"), "real lobby parser localized names");
            check(PLAIN.serialize(friends.appearance().lore(Locale.GERMANY).getFirst()).equals("Deutsche Beschreibung")
                    && friends.appearance().lore(Locale.GERMANY).getLast().equals(Component.empty().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)), "real lobby parser localized lore preserves blank lines");
            check(PLAIN.serialize(friends.appearance().name(Locale.JAPAN)).equals("Friends custom"), "unsupported locale uses operator English fallback");
            check(Files.readString(file).equals(lobby), "localized config read never rewrites operator bytes");
            Files.writeString(file, "messages:\n  join:\n    enabled: true\n    format: '<white>Universal <name></white>'\n");
            config.applyState(config.prepareReloadState());
            check(!broadcasts.perRecipient(true) && PLAIN.serialize(broadcasts.renderJoinMessage((Player) subject.audience)).equals("Universal " + attack), "legacy scalar uses original universal event path");
            Path quests = root.resolve("daily-quests.yml");
            String questConfig = """
                    enabled: true
                    quests-per-day: 1
                    quests:
                      same-id:
                        name: {en: 'English quest', de: 'Deutsche Quest'}
                        description: {en: '<red>Literal English', de: '<red>Wörtlich Deutsch'}
                        progress-key: 'playtime:minute'
                        target: 30
                        reward-coins: 250
                    """;
            Files.writeString(quests, questConfig);
            var daily = new DailyQuestConfig(quests, logger); daily.initialize();
            var definition = daily.getState().definitions().getFirst();
            check(definition.id().equals("same-id") && definition.nameText().resolve(Locale.GERMANY).equals("Deutsche Quest")
                    && definition.name().equals("English quest"), "real quest loader localized text with stable definition ID");
            check(definition.descriptionText().resolve(Locale.GERMANY).equals("<red>Wörtlich Deutsch")
                    && Files.readString(quests).equals(questConfig), "quest description remains plain operator text and file unchanged");
        } finally {
            Files.deleteIfExists(root.resolve("lobby.yml")); Files.deleteIfExists(root.resolve("daily-quests.yml")); Files.deleteIfExists(root);
        }
        System.out.println("LocalizationSurfacesHarness passed " + checks + " checks.");
    }
    private static boolean hasClick(Component c) { return c.clickEvent() != null || c.children().stream().anyMatch(LocalizationSurfacesHarness::hasClick); }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
    private static final class Viewer {
        final Locale locale; final String name; final UUID id = UUID.randomUUID();
        final List<Component> received = new ArrayList<>(); final Audience audience;
        Viewer(Locale locale, String name, boolean console) {
            this.locale = locale; this.name = name;
            Class<?> type = console ? ConsoleCommandSender.class : Player.class;
            audience = (Audience) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
                case "locale" -> this.locale; case "getName" -> this.name; case "getUniqueId" -> id;
                case "sendMessage" -> { for (Object argument : args) if (argument instanceof Component c) received.add(c); yield null; }
                case "hashCode" -> id.hashCode(); case "equals" -> proxy == args[0]; case "toString" -> name;
                default -> null;
            });
        }
        String last() { return PLAIN.serialize(received.getLast()); }
    }
}

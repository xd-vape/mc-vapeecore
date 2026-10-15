package dev.vapee.core.message;

import static dev.vapee.core.i18n.Translations.tr;
import static dev.vapee.core.quest.QuestCompletionFixture.*;
import dev.vapee.core.friend.*;
import dev.vapee.core.i18n.*;
import dev.vapee.core.presentation.PresentationFixture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

public final class MessageStyleHarness {
    static int checks;
    public static void main(String[] args) throws Exception {
        try (var f = new PresentationFixture()) {
            check(f.config.getMessagePrefix().isEmpty(), "actual default config prefix is empty");
            var localization = LocalizationService.bundled(Logger.getAnonymousLogger());
            var messages = new MessageService(f.config, localization);
            var output = new ArrayList<Component>(); var locale = new Locale[]{Locale.US}; UUID owner = UUID.randomUUID();
            Player player = proxy(Player.class, (name, values) -> switch(name) {
                case "getUniqueId" -> owner; case "locale" -> locale[0]; case "getName" -> "Owner";
                case "sendMessage" -> { for (var value : values) if (value instanceof Component component) output.add(component); yield null; }
                default -> DEFAULT;
            });
            var friends = new FriendMessages(messages, ignored -> null);
            for (var language : List.of(Locale.US, Locale.GERMANY)) {
                locale[0] = language;
                friends.report(player, "add", UUID.randomUUID(), "<red>Steve", FriendResult.SUCCESS);
                var result = output.getLast(); var plain = plain(result);
                check(!plain.contains("VapeeCore") && plain.contains("<red>Steve"), "normal success has no plugin prefix and preserves literal target " + language);
                check(hasColor(result, "<red>Steve", MessageStyle.HIGHLIGHT), "important literal target uses accent");
                check(hasColor(result, language.equals(Locale.US) ? "Friend request" : "Freundschaftsanfrage", MessageStyle.BODY), "success body is restrained neutral");
                messages.send(player, tr("common.command-denied").color(MessageStyle.ERROR));
                check(hasColor(output.getLast(), "", MessageStyle.ERROR), "error remains visibly distinct");
                messages.send(player, tr("common.unavailable").color(MessageStyle.WARNING));
                check(hasColor(output.getLast(), "", MessageStyle.WARNING), "warning semantic color retained");
                messages.send(player, MessageStyle.activity(tr("activity.blackjack"), tr("blackjack.result.WIN")));
                check(plain(output.getLast()).startsWith("Blackjack › ") && !plain(output.getLast()).contains("VapeeCore"), "activity has useful context without default plugin prefix");
                String body = "literal <red>hello</red>";
                messages.send(player, MessageStyle.activity(tr("activity.blackjack"), Component.text(body)));
                check(plain(output.getLast()).endsWith(body), "context keeps dynamic activity body literal");
            }
            Files.writeString(f.directory.resolve("config.yml"), "messages:\n  prefix: '<gold>[Custom]</gold> '\n");
            var change = f.config.prepareReload(); change.apply();
            messages.send(player, tr("common.unavailable"));
            check(plain(output.getLast()).startsWith("[Custom] "), "explicit operator prefix still works after reload");
            check(hasColor(output.getLast(), "[Custom]", NamedTextColor.GOLD), "operator prefix formatting preserved");
            change.rollback(); messages.send(player, tr("common.unavailable"));
            check(!plain(output.getLast()).startsWith("[Custom]"), "reload rollback restores empty prefix");
        }
        System.out.println("MessageStyleHarness passed " + checks + " checks.");
    }
    static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    static boolean hasColor(Component c, String text, NamedTextColor color) {
        return color.equals(c.color()) && plain(c).contains(text) || c.children().stream().anyMatch(child -> hasColor(child,text,color));
    }
    static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
}

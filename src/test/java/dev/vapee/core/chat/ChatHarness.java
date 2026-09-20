package dev.vapee.core.chat;

import dev.vapee.core.rank.RankInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Optional;
import java.util.OptionalInt;

public final class ChatHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private ChatHarness() {
    }

    public static void main(String[] args) {
        RankInfo rank = new RankInfo(
                "vip",
                "VIP Supporter",
                Optional.empty(),
                OptionalInt.empty()
        );
        ChatService.RankValues values = ChatService.resolveRankValues(
                Optional.of(rank),
                Optional.of("stale-group")
        );
        Component playerMessage = Component.text("<red>literal player text</red>");
        Component rendered = MiniMessage.miniMessage().deserialize(
                "<rank>|<rank_id>|<group>|<message>",
                ChatService.createPlaceholders(
                        Component.empty(),
                        Component.text("Player"),
                        Component.empty(),
                        values,
                        playerMessage
                )
        );
        String text = PLAIN.serialize(rendered);
        check(text.startsWith("VIP Supporter|vip|vip|"),
                "chat resolves <rank>, <rank_id>, and compatibility <group>");
        check(text.endsWith("<red>literal player text</red>"),
                "player chat remains an Adventure component and is never parsed as MiniMessage");

        ChatService.RankValues fallback = ChatService.resolveRankValues(
                Optional.empty(),
                Optional.of("default")
        );
        check(fallback.displayName().equals("default") && fallback.id().equals("default"),
                "chat rank placeholders use the raw primary-group fallback");

        System.out.println("ChatHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

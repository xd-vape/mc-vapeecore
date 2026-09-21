package dev.vapee.core.chat;

import dev.vapee.core.rank.RankInfo;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
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
                Optional.of(NamedTextColor.GOLD),
                OptionalInt.empty()
        );
        Component playerMessage = Component.text("<red>literal player text</red>");
        Component rendered = MiniMessage.miniMessage().deserialize(
                "<rank>|<rank_name>|<name>|<rank_id>|<group>|<message>",
                ChatService.createPlaceholders(
                        Component.empty(),
                        Component.text("Player"),
                        Component.empty(),
                        Optional.of(rank),
                        playerMessage
                )
        );
        String text = PLAIN.serialize(rendered);
        check(text.startsWith("VIP Supporter|Player|Player|vip|vip|"),
                "chat resolves rank, rank_name, unchanged name, and raw rank IDs");
        check(hasColoredText(rendered, "VIP Supporter", NamedTextColor.GOLD),
                "chat <rank> uses the friendly rank display name and color");
        check(hasColoredText(rendered, "Player", NamedTextColor.GOLD),
                "chat <rank_name> applies the rank color to the player display name");
        check(text.endsWith("<red>literal player text</red>"),
                "player chat remains an Adventure component and is never parsed as MiniMessage");

        RankInfo missingColor = new RankInfo(
                "event_host",
                "Event Host",
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty()
        );
        Component neutral = MiniMessage.miniMessage().deserialize(
                "<rank>|<rank_name>",
                ChatService.createPlaceholders(
                        Component.empty(),
                        Component.text("Host"),
                        Component.empty(),
                        Optional.of(missingColor),
                        Component.empty()
                )
        );
        check(hasColoredText(neutral, "Event Host", NamedTextColor.WHITE)
                        && hasColoredText(neutral, "Host", NamedTextColor.WHITE),
                "missing chat rank color uses the neutral white fallback");

        MiniMessage.builder().strict(true).build().deserialize(
                "<rank_name><rank><rank_id><group><message>",
                ChatService.emptyPlaceholders()
        );
        check(true, "strict chat validation recognizes rank_name");

        Player source = player();
        List<ChatRenderer> renderers = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        for (String message : List.of("first", "second", "third")) {
            ChatRenderer renderer = ChatListener.createRenderer((player, displayName, component) -> component);
            renderers.add(renderer);
            outputs.add(PLAIN.serialize(renderer.render(
                    source,
                    Component.text("Player"),
                    Component.text(message),
                    Audience.empty()
            )));
        }
        check(outputs.equals(List.of("first", "second", "third")),
                "per-event viewer-unaware renderers never repeat the first message");
        check(renderers.get(0) != renderers.get(1) && renderers.get(1) != renderers.get(2),
                "each chat event receives a distinct viewer-unaware renderer");
        check(java.util.Arrays.stream(ChatListener.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == ChatRenderer.class),
                "ChatListener cannot cache a ChatRenderer field across events");

        System.out.println("ChatHarness passed " + checks + " checks.");
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == arguments[0];
                    if (method.getName().equals("toString")) return "Player";
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private static boolean hasColoredText(Component component, String text, TextColor color) {
        if (color.equals(component.color()) && PLAIN.serialize(component).contains(text)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> hasColoredText(child, text, color));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

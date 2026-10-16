package dev.vapee.core.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import java.util.ArrayList;
import java.util.List;

/** Checks the actual text spans, including inherited colors, rather than an ancestor's subtree color. */
public final class MessageColorAssertions {
    private MessageColorAssertions() { }
    public static boolean hasTextColor(Component message, String expectedText, TextColor expectedColor) {
        var text = new StringBuilder(); var colors = new ArrayList<TextColor>();
        collect(message, null, text, colors);
        int start = text.indexOf(expectedText);
        if (start < 0 || expectedText.isEmpty()) return false;
        while (start >= 0) {
            for (int i = start; i < start + expectedText.length(); i++)
                if (!expectedColor.equals(colors.get(i))) return false;
            start = text.indexOf(expectedText, start + expectedText.length());
        }
        return true;
    }
    private static void collect(Component component, TextColor inherited, StringBuilder text, List<TextColor> colors) {
        TextColor current = component.color() == null ? inherited : component.color();
        if (component instanceof TextComponent literal) {
            text.append(literal.content());
            for (int i = 0; i < literal.content().length(); i++) colors.add(current);
        }
        for (Component child : component.children()) collect(child, current, text, colors);
    }
}

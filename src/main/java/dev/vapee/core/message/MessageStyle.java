package dev.vapee.core.message;

import dev.vapee.core.i18n.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Small semantic palette for core chat feedback; it never changes operator templates or user text. */
public final class MessageStyle {
    public static final NamedTextColor BRACKET = NamedTextColor.DARK_GRAY;
    /** Compatibility name; this role now applies to prefix brackets, not feature labels. */
    public static final NamedTextColor PREFIX = BRACKET;
    public static final NamedTextColor DEFAULT_LABEL = NamedTextColor.GRAY;
    public static final NamedTextColor FRIENDS_LABEL = NamedTextColor.GOLD;
    public static final NamedTextColor CLAN_LABEL = NamedTextColor.DARK_PURPLE;
    public static final NamedTextColor STAFF_LABEL = NamedTextColor.DARK_GRAY;
    private static final String FORMATTED_PREFIX = "vapeecore.format.prefix";
    public static final NamedTextColor TEXT = NamedTextColor.GRAY;
    public static final NamedTextColor MUTED = NamedTextColor.DARK_GRAY;
    /** Compatibility name for the existing neutral body palette. */
    public static final NamedTextColor BODY = TEXT;
    public static final TextColor DEFAULT_HIGHLIGHT = TextColor.color(0xE5BF75);
    /** Server-wide palette owned by ConfigService's applied snapshot; read when constructing new output. */
    public static volatile TextColor HIGHLIGHT = DEFAULT_HIGHLIGHT;
    public static final NamedTextColor SUCCESS = NamedTextColor.GREEN;
    public static final NamedTextColor WARNING = NamedTextColor.YELLOW;
    public static final NamedTextColor ERROR = NamedTextColor.RED;
    public static final NamedTextColor ONLINE = NamedTextColor.GREEN;
    public static final NamedTextColor OFFLINE = NamedTextColor.GRAY;
    private static final TagResolver SEMANTIC_TAGS = TagResolver.resolver(
            accentTag(),
            TagResolver.resolver("vc_prefix", Tag.styling(PREFIX)),
            TagResolver.resolver("vc_text", Tag.styling(TEXT)),
            TagResolver.resolver("vc_muted", Tag.styling(MUTED)),
            TagResolver.resolver("vc_success", Tag.styling(SUCCESS)),
            TagResolver.resolver("vc_error", Tag.styling(ERROR)),
            TagResolver.resolver("vc_warning", Tag.styling(WARNING)),
            TagResolver.resolver("vc_online", Tag.styling(ONLINE)),
            TagResolver.resolver("vc_offline", Tag.styling(OFFLINE)));
    private MessageStyle() { }

    public static void applyAccentColor(TextColor color) { HIGHLIGHT = java.util.Objects.requireNonNull(color); }

    public static TextColor accent() { return HIGHLIGHT; }
    public static TagResolver semanticTags() { return SEMANTIC_TAGS; }
    public static Component text(Component body) { return Objects.requireNonNull(body).colorIfAbsent(TEXT); }
    /** These helpers color status labels, not whole explanations or rank-colored player names. */
    public static Component success(Component label) { return label.color(SUCCESS); }
    public static Component error(Component label) { return label.color(ERROR); }
    /** Presentation only; the caller owns the permission decision and delivery wrapper. */
    public static Component commandDenied() { return dev.vapee.core.i18n.Translations.tr("common.command-denied").color(ERROR); }
    public static Component warning(Component label) { return label.color(WARNING); }
    public static Component status(Component label, boolean online) { return label.color(online ? ONLINE : OFFLINE); }

    /** A new feature supplies a bare localized label; actions are separate Components. */
    public static Component prefix(Component label) {
        return prefix(label, DEFAULT_LABEL);
    }
    public static Component prefix(Component label, TextColor labelColor) {
        return Component.empty().append(Component.text("[", BRACKET))
                .append(Objects.requireNonNull(label).color(Objects.requireNonNull(labelColor)))
                .append(Component.text("]", BRACKET));
    }
    public static Component feature(Component label, Component body) { return feature(label, DEFAULT_LABEL, body); }
    public static Component feature(Component label, TextColor labelColor, Component body) {
        return compose(prefix(label, labelColor), body);
    }
    /** Existing localized channel labels already include their brackets. */
    public static Component message(Component formattedPrefix, Component body) {
        return message(formattedPrefix, DEFAULT_LABEL, body);
    }
    public static Component message(Component formattedPrefix, TextColor labelColor, Component body) {
        Objects.requireNonNull(formattedPrefix);
        Objects.requireNonNull(labelColor);
        Component styled = containsTranslation(formattedPrefix)
                ? Component.translatable(FORMATTED_PREFIX, formattedPrefix).color(labelColor)
                : formatExistingPrefix(formattedPrefix, labelColor);
        return compose(styled, body);
    }
    private static Component compose(Component prefix, Component body) {
        return Component.empty().color(TEXT).append(prefix).append(Component.space()).append(text(body));
    }
    private static boolean containsTranslation(Component component) {
        return component instanceof TranslatableComponent || component.children().stream().anyMatch(MessageStyle::containsTranslation);
    }
    /** Internal localized-prefix adapter; feature identity/color is supplied before translation. */
    public static boolean isFormattedPrefix(Component component) {
        return component instanceof TranslatableComponent translated && translated.key().equals(FORMATTED_PREFIX);
    }
    public static Component renderFormattedPrefix(TranslatableComponent template, UnaryOperator<Component> localize) {
        if (!isFormattedPrefix(template) || template.arguments().size() != 1
                || !(template.arguments().getFirst().value() instanceof Component value))
            throw new IllegalArgumentException("Invalid formatted prefix template");
        Component result = formatExistingPrefix(localize.apply(value), template.color() == null ? DEFAULT_LABEL : template.color())
                .applyFallbackStyle(template.style().color(null));
        for (Component child : template.children()) result = result.append(localize.apply(child));
        return result;
    }
    private static Component formatExistingPrefix(Component prefix, TextColor labelColor) {
        // Preserve complete operator wording, whitespace, styles and events; never parse it again.
        return prefix.colorIfAbsent(labelColor).replaceText(TextReplacementConfig.builder()
                .match("\\[|\\]").replaceInsideHoverEvents(false)
                .replacement((match, builder) -> builder.color(BRACKET).build()).build());
    }
    /** Input must be localized first. No serialization or MiniMessage parsing of dynamic content. */
    public static Component block(Component... lines) {
        Objects.requireNonNull(lines);
        if (lines.length == 0) throw new IllegalArgumentException("A message block needs content");
        Component result = Component.newline().color(TEXT);
        var indent = TextReplacementConfig.builder().matchLiteral("\n")
                .replaceInsideHoverEvents(false)
                .replacement(Component.newline().append(Component.text("  "))).build();
        for (Component line : lines) {
            result = result.append(Component.text("  "))
                    .append(text(Objects.requireNonNull(line)).replaceText(indent)).append(Component.newline());
        }
        return result;
    }

    /** Only explicit accent tags use the palette; ordinary colors and rank metadata are never recolored. */
    public static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver accentTag() {
        return net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver("accent", (args, context) ->
                net.kyori.adventure.text.minimessage.tag.Tag.styling(HIGHLIGHT));
    }

    public static Component value(String literal) { return Component.text(literal, HIGHLIGHT); }
    /** Literal command syntax: only the command root is accented, arguments stay neutral. */
    public static Component syntax(String syntax) {
        int split = syntax.indexOf(' ');
        return split < 0 ? value(syntax) : value(syntax.substring(0, split))
                .append(Component.text(syntax.substring(split), BODY));
    }

    /** Compact localized usage. Pass literal executable tokens separately from argument syntax. */
    public static Component usage(String command, String arguments) {
        return Translations.tr("common.usage").color(TEXT).append(usageSyntax(command, arguments));
    }

    /** Usage-only rendering; help pages deliberately retain syntax(String)'s existing contract. */
    public static Component usageSyntax(String command, String arguments) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(arguments);
        if (!command.startsWith("/") || command.isBlank()
                || command.chars().anyMatch(c -> c == '<' || c == '>' || c == '[' || c == ']'
                        || Character.isISOControl(c)))
            throw new IllegalArgumentException("Usage command must contain executable tokens, not placeholders");
        Component result = Component.text(command, accent());
        if (!arguments.isEmpty()) {
            Component syntax = Component.text(arguments, TEXT).replaceText(TextReplacementConfig.builder()
                    .match("\\[[^\\]]*\\]").replaceInsideHoverEvents(false)
                    .replacement((match, builder) -> builder.color(MUTED).build()).build());
            result = result.append(Component.space().color(TEXT)).append(syntax);
        }
        return result.clickEvent(ClickEvent.suggestCommand(command + (arguments.isEmpty() ? "" : " ")))
                .hoverEvent(HoverEvent.showText(Translations.tr("common.command-insert").color(TEXT)));
    }
    public static Component activity(Component label, Component body) {
        return Translations.tr("activity.context", Map.of("activity", label, "body", body.colorIfAbsent(BODY)));
    }
}

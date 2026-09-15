package dev.vapee.core.presentation;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.presentation.config.PresentationConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PresentationRenderer {

    private static final String SCOREBOARD_TITLE_FALLBACK = "<server>";
    private static final String SCOREBOARD_LINE_FALLBACK = "";
    private static final String TABLIST_NAME_FALLBACK = "<name>";
    private static final String TABLIST_HEADER_FALLBACK = "<server>";
    private static final String TABLIST_FOOTER_FALLBACK = "";

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final MessageService messageService;
    private final LuckPermsService luckPermsService;
    private final EconomyService economyService;
    private final PresentationConfig.MetaFormat metaFormat;
    private final Logger logger;
    private final LegacyComponentSerializer legacySerializer;
    private final MiniMessage strictMiniMessage;
    private final String scoreboardTitle;
    private final List<String> scoreboardLines;
    private final String tablistNameFormat;
    private final List<String> tablistHeader;
    private final List<String> tablistFooter;

    public PresentationRenderer(
            JavaPlugin plugin,
            ConfigService configService,
            MessageService messageService,
            LuckPermsService luckPermsService,
            EconomyService economyService,
            PresentationConfig presentationConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.luckPermsService = Objects.requireNonNull(luckPermsService, "luckPermsService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        PresentationConfig validatedConfig = Objects.requireNonNull(presentationConfig, "presentationConfig");
        this.metaFormat = validatedConfig.getMetaFormat();
        this.logger = plugin.getLogger();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.strictMiniMessage = MiniMessage.builder().strict(true).build();

        this.scoreboardTitle = validateTemplate(
                "scoreboard.title",
                validatedConfig.getScoreboardTitle(),
                SCOREBOARD_TITLE_FALLBACK,
                validatedConfig
        );
        this.scoreboardLines = validateTemplates(
                "scoreboard.lines",
                validatedConfig.getScoreboardLines(),
                SCOREBOARD_LINE_FALLBACK,
                validatedConfig
        );
        this.tablistNameFormat = validateTemplate(
                "tablist.name-format",
                validatedConfig.getTablistNameFormat(),
                TABLIST_NAME_FALLBACK,
                validatedConfig
        );
        this.tablistHeader = validateTemplates(
                "tablist.header",
                validatedConfig.getTablistHeader(),
                TABLIST_HEADER_FALLBACK,
                validatedConfig
        );
        this.tablistFooter = validateTemplates(
                "tablist.footer",
                validatedConfig.getTablistFooter(),
                TABLIST_FOOTER_FALLBACK,
                validatedConfig
        );
    }

    public RenderedPresentation render(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        TagResolver placeholders = createPlaceholders(validatedPlayer);

        Component renderedScoreboardTitle = renderTemplate(scoreboardTitle, placeholders);
        List<Component> renderedScoreboardLines = renderTemplates(scoreboardLines, placeholders);
        Component renderedTablistName = renderTemplate(tablistNameFormat, placeholders);
        Component renderedTablistHeader = renderMultiline(tablistHeader, placeholders);
        Component renderedTablistFooter = renderMultiline(tablistFooter, placeholders);

        return new RenderedPresentation(
                renderedScoreboardTitle,
                renderedScoreboardLines,
                renderedTablistName,
                renderedTablistHeader,
                renderedTablistFooter
        );
    }

    public Component renderMeta(String rawMeta) {
        String value = Objects.requireNonNull(rawMeta, "rawMeta");
        return switch (metaFormat) {
            case LEGACY_AMPERSAND -> legacySerializer.deserialize(value);
            case MINI_MESSAGE -> messageService.deserialize(value);
            case PLAIN -> Component.text(value);
        };
    }

    private TagResolver createPlaceholders(Player player) {
        UUID uniqueId = player.getUniqueId();
        Component prefix = luckPermsService.getPrefix(uniqueId)
                .map(this::renderMeta)
                .orElse(Component.empty());
        Component suffix = luckPermsService.getSuffix(uniqueId)
                .map(this::renderMeta)
                .orElse(Component.empty());
        Component group = luckPermsService.getPrimaryGroup(uniqueId)
                .map(Component::text)
                .orElse(Component.empty());
        Component coins = renderCoins(economyService.getCoins(uniqueId));

        return TagResolver.resolver(
                Placeholder.component("server", Component.text(configService.getServerName())),
                Placeholder.component("name", player.displayName()),
                Placeholder.component("prefix", prefix),
                Placeholder.component("suffix", suffix),
                Placeholder.component("group", group),
                Placeholder.component("coins", coins),
                Placeholder.component("online", Component.text(plugin.getServer().getOnlinePlayers().size())),
                Placeholder.component("max_players", Component.text(plugin.getServer().getMaxPlayers()))
        );
    }

    private Component renderCoins(OptionalLong coins) {
        if (coins.isEmpty()) {
            return Component.empty();
        }
        return Component.text(NumberFormat.getIntegerInstance(Locale.US).format(coins.getAsLong()));
    }

    private Component renderTemplate(String template, TagResolver placeholders) {
        return messageService.deserialize(template, placeholders);
    }

    private List<Component> renderTemplates(List<String> templates, TagResolver placeholders) {
        List<Component> components = new ArrayList<>(templates.size());
        for (String template : templates) {
            components.add(renderTemplate(template, placeholders));
        }
        return List.copyOf(components);
    }

    private Component renderMultiline(List<String> templates, TagResolver placeholders) {
        if (templates.isEmpty()) {
            return Component.empty();
        }
        return Component.join(JoinConfiguration.newlines(), renderTemplates(templates, placeholders));
    }

    private List<String> validateTemplates(
            String path,
            List<String> templates,
            String fallback,
            PresentationConfig presentationConfig
    ) {
        List<String> validatedTemplates = new ArrayList<>(templates.size());
        for (int index = 0; index < templates.size(); index++) {
            validatedTemplates.add(validateTemplate(
                    path + "[" + index + "]",
                    templates.get(index),
                    fallback,
                    presentationConfig
            ));
        }
        return List.copyOf(validatedTemplates);
    }

    private String validateTemplate(
            String path,
            String template,
            String fallback,
            PresentationConfig presentationConfig
    ) {
        try {
            strictMiniMessage.deserialize(template, emptyPlaceholders());
            return template;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Invalid MiniMessage template '" + path + "' in "
                            + presentationConfig.getConfigFile() + "; using the internal fallback. "
                            + "The file was left unchanged.",
                    exception
            );
            return fallback;
        }
    }

    private TagResolver emptyPlaceholders() {
        return TagResolver.resolver(
                Placeholder.component("server", Component.empty()),
                Placeholder.component("name", Component.empty()),
                Placeholder.component("prefix", Component.empty()),
                Placeholder.component("suffix", Component.empty()),
                Placeholder.component("group", Component.empty()),
                Placeholder.component("coins", Component.empty()),
                Placeholder.component("online", Component.empty()),
                Placeholder.component("max_players", Component.empty())
        );
    }

    public record RenderedPresentation(
            Component scoreboardTitle,
            List<Component> scoreboardLines,
            Component tablistName,
            Component tablistHeader,
            Component tablistFooter
    ) {

        public RenderedPresentation {
            Objects.requireNonNull(scoreboardTitle, "scoreboardTitle");
            scoreboardLines = List.copyOf(Objects.requireNonNull(scoreboardLines, "scoreboardLines"));
            Objects.requireNonNull(tablistName, "tablistName");
            Objects.requireNonNull(tablistHeader, "tablistHeader");
            Objects.requireNonNull(tablistFooter, "tablistFooter");
        }
    }
}

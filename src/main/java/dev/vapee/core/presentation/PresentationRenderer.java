package dev.vapee.core.presentation;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
import dev.vapee.core.presentation.config.PresentationConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
    private final RankService rankService;
    private final EconomyService economyService;
    private final Logger logger;
    private final LegacyComponentSerializer legacySerializer;
    private final MiniMessage strictMiniMessage;
    private final Path configFile;

    private volatile RenderState state;

    public PresentationRenderer(
            JavaPlugin plugin,
            ConfigService configService,
            MessageService messageService,
            LuckPermsService luckPermsService,
            RankService rankService,
            EconomyService economyService,
            PresentationConfig presentationConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.luckPermsService = Objects.requireNonNull(luckPermsService, "luckPermsService");
        this.rankService = Objects.requireNonNull(rankService, "rankService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        PresentationConfig validatedConfig = Objects.requireNonNull(presentationConfig, "presentationConfig");
        this.logger = plugin.getLogger();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.strictMiniMessage = MiniMessage.builder().strict(true).build();
        this.configFile = validatedConfig.getConfigFile();
        this.state = prepareState(validatedConfig.getState());
    }

    public RenderState getState() {
        return state;
    }

    public RenderState prepareState(PresentationConfig.State configState) {
        PresentationConfig.State validatedConfigState = Objects.requireNonNull(configState, "configState");
        String scoreboardTitle = validateTemplate(
                "scoreboard.title",
                validatedConfigState.scoreboardTitle(),
                SCOREBOARD_TITLE_FALLBACK
        );
        List<String> scoreboardLines = validateTemplates(
                "scoreboard.lines",
                validatedConfigState.scoreboardLines(),
                SCOREBOARD_LINE_FALLBACK
        );
        String tablistNameFormat = validateTemplate(
                "tablist.name-format",
                validatedConfigState.tablistNameFormat(),
                TABLIST_NAME_FALLBACK
        );
        List<String> tablistHeader = validateTemplates(
                "tablist.header",
                validatedConfigState.tablistHeader(),
                TABLIST_HEADER_FALLBACK
        );
        List<String> tablistFooter = validateTemplates(
                "tablist.footer",
                validatedConfigState.tablistFooter(),
                TABLIST_FOOTER_FALLBACK
        );

        return new RenderState(
                validatedConfigState.metaFormat(),
                scoreboardTitle,
                scoreboardLines,
                tablistNameFormat,
                tablistHeader,
                tablistFooter
        );
    }

    public void applyState(RenderState newState) {
        state = Objects.requireNonNull(newState, "newState");
    }

    public RenderedPresentation render(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        RenderState currentState = state;
        TagResolver placeholders = createPlaceholders(validatedPlayer, currentState.metaFormat());

        Component renderedScoreboardTitle = renderTemplate(currentState.scoreboardTitle(), placeholders);
        List<Component> renderedScoreboardLines = renderTemplates(currentState.scoreboardLines(), placeholders);
        Component renderedTablistName = renderTemplate(currentState.tablistNameFormat(), placeholders);
        Component renderedTablistHeader = renderMultiline(currentState.tablistHeader(), placeholders);
        Component renderedTablistFooter = renderMultiline(currentState.tablistFooter(), placeholders);

        return new RenderedPresentation(
                renderedScoreboardTitle,
                renderedScoreboardLines,
                renderedTablistName,
                renderedTablistHeader,
                renderedTablistFooter
        );
    }

    public Component renderMeta(String rawMeta) {
        return renderMeta(rawMeta, state.metaFormat());
    }

    private Component renderMeta(String rawMeta, PresentationConfig.MetaFormat metaFormat) {
        String value = Objects.requireNonNull(rawMeta, "rawMeta");
        return switch (metaFormat) {
            case LEGACY_AMPERSAND -> legacySerializer.deserialize(value);
            case MINI_MESSAGE -> messageService.deserialize(value);
            case PLAIN -> Component.text(value);
        };
    }

    private TagResolver createPlaceholders(Player player, PresentationConfig.MetaFormat metaFormat) {
        UUID uniqueId = player.getUniqueId();
        Component prefix = luckPermsService.getPrefix(uniqueId)
                .map(value -> renderMeta(value, metaFormat))
                .orElse(Component.empty());
        Component suffix = luckPermsService.getSuffix(uniqueId)
                .map(value -> renderMeta(value, metaFormat))
                .orElse(Component.empty());
        Optional<RankInfo> rankInfo = rankService.getPrimaryRank(uniqueId);
        Component coins = renderCoins(economyService.getCoins(uniqueId));
        long playtimeTicks = (long) player.getStatistic(Statistic.PLAY_ONE_MINUTE);

        return TagResolver.resolver(
                createRankAndPlaytimePlaceholders(rankInfo, player.displayName(), playtimeTicks),
                Placeholder.component("server", Component.text(configService.getServerName())),
                Placeholder.component("prefix", prefix),
                Placeholder.component("suffix", suffix),
                Placeholder.component("coins", coins),
                Placeholder.component("online", Component.text(plugin.getServer().getOnlinePlayers().size())),
                Placeholder.component("max_players", Component.text(plugin.getServer().getMaxPlayers()))
        );
    }

    static TagResolver createRankAndPlaytimePlaceholders(
            Optional<RankInfo> rankInfo,
            Component displayName,
            long playtimeTicks
    ) {
        Optional<RankInfo> validatedRankInfo = Objects.requireNonNull(rankInfo, "rankInfo");
        Component validatedDisplayName = Objects.requireNonNull(displayName, "displayName");
        Component rank = validatedRankInfo.map(RankInfo::displayComponent).orElse(Component.empty());
        Component rankName = validatedRankInfo.map(value -> value.colorize(validatedDisplayName))
                .orElse(validatedDisplayName);
        Component rankId = Component.text(validatedRankInfo.map(RankInfo::id).orElse(""));
        return TagResolver.resolver(
                Placeholder.component("name", validatedDisplayName),
                Placeholder.component("rank", rank),
                Placeholder.component("rank_name", rankName),
                Placeholder.component("rank_id", rankId),
                Placeholder.component("group", rankId),
                Placeholder.unparsed("playtime", PlaytimeFormatter.formatTicks(playtimeTicks))
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
            String fallback
    ) {
        List<String> validatedTemplates = new ArrayList<>(templates.size());
        for (int index = 0; index < templates.size(); index++) {
            validatedTemplates.add(validateTemplate(
                    path + "[" + index + "]",
                    templates.get(index),
                    fallback
            ));
        }
        return List.copyOf(validatedTemplates);
    }

    private String validateTemplate(
            String path,
            String template,
            String fallback
    ) {
        try {
            strictMiniMessage.deserialize(template, emptyPlaceholders());
            return template;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Invalid MiniMessage template '" + path + "' in "
                            + configFile + "; using the internal fallback. "
                            + "The file was left unchanged.",
                    exception
            );
            return fallback;
        }
    }

    static TagResolver emptyPlaceholders() {
        return TagResolver.resolver(
                Placeholder.component("server", Component.empty()),
                Placeholder.component("name", Component.empty()),
                Placeholder.component("rank_name", Component.empty()),
                Placeholder.component("prefix", Component.empty()),
                Placeholder.component("suffix", Component.empty()),
                Placeholder.component("rank", Component.empty()),
                Placeholder.component("rank_id", Component.empty()),
                Placeholder.component("group", Component.empty()),
                Placeholder.component("playtime", Component.empty()),
                Placeholder.component("coins", Component.empty()),
                Placeholder.component("online", Component.empty()),
                Placeholder.component("max_players", Component.empty())
        );
    }

    public record RenderState(
            PresentationConfig.MetaFormat metaFormat,
            String scoreboardTitle,
            List<String> scoreboardLines,
            String tablistNameFormat,
            List<String> tablistHeader,
            List<String> tablistFooter
    ) {

        public RenderState {
            Objects.requireNonNull(metaFormat, "metaFormat");
            Objects.requireNonNull(scoreboardTitle, "scoreboardTitle");
            scoreboardLines = List.copyOf(Objects.requireNonNull(scoreboardLines, "scoreboardLines"));
            Objects.requireNonNull(tablistNameFormat, "tablistNameFormat");
            tablistHeader = List.copyOf(Objects.requireNonNull(tablistHeader, "tablistHeader"));
            tablistFooter = List.copyOf(Objects.requireNonNull(tablistFooter, "tablistFooter"));
        }
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

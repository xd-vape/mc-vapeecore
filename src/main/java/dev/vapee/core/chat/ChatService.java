package dev.vapee.core.chat;

import dev.vapee.core.chat.config.ChatConfig;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.rank.RankInfo;
import dev.vapee.core.rank.RankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatService {

    private final LuckPermsService luckPermsService;
    private final RankService rankService;
    private final MessageService messageService;
    private final Logger logger;
    private final LegacyComponentSerializer legacySerializer;
    private final Path configFile;

    private volatile RuntimeState state;

    public ChatService(
            LuckPermsService luckPermsService,
            RankService rankService,
            ChatConfig chatConfig,
            MessageService messageService,
            Logger logger
    ) {
        this.luckPermsService = Objects.requireNonNull(luckPermsService, "luckPermsService");
        this.rankService = Objects.requireNonNull(rankService, "rankService");
        ChatConfig validatedChatConfig = Objects.requireNonNull(chatConfig, "chatConfig");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.configFile = validatedChatConfig.getConfigFile();
        this.state = prepareState(validatedChatConfig.getState());
    }

    public boolean isEnabled() {
        return state.enabled();
    }

    public RuntimeState getState() {
        return state;
    }

    public RuntimeState prepareState(ChatConfig.State configState) {
        ChatConfig.State validatedConfigState = Objects.requireNonNull(configState, "configState");
        return new RuntimeState(
                validatedConfigState.enabled(),
                validateFormat(validatedConfigState.format(), configFile, logger),
                validatedConfigState.metaFormat()
        );
    }

    public void applyState(RuntimeState newState) {
        state = Objects.requireNonNull(newState, "newState");
    }

    public Component render(Player source, Component sourceDisplayName, Component message) {
        Player validatedSource = Objects.requireNonNull(source, "source");
        Component validatedDisplayName = Objects.requireNonNull(sourceDisplayName, "sourceDisplayName");
        Component validatedMessage = Objects.requireNonNull(message, "message");
        RuntimeState currentState = state;

        try {
            UUID uniqueId = validatedSource.getUniqueId();
            Component prefix = luckPermsService.getPrefix(uniqueId)
                    .map(value -> deserializeMeta(value, currentState.metaFormat()))
                    .orElse(Component.empty());
            Component suffix = luckPermsService.getSuffix(uniqueId)
                    .map(value -> deserializeMeta(value, currentState.metaFormat()))
                    .orElse(Component.empty());
            Optional<RankInfo> rankInfo = rankService.getPrimaryRank(uniqueId);

            TagResolver placeholders = createPlaceholders(
                    prefix,
                    validatedDisplayName,
                    suffix,
                    rankInfo,
                    validatedMessage
            );
            return messageService.deserialize(currentState.chatFormat(), placeholders);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not render chat for " + validatedSource.getUniqueId()
                            + "; using the safe component fallback.",
                    exception
            );
            return fallback(validatedDisplayName, validatedMessage);
        }
    }

    static TagResolver createPlaceholders(
            Component prefix,
            Component displayName,
            Component suffix,
            Optional<RankInfo> rankInfo,
            Component message
    ) {
        Optional<RankInfo> validatedRankInfo = Objects.requireNonNull(rankInfo, "rankInfo");
        String rawRankId = validatedRankInfo.map(RankInfo::id).orElse("");
        Component rankId = Component.text(rawRankId);
        Component rank = validatedRankInfo.map(RankInfo::displayComponent).orElse(Component.empty());
        Component rankName = validatedRankInfo.map(value -> value.colorize(displayName)).orElse(displayName);
        return TagResolver.resolver(
                    Placeholder.component("prefix", prefix),
                    Placeholder.component("name", displayName),
                    Placeholder.component("rank_name", rankName),
                    Placeholder.component("suffix", suffix),
                    Placeholder.component("rank", rank),
                    Placeholder.component("rank_id", rankId),
                    Placeholder.component("group", rankId),
                    Placeholder.component("message", message)
        );
    }

    private Component deserializeMeta(String value, ChatConfig.MetaFormat metaFormat) {
        return switch (metaFormat) {
            case LEGACY_AMPERSAND -> legacySerializer.deserialize(value);
            case MINI_MESSAGE -> messageService.deserialize(value);
            case PLAIN -> Component.text(value);
        };
    }

    static String validateFormat(String configuredFormat, Path configFile, Logger logger) {
        TagResolver emptyPlaceholders = emptyPlaceholders();

        try {
            MiniMessage.builder()
                    .strict(true)
                    .build()
                    .deserialize(configuredFormat, emptyPlaceholders);
            return configuredFormat;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Invalid MiniMessage chat format in " + configFile
                            + "; using the internal default. The file was left unchanged.",
                    exception
            );
            return ChatConfig.DEFAULT_FORMAT;
        }
    }

    static TagResolver emptyPlaceholders() {
        return TagResolver.resolver(
                Placeholder.component("prefix", Component.empty()),
                Placeholder.component("name", Component.empty()),
                Placeholder.component("rank_name", Component.empty()),
                Placeholder.component("suffix", Component.empty()),
                Placeholder.component("rank", Component.empty()),
                Placeholder.component("rank_id", Component.empty()),
                Placeholder.component("group", Component.empty()),
                Placeholder.component("message", Component.empty())
        );
    }

    private Component fallback(Component sourceDisplayName, Component message) {
        return sourceDisplayName
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message);
    }

    public record RuntimeState(
            boolean enabled,
            String chatFormat,
            ChatConfig.MetaFormat metaFormat
    ) {

        public RuntimeState {
            Objects.requireNonNull(chatFormat, "chatFormat");
            Objects.requireNonNull(metaFormat, "metaFormat");
        }
    }

}

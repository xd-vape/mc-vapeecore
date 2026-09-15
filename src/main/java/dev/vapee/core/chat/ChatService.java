package dev.vapee.core.chat;

import dev.vapee.core.chat.config.ChatConfig;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.permission.LuckPermsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatService {

    private final LuckPermsService luckPermsService;
    private final MessageService messageService;
    private final Logger logger;
    private final LegacyComponentSerializer legacySerializer;
    private final String chatFormat;
    private final ChatConfig.MetaFormat metaFormat;

    public ChatService(
            LuckPermsService luckPermsService,
            ChatConfig chatConfig,
            MessageService messageService,
            Logger logger
    ) {
        this.luckPermsService = Objects.requireNonNull(luckPermsService, "luckPermsService");
        ChatConfig validatedChatConfig = Objects.requireNonNull(chatConfig, "chatConfig");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.chatFormat = validateFormat(validatedChatConfig.getFormat());
        this.metaFormat = validatedChatConfig.getMetaFormat();
    }

    public Component render(Player source, Component sourceDisplayName, Component message) {
        Player validatedSource = Objects.requireNonNull(source, "source");
        Component validatedDisplayName = Objects.requireNonNull(sourceDisplayName, "sourceDisplayName");
        Component validatedMessage = Objects.requireNonNull(message, "message");

        try {
            UUID uniqueId = validatedSource.getUniqueId();
            Component prefix = luckPermsService.getPrefix(uniqueId)
                    .map(this::deserializeMeta)
                    .orElse(Component.empty());
            Component suffix = luckPermsService.getSuffix(uniqueId)
                    .map(this::deserializeMeta)
                    .orElse(Component.empty());

            TagResolver placeholders = TagResolver.resolver(
                    Placeholder.component("prefix", prefix),
                    Placeholder.component("name", validatedDisplayName),
                    Placeholder.component("suffix", suffix),
                    Placeholder.component("message", validatedMessage)
            );
            return messageService.deserialize(chatFormat, placeholders);
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

    private Component deserializeMeta(String value) {
        return switch (metaFormat) {
            case LEGACY_AMPERSAND -> legacySerializer.deserialize(value);
            case MINI_MESSAGE -> messageService.deserialize(value);
            case PLAIN -> Component.text(value);
        };
    }

    private String validateFormat(String configuredFormat) {
        TagResolver emptyPlaceholders = TagResolver.resolver(
                Placeholder.component("prefix", Component.empty()),
                Placeholder.component("name", Component.empty()),
                Placeholder.component("suffix", Component.empty()),
                Placeholder.component("message", Component.empty())
        );

        try {
            MiniMessage.builder()
                    .strict(true)
                    .build()
                    .deserialize(configuredFormat, emptyPlaceholders);
            return configuredFormat;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Invalid MiniMessage chat format; using the internal default. The file was left unchanged.",
                    exception
            );
            return ChatConfig.DEFAULT_FORMAT;
        }
    }

    private Component fallback(Component sourceDisplayName, Component message) {
        return sourceDisplayName
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message);
    }
}

package dev.vapee.core.privatemessage;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.privatemessage.config.PrivateMessageConfig;
import dev.vapee.core.social.SocialService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PrivateMessageService {

    private final Server server;
    private final PlayerSettingsService playerSettingsService;
    private final SocialService socialService;
    private final TemplateDeserializer templateDeserializer;
    private final Logger logger;
    private final Path configFile;
    private final MiniMessage strictMiniMessage;
    private final Map<UUID, UUID> lastConversationPartners = new HashMap<>();

    private volatile RuntimeState state;

    public PrivateMessageService(
            Server server,
            PlayerSettingsService playerSettingsService,
            SocialService socialService,
            MessageService messageService,
            Logger logger,
            PrivateMessageConfig privateMessageConfig
    ) {
        this(
                server,
                playerSettingsService,
                socialService,
                Objects.requireNonNull(messageService, "messageService")::deserialize,
                logger,
                Objects.requireNonNull(privateMessageConfig, "privateMessageConfig").getConfigFile(),
                privateMessageConfig.getState()
        );
    }

    PrivateMessageService(
            Server server,
            PlayerSettingsService playerSettingsService,
            SocialService socialService,
            TemplateDeserializer templateDeserializer,
            Logger logger,
            Path configFile,
            PrivateMessageConfig.State initialConfigState
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.socialService = Objects.requireNonNull(socialService, "socialService");
        this.templateDeserializer = Objects.requireNonNull(templateDeserializer, "templateDeserializer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.strictMiniMessage = MiniMessage.builder().strict(true).build();
        this.state = prepareState(initialConfigState);
    }

    public PrivateMessageResult send(Player sender, Player recipient, String rawMessage) {
        Player validatedSender = Objects.requireNonNull(sender, "sender");
        Player validatedRecipient = Objects.requireNonNull(recipient, "recipient");
        String validatedMessage = requireMessage(rawMessage);
        RuntimeState currentState = state;

        if (!currentState.enabled()) {
            return PrivateMessageResult.FEATURE_DISABLED;
        }

        UUID senderUniqueId = validatedSender.getUniqueId();
        UUID recipientUniqueId = validatedRecipient.getUniqueId();
        if (senderUniqueId.equals(recipientUniqueId)) {
            return PrivateMessageResult.CANNOT_MESSAGE_SELF;
        }
        if (playerSettingsService.getSettings(senderUniqueId).isEmpty()) {
            return PrivateMessageResult.SENDER_NOT_LOADED;
        }
        if (!validatedRecipient.isOnline()) {
            return PrivateMessageResult.TARGET_OFFLINE;
        }

        Optional<Boolean> recipientSetting = playerSettingsService.arePrivateMessagesEnabled(recipientUniqueId);
        if (recipientSetting.isEmpty()) {
            return PrivateMessageResult.RECIPIENT_NOT_LOADED;
        }
        if (!recipientSetting.get()) {
            return PrivateMessageResult.RECIPIENT_DISABLED;
        }
        if (socialService.isIgnoring(senderUniqueId, recipientUniqueId)) {
            return PrivateMessageResult.SENDER_IGNORES_RECIPIENT;
        }
        if (socialService.isIgnoring(recipientUniqueId, senderUniqueId)) {
            return PrivateMessageResult.RECIPIENT_IGNORES_SENDER;
        }

        Component message = Component.text(validatedMessage);
        Component senderName = Objects.requireNonNull(validatedSender.displayName(), "sender display name");
        Component recipientName = Objects.requireNonNull(validatedRecipient.displayName(), "recipient display name");
        RenderedMessages renderedMessages = renderMessages(
                currentState,
                senderUniqueId,
                recipientUniqueId,
                senderName,
                recipientName,
                message
        );

        validatedSender.sendMessage(renderedMessages.outgoing());
        validatedRecipient.sendMessage(renderedMessages.incoming());

        lastConversationPartners.put(senderUniqueId, recipientUniqueId);
        lastConversationPartners.put(recipientUniqueId, senderUniqueId);
        return PrivateMessageResult.SUCCESS;
    }

    public PrivateMessageResult reply(Player sender, String rawMessage) {
        Player validatedSender = Objects.requireNonNull(sender, "sender");
        String validatedMessage = requireMessage(rawMessage);
        if (!state.enabled()) {
            return PrivateMessageResult.FEATURE_DISABLED;
        }

        UUID senderUniqueId = validatedSender.getUniqueId();
        UUID targetUniqueId = lastConversationPartners.get(senderUniqueId);
        if (targetUniqueId == null) {
            return PrivateMessageResult.NO_REPLY_TARGET;
        }

        Player target = server.getPlayer(targetUniqueId);
        if (target == null || !target.isOnline()) {
            removePlayer(targetUniqueId);
            return PrivateMessageResult.TARGET_OFFLINE;
        }
        return send(validatedSender, target, validatedMessage);
    }

    public boolean isEnabled() {
        return state.enabled();
    }

    public RuntimeState getState() {
        return state;
    }

    public RuntimeState prepareState(PrivateMessageConfig.State configState) {
        PrivateMessageConfig.State validatedState = Objects.requireNonNull(configState, "configState");
        return new RuntimeState(
                validatedState.enabled(),
                validateTemplate(
                        "format.outgoing",
                        validatedState.outgoingFormat(),
                        PrivateMessageConfig.DEFAULT_OUTGOING_FORMAT
                ),
                validateTemplate(
                        "format.incoming",
                        validatedState.incomingFormat(),
                        PrivateMessageConfig.DEFAULT_INCOMING_FORMAT
                )
        );
    }

    public void applyState(RuntimeState newState) {
        state = Objects.requireNonNull(newState, "newState");
    }

    public void removePlayer(UUID uniqueId) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        lastConversationPartners.entrySet().removeIf(entry ->
                entry.getKey().equals(validatedUniqueId) || entry.getValue().equals(validatedUniqueId)
        );
    }

    public void clearConversations() {
        lastConversationPartners.clear();
    }

    private String requireMessage(String rawMessage) {
        String validatedMessage = Objects.requireNonNull(rawMessage, "rawMessage");
        if (validatedMessage.isBlank()) {
            throw new IllegalArgumentException("Private message must not be blank");
        }
        return validatedMessage;
    }

    private String validateTemplate(String path, String template, String fallback) {
        try {
            strictMiniMessage.deserialize(template, emptyPlaceholders());
            return template;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Invalid MiniMessage template '" + path + "' in " + configFile
                            + "; using the internal fallback. The file was left unchanged.",
                    exception
            );
            return fallback;
        }
    }

    private RenderedMessages renderMessages(
            RuntimeState currentState,
            UUID senderUniqueId,
            UUID recipientUniqueId,
            Component senderName,
            Component recipientName,
            Component message
    ) {
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.component("sender", senderName),
                Placeholder.component("recipient", recipientName),
                Placeholder.component("message", message)
        );

        try {
            return new RenderedMessages(
                    templateDeserializer.deserialize(currentState.outgoingFormat(), placeholders),
                    templateDeserializer.deserialize(currentState.incomingFormat(), placeholders)
            );
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not render a private message from " + senderUniqueId + " to " + recipientUniqueId
                            + "; using safe component fallbacks.",
                    exception
            );
            return fallback(senderName, recipientName, message);
        }
    }

    private RenderedMessages fallback(Component senderName, Component recipientName, Component message) {
        Component outgoing = Component.text("[You → ", NamedTextColor.DARK_GRAY)
                .append(recipientName)
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(message.color(NamedTextColor.WHITE));
        Component incoming = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(senderName)
                .append(Component.text(" → You] ", NamedTextColor.DARK_GRAY))
                .append(message.color(NamedTextColor.WHITE));
        return new RenderedMessages(outgoing, incoming);
    }

    private TagResolver emptyPlaceholders() {
        return TagResolver.resolver(
                Placeholder.component("sender", Component.empty()),
                Placeholder.component("recipient", Component.empty()),
                Placeholder.component("message", Component.empty())
        );
    }

    @FunctionalInterface
    interface TemplateDeserializer {
        Component deserialize(String template, TagResolver placeholders);
    }

    public record RuntimeState(boolean enabled, String outgoingFormat, String incomingFormat) {

        public RuntimeState {
            Objects.requireNonNull(outgoingFormat, "outgoingFormat");
            Objects.requireNonNull(incomingFormat, "incomingFormat");
        }
    }

    private record RenderedMessages(Component outgoing, Component incoming) {

        private RenderedMessages {
            Objects.requireNonNull(outgoing, "outgoing");
            Objects.requireNonNull(incoming, "incoming");
        }
    }
}

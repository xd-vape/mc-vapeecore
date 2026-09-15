package dev.vapee.core.message;

import dev.vapee.core.config.ConfigService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Objects;

public final class MessageService {

    private final ConfigService configService;
    private final MiniMessage miniMessage;

    public MessageService(ConfigService configService) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public Component deserialize(String message) {
        return miniMessage.deserialize(Objects.requireNonNull(message, "message"));
    }

    public Component deserialize(String message, TagResolver resolver) {
        return miniMessage.deserialize(
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(resolver, "resolver")
        );
    }

    public void send(Audience audience, String message) {
        Objects.requireNonNull(audience, "audience")
                .sendMessage(deserialize(configService.getMessagePrefix() + message));
    }
}

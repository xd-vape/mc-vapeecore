package dev.vapee.core.message;

import dev.vapee.core.config.ConfigService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Objects;
import java.util.function.Supplier;

public final class MessageService {

    private final Supplier<String> prefixSupplier;
    private final MiniMessage miniMessage;

    public MessageService(ConfigService configService) {
        this(Objects.requireNonNull(configService, "configService")::getMessagePrefix);
    }

    MessageService(Supplier<String> prefixSupplier) {
        this.prefixSupplier = Objects.requireNonNull(prefixSupplier, "prefixSupplier");
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
        send(audience, deserialize(Objects.requireNonNull(message, "message")));
    }

    public void send(Audience audience, String message, TagResolver resolver) {
        send(
                audience,
                deserialize(
                        Objects.requireNonNull(message, "message"),
                        Objects.requireNonNull(resolver, "resolver")
                )
        );
    }

    public void send(Audience audience, Component message) {
        Objects.requireNonNull(audience, "audience").sendMessage(
                deserialize(Objects.requireNonNull(prefixSupplier.get(), "messagePrefix"))
                        .append(Objects.requireNonNull(message, "message"))
        );
    }
}

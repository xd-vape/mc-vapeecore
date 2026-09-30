package dev.vapee.core.moderation;

import dev.vapee.core.module.CoreModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Owns one moderation service, no commands, enforcement, listeners, tasks or reload. */
public final class ModerationModule implements CoreModule {
    private final Supplier<ModerationRepository> repositoryFactory;
    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private ModerationService service;

    public ModerationModule(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        repositoryFactory = () -> new FileModerationRepository(
                plugin.getDataFolder().toPath().resolve("moderation.yml"), plugin.getLogger());
        clock = Clock.systemUTC();
        idSupplier = UUID::randomUUID;
    }

    // Local lifecycle seam exercises this actual module without inventing a Bukkit server.
    ModerationModule(Supplier<ModerationRepository> repositoryFactory, Clock clock, Supplier<UUID> idSupplier) {
        this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    @Override public String getName() { return "Moderation"; }

    @Override public void enable() {
        if (service != null) throw new IllegalStateException("ModerationModule already enabled");
        ModerationService loaded = new ModerationService(repositoryFactory.get(), clock, idSupplier);
        service = loaded;
    }

    @Override public void disable() { service = null; }

    public ModerationService getModerationService() {
        if (service == null) throw new IllegalStateException("ModerationModule is not enabled");
        return service;
    }
}

package dev.vapee.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Copies bundled bytes only for a missing file; callers own diagnostics and failure policy. */
public final class ConfigFiles {
    private ConfigFiles() { }

    public static boolean copyDefault(Path file, Supplier<InputStream> resourceSupplier,
                                      Supplier<? extends RuntimeException> missingResource) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) return false;
        try (InputStream resource = resourceSupplier.get()) {
            if (resource == null) throw missingResource.get();
            Files.copy(resource, file);
        }
        return true;
    }
}

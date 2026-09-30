package dev.vapee.core.moderation;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Path;

@FunctionalInterface
interface ModerationFileMover {
    void move(Path source, Path target, CopyOption... options) throws IOException;
}

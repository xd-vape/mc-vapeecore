package dev.vapee.core.clan;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Path;

@FunctionalInterface
interface ClanFileMover {
    void move(Path source, Path target, CopyOption... options) throws IOException;
}

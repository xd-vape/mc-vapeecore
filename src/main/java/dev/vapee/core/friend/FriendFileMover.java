package dev.vapee.core.friend;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Path;

@FunctionalInterface
interface FriendFileMover {

    void move(Path source, Path target, CopyOption... options) throws IOException;
}

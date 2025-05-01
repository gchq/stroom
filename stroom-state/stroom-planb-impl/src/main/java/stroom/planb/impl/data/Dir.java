package stroom.planb.impl.data;

import stroom.util.io.FileUtil;

import java.nio.file.Path;
import java.util.Objects;

public class Dir implements AutoCloseable {

    private final DirQueue dirQueue;
    private final Path path;

    Dir(final DirQueue dirQueue,
        final Path path) {
        this.dirQueue = Objects.requireNonNull(dirQueue);
        this.path = Objects.requireNonNull(path);
    }

    public Path getPath() {
        return path;
    }

    @Override
    public void close() {
        dirQueue.close(this);
    }

    @Override
    public String toString() {
        return FileUtil.getCanonicalPath(path);
    }
}

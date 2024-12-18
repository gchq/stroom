package stroom.proxy.app.handler;

import stroom.util.io.FileUtil;

import java.nio.file.Path;

public class Dir implements AutoCloseable {

    private final DirQueue dirQueue;
    private final Path path;

    Dir(final DirQueue dirQueue,
                final Path path) {
        this.dirQueue = dirQueue;
        this.path = path;
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

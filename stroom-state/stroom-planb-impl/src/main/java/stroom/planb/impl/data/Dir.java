package stroom.planb.impl.data;

import stroom.util.io.FileUtil;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class Dir implements AutoCloseable {
    private final DirQueue dirQueue;
    private final Path path;
    private final CountDownLatch countDownLatch;

    Dir(final DirQueue dirQueue,
        final Path path,
        final CountDownLatch countDownLatch) {
        this.dirQueue = Objects.requireNonNull(dirQueue);
        this.path = Objects.requireNonNull(path);
        this.countDownLatch = countDownLatch;
    }

    public Path getPath() {
        return path;
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
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

package stroom.planb.impl.data;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SequentialFile {

    private final Path root;
    private final List<Path> subDirs;
    private final Path zip;
    private final CountDownLatch countDownLatch;

    public SequentialFile(final Path root,
                          final List<Path> subDirs,
                          final Path zip,
                          final CountDownLatch countDownLatch) {
        this.root = root;
        this.subDirs = subDirs;
        this.zip = zip;
        this.countDownLatch = countDownLatch;
    }

    public Path getRoot() {
        return root;
    }

    public List<Path> getSubDirs() {
        return subDirs;
    }

    public Path getZip() {
        return zip;
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    @Override
    public String toString() {
        return zip.toString();
    }
}

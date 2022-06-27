package stroom.proxy.repo.store;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

class DirManager {

    private final Map<Path, AtomicInteger> pathUses = new HashMap<>();
    private final ReentrantLock directoryLock = new ReentrantLock();

    public void createDirsUnderLock(final FileSet fileSet, final Runnable runnable) throws IOException {
        try {
            directoryLock.lockInterruptibly();
            try {
                // Create directories.
                Files.createDirectories(fileSet.getDir());

                for (int i = 0; i < fileSet.getSubDirs().size(); i++) {
                    final Path path = fileSet.getSubDirs().get(i);
                    pathUses.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
                }

                runnable.run();
            } finally {
                directoryLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public void deleteDirsUnderLock(final FileSet fileSet) {
        try {
            directoryLock.lockInterruptibly();
            try {
                boolean success = true;
                for (int i = fileSet.getSubDirs().size() - 1; i >= 0 && success; i--) {
                    final Path path = fileSet.getSubDirs().get(i);
                    final AtomicInteger count = pathUses.get(path);
                    if (count != null) {
                        final int num = count.decrementAndGet();
                        if (num == 0) {
                            pathUses.remove(path);
                            success = FileUtil.delete(path);
                        }
                    } else {
                        success = FileUtil.delete(path);
                    }
                }
            } finally {
                directoryLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }
}

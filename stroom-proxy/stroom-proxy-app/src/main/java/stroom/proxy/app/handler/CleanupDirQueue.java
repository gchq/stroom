package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.StringIdUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class CleanupDirQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CleanupDirQueue.class);

    private final Path dir;
    private final AtomicLong count = new AtomicLong();

    @Inject
    CleanupDirQueue(final DataDirProvider dataDirProvider) {
        dir = dataDirProvider.get().resolve("99_deleting");
        DirUtil.ensureDirExists(dir);
        FileUtil.deleteContents(dir);
    }

    public void add(final Path sourceDir) {
        try {
            // We will move before delete to help ensure we don't end up partially deleting dir contents in place.
            // Make sure we get a unique dir name.
            final Path deleteDir = dir.resolve(StringIdUtil.idToString(count.incrementAndGet()));
            Files.move(sourceDir, deleteDir, StandardCopyOption.ATOMIC_MOVE);
            FileUtil.deleteDir(deleteDir);
        } catch (final Exception e) {
            LOGGER.error(() -> "Failed to cleanup dir: " + FileUtil.getCanonicalPath(sourceDir), e);
        }
    }
}

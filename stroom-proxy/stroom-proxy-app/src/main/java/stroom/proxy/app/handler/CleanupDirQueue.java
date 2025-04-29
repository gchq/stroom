package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.StringIdUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
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
        LOGGER.info("Initialising CleanupDirQueue. Deleting contents of {}", LogUtil.path(dir));
        FileUtil.deleteContents(dir);
    }

    /**
     * Synchronously move sourceDir then delete it and its contents.
     * Swallows all exceptions.
     */
    public void add(final Path sourceDir) {
        Objects.requireNonNull(sourceDir);
        if (Files.isDirectory(sourceDir)) {
            try {
                // We will move before delete to help ensure we don't end up partially deleting dir contents in place.
                // Make sure we get a unique dir name.
                final Path deleteDir = dir.resolve(StringIdUtil.idToString(count.incrementAndGet()));
                LOGGER.debug("Moving {} => {}", sourceDir, deleteDir);
                Files.move(sourceDir, deleteDir, StandardCopyOption.ATOMIC_MOVE);
                LOGGER.debug("Deleting {} and its contents", deleteDir);
                FileUtil.deleteDir(deleteDir);
            } catch (final Exception e) {
                LOGGER.error(() -> "Failed to cleanup dir: " + FileUtil.getCanonicalPath(sourceDir), e);
            }
        } else {
            if (Files.exists(sourceDir)) {
                LOGGER.debug("sourceDir {} is not a directory", sourceDir);
            } else {
                LOGGER.debug("sourceDir {} does not exist", sourceDir);
            }
        }
    }
}

package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.AbstractFileVisitor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class TestUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtil.class);

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    static void clearTestDir() throws IOException {
        final Path path = getCurrentTestPath();
        try {
            if (path != null && Files.isDirectory(path)) {
                Files.walkFileTree(path, new AbstractFileVisitor() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                        delete(file);
                        return super.visitFile(file, attrs);
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                        if (!path.equals(dir)) {
                            delete(dir);
                        }
                        return super.postVisitDirectory(dir, exc);
                    }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to clear directory " + path, e);
        }
    }

    private static void delete(final Path path) {
        try {
            if (path != null) {
                Files.delete(path);
            }
        } catch (IOException e) {
            LOGGER.trace(e.getMessage(), e);
        }
    }

    static Path getCurrentTestPath() throws IOException {
        return Files.createTempDirectory("test");
    }

    static Path createUniqueTestDir(final Path parentDir) throws IOException {
        if (!Files.isDirectory(parentDir)) {
            throw new IOException("The parent directory '" + parentDir.toAbsolutePath().toString() + "' does not exist");
        }

        Path dir = null;
        for (int i = 0; i < 100; i++) {
            dir = parentDir.resolve(FORMAT.format(ZonedDateTime.now(ZoneOffset.UTC)));
            try {
                Files.createDirectories(dir);
                break;
            } catch (final IOException e) {
                dir = null;
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException ie) {
                    // Ignore.
                }
            }
        }

        if (dir == null) {
            throw new IOException("Unable to create unique test dir in: " + parentDir.toAbsolutePath().toString());
        }

        return dir;
    }
}

package stroom.proxy.repo;

import stroom.util.logging.StroomLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

public final class TestUtil {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestUtil.class);

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    static void clearTestDir() throws IOException {
        final Path dir = getCurrentTestPath();
        try (final Stream<Path> stream = Files.walk(dir).filter(p -> !p.equals(dir)).sorted(Comparator.reverseOrder())) {
            stream.forEach(TestUtil::delete);
        } catch (IOException e) {
            throw new RuntimeException("Unable to clear directory " + dir, e);
        }
    }

    private static void delete(final Path path) {
        try {
            Files.delete(path);
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

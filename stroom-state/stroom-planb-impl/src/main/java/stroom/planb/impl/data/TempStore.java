package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Singleton
public class TempStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TempStore.class);

    private final Path receiveDir;
    private final AtomicLong tempId = new AtomicLong();

    @Inject
    public TempStore(final StatePaths statePaths) {

        // Create the root directory
        ensureDirExists(statePaths.getRootDir());

        // Create the receive directory.
        receiveDir = statePaths.getReceiveDir();
        if (ensureDirExists(receiveDir)) {
            if (!FileUtil.deleteContents(receiveDir)) {
                throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(receiveDir));
            }
        }
    }

    private boolean ensureDirExists(final Path path) {
        if (Files.isDirectory(path)) {
            return true;
        }

        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return false;
    }

    private record NumericFile(Path dir, long num) {

    }

    private static class NumericFileTest implements Consumer<NumericFile> {

        private final Comparator<Long> comparator;
        private NumericFile current;

        public NumericFileTest(final Comparator<Long> comparator) {
            this.comparator = comparator;
        }

        @Override
        public void accept(final NumericFile numericFile) {
            if (current == null || comparator.compare(numericFile.num, current.num) > 0) {
                current = numericFile;
            }
        }

        public Optional<NumericFile> get() {
            return Optional.ofNullable(current);
        }
    }
}

package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Strings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class NumberedDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NumberedDirProvider.class);

    private final Path parentDir;
    private final AtomicLong sequence = new AtomicLong();

    public NumberedDirProvider(final Path parentDir) {
        this.parentDir = parentDir;
        final long maxId = getMaxDirId(parentDir);
        sequence.set(maxId);
    }

    public Path get() throws IOException {
        final long id = sequence.incrementAndGet();
        final String name = create(id);
        final Path path = parentDir.resolve(name);
        return Files.createDirectory(path);
    }

    /**
     * Create a string to use as part of a file name that is a `0` padded number.
     *
     * @param num The number to create the name from.
     * @return A `0` padded string representing the supplied number.
     */
    private String create(final long num) {
        return Strings.padStart(Long.toString(num), 10, '0');
    }

    /**
     * Get the max id of any numerically named file found in the supplied dir.
     *
     * @param parentDir The parent dir to look at.
     * @return The max id of all files found in a dir or 0 if non found.
     */
    private long getMaxDirId(final Path parentDir) {
        return findDir(parentDir, (num, current) -> num > current).map(DirId::num).orElse(0L);
    }

    private static Optional<DirId> findDir(final Path path,
                                           final BiFunction<Long, Long, Boolean> comparator) {
        DirId current = null;
        try (final Stream<Path> stream = Files.list(path)) {
            final Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                final Path file = iterator.next();
                // Parse numeric part of dir.
                final long num = parse(file.getFileName().toString());

                // If this is the biggest/smallest num we have seen then set it and remember the dir.
                if (current == null || comparator.apply(num, current.num)) {
                    current = new DirId(file, num);
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return Optional.ofNullable(current);
    }

    /**
     * Extract the number represented by the supplied `0` padded string.
     *
     * @param string A directory name.
     * @return The long part of the string if found, else null.
     */
    private static long parse(final String string) {
        try {
            if (string.isEmpty()) {
                throw new NumberFormatException("Empty string");
            }

            // Strip leading 0's.
            int start = 0;
            for (int i = 0; i < string.length(); i++) {
                if (string.charAt(i) != '0') {
                    break;
                }
                start = i + 1;
            }
            final String numericPart;
            if (start == 0) {
                // If there were no leading 0's then the whole string is a number.
                numericPart = string;
            } else if (start == string.length()) {
                // If all characters were 0 then this string represents 0.
                return 0L;
            } else {
                numericPart = string.substring(start);
            }

            // Parse numeric part of dir name.
            return Long.parseLong(numericPart);
        } catch (final NumberFormatException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private record DirId(Path dir, long num) {

    }
}

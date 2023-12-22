package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Strings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class NumericFileNameUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NumericFileNameUtil.class);

    /**
     * Create a string to use as a directory name or part of a file name that is a `0` padded number.
     *
     * @param num The number to create the name from.
     * @return A `0` padded string representing the supplied number.
     */
    public static String create(final long num) {
        return Strings.padStart(Long.toString(num), 10, '0');
    }

    /**
     * Extract the number represented by the supplied `0` padded string.
     *
     * @param string A file or directory name.
     * @return The long part of the string if found, else null.
     */
    public static Long parse(final String string) {
        // Strip leading 0's.
        int start = 0;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) != '0') {
                break;
            }
            start = i + 1;
        }
        int end = string.indexOf(".");
        final String numericPart;
        if (start == 0 && end == -1) {
            numericPart = string;
        } else if (end == -1) {
            numericPart = string.substring(start);
        } else {
            numericPart = string.substring(start, end);
        }

        // Parse numeric part of dir.
        if (!numericPart.isEmpty()) {
            try {
                return Long.parseLong(numericPart);
            } catch (final NumberFormatException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }
        return null;
    }

    /**
     * Get the min id of any numerically named file found in the supplied dir.
     *
     * @param parentDir The parent dir to look at.
     * @return The min id of all files found in a dir or 0 if non found.
     */
    public static long getMinId(final Path parentDir) {
        return getFileId(parentDir, (num, current) -> num < current);
    }

    /**
     * Get the max id of any numerically named file found in the supplied dir.
     *
     * @param parentDir The parent dir to look at.
     * @return The max id of all files found in a dir or 0 if non found.
     */
    public static long getMaxId(final Path parentDir) {
        return getFileId(parentDir, (num, current) -> num > current);
    }

    private static long getFileId(final Path path, final BiFunction<Long, Long, Boolean> comparator) {
        // First find the depth dir.
        Optional<NumericFile> optional = getNumericFile(path, comparator);
        if (optional.isPresent()) {
            final NumericFile depthDir = optional.get();
            NumericFile parent = depthDir;

            // Get the max file.
            for (int i = 0; i <= depthDir.num; i++) {
                optional = getNumericFile(parent.dir, comparator);
                if (optional.isPresent()) {
                    parent = optional.get();
                } else {
                    break;
                }
            }

            return parent.num;
        }

        return 0;
    }

    private static Optional<NumericFile> getNumericFile(final Path path,
                                                        final BiFunction<Long, Long, Boolean> comparator) {
        final AtomicReference<NumericFile> result = new AtomicReference<>();
        try (final Stream<Path> stream = Files.list(path)) {
            stream.forEach(file -> {
                // Parse numeric part of dir.
                final Long num = parse(file.getFileName().toString());

                // If this is the biggest num we have seen then set it and remember the dir.
                final NumericFile current = result.get();
                if (num != null && (current == null || comparator.apply(num, current.num))) {
                    result.set(new NumericFile(file, num));
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return Optional.ofNullable(result.get());
    }

    private record NumericFile(Path dir, long num) {

    }
}

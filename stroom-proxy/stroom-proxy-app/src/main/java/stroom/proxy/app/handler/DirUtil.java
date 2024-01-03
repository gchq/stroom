package stroom.proxy.app.handler;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.StringIdUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DirUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DirUtil.class);

    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");

    public static Path createPath(final Path root,
                                  final long id) {
        // Convert the id to a padded string.
        final String idString = StringIdUtil.idToString(id);
        Path dir = root;

        // Create sub dirs.
        // Add depth.
        final int depth = (idString.length() / 3) - 1;
        dir = dir.resolve(Integer.toString(depth));

        // Add dirs from parts of id string.
        for (int i = 0; i < idString.length() - 3; i += 3) {
            dir = dir.resolve(idString.substring(i, i + 3));
        }

        dir = dir.resolve(idString);

        return dir;
    }

    /**
     * Get the min id of any numerically named file found in the supplied dir.
     *
     * @param parentDir The parent dir to look at.
     * @return The min id of all files found in a dir or 0 if non found.
     */
    public static long getMinDirId(final Path parentDir) {
        return getDirId(parentDir, (num, current) -> num < current);
    }

    /**
     * Get the max id of any numerically named file found in the supplied dir.
     *
     * @param parentDir The parent dir to look at.
     * @return The max id of all files found in a dir or 0 if non found.
     */
    public static long getMaxDirId(final Path parentDir) {
        return getDirId(parentDir, (num, current) -> num > current);
    }

    private static long getDirId(final Path path, final BiFunction<Long, Long, Boolean> comparator) {
        // First find the depth dir.
        Optional<DirId> optional = findDir(path, comparator);
        if (optional.isPresent()) {
            final DirId depthDir = optional.get();
            DirId parent = depthDir;

            // Get the min/max dir id.
            for (int i = 0; i <= depthDir.num; i++) {
                optional = findDir(parent.dir, comparator);
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

    public static void ensureDirExists(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            LOGGER.error(() -> "Error creating directories for: " + FileUtil.getCanonicalPath(path), e);
            throw new UncheckedIOException(e);
        }
    }

    public static String makeSafeName(final String string) {
        return SAFE_NAME_PATTERN.matcher(string).replaceAll("_");
    }

    private record DirId(Path dir, long num) {

    }
}
